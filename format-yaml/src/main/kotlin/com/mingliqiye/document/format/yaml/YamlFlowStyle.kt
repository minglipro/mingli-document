package com.mingliqiye.document.format.yaml

/**
 * SnakeYAML `DumperOptions.FlowStyle` 的公开映射，用于控制 `format` 输出布局。
 *
 * 未显式设置时由 [YamlConfig.pretty] 推导（`pretty=true` -> [BLOCK]，
 * `pretty=false` -> [FLOW]，保持本库历史行为）。上游实现 YAML 始终使用 block 布局，
 * 需要完全对齐时可显式配置 [BLOCK]。
 */
public enum class YamlFlowStyle {

    /** 流式（`{a: 1}` / `[1, 2]`）布局。 */
    FLOW,

    /** 块式布局。 */
    BLOCK,
}
