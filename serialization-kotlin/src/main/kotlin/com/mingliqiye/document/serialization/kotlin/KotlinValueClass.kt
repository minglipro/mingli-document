package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.kotlin
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

/**
 * Kotlin value class（`@JvmInline value class`）的反射元数据。
 *
 * 负责识别 value class、读取底层类型，并在 boxed 形态与底层值之间转换。
 * 转换优先使用编译器生成的 `box-impl` / `unbox-impl` 静态/实例方法，
 * 缺失时回退到私有构造器与实例字段。
 */
internal class KotlinValueClass(
    val kClass: KClass<*>,
    val underlyingKType: KType,
    val underlyingType: java.lang.reflect.Type,
) {

    val javaClass: Class<*> = kClass.java

    /** 底层类型在文档中是否允许 `null`。 */
    val underlyingNullable: Boolean = underlyingKType.isMarkedNullable

    /** 底层 JVM 类型是否为原始类型。 */
    val underlyingPrimitive: Boolean = underlyingType is Class<*> && (underlyingType as Class<*>).isPrimitive

    private val constructor: Constructor<*>? = kClass.java.declaredConstructors.firstOrNull { it.parameterCount == 1 }
        ?.apply { isAccessible = true }

    private val valueField: Field? = kClass.java.declaredFields.firstOrNull { !Modifier.isStatic(it.modifiers) }
        ?.apply { isAccessible = true }

    private val boxMethod: Method? = staticMethod("box-impl")

    private val unboxMethod: Method? = kClass.java.declaredMethods.firstOrNull {
        !Modifier.isStatic(it.modifiers) && it.name == "unbox-impl" && it.parameterCount == 0
    }?.apply { isAccessible = true }

    /**
     * 把底层值装箱成 value class 实例。
     */
    fun box(unboxed: Any?): Any {
        boxMethod?.let { method ->
            return try {
                method.invoke(null, unboxed) ?: throw DocumentSerializationException(
                    "Value class ${javaClass.name} box-impl returned null",
                )
            } catch (e: DocumentSerializationException) {
                throw e
            } catch (e: Exception) {
                throw DocumentSerializationException("Cannot box value class ${javaClass.name}", e)
            }
        }
        val creator = constructor ?: throw DocumentSerializationException(
            "Value class ${javaClass.name} has no accessible constructor",
        )
        return try {
            creator.newInstance(unboxed)
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot box value class ${javaClass.name}", e)
        }
    }

    /**
     * 把 value class 实例拆箱为底层值。
     */
    fun unbox(value: Any): Any? {
        unboxMethod?.let { method ->
            return try {
                method.invoke(value)
            } catch (e: Exception) {
                throw DocumentSerializationException("Cannot unbox value class ${javaClass.name}", e)
            }
        }
        val field = valueField ?: throw DocumentSerializationException(
            "Value class ${javaClass.name} has no underlying field",
        )
        return try {
            field.get(value)
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot unbox value class ${javaClass.name}", e)
        }
    }

    private fun staticMethod(name: String): Method? = kClass.java.declaredMethods.firstOrNull {
        Modifier.isStatic(it.modifiers) && it.name == name && it.parameterCount == 1
    }?.apply { isAccessible = true }
}

/**
 * [KotlinValueClass] 缓存与校验。
 *
 * 只支持 "单底层值" 形态：编译器生成的 value class 恰好有一个主构造参数。
 * 泛型 value class（底层依赖类型参数）与 value class 嵌套 value class
 * 无法稳定映射到文档标量，直接抛 [DocumentSerializationException]。
 */
internal object KotlinValueClasses {

    private val cache = ConcurrentHashMap<Class<*>, KotlinValueClass>()

    /**
     * 获取 value class 元数据并做可用性校验。
     *
     * @throws DocumentSerializationException 类型不是 value class，或使用泛型/嵌套
     *   value class 等不受支持的形态时抛出
     */
    fun metadata(kClass: KClass<*>): KotlinValueClass {
        if (!kClass.isValue) {
            throw DocumentSerializationException("${kClass.java.name} is not a Kotlin value class")
        }
        return cache.computeIfAbsent(kClass.java) { create(it) }
    }

    private fun create(type: Class<*>): KotlinValueClass {
        val kClass = type.kotlin
        if (kClass.typeParameters.isNotEmpty()) {
            throw DocumentSerializationException(
                "Generic value class ${type.name} is not supported; " +
                    "the document representation of its unbound type parameter is unknown",
            )
        }
        val constructor = kClass.primaryConstructor ?: throw DocumentSerializationException(
            "Value class ${type.name} has no primary constructor",
        )
        val parameters = constructor.parameters.filter { it.kind == kotlin.reflect.KParameter.Kind.VALUE }
        if (parameters.size != 1) {
            throw DocumentSerializationException(
                "Value class ${type.name} must declare exactly one primary constructor value, " +
                    "but was ${parameters.size}",
            )
        }
        val underlyingKType = parameters[0].type
        if (containsTypeParameter(underlyingKType)) {
            throw DocumentSerializationException(
                "Value class ${type.name} has a generic underlying type $underlyingKType; " +
                    "generic value classes are not supported",
            )
        }
        val underlyingClass = underlyingKType.classifier as? KClass<*>
        if (underlyingClass != null && underlyingClass.isValue) {
            throw DocumentSerializationException(
                "Value class ${type.name} wraps another value class ${underlyingClass.java.name}, " +
                    "which is not supported; use one level of value class",
            )
        }
        val underlyingJavaType = try {
            underlyingKType.javaType
        } catch (e: Exception) {
            throw DocumentSerializationException(
                "Cannot resolve the underlying Java type of value class ${type.name}",
                e,
            )
        }
        return KotlinValueClass(kClass, underlyingKType, underlyingJavaType)
    }

    /** 递归判断类型中是否包含类型参数引用（例如 `List<T>`）。 */
    fun containsTypeParameter(type: KType): Boolean {
        if (type.classifier is KTypeParameter) {
            return true
        }
        return type.arguments.any { argument -> argument.type?.let(::containsTypeParameter) == true }
    }
}
