package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.serialization.DocumentModule
import com.mingliqiye.document.serialization.ModuleSetupContext

/**
 * 获取 Kotlin data class 支持模块。
 *
 * 模块对象不对外暴露，通过函数获取，避免公开单例实现：
 * ```kotlin
 * val mapper = JsonMapper.builder()
 *     .addModule(kotlinModule())
 *     .build()
 * ```
 */
public fun kotlinModule(): DocumentModule = DefaultKotlinModule

/**
 * Kotlin data class 支持模块默认实现。
 */
private object DefaultKotlinModule : DocumentModule {

    override val moduleName: String
        get() = "kotlin"

    override fun setupModule(context: ModuleSetupContext) {
        context.setObjectInstantiator(KotlinDataClassInstantiator)
    }
}
