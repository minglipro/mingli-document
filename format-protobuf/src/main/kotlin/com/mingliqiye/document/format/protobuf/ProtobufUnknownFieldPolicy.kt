package com.mingliqiye.document.format.protobuf

/**
 * 解析 Protobuf 二进制时对未知字段的处理策略。
 *
 * 未知字段指 wire 数据中出现、但 [com.google.protobuf.Descriptors.Descriptor] 未声明的字段编号，
 * 例如新版本 message 写入了旧版本 schema 不认识的字段。动态 message 解析会保留这些字段。
 *
 * @see ProtobufMapper
 */
public enum class ProtobufUnknownFieldPolicy {

    /**
     * 遇到未知字段时抛出 [com.mingliqiye.document.format.protobuf.exception.ProtobufParseException]。
     *
     * 这是默认策略，避免静默丢失数据。
     */
    FAIL,

    /**
     * 忽略未知字段，只映射 Descriptor 声明的字段。
     */
    IGNORE,
}
