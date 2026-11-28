package com.mingliqiye.document.serialization.kotlin

import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties

/**
 * Kotlin `object` 单例支持。
 *
 * 反序列化 sealed class / sealed interface 时，子类型可能是 `object`、
 * `data object` 或普通 `object`。这些类型没有可调用的构造器，必须复用
 * 编译器生成的 `INSTANCE` 字段，否则会破坏单例语义。
 *
 * 只对没有可变属性（`var`）的 object 启用单例复用：带 `var` 的 object
 * 由 Mapper 回退到默认的"新建实例 + 填字段"路径，避免多实例共享状态。
 * 仅当类带 Kotlin `@Metadata` 且声明了自类型的静态 `INSTANCE` 字段时识别，
 * 避免误判普通 Java 单例类。
 */
internal object KotlinObjects {

    private val instanceCache = ConcurrentHashMap<Class<*>, Any>()
    private val none = Any()
    private val statelessCache = ConcurrentHashMap<Class<*>, Boolean>()

    /**
     * Kotlin object 的单例实例；不是 Kotlin object 时返回 `null`。
     */
    fun instanceOf(type: Class<*>): Any? {
        val cached = instanceCache.computeIfAbsent(type) { resolveInstance(it) ?: none }
        return if (cached === none) null else cached
    }

    /**
     * 无 `var` 属性的 Kotlin object 单例；带可变属性或不是 object 时返回 `null`。
     */
    fun statelessInstanceOf(type: Class<*>): Any? {
        val instance = instanceOf(type) ?: return null
        val stateless = statelessCache.computeIfAbsent(type) {
            try {
                type.kotlin.memberProperties.none { property -> property is KMutableProperty<*> }
            } catch (_: Throwable) {
                false
            }
        }
        return if (stateless) instance else null
    }

    private fun resolveInstance(type: Class<*>): Any? {
        if (!type.isAnnotationPresent(Metadata::class.java)) {
            return null
        }
        val field = try {
            type.getDeclaredField(INSTANCE_FIELD)
        } catch (_: NoSuchFieldException) {
            return null
        }
        if (!Modifier.isStatic(field.modifiers) || !type.isAssignableFrom(field.type)) {
            return null
        }
        return try {
            field.isAccessible = true
            field.get(null)
        } catch (_: Throwable) {
            null
        }
    }

    private const val INSTANCE_FIELD = "INSTANCE"
}
