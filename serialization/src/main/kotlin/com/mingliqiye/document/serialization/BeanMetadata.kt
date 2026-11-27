package com.mingliqiye.document.serialization

/**
 * 预编译的 Bean 元数据。
 *
 * 每个类型只构建一次，序列化与反序列化共用。
 */
internal class BeanMetadata(
    val properties: List<BeanPropertyAccessor>,
) {

    private val propertiesByName: Map<String, BeanPropertyAccessor> =
        properties.associateBy { it.metadata.documentName }

    /** 按文档字段名查找属性。 */
    fun property(name: String): BeanPropertyAccessor? = propertiesByName[name]
}
