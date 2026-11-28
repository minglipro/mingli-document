package com.mingliqiye.document.format.protobuf

import com.google.protobuf.Descriptors.Descriptor
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * Protobuf 二进制文档映射器。
 *
 * 继承 [BinaryDocumentMapper]，以 [com.google.protobuf.DynamicMessage] 为中间表示，
 * 通过 `parse(ByteArray, Class)` / `formatBytes` 在 Java 实体与 Protobuf 二进制之间转换：
 * - 写出：实体/Map/Node -> [com.mingliqiye.document.core.Node] -> `DynamicMessage.toByteArray()`
 * - 读入：`DynamicMessage.parseFrom(descriptor, bytes, extensionRegistry)` ->
 *   [com.mingliqiye.document.core.Node] -> 实体
 *
 * 文本 API（`parse(String)` / `format` / `formatPretty`）不受支持，会抛出
 * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]。
 *
 * 映射规则见 [ProtobufWireCodec]：标量、string/bytes、bool、enum、repeated、map、嵌套 message、
 * proto2 group 以及 extension 均有对应处理；解析未知字段的策略由 [unknownFieldPolicy] 决定。
 *
 * ## extension
 *
 * 通过 [extensions] 传入 [ProtobufExtensionRegistry] 后，注册的 extension 会以对象字段形式
 * 读写；默认字段名是 extension 全名（见 [ProtobufExtensionNaming]）。未注册的 extension 编号
 * 仍按未知字段策略处理。注册表必须与 [descriptor] 来自同一个 Descriptor/FileDescriptor 树
 * （Protobuf 按 Descriptor 实例匹配 extension），否则编号会被当作未知字段。示例：
 *
 * ```kotlin
 * val registry = ProtobufExtensionRegistry.of(fileDescriptor)
 * val descriptor = fileDescriptor.findMessageTypeByName("Base")
 * val mapper = ProtobufMapper(descriptor, registry)
 * val node = mapper.parse(bytes, Node::class.java)
 * ```
 *
 * ## MessageSet
 *
 * descriptor 带 `option message_set_wire_format = true` 时自动按 MessageSet wire format 读写：
 * 已知 extension 映射为对象字段，未知 item 由未知字段策略处理（`PRESERVE` 时保留并支持再写出）。
 *
 * @param descriptor 目标 message 的 Protobuf [Descriptor]
 * @param unknownFieldPolicy 解析与写出未知字段的策略，默认 [ProtobufUnknownFieldPolicy.FAIL]
 * @param maxDepth 解析最大嵌套深度，`0` 表示不限制
 * @param extensions extension 注册表，默认 [ProtobufExtensionRegistry.EMPTY]
 */
public class ProtobufMapper @JvmOverloads constructor(
    public val descriptor: Descriptor,
    public val unknownFieldPolicy: ProtobufUnknownFieldPolicy = ProtobufUnknownFieldPolicy.FAIL,
    public val maxDepth: Int = 0,
    public val extensions: ProtobufExtensionRegistry = ProtobufExtensionRegistry.EMPTY,
) : BinaryDocumentMapper(
    FORMAT_NAME,
    { source -> ProtobufWireCodec.decode(descriptor, source, unknownFieldPolicy, maxDepth, extensions) },
    { node, _ -> ProtobufWireCodec.encode(descriptor, node, unknownFieldPolicy, extensions) },
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
     * enum、map、group 与 oneof；字段缺少类型引用或引用无法解析时抛出
     * [com.mingliqiye.document.format.protobuf.exception.ProtobufException]。
     *
     * @param schema Protobuf Schema
     */
    public constructor(schema: ProtobufSchema) : this(schema.toDescriptor())

    /**
     * 使用 [ProtobufSchema] 与未知字段策略创建映射器。
     *
     * @param schema Protobuf Schema
     * @param unknownFieldPolicy 解析与写出未知字段的策略
     */
    public constructor(
        schema: ProtobufSchema,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
    ) : this(schema.toDescriptor(), unknownFieldPolicy)

    /**
     * 使用 [Descriptor] 与 extension 注册表创建映射器。
     *
     * @param descriptor 目标 message 的 Protobuf Descriptor
     * @param extensions extension 注册表
     */
    public constructor(
        descriptor: Descriptor,
        extensions: ProtobufExtensionRegistry,
    ) : this(descriptor, ProtobufUnknownFieldPolicy.FAIL, 0, extensions)

    /**
     * 使用 [Descriptor]、未知字段策略与 extension 注册表创建映射器。
     *
     * @param descriptor 目标 message 的 Protobuf Descriptor
     * @param unknownFieldPolicy 解析与写出未知字段的策略
     * @param extensions extension 注册表
     */
    public constructor(
        descriptor: Descriptor,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        extensions: ProtobufExtensionRegistry,
    ) : this(descriptor, unknownFieldPolicy, 0, extensions)

    /**
     * 使用显式 Descriptor 将字节解析为 Node。
     *
     * 允许同一个 Mapper 实例处理多个 message 类型，无需为每个 Descriptor 创建 Mapper。
     *
     * @param source Protobuf 二进制
     * @param descriptor 本次解析使用的 message Descriptor
     * @return 文档树
     */
    public fun parseToNode(source: ByteArray, descriptor: Descriptor): Node {
        checkInputSize(source.size.toLong())
        return ProtobufWireCodec.decode(descriptor, source, unknownFieldPolicy, maxDepth, extensions)
    }

    /**
     * 使用显式 Descriptor 将字节解析为 Bean。
     */
    public fun <T> parse(source: ByteArray, descriptor: Descriptor, targetType: Class<T>): T = fromNode(parseToNode(source, descriptor), targetType)

    /**
     * 使用显式 Descriptor 将字节解析为泛型 Bean。
     */
    public fun <T> parse(
        source: ByteArray,
        descriptor: Descriptor,
        targetType: DocumentTypeReference<T>,
    ): T = fromNode(parseToNode(source, descriptor), targetType)

    /**
     * 使用显式 Descriptor 将值写成 Protobuf 字节。
     *
     * @param value Java 实体、Map、集合或 Node
     * @param descriptor 本次写出使用的 message Descriptor
     * @return Protobuf 二进制
     */
    public fun formatBytes(value: Any?, descriptor: Descriptor): ByteArray = ProtobufWireCodec.encode(descriptor, toNode(value), unknownFieldPolicy, extensions)

    public companion object {

        /** 格式名称。 */
        public const val FORMAT_NAME: String = "protobuf"
    }
}
