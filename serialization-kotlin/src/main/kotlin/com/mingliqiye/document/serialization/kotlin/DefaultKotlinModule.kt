package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.serialization.DocumentModule
import com.mingliqiye.document.serialization.ModuleSetupContext

/**
 * Kotlin data class 支持模块默认实现。
 */
internal object DefaultKotlinModule : DocumentModule {

    override val moduleName: String
        get() = "kotlin"

    override fun setupModule(context: ModuleSetupContext) {
        context.setObjectInstantiator(KotlinDataClassInstantiator)
    }
}
