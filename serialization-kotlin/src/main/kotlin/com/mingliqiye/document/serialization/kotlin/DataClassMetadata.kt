package com.mingliqiye.document.serialization.kotlin

import java.lang.reflect.Constructor

/**
 * Kotlin data class 构造信息缓存。
 *
 * @property javaConstructor 完整参数构造器（所有构造参数都必须传值）
 * @property defaultsConstructor Kotlin 默认参数 synthetic 构造器
 *   （`(参数..., mask: Int..., DefaultConstructorMarker)`）；没有默认参数时为 `null`
 * @property directConstruction 是否可以无条件走 [javaConstructor]
 * @property parameters 构造参数元数据
 */
internal class DataClassMetadata(
    val javaConstructor: Constructor<*>?,
    val defaultsConstructor: Constructor<*>?,
    val directConstruction: Boolean,
    val parameters: List<DataClassParameterMetadata>,
) {

    val parametersByField: Map<String, Pair<Int, DataClassParameterMetadata>> =
        parameters.withIndex().associate { (index, parameter) ->
            parameter.fieldName to (index to parameter)
        }

    val parameterCount: Int = parameters.size

    /** 默认参数 mask 使用的 `int` 数量（Kotlin 每 32 个参数一个 mask）。 */
    val maskCount: Int = (parameterCount + 31) / 32
}
