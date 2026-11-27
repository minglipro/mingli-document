package com.mingliqiye.document.serialization.kotlin

import java.lang.reflect.Type
import kotlin.reflect.KParameter

/**
 * data class 单个构造参数缓存。
 */
internal class DataClassParameterMetadata(
    val parameter: KParameter,
    val fieldName: String,
    val ignored: Boolean,
    val javaType: Type,
)
