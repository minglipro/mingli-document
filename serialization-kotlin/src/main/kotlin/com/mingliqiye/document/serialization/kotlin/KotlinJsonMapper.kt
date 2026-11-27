package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.format.json.JsonMapper

/**
 * 默认支持 Kotlin data class 的 JSON Mapper。
 *
 * 等价于：
 * ```kotlin
 * JsonMapper.builder()
 *     .objectInstantiator(KotlinDataClassInstantiator)
 *     .build()
 * ```
 */
public class KotlinJsonMapper : JsonMapper(KotlinDataClassInstantiator)
