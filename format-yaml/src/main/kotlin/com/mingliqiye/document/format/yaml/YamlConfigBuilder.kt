package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.FieldKey
import com.mingliqiye.document.serialization.JsonConverter
import java.util.LinkedHashMap

/**
 * [YamlConfig] 构建器。
 */
public class YamlConfigBuilder {

    private val fieldConverters = LinkedHashMap<FieldKey, JsonConverter<*>>()
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
        converter: JsonConverter<*>,
    ): YamlConfigBuilder {
        fieldConverters[FieldKey(declaringClass, name)] = converter
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
    public fun build(): YamlConfig = YamlConfig(LinkedHashMap(fieldConverters), pretty)
}
