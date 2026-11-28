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
    /**
     * 是否允许未知自定义 tag（如 `!foo`、`!env`、`!!foo` 与
     * `!<tag:example.com,2024:foo>`）。
     *
     * 默认 `false`，保持 SnakeYAML `SafeConstructor` 行为：未知 tag 抛出
     * [com.mingliqiye.document.format.yaml.exception.YamlParseException]。
     * 开启后按节点种类还原：标量 -> StringNode，序列 -> ArrayNode，
     * 映射 -> ObjectNode；同时作用于 Node 路径（[YamlReader]）与原生 Token 路径
     * （[YamlMapper.parse]）。构造阶段只还原基础类型，不会实例化任意 Java 类型。
     */
    public val allowCustomTags: Boolean = false,
) : DocumentMapperConfig(base) {

    public companion object {

        /**
         * 创建 YAML 配置构建器。
         */
        @JvmStatic
        public fun builder(): YamlConfigBuilder = YamlConfigBuilder()
    }
}
