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
import jakarta.persistence.MapKeyJoinColumn
import jakarta.persistence.MapKeyJoinColumns
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
     * 元数据构建期间会递归解析嵌入类/Map 键实体类，因此这里使用可重入的
     * `synchronized` 两段式缓存，而不是 `ConcurrentHashMap.computeIfAbsent`
     * （后者在嵌套解析不同 key 且哈希桶冲突时会抛 `Recursive update`）。
     *
     * @param type 实体类或嵌入类；Hibernate 代理类会解析到其 `@Entity` 父类
     */
    fun resolve(type: Class<*>): JpaEntityMetadata {
        cache[type]?.let { return it }
        synchronized(cache) {
            cache[type]?.let { return it }
            val metadata = build(type)
            cache[type] = metadata
            return metadata
        }
    }

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
            convertOverrides = buildConvertOverrides(entityClass, properties),
            identifier = buildIdentifier(entityClass, properties),
        )
    }

    // ------------------------------------------------------------------
    // @Convert(attributeName = ...) 覆盖规则
    // ------------------------------------------------------------------

    /**
     * 构建当前类的嵌套属性路径转换器覆盖表（键为相对本类的点分属性路径）。
     *
     * 来源与优先级（后者覆盖前者）：
     * 1. 类层次上（父类在前）类级 `@Convert(attributeName = "...")`；
     * 2. 属性级 `@Convert(attributeName = "...")`（相对该属性，做前缀展开）。
     */
    private fun buildConvertOverrides(
        entityClass: Class<*>,
        properties: List<JpaProperty>,
    ): Map<String, JpaConvertOverride> {
        val overrides = LinkedHashMap<String, JpaConvertOverride>()
        for (klass in hierarchy(entityClass)) {
            for (annotation in klass.getAnnotationsByType(Convert::class.java)) {
                val owner = "${klass.name} (class-level @Convert)"
                val path = normalizeConvertPath(annotation.attributeName, owner)
                val override = convertOverrideOf(annotation, owner)
                if (overrides.put(path, override) != null) {
                    throw JpaConfigException(
                        "Duplicate @Convert(attributeName = '$path') in ${entityClass.name}",
                    )
                }
            }
        }
        for (property in properties) {
            property.nestedConvertOverrides.forEach { (relativePath, override) ->
                val path = normalizeConvertPath("${property.name}.$relativePath", property.name)
                overrides[path] = override
            }
        }
        validateConvertOverrides(entityClass, properties, overrides)
        return overrides
    }

    private fun normalizeConvertPath(attributeName: String, owner: String): String {
        val trimmed = attributeName.trim()
        if (trimmed.isEmpty()) {
            throw JpaConfigException("@Convert on $owner must specify attributeName")
        }
        val segments = trimmed.split('.').map { it.trim() }
        if (segments.any { it.isEmpty() }) {
            throw JpaConfigException("@Convert(attributeName = '$attributeName') on $owner contains an empty path segment")
        }
        return segments.joinToString(".")
    }

    private fun convertOverrideOf(annotation: Convert, owner: String): JpaConvertOverride {
        if (annotation.disableConversion) {
            if (annotation.converter.java != java.lang.Void.TYPE) {
                throw JpaConfigException(
                    "@Convert on $owner must not specify converter together with disableConversion = true",
                )
            }
            return JpaConvertOverride(null)
        }
        if (annotation.converter.java == java.lang.Void.TYPE) {
            throw JpaConfigException(
                "@Convert on $owner must specify a converter class or disableConversion = true",
            )
        }
        return JpaConvertOverride(converterFor(annotation.converter.java))
    }

    /**
     * 校验嵌套属性路径：每段必须是当前类的属性名；中间段必须是 `@Embedded`，
     * 最后一段必须是基本属性，且覆盖转换器的属性类型必须匹配。
     */
    private fun validateConvertOverrides(
        entityClass: Class<*>,
        properties: List<JpaProperty>,
        overrides: Map<String, JpaConvertOverride>,
    ) {
        for ((path, override) in overrides) {
            var currentProperties = properties
            var currentClass: Class<*> = entityClass
            val segments = path.split('.')
            for (index in segments.indices) {
                val segment = segments[index]
                val property = currentProperties.firstOrNull { it.name == segment }
                    ?: throw JpaConfigException(
                        "Invalid @Convert(attributeName = '$path') on ${entityClass.name}: " +
                            "${currentClass.name} has no property '$segment'",
                    )
                if (index != segments.lastIndex) {
                    if (property.kind != JpaPropertyKind.EMBEDDED) {
                        throw JpaConfigException(
                            "Invalid @Convert(attributeName = '$path') on ${entityClass.name}: " +
                                "'${property.name}' is not an @Embedded property",
                        )
                    }
                    val nested = resolve(property.declaredType)
                    currentProperties = nested.properties
                    currentClass = nested.entityClass
                    continue
                }
                if (property.kind != JpaPropertyKind.SCALAR) {
                    throw JpaConfigException(
                        "Invalid @Convert(attributeName = '$path') on ${entityClass.name}: " +
                            "'${property.name}' is not a basic attribute",
                    )
                }
                val converter = override.converter ?: continue
                if (!converter.appliesTo(property.declaredType)) {
                    throw JpaConfigException(
                        "Attribute converter ${converter.converterClass.name} converts " +
                            "${converter.entityType.name} but '${currentClass.name}.${property.name}' " +
                            "is declared as ${property.declaredType.name}",
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 主键元数据
    // ------------------------------------------------------------------

    private fun buildIdentifier(entityClass: Class<*>, properties: List<JpaProperty>): JpaIdentifierMetadata? {
        val ids = properties.filter { it.id }
        if (ids.isEmpty()) {
            return null
        }
        val embeddedIds = ids.filter { it.embeddedId || it.kind == JpaPropertyKind.EMBEDDED }
        if (embeddedIds.isNotEmpty()) {
            if (ids.size != 1) {
                throw JpaConfigException(
                    "Entity ${entityClass.name} mixes @EmbeddedId with other @Id attributes",
                )
            }
            return JpaIdentifierMetadata(JpaIdentifierKind.EMBEDDED, embeddedIds, embeddedIds[0].declaredType)
        }
        if (ids.size == 1) {
            return JpaIdentifierMetadata(JpaIdentifierKind.SIMPLE, ids)
        }
        for (property in ids) {
            if (property.kind != JpaPropertyKind.SCALAR) {
                throw JpaConfigException(
                    "@IdClass identifier property ${entityClass.name}.${property.name} must be a basic attribute",
                )
            }
        }
        return JpaIdentifierMetadata(JpaIdentifierKind.ID_CLASS, ids)
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
            val nestedConvertOverrides = nestedConvertOverridesOf(source, ownerClass, propertyName)
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
                nestedConvertOverrides = nestedConvertOverrides,
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
        // 关系属性/集合属性上的 @Convert(attributeName = ...) 不支持；显式 @Convert（无 attributeName）
        // 对关系属性表示“整个关联值按基本属性转换”，对 @ElementCollection 表示元素值转换。
        for (annotation in source.annotations(Convert::class.java)) {
            if (annotation.attributeName.isNotEmpty()) {
                throw JpaConfigException(
                    "@Convert(attributeName = '${annotation.attributeName}') on " +
                        "'${ownerClass.name}.$propertyName' is not supported; attributeName 只用于 " +
                        "@Embedded/@EmbeddedId 属性内部的点分属性路径",
                )
            }
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
        val mapKeyJoinColumn = source.annotation(MapKeyJoinColumn::class.java)
        val mapKeyJoinColumns = source.annotation(MapKeyJoinColumns::class.java)
        val mapKeyEnumerated = source.annotation(MapKeyEnumerated::class.java)?.value
        val mapKeyTemporal = source.annotation(MapKeyTemporal::class.java)?.value
        if (!map &&
            (
                mapKey != null ||
                    mapKeyClass != null ||
                    mapKeyColumn != null ||
                    mapKeyJoinColumn != null ||
                    mapKeyJoinColumns != null ||
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
        val mapKeyEntity = mapKeyType != null && findEntityClass(mapKeyType) != null
        if (mapKeyType != null) {
            if (mapKeyEntity) {
                if (elementCollection != null) {
                    throw JpaConfigException(
                        "Entity Map key type ${mapKeyType.name} on '${ownerClass.name}.$propertyName' " +
                            "is not supported for @ElementCollection; entity keys require a relationship Map",
                    )
                }
                if (mapKey != null) {
                    throw JpaConfigException(
                        "@MapKey cannot be combined with an entity Map key type " +
                            "on '${ownerClass.name}.$propertyName' (entity keys use @MapKeyJoinColumn)",
                    )
                }
                if (mapKeyColumn != null || mapKeyEnumerated != null || mapKeyTemporal != null) {
                    throw JpaConfigException(
                        "Entity Map key on '${ownerClass.name}.$propertyName' only supports " +
                            "@MapKeyJoinColumn/@MapKeyJoinColumns",
                    )
                }
                if (resolve(mapKeyType).identifier == null) {
                    throw JpaConfigException(
                        "Entity Map key type ${mapKeyType.name} on '${ownerClass.name}.$propertyName' " +
                            "must declare an @Id/@EmbeddedId",
                    )
                }
            } else {
                if (mapKeyJoinColumn != null || mapKeyJoinColumns != null) {
                    throw JpaConfigException(
                        "@MapKeyJoinColumn on '${ownerClass.name}.$propertyName' requires an entity Map " +
                            "key type but the key type is ${mapKeyType.name}",
                    )
                }
                validateMapKeyType(propertyName, mapKeyType, ownerClass)
            }
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
        val associationConverter = if (associationKind != null) {
            resolveAssociationConverter(source, declaredType, ownerClass, propertyName)
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
            converter = associationConverter ?: elementConverter,
            map = map,
            mapKeyType = mapKeyType,
            mapKeyEnumerated = mapKeyEnumerated,
            mapKeyTemporal = mapKeyTemporal,
            mapKeyPropertyName = mapKey?.name,
            mapKeyEntity = mapKeyEntity,
        )
    }

    /**
     * 解析关系属性上的显式 `@Convert`（无 attributeName）。
     *
     * 语义：整个关联值按基本属性处理——写出时 `convertToDatabaseColumn` 的结果作为
     * 文档节点值，读入时 `convertToEntityAttribute` 的结果写回关联属性；不展开关联。
     * 显式 `@Convert` 优先于关联展开；autoApply 不作用于关系属性。
     */
    private fun resolveAssociationConverter(
        source: JpaAnnotationSource,
        attributeType: Class<*>,
        ownerClass: Class<*>,
        propertyName: String,
    ): JpaAttributeConverter? {
        val forward = source.annotations(Convert::class.java).filter { it.attributeName.isEmpty() }
        if (forward.isEmpty()) {
            return null
        }
        if (forward.size > 1) {
            throw JpaConfigException(
                "Relationship property '${ownerClass.name}.$propertyName' declares multiple @Convert annotations",
            )
        }
        val convert = forward[0]
        if (convert.disableConversion) {
            return null
        }
        if (convert.converter.java == java.lang.Void.TYPE) {
            throw JpaConfigException(
                "@Convert on relationship property '${ownerClass.name}.$propertyName' must specify " +
                    "a converter class or disableConversion = true",
            )
        }
        val converter = converterFor(convert.converter.java)
        if (!converter.appliesTo(attributeType)) {
            throw JpaConfigException(
                "Attribute converter ${converter.converterClass.name} converts " +
                    "${converter.entityType.name} but relationship property " +
                    "'${ownerClass.name}.$propertyName' is declared as ${attributeType.name}",
            )
        }
        return converter
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
            return declaredKeyClass
        }
        val parameterizedType = genericType as? ParameterizedType
        val argument = parameterizedType?.actualTypeArguments?.getOrNull(0)
        val keyType = rawClassOf(argument)
        if (keyType != null && keyType != Any::class.java) {
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
                    "map keys must be basic types (String/number/boolean/char/enum/date-time/UUID) " +
                    "or entity types via @MapKeyJoinColumn",
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

    /**
     * 收集 `@Embedded`/`@EmbeddedId` 属性上的 `@Convert(attributeName = ...)`：
     * 路径相对该属性（可点分），返回给外层元数据做前缀展开。
     */
    private fun nestedConvertOverridesOf(
        source: JpaAnnotationSource,
        ownerClass: Class<*>,
        propertyName: String,
    ): Map<String, JpaConvertOverride> {
        val annotations = source.annotations(Convert::class.java)
        if (annotations.isEmpty()) {
            return emptyMap()
        }
        val overrides = LinkedHashMap<String, JpaConvertOverride>()
        for (annotation in annotations) {
            if (annotation.attributeName.isEmpty()) {
                throw JpaConfigException(
                    "@Convert on embedded property '${ownerClass.name}.$propertyName' requires " +
                        "attributeName (use @Convert(attributeName = \"...\") or annotate the " +
                        "embeddable's basic attributes directly)",
                )
            }
            val owner = "${ownerClass.name}.$propertyName"
            val path = normalizeConvertPath(annotation.attributeName, owner)
            val override = convertOverrideOf(annotation, owner)
            if (overrides.put(path, override) != null) {
                throw JpaConfigException(
                    "Duplicate @Convert(attributeName = '${annotation.attributeName}') on " +
                        "'${ownerClass.name}.$propertyName'",
                )
            }
        }
        return overrides
    }

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
                    "'${ownerClass.name}.$propertyName' is not supported; attributeName 只能用于 " +
                    "@Embedded/@EmbeddedId 属性内部的基本属性路径",
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
