package com.mingliqiye.document.serialization

/**
 * Mapper 配置。
 *
 * 支持字段级转换器、缺字段策略、未知字段策略、null 字段写出策略与
 * 字段命名策略。
 */
public open class DocumentMapperConfig protected constructor(
    private val fieldConverters: Map<FieldKey, DocumentBaseConverter<*>>,
    /** 文档缺少 Bean 字段时的处理策略。 */
    public val missingFieldPolicy: MissingFieldPolicy = MissingFieldPolicy.KEEP_DEFAULT,
    /** 遇到未知字段是否抛出异常。 */
    public val failOnUnknownFields: Boolean = false,
    /** Bean 字段为 null 时是否写出。 */
    public val writeNulls: Boolean = true,
    /**
     * 未标注 [com.mingliqiye.document.annotation.DocumentName] 的字段使用的
     * 文档字段命名策略，默认 [PropertyNamingStrategy.AS_IS]。
     */
    public val namingStrategy: PropertyNamingStrategy = PropertyNamingStrategy.AS_IS,
) {

    /**
     * 复制另一个配置。
     */
    protected constructor(other: DocumentMapperConfig) : this(
        other.fieldConverters,
        other.missingFieldPolicy,
        other.failOnUnknownFields,
        other.writeNulls,
        other.namingStrategy,
    )

    internal fun fieldConverter(property: DocumentProperty): DocumentBaseConverter<*>? = fieldConverters[FieldKey(property.declaringClass, property.name)]

    internal val hasFieldConverters: Boolean
        get() = fieldConverters.isNotEmpty()

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

        internal fun from(
            fieldConverters: Map<FieldKey, DocumentBaseConverter<*>>,
            missingFieldPolicy: MissingFieldPolicy,
            failOnUnknownFields: Boolean,
            writeNulls: Boolean,
            namingStrategy: PropertyNamingStrategy,
        ): DocumentMapperConfig = DocumentMapperConfig(
            fieldConverters,
            missingFieldPolicy,
            failOnUnknownFields,
            writeNulls,
            namingStrategy,
        )
    }
}
