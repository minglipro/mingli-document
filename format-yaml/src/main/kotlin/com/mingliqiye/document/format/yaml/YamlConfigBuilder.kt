package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy

/**
 * [YamlConfig] 构建器。
 */
public class YamlConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var pretty = false

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
    ): YamlConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): YamlConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     */
    public fun failOnUnknownFields(fail: Boolean): YamlConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     */
    public fun writeNulls(write: Boolean): YamlConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * `format()` 是否默认输出块格式 YAML。
     */
    public fun pretty(pretty: Boolean): YamlConfigBuilder {
        this.pretty = pretty
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): YamlConfig = YamlConfig(base.build(), pretty)
}
