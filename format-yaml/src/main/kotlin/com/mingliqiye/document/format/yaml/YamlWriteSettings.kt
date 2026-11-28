package com.mingliqiye.document.format.yaml

import org.yaml.snakeyaml.DumperOptions

/**
 * YAML 写出设置。
 *
 * 由 [YamlConfig] 提取，供 [YamlWriter] 构建 SnakeYAML `DumperOptions` 与
 * 自定义 Representer（上游实现 风格字符串引号/字面量块）。内部的不可变快照，
 * 不属于公共 API。
 */
internal data class YamlWriteSettings(
    /** 上游实现 YAML 对齐的写出特性集合。 */
    val writeFeatures: Set<YamlWriteFeature> = YamlConfig.DEFAULT_WRITE_FEATURES,
    /** 块式输出缩进（SnakeYAML 允许 2..9）。 */
    val indent: Int = 2,
    /** 输出行宽；`0` 使用 SnakeYAML 默认值（80）。 */
    val lineWidth: Int = 0,
    /** 是否直接输出 Unicode 字符。 */
    val allowUnicode: Boolean = true,
    /** 是否输出显式文档结束标记 `...`。 */
    val explicitEnd: Boolean = false,
    /** 显式布局；`null` 时由 `pretty` 推导。 */
    val defaultFlowStyle: YamlFlowStyle? = null,
) {

    fun isEnabled(feature: YamlWriteFeature): Boolean = feature in writeFeatures

    /** SnakeYAML 布局枚举映射；`null` 表示由调用方按 `pretty` 推导。 */
    fun flowStyle(): DumperOptions.FlowStyle? = when (defaultFlowStyle) {
        YamlFlowStyle.FLOW -> DumperOptions.FlowStyle.FLOW
        YamlFlowStyle.BLOCK -> DumperOptions.FlowStyle.BLOCK
        null -> null
    }

    companion object {

        /** 默认设置（对齐 上游实现 YAML 2.15 默认特性）。 */
        val DEFAULT: YamlWriteSettings = YamlWriteSettings()

        /**
         * 从 [com.mingliqiye.document.serialization.DocumentMapperConfig] 提取；
         * 非 [YamlConfig] 时使用默认值。
         */
        fun of(config: com.mingliqiye.document.serialization.DocumentMapperConfig): YamlWriteSettings {
            val yamlConfig = config as? YamlConfig ?: return DEFAULT
            return YamlWriteSettings(
                writeFeatures = yamlConfig.writeFeatures,
                indent = yamlConfig.indent,
                lineWidth = yamlConfig.lineWidth,
                allowUnicode = yamlConfig.allowUnicode,
                explicitEnd = yamlConfig.explicitEnd,
                defaultFlowStyle = yamlConfig.defaultFlowStyle,
            )
        }
    }
}
