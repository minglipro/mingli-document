package com.mingliqiye.document.serialization.kotlin

import kotlin.reflect.KParameter

/**
 * data class 单个构造参数缓存。
 */
internal class DataClassParameterMetadata(
    val parameter: KParameter,
    val fieldName: String,
    val ignored: Boolean,
)
