package com.mingliqiye.document.jpa.hibernate.internal

import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaMappingException
import jakarta.persistence.IdClass
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

/**
 * 返回适合 `EntityManager.find`/`Session.get` 的标识符对象。
 *
 * - 单主键（含 `@EmbeddedId`）：返回 id 属性值；`@EmbeddedId` 时即嵌入 id 实例；
 * - `@IdClass` 复合主键：以 idClass 类型新建实例并从实体的 `@Id` 属性填充；
 * - 无法解析（没有 id 属性或类型不受支持）时返回 `null`。
 *
 * @param entity 实体实例（标识符属性必须已赋值）
 * @param entityType Hibernate/JPA 元模型中的实体类型
 * @param path 当前文档路径，用于异常信息
 */
internal fun identifierValue(entity: Any, entityType: EntityType<*>, path: String): Any? {
    val singleId = singleIdAttribute(entityType)
    if (singleId != null) {
        return readAttributeValue(entity, singleId, path)
    }
    val idClassAttributes = idClassAttributesOf(entityType)
    if (idClassAttributes.isEmpty()) {
        return null
    }
    val idClassType = idClassTypeOf(entityType) ?: return null
    val idInstance = newInstanceOf(idClassType, path)
    for (attribute in idClassAttributes) {
        val value = readAttributeValue(entity, attribute, path)
        setNamedPropertyValue(idInstance, attribute.name, value, "$path.${attribute.name}")
    }
    return idInstance
}

/**
 * `@IdClass` 的 id 类型。
 *
 * Hibernate 对 IdClass 复合主键的 `EntityType.idType` 可能为 `null`，此时回退到
 * 实体类上的 `@IdClass` 注解值。
 */
internal fun idClassTypeOf(entityType: EntityType<*>): Class<*>? {
    val idType = entityType.idType
    if (idType != null) {
        val javaType = idType.javaType as? Class<*>
        if (javaType != null && javaType != entityType.javaType) {
            return javaType
        }
    }
    return entityType.javaType.getAnnotation(IdClass::class.java)?.value?.java
}

/**
 * 实体类型的单主键属性（`@EmbeddedId` 时返回嵌入 id 属性）；没有单主键时返回 `null`。
 */
internal fun singleIdAttribute(entityType: EntityType<*>): SingularAttribute<*, *>? {
    if (!entityType.hasSingleIdAttribute()) {
        return null
    }
    @Suppress("UNCHECKED_CAST")
    val idClass = entityType.idType.javaType as? Class<Any> ?: return null
    return try {
        entityType.getId(idClass)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * `@IdClass` 的多个 `@Id` 属性；不是 IdClass 复合主键时返回空集合。
 */
internal fun idClassAttributesOf(entityType: EntityType<*>): Set<SingularAttribute<*, *>> = try {
    entityType.idClassAttributes
} catch (_: IllegalArgumentException) {
    emptySet()
} catch (_: IllegalStateException) {
    emptySet()
}

/**
 * 通过无参构造器实例化类型（用于重建 `@IdClass`/`@EmbeddedId` 标识符对象）。
 */
internal fun newInstanceOf(type: Class<*>, path: String): Any {
    if (type.isInterface || Modifier.isAbstract(type.modifiers)) {
        throw HibernateJpaMappingException(
            "Identifier type ${type.name} at $path must be a concrete class",
        )
    }
    val constructor = try {
        type.getDeclaredConstructor()
    } catch (e: NoSuchMethodException) {
        throw HibernateJpaMappingException(
            "Identifier type ${type.name} must declare a no-argument constructor at $path",
            e,
        )
    }
    return try {
        constructor.isAccessible = true
        constructor.newInstance()
    } catch (e: InvocationTargetException) {
        throw HibernateJpaMappingException("Constructor of identifier type ${type.name} failed at $path", e.cause ?: e)
    } catch (e: ReflectiveOperationException) {
        throw HibernateJpaMappingException("Cannot instantiate identifier type ${type.name} at $path", e)
    } catch (e: IllegalArgumentException) {
        throw HibernateJpaMappingException("Cannot instantiate identifier type ${type.name} at $path", e)
    }
}

/**
 * 按字段名（找不到时按 setter）把值写入标识符对象。
 *
 * `@IdClass` 的 idClass 字段名与实体 `@Id` 属性名一致，JPA 规范要求属性名匹配。
 */
internal fun setNamedPropertyValue(target: Any, name: String, value: Any?, path: String) {
    var current: Class<*>? = target.javaClass
    while (current != null && current != Any::class.java) {
        val field = try {
            current.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            null
        }
        if (field != null && !Modifier.isStatic(field.modifiers)) {
            try {
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (e: ReflectiveOperationException) {
                throw HibernateJpaMappingException("Cannot write identifier component '$name' at $path", e)
            } catch (e: IllegalArgumentException) {
                throw HibernateJpaMappingException("Cannot write identifier component '$name' at $path", e)
            }
        }
        current = current.superclass
    }
    val setterName = "set" + name.replaceFirstChar { it.uppercaseChar() }
    current = target.javaClass
    while (current != null && current != Any::class.java) {
        for (method in current.declaredMethods) {
            if (method.name != setterName || method.parameterCount != 1) {
                continue
            }
            try {
                method.isAccessible = true
                method.invoke(target, value)
                return
            } catch (e: InvocationTargetException) {
                throw HibernateJpaMappingException(
                    "Setter of identifier component '$name' failed at $path",
                    e.cause ?: e,
                )
            } catch (e: ReflectiveOperationException) {
                throw HibernateJpaMappingException("Cannot write identifier component '$name' at $path", e)
            } catch (e: IllegalArgumentException) {
                throw HibernateJpaMappingException("Cannot write identifier component '$name' at $path", e)
            }
        }
        current = current.superclass
    }
    throw HibernateJpaMappingException(
        "Cannot write identifier component '$name' of ${target.javaClass.name} at $path",
    )
}
