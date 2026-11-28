package com.mingliqiye.document.format.protobuf

import com.google.protobuf.CodedInputStream
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.WireFormat
import com.mingliqiye.document.format.protobuf.exception.ProtobufParseException
import java.io.ByteArrayOutputStream

/**
 * MessageSet wire format 的额外校验。
 *
 * protobuf-java 原生 [DynamicMessage] 支持 MessageSet 解析，但已知 extension 的 payload 采用
 * LazyField 惰性解析：payload 非法时会被静默替换为默认实例。为满足“畸形 wire 必须报错”的语义，
 * 在交给原生解析器前先做一次结构化校验：
 * - item 必须以 start-group(1) 开始、end-group(1) 结束，缺少结束标签或字段编号不匹配时报错
 * - 已知 extension 的 message payload 必须能按扩展消息描述符解析，否则报错
 *
 * 校验只做结构性检查，不重复产出 Node；映射仍由 [DynamicMessage] + [ProtobufWireCodec] 完成，
 * 因此未知 item 的未知字段策略与原生命令保持一致。
 */
internal object ProtobufMessageSetSupport {

    private const val MESSAGE_SET_ITEM_TAG: Int = (1 shl 3) or WireFormat.WIRETYPE_START_GROUP
    private const val MESSAGE_SET_ITEM_END_TAG: Int = (1 shl 3) or WireFormat.WIRETYPE_END_GROUP
    private const val MESSAGE_SET_TYPE_ID_TAG: Int = (2 shl 3) or WireFormat.WIRETYPE_VARINT
    private const val MESSAGE_SET_MESSAGE_TAG: Int = (3 shl 3) or WireFormat.WIRETYPE_LENGTH_DELIMITED

    /**
     * 校验 MessageSet 二进制结构。
     *
     * @param descriptor MessageSet 消息描述符
     * @param source Protobuf 二进制内容
     * @param extensions extension 注册表
     * @throws ProtobufParseException 结构非法或已知 extension 的 payload 无法解析时抛出
     */
    fun validate(descriptor: Descriptor, source: ByteArray, extensions: ProtobufExtensionRegistry) {
        val input = CodedInputStream.newInstance(source)
        try {
            while (true) {
                val tag = input.readTag()
                if (tag == 0) {
                    return
                }
                if (tag == MESSAGE_SET_ITEM_TAG) {
                    validateItem(input, descriptor, extensions)
                } else if (!input.skipField(tag)) {
                    throw ProtobufParseException("MessageSet ${descriptor.fullName} 顶层出现非法的 end-group 标签")
                }
            }
        } catch (e: InvalidProtocolBufferException) {
            throw ProtobufParseException("解析 Protobuf MessageSet ${descriptor.fullName} 失败: ${e.message}", e)
        }
    }

    private fun validateItem(
        input: CodedInputStream,
        descriptor: Descriptor,
        extensions: ProtobufExtensionRegistry,
    ) {
        var typeId = 0
        val payload = ByteArrayOutputStream()
        while (true) {
            val tag = input.readTag()
            if (tag == 0) {
                throw ProtobufParseException(
                    "MessageSet ${descriptor.fullName} 的 item 缺少 end-group 标签",
                )
            }
            when (tag) {
                MESSAGE_SET_ITEM_END_TAG -> break
                MESSAGE_SET_TYPE_ID_TAG -> typeId = input.readUInt32()
                MESSAGE_SET_MESSAGE_TAG -> payload.write(input.readBytes().toByteArray())
                else -> if (!input.skipField(tag)) {
                    throw ProtobufParseException(
                        "MessageSet ${descriptor.fullName} 的 item 出现非法的 end-group 标签",
                    )
                }
            }
        }
        if (typeId == 0) {
            return
        }
        val extension = extensions.extension(descriptor, typeId) ?: return
        val messageType = extension.messageType
            ?: throw ProtobufParseException(
                "MessageSet ${descriptor.fullName} 的 extension ${extension.fullName} 不是 message 类型",
            )
        try {
            DynamicMessage.newBuilder(messageType)
                .mergeFrom(payload.toByteArray(), extensions.protobufRegistry)
                .buildPartial()
        } catch (e: InvalidProtocolBufferException) {
            throw ProtobufParseException(
                "MessageSet ${descriptor.fullName} 的 item($typeId) payload 无法按 ${messageType.fullName} 解析: ${e.message}",
                e,
            )
        }
    }
}
