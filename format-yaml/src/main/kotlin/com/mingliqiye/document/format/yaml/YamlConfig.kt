package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * YAML Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 YAML 专属选项。
 */
public class YamlConfig internal constructor(
    base: DocumentMapperConfig,
    /** `format()` 是否默认输出块格式 YAML。 */
    public val pretty: Boolean,
    /** 单文档允许的非标量 alias 最大数量；`0` 表示使用 SnakeYAML 默认值。 */
    public val maxAliases: Int = 0,
) : DocumentMapperConfig(base) {

    public companion object {

        /**
         * 创建 YAML 配置构建器。
         */
        @JvmStatic
        public fun builder(): YamlConfigBuilder = YamlConfigBuilder()
    }
}
