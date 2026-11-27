package com.mingliqiye.document.serialization.kotlin

import kotlin.reflect.KFunction

/**
 * Kotlin data class 构造信息缓存。
 */
internal class DataClassMetadata(
    val constructor: KFunction<Any>,
    val parameters: List<DataClassParameterMetadata>,
)
