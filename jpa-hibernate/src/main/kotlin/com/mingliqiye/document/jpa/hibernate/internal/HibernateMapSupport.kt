package com.mingliqiye.document.jpa.hibernate.internal

import com.mingliqiye.document.jpa.JpaMapKeys
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaMappingException
import jakarta.persistence.EnumType
import jakarta.persistence.MapKeyEnumerated
import jakarta.persistence.MapKeyTemporal
import jakarta.persistence.TemporalType
import jakarta.persistence.metamodel.Attribute
import jakarta.persistence.metamodel.EntityType
import java.lang.reflect.AnnotatedElement
import java.util.LinkedHashMap
import java.util.TreeMap

/**
 * 读取 Map 关联属性的实体键类型（`@MapKeyJoinColumn`）。
 *
 * @return 键实体类；键不是实体引用时返回 `null`
 */
internal fun mapKeyEntityClass(attribute: Attribute<*, *>): Class<*>? {
    val map = attribute as? jakarta.persistence.metamodel.MapAttribute<*, *, *> ?: return null
    return (map.keyType as? EntityType<*>)?.javaType
}

/**
 * 读取 Map 关联属性上的 `@MapKeyEnumerated`。
 *
 * @return 枚举键策略；未标注时返回 `null`（JPA 默认 ORDINAL）
 */
internal fun mapKeyEnumType(attribute: Attribute<*, *>): EnumType? = annotationOf(attribute, MapKeyEnumerated::class.java)?.value

/**
 * 读取 Map 关联属性上的 `@MapKeyTemporal`。
 *
 * @return 日期键策略；未标注时返回 `null`（按目标日期类型的自然精度）
 */
internal fun mapKeyTemporalType(attribute: Attribute<*, *>): TemporalType? = annotationOf(attribute, MapKeyTemporal::class.java)?.value

/**
 * Map 键 -> 文档对象字段名，复用 jpa 模块 [JpaMapKeys] 的规则（与
 * `JpaDocumentMapper` 的 Map 映射完全一致）。
 */
internal fun mapKeyFieldName(attribute: Attribute<*, *>, key: Any?, path: String): String = JpaMapKeys.toFieldName(
    key,
    mapKeyType(attribute, path),
    mapKeyEnumType(attribute),
    mapKeyTemporalType(attribute),
    path,
)

/**
 * 文档对象字段名 -> Map 键，复用 jpa 模块 [JpaMapKeys] 的规则。
 */
internal fun mapKeyValue(attribute: Attribute<*, *>, text: String, path: String): Any = JpaMapKeys.fromFieldName(
    text,
    mapKeyType(attribute, path),
    mapKeyEnumType(attribute),
    mapKeyTemporalType(attribute),
    path,
)

private fun mapKeyType(attribute: Attribute<*, *>, path: String): Class<*> {
    val keyType = (attribute as? jakarta.persistence.metamodel.MapAttribute<*, *, *>)?.keyJavaType
        ?: throw HibernateJpaMappingException("Attribute '${attribute.name}' is not a Map attribute at $path")
    if (keyType == Any::class.java) {
        throw HibernateJpaMappingException(
            "Cannot determine the Map key type of attribute '${attribute.name}' at $path",
        )
    }
    return keyType
}

/**
 * 创建 Map 实例：`SortedMap` -> `TreeMap`、其他接口 -> `LinkedHashMap`、
 * 具体类型使用无参构造器。
 */
internal fun newMapInstance(declaredType: Class<*>, path: String): MutableMap<Any?, Any?> {
    if (declaredType.isInterface) {
        return if (java.util.SortedMap::class.java.isAssignableFrom(declaredType)) TreeMap() else LinkedHashMap()
    }
    val instance = try {
        declaredType.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
    } catch (e: ReflectiveOperationException) {
        throw HibernateJpaMappingException("Cannot instantiate map type ${declaredType.name} at $path", e)
    } catch (e: IllegalArgumentException) {
        throw HibernateJpaMappingException("Cannot instantiate map type ${declaredType.name} at $path", e)
    }
    @Suppress("UNCHECKED_CAST")
    return instance as? MutableMap<Any?, Any?>
        ?: throw HibernateJpaMappingException("Type ${declaredType.name} is not a Map at $path")
}

private fun <A : Annotation> annotationOf(attribute: Attribute<*, *>, type: Class<A>): A? {
    val member = attribute.javaMember
    return (member as? AnnotatedElement)?.getAnnotation(type)
}
