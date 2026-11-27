package com.mingliqiye.document.format.json

import com.mingliqiye.document.serialization.FieldKey
import com.mingliqiye.document.serialization.JsonConverter
import java.util.LinkedHashMap

/**
 * [JsonConfig] 构建器。
 */
public class JsonConfigBuilder {

    private val fieldConverters = LinkedHashMap<FieldKey, JsonConverter<*>>()
    private var allowComments = true
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
    ): JsonConfigBuilder {
        fieldConverters[FieldKey(declaringClass, name)] = converter
        return this
    }

    /**
     * 是否允许 JSONC 注释。
     */
    public fun allowComments(allowComments: Boolean): JsonConfigBuilder {
        this.allowComments = allowComments
        return this
    }

    /**
     * `format()` 是否默认输出格式化 JSON。
     */
    public fun pretty(pretty: Boolean): JsonConfigBuilder {
        this.pretty = pretty
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): JsonConfig = JsonConfig(LinkedHashMap(fieldConverters), allowComments, pretty)
}
