package com.mingliqiye.document.jpa.internal

import com.mingliqiye.document.jpa.exception.JpaMappingException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID

/**
 * JPA 集成内部共用的反射与类型判断工具。
 */

/** 是否为 `java.time` 中受支持的日期时间类型。 */
internal fun isJavaTimeType(type: Class<*>): Boolean = type == LocalDate::class.java ||
    type == LocalTime::class.java ||
    type == LocalDateTime::class.java ||
    type == Instant::class.java ||
    type == OffsetDateTime::class.java ||
    type == OffsetTime::class.java ||
    type == ZonedDateTime::class.java

/**
 * 是否为可直接作为标量值处理的类型。
 *
 * 集合元素、transient 属性等场景用该判断区分“标量值”与“嵌入对象”。
 */
internal fun isScalarValueType(type: Class<*>): Boolean = type.isPrimitive ||
    type.isEnum ||
    type.isArray ||
    CharSequence::class.java.isAssignableFrom(type) ||
    Number::class.java.isAssignableFrom(type) ||
    Boolean::class.javaObjectType.isAssignableFrom(type) ||
    Char::class.javaObjectType.isAssignableFrom(type) ||
    Date::class.java.isAssignableFrom(type) ||
    Calendar::class.java.isAssignableFrom(type) ||
    type == UUID::class.java ||
    isJavaTimeType(type)

/**
 * 创建集合实例。
 *
 * 接口/抽象类型按 Set、SortedSet、Queue、List/Collection 的优先级选择实现；
 * 具体类型使用无参构造器。
 */
internal fun newCollectionInstance(type: Class<*>, path: String): MutableCollection<Any?> {
    if (type.isInterface) {
        return when {
            java.util.SortedSet::class.java.isAssignableFrom(type) -> java.util.TreeSet()
            Set::class.java.isAssignableFrom(type) -> LinkedHashSet()
            java.util.Queue::class.java.isAssignableFrom(type) -> java.util.LinkedList()
            List::class.java.isAssignableFrom(type) -> ArrayList()
            Collection::class.java.isAssignableFrom(type) || type == Iterable::class.java -> ArrayList()
            else -> throw JpaMappingException("Unsupported collection type ${type.name} at $path")
        }
    }
    val instance = newInstanceOf(type, path)
    @Suppress("UNCHECKED_CAST")
    return instance as? MutableCollection<Any?>
        ?: throw JpaMappingException("Type ${type.name} is not a collection at $path")
}

/**
 * 创建 Map 实例。
 */
internal fun newMapInstance(type: Class<*>, path: String): MutableMap<Any?, Any?> {
    if (type.isInterface) {
        return if (java.util.SortedMap::class.java.isAssignableFrom(type)) java.util.TreeMap() else LinkedHashMap()
    }
    val instance = newInstanceOf(type, path)
    @Suppress("UNCHECKED_CAST")
    return instance as? MutableMap<Any?, Any?>
        ?: throw JpaMappingException("Type ${type.name} is not a map at $path")
}

/** 将基本类型装箱；非基本类型原样返回。 */
internal fun boxedType(type: Class<*>): Class<*> {
    if (!type.isPrimitive) {
        return type
    }
    return when (type.name) {
        "boolean" -> Boolean::class.javaObjectType
        "byte" -> Byte::class.javaObjectType
        "short" -> Short::class.javaObjectType
        "int" -> Int::class.javaObjectType
        "long" -> Long::class.javaObjectType
        "float" -> Float::class.javaObjectType
        "double" -> Double::class.javaObjectType
        "char" -> Char::class.javaObjectType
        else -> type
    }
}

/**
 * 解析泛型 [Type] 对应的原始类型。
 *
 * 处理 `Class`、`ParameterizedType`、`WildcardType` 与 `TypeVariable` 的界。
 */
internal fun rawTypeOf(type: Type?): Class<*>? = when (type) {
    null -> null
    is Class<*> -> type
    is ParameterizedType -> rawTypeOf(type.rawType)
    is WildcardType -> rawTypeOf(type.upperBounds.firstOrNull())
    is TypeVariable<*> -> rawTypeOf(type.bounds.firstOrNull())
    else -> null
}

/**
 * 通过无参构造器实例化。
 */
internal fun newInstanceOf(type: Class<*>, path: String): Any {
    val constructor = try {
        type.getDeclaredConstructor()
    } catch (e: NoSuchMethodException) {
        throw JpaMappingException("Type ${type.name} must declare a no-argument constructor at $path", e)
    }
    return try {
        constructor.isAccessible = true
        constructor.newInstance()
    } catch (e: ReflectiveOperationException) {
        throw JpaMappingException("Cannot instantiate ${type.name} at $path", e)
    } catch (e: IllegalArgumentException) {
        throw JpaMappingException("Cannot instantiate ${type.name} at $path", e)
    }
}
