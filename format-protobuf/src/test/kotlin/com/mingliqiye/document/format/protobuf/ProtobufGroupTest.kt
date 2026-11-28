package com.mingliqiye.document.format.protobuf

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.DynamicMessage
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
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
 * proto2 group wire format 读写测试。
 *
 * group 在 wire 上使用 start-group/end-group 标签；Descriptor 由 DescriptorProto 手工构造或由
 * [ProtobufSchema.groupField] 生成，均不依赖 protoc。
 */
class ProtobufGroupTest {

    private val container: Descriptor = groupDescriptor()

    private fun field(name: String): FieldDescriptor = assertNotNull(container.findFieldByName(name))

    @Test
    fun descriptorExposesGroupFields() {
        val payload = field("payload")
        assertEquals(FieldDescriptor.Type.GROUP, payload.type)
        assertEquals("grp.Container.Payload", payload.messageType.fullName)
        assertTrue(field("items").isRepeated)
        assertEquals(FieldDescriptor.Type.GROUP, field("items").type)
        assertTrue(field("required_group").isRequired)
        assertEquals(FieldDescriptor.Type.GROUP, field("required_group").type)
        assertEquals(FieldDescriptor.Type.INT32, field("id").type)
    }

    @Test
    fun formatWritesStartEndGroupTags() {
        val mapper = ProtobufMapper(container)
        val node = ObjectNode.of(
            "payload" to ObjectNode.of(
                "name" to StringNode.of("p"),
                "value" to NumberNode.of(7),
            ),
            "required_group" to ObjectNode.of("note" to StringNode.of("r")),
            "id" to NumberNode.of(5),
        )
        val payloadField = field("payload")
        val requiredField = field("required_group")
        val actual = mapper.formatBytes(node)
        val expected = DynamicMessage.newBuilder(container)
            .setField(
                payloadField,
                DynamicMessage.newBuilder(payloadField.messageType)
                    .setField(payloadField.messageType.findFieldByName("name"), "p")
                    .setField(payloadField.messageType.findFieldByName("value"), 7)
                    .build(),
            )
            .setField(requiredField, DynamicMessage.newBuilder(requiredField.messageType).setField(requiredField.messageType.findFieldByName("note"), "r").build())
            .setField(field("id"), 5)
            .build()
            .toByteArray()
        assertContentEquals(expected, actual)
        // 字段 1 wire type 3（start group）= 0x0B。
        assertEquals(0x0B.toByte(), actual[0])
        assertEquals(node, mapper.parse(actual, Node::class.java).asObject())
    }

    @Test
    fun parseReadsExternallyBuiltGroupWire() {
        val payload = DynamicMessage.newBuilder(field("payload").messageType)
            .setField(field("payload").messageType.findFieldByName("name"), "external")
            .build()
        val requiredField = field("required_group")
        val message = DynamicMessage.newBuilder(container)
            .setField(field("payload"), payload)
            .setField(requiredField, DynamicMessage.newBuilder(requiredField.messageType).build())
            .setField(field("id"), 9)
            .build()
        val node = ProtobufMapper(container)
            .parse(message.toByteArray(), Node::class.java)
            .asObject()
        assertEquals(
            ObjectNode.of(
                "payload" to ObjectNode.of("name" to StringNode.of("external")),
                "required_group" to ObjectNode.of(),
                "id" to NumberNode.of(9),
            ),
            node,
        )
    }

    @Test
    fun repeatedAndRequiredGroupsRoundTrip() {
        val itemType = field("items").messageType
        val itemTag = itemType.findFieldByName("tag")
        val message = DynamicMessage.newBuilder(container)
            .addRepeatedField(field("items"), DynamicMessage.newBuilder(itemType).setField(itemTag, "a").build())
            .addRepeatedField(field("items"), DynamicMessage.newBuilder(itemType).setField(itemTag, "b").build())
            .setField(
                field("required_group"),
                DynamicMessage.newBuilder(field("required_group").messageType)
                    .setField(field("required_group").messageType.findFieldByName("note"), "required")
                    .build(),
            )
            .build()
        val mapper = ProtobufMapper(container)
        val node = ObjectNode.of(
            "items" to ArrayNode.of(
                ObjectNode.of("tag" to StringNode.of("a")),
                ObjectNode.of("tag" to StringNode.of("b")),
            ),
            "required_group" to ObjectNode.of("note" to StringNode.of("required")),
        )
        val decoded = mapper.parse(message.toByteArray(), Node::class.java).asObject()
        assertEquals(node, decoded)
        assertEquals(decoded, mapper.parse(mapper.formatBytes(decoded), Node::class.java).asObject())

        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("items" to ArrayNode.of(ObjectNode.EMPTY)))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(
                ObjectNode.of(
                    "required_group" to NullNode.INSTANCE,
                ),
            )
        }
        assertFailsWith<ProtobufParseException> {
            mapper.parse(DynamicMessage.newBuilder(container).buildPartial().toByteArray(), Node::class.java)
        }
    }

    @Test
    fun groupCountsTowardsMaxDepth() {
        val requiredField = field("required_group")
        val message = DynamicMessage.newBuilder(container)
            .setField(
                field("payload"),
                DynamicMessage.newBuilder(field("payload").messageType)
                    .setField(field("payload").messageType.findFieldByName("name"), "p")
                    .build(),
            )
            .setField(requiredField, DynamicMessage.newBuilder(requiredField.messageType).build())
            .build()
        val bytes = message.toByteArray()
        assertFailsWith<ProtobufParseException> {
            ProtobufMapper(container, ProtobufUnknownFieldPolicy.FAIL, maxDepth = 1)
                .parse(bytes, Node::class.java)
        }
        ProtobufMapper(container, ProtobufUnknownFieldPolicy.FAIL, maxDepth = 2)
            .parse(bytes, Node::class.java)
    }

    @Test
    fun malformedGroupWireThrows() {
        val mapper = ProtobufMapper(container)
        // 只有 start group 标签，缺少 end group。
        assertFailsWith<ProtobufParseException> {
            mapper.parse(byteArrayOf(0x0B), Node::class.java)
        }
        // start group 字段 1 用 end group 字段 2（0x14）结束，字段编号不匹配。
        assertFailsWith<ProtobufParseException> {
            mapper.parse(byteArrayOf(0x0B, 0x14), Node::class.java)
        }
        // group 值必须是对象节点。
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("payload" to StringNode.of("not-object")))
        }
        // group 元素必须是对象节点。
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("items" to ArrayNode.of(NumberNode.of(1))))
        }
    }

    @Test
    fun schemaGeneratesGroupDescriptorAndRoundTrips() {
        val schema = ProtobufSchema.builder("Container", "schema.grp")
            .field("id", 3, ProtobufType.INT32, required = false)
            .nestedMessage("Payload") {
                field("name", 1, ProtobufType.STRING, required = false)
                field("value", 2, ProtobufType.INT32, required = false)
            }
            .groupField("payload", 1, "Payload")
            .build()
        val descriptor = schema.toDescriptor()
        val payload = assertNotNull(descriptor.findFieldByName("payload"))
        assertEquals(FieldDescriptor.Type.GROUP, payload.type)
        assertEquals("schema.grp.Container.Payload", payload.messageType.fullName)

        val mapper = ProtobufMapper(schema)
        val node = ObjectNode.of(
            "id" to NumberNode.of(3),
            "payload" to ObjectNode.of("name" to StringNode.of("x"), "value" to NumberNode.of(4)),
        )
        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java).asObject())

        val restored = ProtobufSchema.fromDescriptor(descriptor)
        val restoredPayload = assertNotNull(restored.protobufField("payload"))
        assertEquals(ProtobufType.GROUP, restoredPayload.protobufType)
        assertEquals(
            ProtobufTypeReference.MessageType("Payload"),
            restoredPayload.typeReference,
        )
        assertEquals(descriptor.toProto(), restored.toDescriptor().toProto())
        assertEquals(
            ProtobufSchema.fromJsonDescription(schema.toJsonDescription()).toDescriptor().toProto(),
            descriptor.toProto(),
        )
        assertEquals(ProtobufType.GROUP, ProtobufSchema.fromJsonDescription(schema.toJsonDescription()).protobufField("payload")?.protobufType)
    }

    @Test
    fun groupSchemaValidationRejectsInvalidDeclarations() {
        assertFailsWith<ProtobufException> {
            ProtobufSchema.builder("Bad")
                .field("payload", 1, ProtobufType.GROUP)
                .build()
                .toDescriptor()
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.builder("Bad")
                .mapField("entries", 1, ProtobufType.STRING, ProtobufType.GROUP)
                .build()
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchemaField(
                name = "payload",
                fieldNumber = 1,
                protobufType = ProtobufType.GROUP,
                typeReference = ProtobufTypeReference.MessageType("Payload"),
                oneofName = "o",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchemaField(
                name = "payload",
                fieldNumber = 1,
                protobufType = ProtobufType.GROUP,
                typeReference = ProtobufTypeReference.EnumType("E"),
            )
        }
    }

    private fun groupDescriptor(): Descriptor {
        fun fieldProto(
            name: String,
            number: Int,
            type: FieldDescriptorProto.Type,
            label: FieldDescriptorProto.Label = FieldDescriptorProto.Label.LABEL_OPTIONAL,
            typeName: String? = null,
        ): FieldDescriptorProto {
            val builder = FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setLabel(label)
                .setType(type)
            if (typeName != null) {
                builder.setTypeName(typeName)
            }
            return builder.build()
        }

        val payload = DescriptorProto.newBuilder()
            .setName("Payload")
            .addField(fieldProto("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(fieldProto("value", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .build()
        val item = DescriptorProto.newBuilder()
            .setName("Item")
            .addField(fieldProto("tag", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build()
        val mandatory = DescriptorProto.newBuilder()
            .setName("Mandatory")
            .addField(fieldProto("note", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build()
        val containerProto = DescriptorProto.newBuilder()
            .setName("Container")
            .addField(fieldProto("payload", 1, FieldDescriptorProto.Type.TYPE_GROUP, typeName = ".grp.Container.Payload"))
            .addField(
                fieldProto(
                    "items",
                    2,
                    FieldDescriptorProto.Type.TYPE_GROUP,
                    label = FieldDescriptorProto.Label.LABEL_REPEATED,
                    typeName = ".grp.Container.Item",
                ),
            )
            .addField(fieldProto("id", 3, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(
                fieldProto(
                    "required_group",
                    4,
                    FieldDescriptorProto.Type.TYPE_GROUP,
                    label = FieldDescriptorProto.Label.LABEL_REQUIRED,
                    typeName = ".grp.Container.Mandatory",
                ),
            )
            .addNestedType(payload)
            .addNestedType(item)
            .addNestedType(mandatory)
            .build()
        val fileProto = FileDescriptorProto.newBuilder()
            .setName("grp/group.proto")
            .setPackage("grp")
            .setSyntax("proto2")
            .addMessageType(containerProto)
            .build()
        return com.google.protobuf.Descriptors.FileDescriptor.buildFrom(fileProto, emptyArray())
            .findMessageTypeByName("Container")!!
    }
}
