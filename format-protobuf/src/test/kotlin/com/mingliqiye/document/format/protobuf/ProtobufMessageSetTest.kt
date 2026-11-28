package com.mingliqiye.document.format.protobuf

import com.google.protobuf.Descriptors.DescriptorValidationException
import com.google.protobuf.DynamicMessage
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.protobuf.exception.ProtobufException
import com.mingliqiye.document.format.protobuf.exception.ProtobufParseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MessageSet wire format 读写测试。
 *
 * MessageSet item 的 wire 结构为 `start group(1) + type_id(2) + message(3) + end group(1)`；
 * 测试同时覆盖 protobuf-java 原生解析、未知 item 策略与非法 wire。
 */
class ProtobufMessageSetTest {

    private val root = ProtobufTestFixtures.messageSetRoot

    private val registry = ProtobufTestFixtures.messageSetRegistry

    @Test
    fun descriptorIsMessageSet() {
        assertTrue(root.options.messageSetWireFormat)
        assertEquals(0, root.fields.size)
        assertEquals(listOf(100, 101), ProtobufTestFixtures.messageSetExtensions.map { it.number })
        assertEquals("ms.payload_a", registry.documentName(ProtobufTestFixtures.messageSetExtensions[0]))

        // MessageSet 同样支持 SIMPLE_NAME 命名。
        val simple = ProtobufExtensionRegistry.of(ProtobufTestFixtures.messageSetFile, ProtobufExtensionNaming.SIMPLE_NAME)
        assertEquals(
            ObjectNode.of("payload_a" to ObjectNode.of("text" to StringNode.of("hi"))),
            ProtobufMapper(root, simple).parse(messageSetItem(100, payloadText("hi")), Node::class.java).asObject(),
        )
    }

    @Test
    fun parseKnownMessageSetItems() {
        val bytes = messageSetItem(100, payloadText("hi")) + messageSetItem(101, payloadCount(3))
        val mapper = ProtobufMapper(root, registry)
        assertEquals(
            ObjectNode.of(
                "ms.payload_a" to ObjectNode.of("text" to StringNode.of("hi")),
                "ms.payload_b" to ObjectNode.of("count" to NumberNode.of(3)),
            ),
            mapper.parse(bytes, Node::class.java).asObject(),
        )

        // 未注册 extension 时 MessageSet item 按未知字段处理。
        assertFailsWith<ProtobufParseException> { ProtobufMapper(root).parse(bytes, Node::class.java) }
        assertEquals(
            ObjectNode.EMPTY,
            ProtobufMapper(root, ProtobufUnknownFieldPolicy.IGNORE).parse(bytes, Node::class.java).asObject(),
        )
    }

    @Test
    fun parseMessageBeforeTypeId() {
        val payload = payloadText("hi")
        // 先写 message（字段 3）再写 type_id（字段 2），protobuf 规范允许该顺序。
        val item = byteArrayOf(0x0B) + bytesOf(0x1A, payload.size) + payload + bytesOf(0x10, 100) + byteArrayOf(0x0C)
        assertEquals(
            ObjectNode.of("ms.payload_a" to ObjectNode.of("text" to StringNode.of("hi"))),
            ProtobufMapper(root, registry).parse(item, Node::class.java).asObject(),
        )
    }

    @Test
    fun formatWritesMessageSetWire() {
        val node = ObjectNode.of(
            "ms.payload_a" to ObjectNode.of("text" to StringNode.of("hi")),
            "ms.payload_b" to ObjectNode.of("count" to NumberNode.of(3)),
        )
        val expected = messageSetItem(100, payloadText("hi")) + messageSetItem(101, payloadCount(3))
        val mapper = ProtobufMapper(root, registry)
        assertContentEquals(expected, mapper.formatBytes(node))

        val message = DynamicMessage.parseFrom(root, expected, registry.protobufRegistry)
        val extension = assertNotNull(ProtobufTestFixtures.messageSetExtensions.firstOrNull { it.number == 100 })
        val payload = message.getField(extension) as DynamicMessage
        assertEquals("hi", payload.getField(ProtobufTestFixtures.messageSetPayload.findFieldByName("text")))
        assertEquals(
            ObjectNode.of(
                "ms.payload_a" to ObjectNode.of("text" to StringNode.of("hi")),
                "ms.payload_b" to ObjectNode.of("count" to NumberNode.of(3)),
            ),
            mapper.parse(expected, Node::class.java).asObject(),
        )
    }

    @Test
    fun unknownMessageSetItemsFollowPolicy() {
        val unknownItem = messageSetItem(102, payloadText("unknown"))
        val mapper = ProtobufMapper(root, registry)
        assertFailsWith<ProtobufParseException> { mapper.parse(unknownItem, Node::class.java) }

        val lenient = ProtobufMapper(root, ProtobufUnknownFieldPolicy.IGNORE, registry)
        assertEquals(ObjectNode.EMPTY, lenient.parse(unknownItem, Node::class.java).asObject())

        val preserved = ProtobufMapper(root, ProtobufUnknownFieldPolicy.PRESERVE, registry)
        val node = preserved.parse(unknownItem, Node::class.java).asObject()
        val unknownFields = assertNotNull(node[ProtobufUnknownFields.FIELD_NAME]).asObject()
        assertEquals(setOf("102"), unknownFields.keys)
        val entry = assertNotNull(unknownFields["102"]).asObject()
        assertEquals(BinaryNode.of(payloadText("unknown")), entry[ProtobufUnknownFields.LENGTH_DELIMITED])
        assertEquals(
            node,
            preserved.parse(preserved.formatBytes(node), Node::class.java).asObject(),
        )
        // 未知 item 数量为 1 时写回字节与原始 wire 完全一致。
        assertContentEquals(unknownItem, preserved.formatBytes(node))
    }

    @Test
    fun knownAndUnknownItemsRoundTrip() {
        val bytes = messageSetItem(100, payloadText("hi")) + messageSetItem(102, payloadText("unknown"))
        val mapper = ProtobufMapper(root, ProtobufUnknownFieldPolicy.PRESERVE, registry)
        val node = mapper.parse(bytes, Node::class.java).asObject()
        assertEquals(StringNode.of("hi"), assertNotNull(node["ms.payload_a"]).asObject()["text"])
        assertNotNull(node[ProtobufUnknownFields.FIELD_NAME])
        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java).asObject())
    }

    @Test
    fun malformedMessageSetThrows() {
        val mapper = ProtobufMapper(root, registry)
        // 只有 start group。
        assertFailsWith<ProtobufParseException> { mapper.parse(byteArrayOf(0x0B), Node::class.java) }
        // 缺少 end group。
        assertFailsWith<ProtobufParseException> {
            mapper.parse(byteArrayOf(0x0B, 0x10, 0x64), Node::class.java)
        }
        // message 字段长度超出剩余字节。
        assertFailsWith<ProtobufParseException> {
            mapper.parse(byteArrayOf(0x0B, 0x10, 0x64, 0x1A, 0x05, 0x01), Node::class.java)
        }
        // MessageSet item 的 payload 内部是畸形 Payload。
        val badPayload = byteArrayOf(0x0A, 0x05, 0x01)
        assertFailsWith<ProtobufParseException> {
            mapper.parse(messageSetItem(100, badPayload), Node::class.java)
        }
    }

    @Test
    fun messageSetSchemaDescriptor() {
        val schema = ProtobufSchema.builder("MessageSetRoot", "ms.schema")
            .messageSetWireFormat()
            .build()
        val descriptor = schema.toDescriptor()
        assertTrue(descriptor.options.messageSetWireFormat)
        assertEquals(0, descriptor.fields.size)

        val restored = ProtobufSchema.fromDescriptor(descriptor)
        assertTrue(restored.messageSetWireFormat)
        assertEquals(descriptor.toProto(), restored.toDescriptor().toProto())
        val fromJson = ProtobufSchema.fromJsonDescription(schema.toJsonDescription())
        assertTrue(fromJson.messageSetWireFormat)
        assertEquals(descriptor.toProto(), fromJson.toDescriptor().toProto())

        val mapper = ProtobufMapper(schema)
        assertContentEquals(ByteArray(0), mapper.formatBytes(ObjectNode.EMPTY))
        assertEquals(ObjectNode.EMPTY, mapper.parse(ByteArray(0), Node::class.java).asObject())

        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.builder("BadMessageSet", "ms.schema")
                .field("id", 1, ProtobufType.INT32)
                .messageSetWireFormat()
                .build()
        }
    }

    @Test
    fun messageSetDescriptorRejectsInvalidExtensionKind() {
        assertFailsWith<DescriptorValidationException> {
            messageSetFileWithVarintExtension()
        }
    }

    @Test
    fun schemaMessageSetWithExtensionRegistryRejectsFieldWrite() {
        val mapper = ProtobufMapper(ProtobufSchema.builder("MessageSetRoot", "ms.schema").messageSetWireFormat().build())
        // MessageSet 只能通过 extension 表达内容，普通字段名不可写。
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("id" to NumberNode.of(1)))
        }
    }

    private fun messageSetItem(typeId: Int, payload: ByteArray): ByteArray = byteArrayOf(0x0B) +
        bytesOf(0x10, typeId) +
        bytesOf(0x1A, payload.size) +
        payload +
        byteArrayOf(0x0C)

    private fun payloadText(text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        return bytesOf(0x0A, textBytes.size) + textBytes
    }

    private fun payloadCount(count: Int): ByteArray = bytesOf(0x10, count)

    private fun bytesOf(vararg values: Int): ByteArray = ByteArray(values.size) { index -> values[index].toByte() }

    private fun messageSetFileWithVarintExtension() {
        val rootProto = com.google.protobuf.DescriptorProtos.DescriptorProto.newBuilder()
            .setName("MessageSetRoot")
            .setOptions(
                com.google.protobuf.DescriptorProtos.MessageOptions.newBuilder().setMessageSetWireFormat(true),
            )
            .addExtensionRange(com.google.protobuf.DescriptorProtos.DescriptorProto.ExtensionRange.newBuilder().setStart(4).setEnd(100))
            .build()
        val extension = com.google.protobuf.DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("bad")
            .setNumber(50)
            .setLabel(com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setType(com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
            .setExtendee(".ms.bad.MessageSetRoot")
            .build()
        val fileProto = com.google.protobuf.DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("ms/bad_message_set.proto")
            .setPackage("ms.bad")
            .setSyntax("proto2")
            .addMessageType(rootProto)
            .addExtension(extension)
            .build()
        com.google.protobuf.Descriptors.FileDescriptor.buildFrom(fileProto, emptyArray())
    }
}
