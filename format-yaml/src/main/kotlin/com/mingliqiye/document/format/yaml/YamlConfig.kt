package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.FieldKey
import com.mingliqiye.document.serialization.JsonConverter

/**
 * YAML Mapper 配置。
 *
 * 在字段级转换配置基础上增加 YAML 专属选项。
 */
public class YamlConfig internal constructor(
    fieldConverters: Map<FieldKey, JsonConverter<*>>,
    /** `format()` 是否默认输出块格式 YAML。 */
    public val pretty: Boolean,
) : DocumentMapperConfig(fieldConverters) {

    public companion object {

        /**
         * 创建 YAML 配置构建器。
         */
        @JvmStatic
        public fun builder(): YamlConfigBuilder = YamlConfigBuilder()
    }
}
