package com.mingliqiye.document.format.protobuf

/**
 * 解析 Protobuf 二进制时对未知字段的处理策略。
 *
 * 未知字段指 wire 数据中出现、但 [com.google.protobuf.Descriptors.Descriptor] 未声明（也不是
 * [ProtobufExtensionRegistry] 注册的 extension）的字段编号，例如新版本 message 写入了旧版本
 * schema 不认识的字段。动态 message 解析会保留这些字段。
 *
 * @see ProtobufMapper
 * @see ProtobufUnknownFields
 */
public enum class ProtobufUnknownFieldPolicy {

    /**
     * 遇到未知字段时抛出 [com.mingliqiye.document.format.protobuf.exception.ProtobufParseException]。
     *
     * 这是默认策略，避免静默丢失数据。写出时对象中出现未声明字段（包括
     * [ProtobufUnknownFields.FIELD_NAME]）也抛出异常。
     */
    FAIL,

    /**
     * 忽略未知字段，只映射 Descriptor 声明的字段与已注册的 extension。
     *
     * 写出时对象中出现未声明字段仍然抛出异常。
     */
    IGNORE,

    /**
     * 保留未知字段：解析时映射为 [ProtobufUnknownFields.FIELD_NAME] 字段（结构见
     * [ProtobufUnknownFields]），写出时按原 wire 类型还原。解析与写出必须使用同一策略。
     *
     * 该模式保证无法识别的新字段在 round-trip 中不丢失；数值按无符号十进制、二进制按
     * [com.mingliqiye.document.core.BinaryNode] 表达。
     */
    PRESERVE,
}
