package com.mingliqiye.document.jpa.internal

import com.mingliqiye.document.jpa.JpaAccessType
import com.mingliqiye.document.jpa.JpaConfig
import com.mingliqiye.document.jpa.exception.JpaConfigException
import jakarta.persistence.Access
import jakarta.persistence.AccessType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embedded
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapKey
import jakarta.persistence.MapKeyClass
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.MapKeyEnumerated
import jakarta.persistence.MapKeyTemporal
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Temporal
import jakarta.persistence.TemporalType
import jakarta.persistence.Transient
import jakarta.persistence.Version
import java.beans.Introspector
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.concurrent.ConcurrentHashMap

/**
 * 根据 JPA 注解构建实体元数据，并按类缓存。
 *
 * 只读取类/字段/getter 上的注解，不触发任何 Hibernate 运行期行为。
 *
 * 本解析器还负责：
 * - 解析 `@Convert`/autoApply `AttributeConverter`（转换器类必须显式注册 autoApply）；
 * - 解析 `@Inheritance`/`@DiscriminatorColumn`/`@DiscriminatorValue` 继承层次；
 * - 解析 Map 关联/Map `@ElementCollection` 的键类型与值类型。
 */
internal class JpaMetadataResolver(private val config: JpaConfig) {

    private val cache = ConcurrentHashMap<Class<*>, JpaEntityMetadata>()

    private val converterCache = ConcurrentHashMap<Class<*>, JpaAttributeConverter>()

    /** 已注册的 autoApply 转换器，按注册顺序；同一属性类型只允许一个。 */
    private val autoApplyConverters: List<JpaAttributeConverter> = createAutoApplyConverters()

    private val inheritanceCache = ConcurrentHashMap<Class<*>, Any>()

    private val noInheritance = Any()

    private val candidatesCache = ConcurrentHashMap<Class<*>, Map<String, Class<*>>>()

    /**
     * 解析并缓存指定类的映射元数据。
     *
     * @param type 实体类或嵌入类；Hibernate 代理类会解析到其 `@Entity` 父类
     */
    fun resolve(type: Class<*>): JpaEntityMetadata = cache.computeIfAbsent(type) { build(it) }

    /**
     * 在类层次中查找 `@Entity` 注解所在的类。
     *
     * @param type 起始类
     * @return `@Entity` 类；没有时返回 `null`
     */
    fun findEntityClass(type: Class<*>): Class<*>? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            if (current.isAnnotationPresent(Entity::class.java)) {
                return current
            }
            current = current.superclass
        }
        return null
    }

    /**
     * 返回类在 JPA 继承层次中的 discriminator 元数据。
     *
     * 类层次中没有 `@Inheritance` 也没有 `@DiscriminatorColumn` 时返回 `null`，
     * 此时文档对象不包含 discriminator 字段（与旧行为一致）。
     *
     * @param type 实体类（可以是子类或声明类型）
     */
    fun inheritanceOf(type: Class<*>): JpaInheritanceMetadata? {
        val cached = inheritanceCache.computeIfAbsent(type) { buildInheritance(it) ?: noInheritance }
        return if (cached === noInheritance) null else cached as JpaInheritanceMetadata
    }

    /**
     * 读入声明类型为 [declaredType] 的文档时，可选的 discriminator 值到具体类的映射。
     *
     * 候选集合 = 声明类型自身 + 配置中注册的、可赋值给声明类型的实体类；
     * 未注册子类的类层次只能在文档中没有 discriminator 时回退到声明类型。
     *
     * @param declaredType 声明类型（父类或具体类）
     */
    fun discriminatorCandidates(declaredType: Class<*>): Map<String, Class<*>> = candidatesCache.computeIfAbsent(declaredType) { buildDiscriminatorCandidates(it) }

    private fun build(given: Class<*>): JpaEntityMetadata {
        val entityClass = findEntityClass(given) ?: given
        val accessType = resolveAccessType(entityClass)
        val properties = when (accessType) {
            AccessType.FIELD -> fieldProperties(entityClass)
            AccessType.PROPERTY -> propertyProperties(entityClass)
            else -> throw JpaConfigException("Unsupported JPA access type $accessType")
        }
        val aliases = LinkedHashMap<String, JpaProperty>(properties.size * 3)
        for (property in properties) {
            val existing = aliases[property.nodeName]
            if (existing != null) {
                throw JpaConfigException(
                    "Duplicate document field name '${property.nodeName}' in ${entityClass.name}: " +
                        "'${existing.name}' and '${property.name}'",
                )
            }
            aliases[property.nodeName] = property
        }
        for (property in properties) {
            if (!aliases.containsKey(property.columnName)) {
                aliases[property.columnName] = property
            }
        }
        for (property in properties) {
            if (!aliases.containsKey(property.name)) {
                aliases[property.name] = property
            }
        }
        return JpaEntityMetadata(
            entityClass = entityClass,
            entityName = entityNameOf(entityClass),
            tableName = tableNameOf(entityClass),
            properties = properties,
            aliases = aliases,
            inheritance = inheritanceOf(entityClass),
        )
    }

    // ------------------------------------------------------------------
    // 继承 / discriminator
    // ------------------------------------------------------------------

    private fun buildInheritance(given: Class<*>): JpaInheritanceMetadata? {
        val entityClass = findEntityClass(given) ?: given
        val classes = ArrayList<Class<*>>()
        var current: Class<*>? = entityClass
        while (current != null && current != Any::class.java) {
            classes.add(current)
            current = current.superclass
        }
        var root: Class<*>? = null
        for (klass in classes) {
            if (klass.isAnnotationPresent(Inheritance::class.java) ||
                klass.isAnnotationPresent(DiscriminatorColumn::class.java)
            ) {
                root = klass
            }
        }
        if (root == null) {
            return null
        }
        val strategy = root.getAnnotation(Inheritance::class.java)?.strategy ?: InheritanceType.SINGLE_TABLE
        val column = discriminatorColumnOf(root)
        val columnName = column?.name?.takeIf { it.isNotEmpty() } ?: DEFAULT_DISCRIMINATOR_COLUMN
        val discriminatorType = column?.discriminatorType ?: DiscriminatorType.STRING
        return JpaInheritanceMetadata(
            declaredClass = entityClass,
            rootClass = root,
            strategy = strategy,
            discriminatorColumnName = columnName,
            discriminatorNodeName = config.namingStrategy.translate(columnName),
            discriminatorType = discriminatorType,
            discriminatorValue = discriminatorValueOf(entityClass, discriminatorType),
        )
    }

    private fun discriminatorColumnOf(root: Class<*>): DiscriminatorColumn? {
        var current: Class<*>? = root
        while (current != null && current != Any::class.java) {
            val column = current.getAnnotation(DiscriminatorColumn::class.java)
            if (column != null) {
                return column
            }
            current = current.superclass
        }
        return null
    }

    private fun discriminatorValueOf(entityClass: Class<*>, discriminatorType: DiscriminatorType): String? {
        var current: Class<*>? = entityClass
        while (current != null && current != Any::class.java) {
            val annotation = current.getAnnotation(DiscriminatorValue::class.java)
            if (annotation != null) {
                val value = annotation.value
                if (discriminatorType == DiscriminatorType.INTEGER) {
                    value.toIntOrNull() ?: throw JpaConfigException(
                        "@DiscriminatorValue('$value') on ${current.name} must be an integer " +
                            "for DiscriminatorType.INTEGER",
                    )
                }
                return value
            }
            current = current.superclass
        }
        if (discriminatorType == DiscriminatorType.INTEGER) {
            // JPA 对 INTEGER discriminator 的默认值由提供者决定；本模块要求显式标注。
            return null
        }
        return entityNameOf(entityClass)
    }

    private fun buildDiscriminatorCandidates(declaredType: Class<*>): Map<String, Class<*>> {
        val classes = LinkedHashSet<Class<*>>()
        classes.add(declaredType)
        for (entityType in config.entityTypes) {
            if (declaredType.isAssignableFrom(entityType)) {
                classes.add(entityType)
            }
        }
        val candidates = LinkedHashMap<String, Class<*>>()
        for (candidate in classes) {
            val inheritance = inheritanceOf(candidate) ?: continue
            val value = inheritance.discriminatorValue ?: continue
            val existing = candidates[value]
            if (existing != null && existing != candidate) {
                throw JpaConfigException(
                    "Duplicate discriminator value '$value' in ${inheritance.rootClass.name}: " +
                        "${existing.name} and ${candidate.name}",
                )
            }
            candidates[value] = candidate
        }
        return candidates
    }

    // ------------------------------------------------------------------
    // 属性访问策略
    // ------------------------------------------------------------------

    private fun resolveAccessType(entityClass: Class<*>): AccessType {
        when (config.accessType) {
            JpaAccessType.FIELD -> return AccessType.FIELD
            JpaAccessType.PROPERTY -> return AccessType.PROPERTY
            JpaAccessType.AUTO -> Unit
        }
        for (klass in hierarchy(entityClass)) {
            klass.getAnnotation(Access::class.java)?.let { return it.value }
        }
        for (klass in hierarchy(entityClass)) {
            for (method in klass.declaredMethods) {
                if (method.isAnnotationPresent(Id::class.java) && getterPropertyName(method) != null) {
                    return AccessType.PROPERTY
                }
            }
        }
        return AccessType.FIELD
    }

    /**
     * 返回参与映射的类层次：从根（父类）到当前类。
     *
     * 当前类本身始终参与；父类只有在 `@Entity`/`@MappedSuperclass` 标注时才
     * 继续向上收集，避免把非持久化基类的字段当成列。
     */
    private fun hierarchy(type: Class<*>): List<Class<*>> {
        val classes = ArrayList<Class<*>>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            if (current !== type && !isPersistentClass(current)) {
                break
            }
            classes.add(current)
            current = current.superclass
        }
        classes.reverse()
        return classes
    }

    private fun isPersistentClass(type: Class<*>): Boolean = type.isAnnotationPresent(Entity::class.java) ||
        type.isAnnotationPresent(MappedSuperclass::class.java)

    private fun fieldProperties(entityClass: Class<*>): List<JpaProperty> {
        val properties = ArrayList<JpaProperty>()
        val names = HashSet<String>()
        for (klass in hierarchy(entityClass)) {
            for (field in klass.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic) {
                    continue
                }
                if (!names.add(field.name)) {
                    continue
                }
                val getter = findGetter(entityClass, field.name, field.type)
                val source = JpaAnnotationSource(field, getter)
                if (isTransient(source, field.modifiers) && !config.includeTransient) {
                    continue
                }
                val setter = findSetter(entityClass, field.name, field.type)
                properties.add(
                    buildProperty(field.name, field.type, field.genericType, field, getter, setter, source, entityClass),
                )
            }
        }
        return properties
    }

    private fun propertyProperties(entityClass: Class<*>): List<JpaProperty> {
        val properties = LinkedHashMap<String, JpaProperty>()
        for (klass in hierarchy(entityClass)) {
            for (method in klass.declaredMethods) {
                if (method.isSynthetic || Modifier.isStatic(method.modifiers)) {
                    continue
                }
                if (Modifier.isPrivate(method.modifiers)) {
                    continue
                }
                val propertyName = getterPropertyName(method) ?: continue
                val field = findField(entityClass, propertyName)
                val source = JpaAnnotationSource(method, field)
                val modifiers = field?.modifiers ?: 0
                if (isTransient(source, modifiers) && !config.includeTransient) {
                    continue
                }
                val setter = findSetter(entityClass, propertyName, method.returnType)
                val genericType = field?.genericType ?: method.genericReturnType
                properties[propertyName] = buildProperty(
                    propertyName,
                    method.returnType,
                    genericType,
                    null,
                    method,
                    setter,
                    source,
                    entityClass,
                )
            }
        }
        return properties.values.toList()
    }

    private fun buildProperty(
        propertyName: String,
        declaredType: Class<*>,
        genericType: Type,
        field: Field?,
        getter: Method?,
        setter: Method?,
        source: JpaAnnotationSource,
        ownerClass: Class<*>,
    ): JpaProperty {
        val column = source.annotation(Column::class.java)
        val id = source.isPresent(Id::class.java)
        val embeddedId = source.isPresent(EmbeddedId::class.java)
        val transientProperty = source.isPresent(Transient::class.java) ||
            field?.let { Modifier.isTransient(it.modifiers) } == true

        if (source.isPresent(Embedded::class.java) || embeddedId) {
            if (Collection::class.java.isAssignableFrom(declaredType) ||
                declaredType.isArray ||
                Map::class.java.isAssignableFrom(declaredType)
            ) {
                throw JpaConfigException(
                    "Embedded property '${ownerClass.name}.$propertyName' must not be a collection, " +
                        "map or array",
                )
            }
            if (hasAssociationAnnotation(source)) {
                throw JpaConfigException(
                    "Property '${ownerClass.name}.$propertyName' must not mix @Embedded/@EmbeddedId " +
                        "with association annotations",
                )
            }
            if (hasConvertAnnotation(source)) {
                throw JpaConfigException(
                    "@Convert on embedded property '${ownerClass.name}.$propertyName' is not supported; " +
                        "annotate the converter on the embeddable's basic attributes instead",
                )
            }
            return JpaProperty(
                name = propertyName,
                nodeName = config.namingStrategy.translate(propertyName),
                columnName = column?.name?.takeIf { it.isNotEmpty() } ?: propertyName,
                kind = JpaPropertyKind.EMBEDDED,
                declaredType = declaredType,
                genericType = genericType,
                field = field,
                getter = getter,
                setter = setter,
                id = id || embeddedId,
                embeddedId = embeddedId,
                nullable = column?.nullable ?: true,
                enumType = null,
                temporalType = null,
                associationKind = null,
                elementType = null,
                transientProperty = false,
            )
        }

        val oneToMany = source.annotation(OneToMany::class.java)
        val manyToMany = source.annotation(ManyToMany::class.java)
        val oneToOne = source.annotation(OneToOne::class.java)
        val manyToOne = source.annotation(ManyToOne::class.java)
        val elementCollection = source.annotation(ElementCollection::class.java)
        val associationKind = when {
            oneToMany != null -> JpaAssociationKind.ONE_TO_MANY
            manyToMany != null -> JpaAssociationKind.MANY_TO_MANY
            oneToOne != null -> JpaAssociationKind.ONE_TO_ONE
            manyToOne != null -> JpaAssociationKind.MANY_TO_ONE
            else -> null
        }
        if (associationKind != null || elementCollection != null) {
            return buildContainerProperty(
                propertyName,
                declaredType,
                genericType,
                field,
                getter,
                setter,
                source,
                ownerClass,
                associationKind,
                elementCollection,
                oneToMany,
                manyToMany,
                oneToOne,
                manyToOne,
                id,
                transientProperty,
            )
        }

        val enumType = source.annotation(Enumerated::class.java)?.value
        val temporalType = source.annotation(Temporal::class.java)?.value
        val converter = resolveAttributeConverter(
            source,
            declaredType,
            id,
            enumType,
            temporalType,
            ownerClass,
            propertyName,
        )
        val columnName = column?.name?.takeIf { it.isNotEmpty() } ?: propertyName
        return JpaProperty(
            name = propertyName,
            nodeName = config.namingStrategy.translate(columnName),
            columnName = columnName,
            kind = JpaPropertyKind.SCALAR,
            declaredType = declaredType,
            genericType = genericType,
            field = field,
            getter = getter,
            setter = setter,
            id = id,
            embeddedId = false,
            nullable = column?.nullable ?: !declaredType.isPrimitive,
            enumType = enumType,
            temporalType = temporalType,
            associationKind = null,
            elementType = null,
            transientProperty = transientProperty,
            converter = converter,
        )
    }

    private fun buildContainerProperty(
        propertyName: String,
        declaredType: Class<*>,
        genericType: Type,
        field: Field?,
        getter: Method?,
        setter: Method?,
        source: JpaAnnotationSource,
        ownerClass: Class<*>,
        associationKind: JpaAssociationKind?,
        elementCollection: ElementCollection?,
        oneToMany: OneToMany?,
        manyToMany: ManyToMany?,
        oneToOne: OneToOne?,
        manyToOne: ManyToOne?,
        id: Boolean,
        transientProperty: Boolean,
    ): JpaProperty {
        if (hasConvertAnnotation(source) && associationKind != null) {
            throw JpaConfigException(
                "@Convert on relationship attribute '${ownerClass.name}.$propertyName' is not supported; " +
                    "converters apply to basic attributes and element collections of basic type only",
            )
        }
        if (declaredType.isArray) {
            throw JpaConfigException("Array association property '${ownerClass.name}.$propertyName' is not supported")
        }
        val map = Map::class.java.isAssignableFrom(declaredType)
        val collection = Collection::class.java.isAssignableFrom(declaredType)
        if (associationKind == JpaAssociationKind.ONE_TO_MANY || associationKind == JpaAssociationKind.MANY_TO_MANY) {
            if (!collection && !map) {
                throw JpaConfigException(
                    "Collection association '${ownerClass.name}.$propertyName' must be a Collection or Map",
                )
            }
        } else if (associationKind == JpaAssociationKind.ONE_TO_ONE || associationKind == JpaAssociationKind.MANY_TO_ONE) {
            if (collection || map) {
                throw JpaConfigException(
                    "Single-valued association '${ownerClass.name}.$propertyName' must not be a Collection or Map",
                )
            }
        }
        if (elementCollection != null && !collection && !map) {
            throw JpaConfigException(
                "@ElementCollection property '${ownerClass.name}.$propertyName' must be a Collection or Map",
            )
        }
        val targetEntity = targetEntityOf(oneToMany, manyToMany, oneToOne, manyToOne, elementCollection)
        val valueType = if (map) {
            resolveMapValueType(propertyName, genericType, targetEntity, ownerClass)
        } else {
            val elementType = resolveElementType(propertyName, declaredType, genericType, targetEntity, ownerClass)
            if (elementType == null) {
                throw JpaConfigException(
                    "Collection property '${ownerClass.name}.$propertyName' requires a generic element type " +
                        "or targetEntity",
                )
            }
            elementType
        }
        val mapKey = source.annotation(MapKey::class.java)
        val mapKeyClass = source.annotation(MapKeyClass::class.java)
        val mapKeyColumn = source.annotation(MapKeyColumn::class.java)
        val mapKeyEnumerated = source.annotation(MapKeyEnumerated::class.java)?.value
        val mapKeyTemporal = source.annotation(MapKeyTemporal::class.java)?.value
        if (!map &&
            (
                mapKey != null ||
                    mapKeyClass != null ||
                    mapKeyColumn != null ||
                    mapKeyEnumerated != null ||
                    mapKeyTemporal != null
                )
        ) {
            throw JpaConfigException(
                "Map key annotations are only supported on Map properties: " +
                    "'${ownerClass.name}.$propertyName' is not a Map",
            )
        }
        if (map && elementCollection != null && mapKey != null) {
            throw JpaConfigException(
                "@MapKey is not supported on @ElementCollection property '${ownerClass.name}.$propertyName'; " +
                    "use @MapKeyColumn for basic map keys",
            )
        }
        val mapKeyType = if (map) {
            resolveMapKeyType(propertyName, genericType, mapKey, mapKeyClass, ownerClass)
        } else {
            null
        }
        val optional = when {
            manyToOne != null -> manyToOne.optional
            oneToOne != null -> oneToOne.optional
            else -> true
        }
        val enumType = source.annotation(Enumerated::class.java)?.value
        val temporalType = source.annotation(Temporal::class.java)?.value
        val elementConverter = if (elementCollection != null) {
            resolveAttributeConverter(
                source,
                valueType,
                false,
                enumType,
                temporalType,
                ownerClass,
                propertyName,
            )
        } else {
            null
        }
        return JpaProperty(
            name = propertyName,
            nodeName = config.namingStrategy.translate(propertyName),
            columnName = propertyName,
            kind = if (associationKind == null) JpaPropertyKind.ELEMENT_COLLECTION else JpaPropertyKind.ASSOCIATION,
            declaredType = declaredType,
            genericType = genericType,
            field = field,
            getter = getter,
            setter = setter,
            id = id,
            embeddedId = false,
            nullable = optional,
            enumType = enumType,
            temporalType = temporalType,
            associationKind = associationKind,
            elementType = valueType,
            transientProperty = transientProperty,
            converter = elementConverter,
            map = map,
            mapKeyType = mapKeyType,
            mapKeyEnumerated = mapKeyEnumerated,
            mapKeyTemporal = mapKeyTemporal,
            mapKeyPropertyName = mapKey?.name,
        )
    }

    // ------------------------------------------------------------------
    // 类型解析
    // ------------------------------------------------------------------

    private fun hasAssociationAnnotation(source: JpaAnnotationSource): Boolean = source.isPresent(OneToMany::class.java) ||
        source.isPresent(ManyToMany::class.java) ||
        source.isPresent(OneToOne::class.java) ||
        source.isPresent(ManyToOne::class.java) ||
        source.isPresent(ElementCollection::class.java)

    private fun targetEntityOf(
        oneToMany: OneToMany?,
        manyToMany: ManyToMany?,
        oneToOne: OneToOne?,
        manyToOne: ManyToOne?,
        elementCollection: ElementCollection?,
    ): Class<*>? {
        val candidate: Class<*>? = when {
            oneToMany != null -> oneToMany.targetEntity.java
            manyToMany != null -> manyToMany.targetEntity.java
            oneToOne != null -> oneToOne.targetEntity.java
            manyToOne != null -> manyToOne.targetEntity.java
            elementCollection != null -> elementCollection.targetClass.java
            else -> null
        } ?: return null
        if (candidate == java.lang.Void.TYPE) {
            return null
        }
        return candidate
    }

    private fun resolveElementType(
        propertyName: String,
        declaredType: Class<*>,
        genericType: Type,
        targetEntity: Class<*>?,
        ownerClass: Class<*>,
    ): Class<*>? {
        if (targetEntity != null) {
            return targetEntity
        }
        if (!Collection::class.java.isAssignableFrom(declaredType)) {
            return declaredType
        }
        val parameterizedType = genericType as? ParameterizedType ?: return null
        val argument = parameterizedType.actualTypeArguments.firstOrNull() ?: return null
        return rawClassOf(argument) ?: throw JpaConfigException(
            "Cannot resolve element type of '${ownerClass.name}.$propertyName': $argument",
        )
    }

    private fun resolveMapValueType(
        propertyName: String,
        genericType: Type,
        targetEntity: Class<*>?,
        ownerClass: Class<*>,
    ): Class<*> {
        if (targetEntity != null) {
            return targetEntity
        }
        val parameterizedType = genericType as? ParameterizedType
            ?: throw JpaConfigException(
                "Map property '${ownerClass.name}.$propertyName' requires a generic value type or targetEntity",
            )
        val argument = parameterizedType.actualTypeArguments.getOrNull(1)
            ?: throw JpaConfigException(
                "Map property '${ownerClass.name}.$propertyName' requires a generic value type or targetEntity",
            )
        return rawClassOf(argument) ?: throw JpaConfigException(
            "Cannot resolve map value type of '${ownerClass.name}.$propertyName': $argument",
        )
    }

    private fun resolveMapKeyType(
        propertyName: String,
        genericType: Type,
        mapKey: MapKey?,
        mapKeyClass: MapKeyClass?,
        ownerClass: Class<*>,
    ): Class<*>? {
        val declaredKeyClass = mapKeyClass?.value?.java
        if (declaredKeyClass != null && declaredKeyClass != java.lang.Void.TYPE) {
            validateMapKeyType(propertyName, declaredKeyClass, ownerClass)
            return declaredKeyClass
        }
        val parameterizedType = genericType as? ParameterizedType
        val argument = parameterizedType?.actualTypeArguments?.getOrNull(0)
        val keyType = rawClassOf(argument)
        if (keyType != null && keyType != Any::class.java) {
            validateMapKeyType(propertyName, keyType, ownerClass)
            return keyType
        }
        if (mapKey != null) {
            // @MapKey：键类型由目标实体的主键/指定属性决定，映射时延迟解析。
            return null
        }
        throw JpaConfigException(
            "Cannot determine the key type of Map property '${ownerClass.name}.$propertyName'; " +
                "declare Map<K, V>, @MapKeyClass or @MapKey",
        )
    }

    private fun validateMapKeyType(propertyName: String, keyType: Class<*>, ownerClass: Class<*>) {
        val supported = isScalarValueType(keyType) &&
            !keyType.isArray &&
            !Collection::class.java.isAssignableFrom(keyType) &&
            !Map::class.java.isAssignableFrom(keyType) &&
            !Entity::class.java.isAssignableFrom(keyType)
        if (!supported) {
            throw JpaConfigException(
                "Unsupported Map key type ${keyType.name} for '${ownerClass.name}.$propertyName'; " +
                    "map keys must be basic types (String/number/boolean/char/enum/date-time/UUID)",
            )
        }
    }

    private fun rawClassOf(type: Type?): Class<*>? = when (type) {
        null -> null
        is Class<*> -> type
        is ParameterizedType -> rawClassOf(type.rawType)
        is WildcardType -> rawClassOf(type.upperBounds.firstOrNull())
        is TypeVariable<*> -> rawClassOf(type.bounds.firstOrNull())
        else -> null
    }

    // ------------------------------------------------------------------
    // @Convert / autoApply 转换器
    // ------------------------------------------------------------------

    private fun createAutoApplyConverters(): List<JpaAttributeConverter> {
        if (config.autoApplyConverters.isEmpty()) {
            return emptyList()
        }
        val converters = ArrayList<JpaAttributeConverter>(config.autoApplyConverters.size)
        val seen = HashSet<Class<*>>()
        for (converterClass in config.autoApplyConverters) {
            if (!seen.add(converterClass)) {
                continue
            }
            converters.add(converterFor(converterClass))
        }
        val duplicates = converters.groupBy { boxedType(it.entityType) }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            val entry = duplicates.entries.first()
            throw JpaConfigException(
                "Multiple auto-apply attribute converters registered for ${entry.key.name}: " +
                    entry.value.joinToString(", ") { it.converterClass.name },
            )
        }
        return converters
    }

    private fun converterFor(converterClass: Class<*>): JpaAttributeConverter = converterCache.computeIfAbsent(converterClass) {
        JpaAttributeConverter.of(it, "autoApply converter")
    }

    private fun hasConvertAnnotation(source: JpaAnnotationSource): Boolean = source.annotations(Convert::class.java).isNotEmpty()

    private fun resolveAttributeConverter(
        source: JpaAnnotationSource,
        attributeType: Class<*>,
        id: Boolean,
        enumType: EnumType?,
        temporalType: TemporalType?,
        ownerClass: Class<*>,
        propertyName: String,
    ): JpaAttributeConverter? {
        val convertAnnotations = source.annotations(Convert::class.java)
        val forward = convertAnnotations.filter { it.attributeName.isEmpty() }
        if (forward.size > 1) {
            throw JpaConfigException(
                "Property '${ownerClass.name}.$propertyName' declares multiple @Convert annotations",
            )
        }
        val convert = forward.firstOrNull()
        if (convert != null) {
            if (convert.disableConversion) {
                return null
            }
            if (convert.converter.java == java.lang.Void.TYPE) {
                throw JpaConfigException(
                    "@Convert on '${ownerClass.name}.$propertyName' must specify a converter class " +
                        "or disableConversion = true",
                )
            }
            val converter = converterFor(convert.converter.java)
            if (!converter.appliesTo(attributeType)) {
                throw JpaConfigException(
                    "Attribute converter ${converter.converterClass.name} converts " +
                        "${converter.entityType.name} but property '${ownerClass.name}.$propertyName' " +
                        "is declared as ${attributeType.name}",
                )
            }
            return converter
        }
        val unsupportedOverride = convertAnnotations.firstOrNull { it.attributeName.isNotEmpty() }
        if (unsupportedOverride != null) {
            throw JpaConfigException(
                "@Convert(attributeName='${unsupportedOverride.attributeName}') on " +
                    "'${ownerClass.name}.$propertyName' is not supported; annotate the embedded type's " +
                    "basic attributes instead",
            )
        }
        if (id || enumType != null || temporalType != null || source.isPresent(Version::class.java)) {
            // JPA 规范：@Id/@EmbeddedId、@Enumerated、@Temporal、@Version 不参与 autoApply。
            return null
        }
        return autoApplyConverters.firstOrNull { it.appliesTo(attributeType) }
    }

    // ------------------------------------------------------------------
    // 反射工具
    // ------------------------------------------------------------------

    private fun getterPropertyName(method: Method): String? {
        if (method.parameterCount != 0) {
            return null
        }
        if (method.returnType == java.lang.Void.TYPE) {
            return null
        }
        val name = method.name
        if (name.length > 3 && name.startsWith("get") && name != "getClass") {
            return Introspector.decapitalize(name.substring(3))
        }
        val booleanGetter = method.returnType == Boolean::class.javaPrimitiveType ||
            method.returnType == Boolean::class.javaObjectType
        if (name.length > 2 && name.startsWith("is") && booleanGetter) {
            return Introspector.decapitalize(name.substring(2))
        }
        return null
    }

    private fun findGetter(entityClass: Class<*>, propertyName: String, fieldType: Class<*>): Method? {
        val capitalized = capitalize(propertyName)
        val candidates = setOf("get$capitalized", "is$capitalized")
        for (method in allMethods(entityClass)) {
            if (method.parameterCount != 0 || Modifier.isStatic(method.modifiers)) {
                continue
            }
            if (method.name !in candidates) {
                continue
            }
            if (isAssignableEitherWay(method.returnType, fieldType)) {
                return method
            }
        }
        return null
    }

    private fun findSetter(entityClass: Class<*>, propertyName: String, valueType: Class<*>): Method? {
        val name = "set" + capitalize(propertyName)
        for (method in allMethods(entityClass)) {
            if (method.name != name || method.parameterCount != 1) {
                continue
            }
            val parameterType = method.parameterTypes[0]
            if (isAssignableEitherWay(parameterType, valueType)) {
                return method
            }
        }
        return null
    }

    private fun findField(entityClass: Class<*>, propertyName: String): Field? {
        for (klass in hierarchy(entityClass)) {
            try {
                return klass.getDeclaredField(propertyName)
            } catch (_: NoSuchFieldException) {
                // 继续向父类查找。
            }
        }
        return null
    }

    private fun allMethods(entityClass: Class<*>): List<Method> {
        val methods = ArrayList<Method>()
        for (klass in hierarchy(entityClass)) {
            methods.addAll(klass.declaredMethods)
        }
        return methods
    }

    private fun isAssignableEitherWay(left: Class<*>, right: Class<*>): Boolean = left == right ||
        left.isAssignableFrom(right) ||
        right.isAssignableFrom(left)

    private fun capitalize(name: String): String = name.replaceFirstChar { it.uppercaseChar() }

    private fun isTransient(source: JpaAnnotationSource, modifiers: Int): Boolean = source.isPresent(Transient::class.java) ||
        Modifier.isTransient(modifiers)

    private fun entityNameOf(entityClass: Class<*>): String {
        var current: Class<*>? = entityClass
        while (current != null && current != Any::class.java) {
            val entity = current.getAnnotation(Entity::class.java)
            if (entity != null) {
                return entity.name.ifEmpty { current.simpleName }
            }
            current = current.superclass
        }
        return entityClass.simpleName
    }

    private fun tableNameOf(entityClass: Class<*>): String? {
        var current: Class<*>? = entityClass
        while (current != null && current != Any::class.java) {
            val table = current.getAnnotation(Table::class.java)
            if (table != null && table.name.isNotEmpty()) {
                return table.name
            }
            current = current.superclass
        }
        return null
    }

    private companion object {
        const val DEFAULT_DISCRIMINATOR_COLUMN: String = "DTYPE"
    }
}
