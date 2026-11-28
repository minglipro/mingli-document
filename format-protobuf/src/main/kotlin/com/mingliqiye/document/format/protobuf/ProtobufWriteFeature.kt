package com.mingliqiye.document.format.protobuf

/**
 * Protobuf 写出特性，对齐 上游实现 `Protobuf 底层库` 实际生效的写出行为开关。
 *
 * 盘点结论（上游实现 2.15.3 与 2.23.0 官方源码）：
 * - `Protobuf 底层库` **没有格式专属的写出 Feature 枚举**：
 *   `ProtobufFactory.getFormatWriteFeatureType()` 被注释并标注
 *   "No format-specific configuration, yet"；
 * - 上游实现 protobuf 生成器实际只读取核心 `JsonGenerator.Feature.IGNORE_UNKNOWN`
 *   来控制写出未声明字段名时的行为（`ProtobufGenerator.writeFieldName`）。
 *
 * 因此本枚举以 上游实现 同名常量封装该行为；默认值与 上游实现 一致（关闭，即未知字段名报错）。
 * 每个常量通过 [legacyName] 暴露对应的 上游实现 `JsonGenerator.Feature` 名称。
 *
 * 上游实现 protobuf 其余写出相关核心特性（`AUTO_CLOSE_JSON_CONTENT`、`AUTO_CLOSE_TARGET`、
 * `FLUSH_PASSED_TO_STREAM`）属于流生命周期，由通用 Mapper 层统一处理，不提供格式开关，
 * 见 `docs/formats/protobuf.md` 配置矩阵。
 *
 * @property legacyName 对应的 上游实现 `JsonGenerator.Feature` 名称
 */
public enum class ProtobufWriteFeature(
    /** 对应的 上游实现 `JsonGenerator.Feature` 名称。 */
    public val legacyName: String,
) {

    /**
     * 写出时跳过对象节点中 Descriptor 与 extension 注册表都未声明的字段名，默认关闭。
     *
     * 对应 上游实现 `JsonGenerator.Feature.IGNORE_UNKNOWN`：
     * - 关闭：抛出 [com.mingliqiye.document.format.protobuf.exception.ProtobufException]
     *   （上游实现 默认）；
     * - 开启：静默跳过未声明字段，继续写出已声明字段。
     *
     * 说明：`$unknownFields` 是 [ProtobufUnknownFields.FIELD_NAME] 保留字段，不属于
     * "未知字段名"；它仍只在 [ProtobufUnknownFieldPolicy.PRESERVE] 策略下被接受。
     */
    IGNORE_UNKNOWN("IGNORE_UNKNOWN"),
}
