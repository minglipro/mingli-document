package com.mingliqiye.document.serialization.kotlin

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.jvm.javaObjectType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection

/**
 * 把 Kotlin [KType] 转换为 data class 实例化可用的 Java [Type]。
 *
 * `kotlin-reflect` 的 `KType.javaType` 在主构造器带默认值、存在 synthetic 构造器时
 * 可能把参数泛型信息退化为原始类型；value class 参数位置则会直接展开成底层
 * Java 类型。这里统一按 JVM 存储形态转换：
 *
 * - 顶层 value class 参数：引用底层类型（底层是原始类型且参数可空时使用 boxed
 *   wrapper，因为可空 value class 的 JVM 表示是 wrapper）；
 * - 泛型参数、集合元素、Map 键值、数组元素中的 value class：保留为 boxed wrapper
 *   类型，由已注册的 value class 编解码器负责拆箱/装箱；
 * - 顶层非空原始类型保留 JVM 原始类型，可空原始类型与泛型参数中的原始类型装箱；
 * - 其它类型保留完整泛型参数，保证 `List<Int>`、`Map<String, List<X>>` 等参数
 *   能按元素类型反序列化。
 */
internal fun KType.toConstructionJavaType(): Type = toJavaType(unboxTopLevelValueClass = true)

/**
 * 获取任意 [Type] 的原始 JVM 类型。
 */
internal fun rawJavaClass(type: Type): Class<*> = when (type) {
    is Class<*> -> type
    is ParameterizedType -> rawJavaClass(type.rawType)
    is GenericArrayType -> java.lang.reflect.Array.newInstance(rawJavaClass(type.genericComponentType), 0).javaClass
    is WildcardType -> rawJavaClass(type.upperBounds.firstOrNull() ?: Any::class.java)
    is TypeVariable<*> -> rawJavaClass(type.bounds.firstOrNull() ?: Any::class.java)
    else -> Any::class.java
}

private fun KType.toJavaType(unboxTopLevelValueClass: Boolean): Type {
    val classifier = classifier
    if (classifier is KClass<*>) {
        if (classifier.isValue) {
            val valueClass = KotlinValueClasses.metadata(classifier)
            if (!unboxTopLevelValueClass) {
                return classifier.java
            }
            if (isMarkedNullable && valueClass.underlyingPrimitive) {
                return classifier.java
            }
            return valueClass.underlyingType
        }
        if (classifier == Array::class) {
            val element = arguments.firstOrNull()?.type ?: return Any::class.java
            return JavaGenericArrayType(element.toJavaType(unboxTopLevelValueClass = false).boxedIfPrimitive())
        }
        val javaType = classifier.java
        if (arguments.isEmpty()) {
            val keepPrimitive = javaType.isPrimitive && unboxTopLevelValueClass && !isMarkedNullable
            return if (keepPrimitive) javaType else classifier.javaObjectType
        }
        return JavaParameterizedType(
            javaType,
            arguments.map { it.toProjectionJavaType() }.toTypedArray(),
        )
    }
    if (classifier is KTypeParameter) {
        val bound = classifier.upperBounds.firstOrNull() ?: return Any::class.java
        return bound.toJavaType(unboxTopLevelValueClass)
    }
    return Any::class.java
}

private fun KTypeProjection.toProjectionJavaType(): Type = type?.toJavaType(unboxTopLevelValueClass = false)?.boxedIfPrimitive() ?: Any::class.java

private fun Type.boxedIfPrimitive(): Type = when (this) {
    is Class<*> -> if (isPrimitive) kotlin.javaObjectType else this
    else -> this
}

/**
 * [ParameterizedType] 的最小实现，用于携带 data class 构造参数的泛型信息。
 */
private class JavaParameterizedType(
    private val raw: Class<*>,
    private val arguments: Array<Type>,
) : ParameterizedType {

    override fun getRawType(): Type = raw

    override fun getActualTypeArguments(): Array<Type> = arguments.copyOf()

    override fun getOwnerType(): Type? = null

    override fun equals(other: Any?): Boolean = other is ParameterizedType &&
        other.ownerType == null &&
        other.rawType == raw &&
        other.actualTypeArguments.contentEquals(arguments)

    override fun hashCode(): Int = arguments.contentHashCode() * 31 + raw.hashCode()

    override fun toString(): String = raw.name + arguments.joinToString(prefix = "<", postfix = ">")
}

/**
 * [GenericArrayType] 的最小实现，用于 `Array<value class>` 等泛型数组。
 */
private class JavaGenericArrayType(private val component: Type) : GenericArrayType {

    override fun getGenericComponentType(): Type = component

    override fun toString(): String = component.typeName + "[]"
}
