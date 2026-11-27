package com.mingliqiye.document.format.protobuf

import com.google.protobuf.Descriptors.Descriptor
import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * Protobuf 二进制文档映射器。
 *
 * 继承 [BinaryDocumentMapper]，以 [com.google.protobuf.DynamicMessage] 为中间表示，
 * 通过 `parse(ByteArray, Class)` / `formatBytes` 在 Java 实体与 Protobuf 二进制之间转换：
 * - 写出：实体/Map/Node -> [com.mingliqiye.document.core.Node] -> `DynamicMessage.toByteArray()`
 * - 读入：`DynamicMessage.parseFrom(descriptor, bytes)` -> [com.mingliqiye.document.core.Node] -> 实体
 *
 * 文本 API（`parse(String)` / `format` / `formatPretty`）不受支持，会抛出
 * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]。
 *
 * 映射规则见 [ProtobufWireCodec]：标量、string/bytes、bool、enum、repeated、map、嵌套 message
 * 以及 optional/required 均有对应处理；解析未知字段的策略由 [unknownFieldPolicy] 决定。
 *
 * @param descriptor 目标 message 的 Protobuf [Descriptor]
 * @param unknownFieldPolicy 解析未知字段的策略，默认 [ProtobufUnknownFieldPolicy.FAIL]
 */
public class ProtobufMapper @JvmOverloads constructor(
    public val descriptor: Descriptor,
    public val unknownFieldPolicy: ProtobufUnknownFieldPolicy = ProtobufUnknownFieldPolicy.FAIL,
) : BinaryDocumentMapper(
    FORMAT_NAME,
    { source -> ProtobufWireCodec.decode(descriptor, source, unknownFieldPolicy) },
    { node, _ -> ProtobufWireCodec.encode(descriptor, node) },
    emptyMap(),
    emptyMap(),
    null,
    null,
    DocumentMapperConfig.empty(),
) {

    /**
     * 使用 [ProtobufSchema] 创建映射器。
     *
     * 通过 [ProtobufSchema.toDescriptor] 生成完整 Descriptor，支持 Schema 内定义的嵌套 message、
     * enum、map 与 oneof；字段缺少类型引用或引用无法解析时抛出
     * [com.mingliqiye.document.format.protobuf.exception.ProtobufException]。
     *
     * @param schema Protobuf Schema
     */
    public constructor(schema: ProtobufSchema) : this(schema.toDescriptor())

    /**
     * 使用 [ProtobufSchema] 与未知字段策略创建映射器。
     *
     * @param schema Protobuf Schema
     * @param unknownFieldPolicy 解析未知字段的策略
     */
    public constructor(
        schema: ProtobufSchema,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
    ) : this(schema.toDescriptor(), unknownFieldPolicy)

    public companion object {

        /** 格式名称。 */
        public const val FORMAT_NAME: String = "protobuf"
    }
}
