package com.mingliqiye.document.serialization.kotlin

import kotlin.reflect.KFunction

/**
 * Kotlin data class 构造信息缓存。
 */
internal class DataClassMetadata(
    val constructor: KFunction<Any>,
    val javaConstructor: java.lang.reflect.Constructor<*>?,
    val directConstruction: Boolean,
    val parameters: List<DataClassParameterMetadata>,
) {

    val parametersByField: Map<String, Pair<Int, DataClassParameterMetadata>> =
        parameters.withIndex().associate { (index, parameter) ->
            parameter.fieldName to (index to parameter)
        }
}
