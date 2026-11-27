package com.mingliqiye.document.serialization

import java.util.LinkedHashMap

/**
 * [DocumentMapperConfig] 构建器。
 */
public class DocumentMapperConfigBuilder {

    private val fieldConverters = LinkedHashMap<FieldKey, DocumentBaseConverter<*>>()
    private var missingFieldPolicy = MissingFieldPolicy.KEEP_DEFAULT
    private var failOnUnknownFields = false
    private var writeNulls = true
    private var namingStrategy = PropertyNamingStrategy.AS_IS

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
        converter: DocumentBaseConverter<*>,
    ): DocumentMapperConfigBuilder {
        fieldConverters[FieldKey(declaringClass, name)] = converter
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     *
     * @param policy 缺失字段策略
     * @return 当前构建器
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): DocumentMapperConfigBuilder {
        this.missingFieldPolicy = policy
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     *
     * @param fail 是否失败
     * @return 当前构建器
     */
    public fun failOnUnknownFields(fail: Boolean): DocumentMapperConfigBuilder {
        this.failOnUnknownFields = fail
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     *
     * @param write 是否写出 null 字段
     * @return 当前构建器
     */
    public fun writeNulls(write: Boolean): DocumentMapperConfigBuilder {
        this.writeNulls = write
        return this
    }

    /**
     * 设置未标注 [com.mingliqiye.document.annotation.DocumentName] 的字段命名策略。
     *
     * @param strategy 字段命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): DocumentMapperConfigBuilder {
        this.namingStrategy = strategy
        return this
    }

    /**
     * 构建不可变配置。
     */
    public fun build(): DocumentMapperConfig = DocumentMapperConfig.from(
        LinkedHashMap(fieldConverters),
        missingFieldPolicy,
        failOnUnknownFields,
        writeNulls,
        namingStrategy,
    )
}
