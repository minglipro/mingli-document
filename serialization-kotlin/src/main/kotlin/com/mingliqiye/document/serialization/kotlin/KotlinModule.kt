package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.serialization.DocumentModule
import com.mingliqiye.document.serialization.ModuleSetupContext
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.reflect.KClass

/**
 * 获取 Kotlin 类型支持模块。
 *
 * 模块对象不对外暴露，通过函数获取，避免公开单例实现：
 * ```kotlin
 * val mapper = JsonMapper.builder()
 *     .addModule(kotlinModule())
 *     .build()
 * ```
 *
 * 默认模块已覆盖：
 * - data class 主构造器实例化（含默认参数、可空参数、嵌套 data class、泛型集合）；
 * - value class 值作为 data class 直接构造参数（引用底层类型、非空原始类型底层）；
 * - 无 `var` 的 Kotlin object / `data object` 单例反序列化（sealed 多态子类型）。
 *
 * 可选参数用于注册 value class 编解码器，使 value class 在 **boxed 位置**
 * （根对象、`List`/`Map`/数组元素、可空原始类型底层等）也能序列化为底层值：
 * ```kotlin
 * val mapper = JsonMapper.builder()
 *     .addModule(kotlinModule(UserId::class, Price::class))
 *     .build()
 * ```
 *
 * sealed class 多态需要注册子类型注册表，`DocumentModule` 无法修改 Mapper 配置，
 * 请使用 [DocumentMapperConfigBuilder.kotlinSealedSubtypes]。
 *
 * @param valueClasses 需要额外注册标量编解码器的 Kotlin value class
 * @throws IllegalArgumentException 传入的不是 Kotlin value class 时抛出
 * @throws DocumentSerializationException value class 形态不受支持（例如泛型 value
 *   class、value class 嵌套 value class）时抛出
 */
@JvmOverloads
public fun kotlinModule(vararg valueClasses: KClass<*>): DocumentModule {
    val codecs = LinkedHashMap<Class<*>, KotlinValueClass>()
    for (kClass in valueClasses) {
        require(kClass.isValue) {
            "${kClass.java.name} is not a Kotlin value class. Only value class scalar codecs " +
                "can be registered here; sealed class subtypes are registered with " +
                "DocumentMapperConfigBuilder.kotlinSealedSubtypes instead."
        }
        val metadata = KotlinValueClasses.metadata(kClass)
        codecs[metadata.javaClass] = metadata
    }
    return KotlinModule(codecs)
}

/**
 * Kotlin 类型支持模块实现。
 *
 * 模块名按注册的 value class 集合区分，允许在同一个 Mapper 上追加多个
 * [kotlinModule] 调用而不会被模块去重覆盖。
 */
internal class KotlinModule(
    private val valueClasses: Map<Class<*>, KotlinValueClass>,
) : DocumentModule {

    override val moduleName: String = if (valueClasses.isEmpty()) {
        "kotlin"
    } else {
        valueClasses.keys.map { it.name }.sorted().joinToString(separator = ",", prefix = "kotlin:", postfix = "")
    }

    override fun setupModule(context: ModuleSetupContext) {
        context.setObjectInstantiator(KotlinDataClassInstantiator)
        context.setPropertyMetadataResolver(KotlinPropertyMetadataResolver)
        for (valueClass in valueClasses.values) {
            context.addConverter(KotlinValueClassConverter(valueClass))
        }
    }
}
