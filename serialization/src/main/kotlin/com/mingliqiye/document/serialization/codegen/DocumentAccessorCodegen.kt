package com.mingliqiye.document.serialization.codegen

import com.mingliqiye.document.serialization.BeanAccessorFactory
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.Function

/**
 * 运行期属性访问代码生成器。
 *
 * 通过 LambdaMetafactory 为 getter/setter 生成直接调用代码，避免热路径上重复
 * 反射查找。找不到可用访问器时返回 `null`，由调用方自行回退。
 */
public class DocumentAccessorCodegen {

    private val getters = ConcurrentHashMap<CacheKey, Optional<Function<Any, Any?>>>()
    private val setters = ConcurrentHashMap<CacheKey, Optional<BiConsumer<Any, Any?>>>()

    /**
     * 生成属性 getter。
     *
     * @param type 声明类型
     * @param propertyName Java 属性名
     * @return 生成的 getter；不可用时返回 `null`
     */
    public fun getter(type: Class<*>, propertyName: String): Function<Any, Any?>? {
        val key = CacheKey(type, propertyName)
        return getters.computeIfAbsent(key) { Optional.ofNullable(createGetter(type, propertyName)) }.orElse(null)
    }

    /**
     * 生成属性 setter。
     *
     * @param type 声明类型
     * @param propertyName Java 属性名
     * @return 生成的 setter；不可用时返回 `null`
     */
    public fun setter(type: Class<*>, propertyName: String): BiConsumer<Any, Any?>? {
        val key = CacheKey(type, propertyName)
        return setters.computeIfAbsent(key) { Optional.ofNullable(createSetter(type, propertyName)) }.orElse(null)
    }

    private fun createGetter(type: Class<*>, propertyName: String): Function<Any, Any?>? {
        val capitalized = propertyName.replaceFirstChar { it.uppercaseChar() }
        for (name in listOf("get$capitalized", "is$capitalized")) {
            val method = findMethod(type, name, 0)
            if (method != null) {
                BeanAccessorFactory.getter(method)?.let { return it }
                method.isAccessible = true
                return Function { target -> method.invoke(target) }
            }
        }
        val field = findField(type, propertyName)
        if (field != null) {
            BeanAccessorFactory.fieldGetter(field)?.let { return it }
            field.isAccessible = true
            return Function { target -> field.get(target) }
        }
        return null
    }

    private fun createSetter(type: Class<*>, propertyName: String): BiConsumer<Any, Any?>? {
        val capitalized = propertyName.replaceFirstChar { it.uppercaseChar() }
        val method = findMethod(type, "set$capitalized", 1)
        if (method != null) {
            BeanAccessorFactory.setter(method)?.let { return it }
            method.isAccessible = true
            return BiConsumer { target, value -> method.invoke(target, value) }
        }
        val field = findField(type, propertyName) ?: return null
        if (java.lang.reflect.Modifier.isFinal(field.modifiers)) {
            return null
        }
        BeanAccessorFactory.fieldSetter(field)?.let { return it }
        field.isAccessible = true
        return BiConsumer { target, value -> field.set(target, value) }
    }

    private fun findMethod(type: Class<*>, name: String, parameterCount: Int): Method? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            for (method in current.declaredMethods) {
                if (method.name == name && method.parameterCount == parameterCount) {
                    return method
                }
            }
            current = current.superclass
        }
        return null
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private data class CacheKey(
        val type: Class<*>,
        val propertyName: String,
    )
}
