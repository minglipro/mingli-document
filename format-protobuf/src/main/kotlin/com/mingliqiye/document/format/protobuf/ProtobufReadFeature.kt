package com.mingliqiye.document.format.protobuf

/**
 * Protobuf 读取特性，对齐 上游实现 `Protobuf 底层库` 实际生效的读取行为开关。
 *
 * 盘点结论（上游实现 2.15.3 与 2.23.0 官方源码）：
 * - `Protobuf 底层库` **没有格式专属的读取 Feature 枚举**：
 *   `ProtobufFactory.getFormatReadFeatureType()` 被注释并标注
 *   "No format-specific configuration, yet"，`ProtobufFactoryBuilder` 也没有额外设置；
 * - 上游实现 protobuf 解析器实际只读取核心 `StreamReadFeature.IGNORE_UNDEFINED`
 *   （旧版 `JsonParser.Feature.IGNORE_UNDEFINED` 的同义新名）来控制未知字段行为。
 *
 * 因此本枚举以 上游实现 同名常量封装该行为；默认值与 上游实现 一致（关闭，即未知字段报错）。
 * 每个常量通过 [legacyName] 暴露对应的 上游实现 `JsonParser.Feature` / `StreamReadFeature` 名称，
 * 便于从 上游实现 配置迁移。
 *
 * 上游实现 protobuf 其余读取相关核心特性（`AUTO_CLOSE_SOURCE`、
 * `USE_FAST_BIG_NUMBER_PARSER`、`STRICT_DUPLICATE_DETECTION`）在本模块不适用或由通用 Mapper 层
 * 统一处理，见 `docs/formats/protobuf.md` 配置矩阵。
 *
 * @property legacyName 对应的 上游实现 特性名称
 */
public enum class ProtobufReadFeature(
    /** 对应的 上游实现 `JsonParser.Feature` / `StreamReadFeature` 名称。 */
    public val legacyName: String,
) {

    /**
     * 忽略 wire 中 Descriptor 与 extension 注册表都未声明的字段，默认关闭。
     *
     * 对应 上游实现 `StreamReadFeature.IGNORE_UNDEFINED`（旧版
     * `JsonParser.Feature.IGNORE_UNDEFINED`）：
     * - 关闭：解析遇到未知字段抛
     *   [com.mingliqiye.document.format.protobuf.exception.ProtobufParseException]（上游实现 默认）；
     * - 开启：丢弃未知字段，等价于 [ProtobufUnknownFieldPolicy.IGNORE]。
     *
     * [ProtobufConfig.unknownFieldPolicy] 为 [ProtobufUnknownFieldPolicy.PRESERVE] 时保留优先：
     * 未知字段映射为 `$unknownFields` 字段，不会因本特性丢失数据。
     */
    IGNORE_UNDEFINED("IGNORE_UNDEFINED"),
}
