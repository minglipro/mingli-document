package com.mingliqiye.document.serialization

import java.util.LinkedHashMap

/**
 * [DocumentMapperConfig] 构建器。
 */
public class DocumentMapperConfigBuilder {

    private val fieldConverters = LinkedHashMap<FieldKey, JsonConverter<*>>()

    /**
     * 为指定字段绑定转换器。
     *
     * @param declaringClass 声明类
     * @param name 属性名
     * @param converter 字段转换器
     * @return 当前构建器
     */
    public fun field(
        declaringClass: Class<*>,
        name: String,
        converter: JsonConverter<*>,
    ): DocumentMapperConfigBuilder {
        fieldConverters[FieldKey(declaringClass, name)] = converter
        return this
    }

    /**
     * 构建不可变配置。
     */
    public fun build(): DocumentMapperConfig = DocumentMapperConfig(LinkedHashMap(fieldConverters))
}
