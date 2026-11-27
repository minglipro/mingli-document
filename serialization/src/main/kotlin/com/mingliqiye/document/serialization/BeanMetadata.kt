package com.mingliqiye.document.serialization

/**
 * 预编译的 Bean 元数据。
 *
 * 每个类型只构建一次，序列化与反序列化共用。
 */
internal class BeanMetadata(
    val properties: List<BeanPropertyAccessor>,
)
