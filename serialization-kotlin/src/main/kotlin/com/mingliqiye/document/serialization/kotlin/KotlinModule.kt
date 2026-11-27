package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.serialization.DocumentModule
import com.mingliqiye.document.serialization.ModuleSetupContext

/**
 * Kotlin data class 支持模块。
 *
 * 类似 Jackson 的 `KotlinModule`，通过 Mapper Builder 注册：
 * ```kotlin
 * val mapper = JsonMapper.builder()
 *     .addModule(KotlinModule)
 *     .build()
 * ```
 */
public object KotlinModule : DocumentModule {

    override val moduleName: String
        get() = "kotlin"

    override fun setupModule(context: ModuleSetupContext) {
        context.setObjectInstantiator(KotlinDataClassInstantiator)
    }
}
