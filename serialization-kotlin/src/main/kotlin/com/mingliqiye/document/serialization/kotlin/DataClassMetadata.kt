package com.mingliqiye.document.serialization.kotlin

import java.lang.reflect.Constructor
import kotlin.reflect.KFunction

/**
 * Kotlin data class 构造信息缓存。
 */
internal class DataClassMetadata(
    val constructor: KFunction<Any>,
    val javaConstructor: Constructor<*>?,
    val directConstruction: Boolean,
    val parameters: List<DataClassParameterMetadata>,
)
