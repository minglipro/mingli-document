package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.serialization.DocumentModule

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
