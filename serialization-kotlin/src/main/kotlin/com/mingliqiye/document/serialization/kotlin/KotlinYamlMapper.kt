package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.format.yaml.YamlMapper

/**
 * 默认支持 Kotlin data class 的 YAML Mapper。
 *
 * 等价于：
 * ```kotlin
 * YamlMapper.builder()
 *     .objectInstantiator(KotlinDataClassInstantiator)
 *     .build()
 * ```
 */
public class KotlinYamlMapper : YamlMapper(KotlinDataClassInstantiator)
