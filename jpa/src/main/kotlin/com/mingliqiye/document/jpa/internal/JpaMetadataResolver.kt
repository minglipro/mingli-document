package com.mingliqiye.document.jpa.internal

import com.mingliqiye.document.jpa.JpaAccessType
import com.mingliqiye.document.jpa.JpaConfig
import com.mingliqiye.document.jpa.exception.JpaConfigException
import jakarta.persistence.Access
import jakarta.persistence.AccessType
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embedded
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Temporal
import jakarta.persistence.Transient
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
 */
internal class JpaMetadataResolver(private val config: JpaConfig) {

    private val cache = ConcurrentHashMap<Class<*>, JpaEntityMetadata>()

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
        )
    }

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
            if (Collection::class.java.isAssignableFrom(declaredType) || declaredType.isArray) {
                throw JpaConfigException(
                    "Embedded property '${ownerClass.name}.$propertyName' must not be a collection or array",
                )
            }
            if (hasAssociationAnnotation(source)) {
                throw JpaConfigException(
                    "Property '${ownerClass.name}.$propertyName' must not mix @Embedded/@EmbeddedId " +
                        "with association annotations",
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
            if (Map::class.java.isAssignableFrom(declaredType)) {
                throw JpaConfigException(
                    "Map association property '${ownerClass.name}.$propertyName' is not supported",
                )
            }
            if (declaredType.isArray) {
                throw JpaConfigException(
                    "Array association property '${ownerClass.name}.$propertyName' is not supported",
                )
            }
            val collection = Collection::class.java.isAssignableFrom(declaredType)
            if (associationKind == JpaAssociationKind.ONE_TO_MANY || associationKind == JpaAssociationKind.MANY_TO_MANY) {
                if (!collection) {
                    throw JpaConfigException(
                        "Collection association '${ownerClass.name}.$propertyName' must be a Collection",
                    )
                }
            } else if (associationKind != null && collection) {
                throw JpaConfigException(
                    "Single-valued association '${ownerClass.name}.$propertyName' must not be a Collection",
                )
            }
            if (elementCollection != null && !collection) {
                throw JpaConfigException(
                    "@ElementCollection property '${ownerClass.name}.$propertyName' must be a Collection",
                )
            }
            val targetEntity = targetEntityOf(oneToMany, manyToMany, oneToOne, manyToOne, elementCollection)
            val elementType = resolveElementType(propertyName, declaredType, genericType, targetEntity, ownerClass)
            if (elementType == null) {
                throw JpaConfigException(
                    "Collection property '${ownerClass.name}.$propertyName' requires a generic element type " +
                        "or targetEntity",
                )
            }
            val optional = when {
                manyToOne != null -> manyToOne.optional
                oneToOne != null -> oneToOne.optional
                else -> true
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
                enumType = source.annotation(Enumerated::class.java)?.value,
                temporalType = source.annotation(Temporal::class.java)?.value,
                associationKind = associationKind,
                elementType = elementType,
                transientProperty = false,
            )
        }

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
            enumType = source.annotation(Enumerated::class.java)?.value,
            temporalType = source.annotation(Temporal::class.java)?.value,
            associationKind = null,
            elementType = null,
            transientProperty = transientProperty,
        )
    }

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

    private fun rawClassOf(type: Type?): Class<*>? = when (type) {
        null -> null
        is Class<*> -> type
        is ParameterizedType -> rawClassOf(type.rawType)
        is WildcardType -> rawClassOf(type.upperBounds.firstOrNull())
        is TypeVariable<*> -> rawClassOf(type.bounds.firstOrNull())
        else -> null
    }

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

    private fun isTransient(source: JpaAnnotationSource, modifiers: Int): Boolean = source.isPresent(Transient::class.java) || Modifier.isTransient(modifiers)

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
}
