package com.mingliqiye.document.serialization

/**
 * Mapper 配置。
 *
 * 当前支持按字段声明类和属性名绑定专用转换器，用于定义单个字段的解析与格式化方式。
 */
public open class DocumentMapperConfig protected constructor(
    private val fieldConverters: Map<FieldKey, JsonConverter<*>>,
) {

    internal fun fieldConverter(property: DocumentProperty): JsonConverter<*>? = fieldConverters[FieldKey(property.declaringClass, property.name)]

    public companion object {

        /**
         * 创建配置构建器。
         */
        @JvmStatic
        public fun builder(): DocumentMapperConfigBuilder = DocumentMapperConfigBuilder()

        /**
         * 空配置。
         */
        @JvmStatic
        public fun empty(): DocumentMapperConfig = DocumentMapperConfig(emptyMap())

        internal fun from(fieldConverters: Map<FieldKey, JsonConverter<*>>): DocumentMapperConfig = DocumentMapperConfig(fieldConverters)
    }
}
