package com.mingliqiye.document.serialization

import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.function.BiConsumer
import java.util.function.Function

/**
 * Bean getter/setter 的 LambdaMetafactory 工厂。
 *
 * 生成直接调用访问器，失败时返回 `null`，由调用方回退到反射。
 */
internal object BeanAccessorFactory {

    private val getterSam: MethodType = MethodType.methodType(Any::class.java, Any::class.java)
    private val setterSam: MethodType = MethodType.methodType(Void.TYPE, Any::class.java, Any::class.java)

    fun getter(method: Method): ((Any) -> Any?)? {
        val handle = methodHandle(method) ?: return null
        return getterFromHandle(handle)
    }

    fun setter(method: Method): ((Any, Any?) -> Unit)? {
        val handle = methodHandle(method) ?: return null
        return setterFromHandle(handle)
    }

    fun fieldGetter(field: Field): ((Any) -> Any?)? {
        val handle = fieldHandle(field, setter = false) ?: return null
        return getterFromHandle(handle)
    }

    fun fieldSetter(field: Field): ((Any, Any?) -> Unit)? {
        val handle = fieldHandle(field, setter = true) ?: return null
        return setterFromHandle(handle)
    }

    private fun methodHandle(method: Method): MethodHandle? = try {
        method.isAccessible = true
        MethodHandles.lookup().unreflect(method)
    } catch (_: Throwable) {
        null
    }

    private fun fieldHandle(field: Field, setter: Boolean): MethodHandle? = try {
        field.isAccessible = true
        val lookup = MethodHandles.lookup()
        if (setter) lookup.unreflectSetter(field) else lookup.unreflectGetter(field)
    } catch (_: Throwable) {
        null
    }

    private fun getterFromHandle(handle: MethodHandle): ((Any) -> Any?)? = try {
        val adapted = handle.asType(MethodType.methodType(Any::class.java, Any::class.java))
        val callSite = LambdaMetafactory.metafactory(
            MethodHandles.lookup(),
            "apply",
            MethodType.methodType(Function::class.java),
            getterSam,
            adapted,
            getterSam,
        )
        val function = callSite.target.invokeWithArguments() as Function<Any, Any>
        { bean -> function.apply(bean) }
    } catch (_: Throwable) {
        null
    }

    private fun setterFromHandle(handle: MethodHandle): ((Any, Any?) -> Unit)? = try {
        val adapted = handle.asType(MethodType.methodType(Void.TYPE, Any::class.java, Any::class.java))
        val callSite = LambdaMetafactory.metafactory(
            MethodHandles.lookup(),
            "accept",
            MethodType.methodType(BiConsumer::class.java),
            setterSam,
            adapted,
            setterSam,
        )
        val consumer = callSite.target.invokeWithArguments() as BiConsumer<Any, Any?>
        { bean, value -> consumer.accept(bean, value) }
    } catch (_: Throwable) {
        null
    }
}
