package com.mingliqiye.document.format.protobuf

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FileDescriptor
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.protobuf.exception.ProtobufException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Protobuf Schema 完整 Descriptor 生成测试。
 *
 * 覆盖嵌套 message、多级嵌套、enum、map、oneof、递归引用，以及 Schema -> Descriptor ->
 * [ProtobufMapper] round-trip 与 Descriptor -> Schema -> Descriptor round-trip。
 */
class ProtobufDescriptorTest {

    @Test
    fun toDescriptorGeneratesNestedMessageEnumMapAndOneof() {
        val descriptor = richSchema().toDescriptor()
        assertEquals("schema.test.Profile", descriptor.fullName)

        val address = assertNotNull(descriptor.findFieldByName("address"))
        assertEquals(FieldDescriptor.Type.MESSAGE, address.type)
        assertEquals("schema.test.Profile.Address", address.messageType.fullName)

        val status = assertNotNull(descriptor.findFieldByName("status"))
        assertEquals(FieldDescriptor.Type.ENUM, status.type)
        assertEquals("schema.test.Profile.Status", status.enumType.fullName)
        assertEquals(listOf("UNKNOWN", "ACTIVE", "BLOCKED"), status.enumType.values.map { it.name })

        val counts = assertNotNull(descriptor.findFieldByName("counts"))
        assertTrue(counts.isMapField)
        assertEquals(FieldDescriptor.Type.STRING, counts.messageType.findFieldByName("key")?.type)
        assertEquals(FieldDescriptor.Type.INT32, counts.messageType.findFieldByName("value")?.type)

        val addressMap = assertNotNull(descriptor.findFieldByName("addressMap"))
        assertTrue(addressMap.isMapField)
        assertEquals(
            "schema.test.Profile.Address",
            addressMap.messageType.findFieldByName("value")?.messageType?.fullName,
        )

        val statusMap = assertNotNull(descriptor.findFieldByName("statusMap"))
        assertTrue(statusMap.isMapField)
        assertEquals(
            "schema.test.Profile.Status",
            statusMap.messageType.findFieldByName("value")?.enumType?.fullName,
        )

        val preferences = assertNotNull(descriptor.findNestedTypeByName("Preferences"))
        val display = assertNotNull(preferences.findNestedTypeByName("Display"))
        assertEquals("schema.test.Profile.Preferences.Display", display.fullName)
        assertNotNull(descriptor.findNestedTypeByName("CountsEntry"))
        assertNotNull(descriptor.findNestedTypeByName("AddressMapEntry"))
        assertNotNull(descriptor.findEnumTypeByName("Status"))

        val contact = assertNotNull(descriptor.findFieldByName("contact"))
        val email = assertNotNull(descriptor.findFieldByName("email"))
        val oneof = assertNotNull(contact.containingOneof)
        assertEquals("contact_info", oneof.name)
        assertEquals(oneof, email.containingOneof)
        assertNull(assertNotNull(descriptor.findFieldByName("id")).containingOneof)
    }

    @Test
    fun generatedDescriptorRoundTripsThroughMapper() {
        val schema = richSchema()
        val mapper = ProtobufMapper(schema)
        val descriptorMapper = ProtobufMapper(schema.toDescriptor())
        val node = richNode()

        assertEquals(mapper.formatBytes(node).toList(), descriptorMapper.formatBytes(node).toList())
        val decoded = mapper.parse(mapper.formatBytes(node), Node::class.java).asObject()
        assertEquals(node, decoded)
    }

    @Test
    fun oneofRejectsMultipleFieldsAndReadsSingleField() {
        val mapper = ProtobufMapper(richSchema())
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(
                ObjectNode.of(
                    "id" to NumberNode.of(1),
                    "contact" to StringNode.of("a"),
                    "email" to StringNode.of("b"),
                ),
            )
        }

        val bytes = mapper.formatBytes(
            ObjectNode.of(
                "id" to NumberNode.of(1),
                "contact" to StringNode.of("x@y"),
            ),
        )
        val decoded = mapper.parse(bytes, Node::class.java).asObject()
        assertEquals(StringNode.of("x@y"), decoded["contact"])
        assertNull(decoded["email"])
    }

    @Test
    fun recursiveMessageReferenceRoundTrips() {
        val schema = ProtobufSchema.builder("Tree", "schema.tree")
            .field("value", 1, ProtobufType.INT32, required = false)
            .messageField("left", 2, "Tree", required = false)
            .nestedMessage("Leaf") {
                field("label", 1, ProtobufType.STRING, required = false)
            }
            .messageField("leaf", 3, "Leaf", required = false)
            .build()
        val descriptor = schema.toDescriptor()
        assertEquals(descriptor.fullName, assertNotNull(descriptor.findFieldByName("left")).messageType.fullName)
        assertEquals("schema.tree.Tree.Leaf", assertNotNull(descriptor.findFieldByName("leaf")).messageType.fullName)

        val mapper = ProtobufMapper(schema)
        val node = ObjectNode.of(
            "value" to NumberNode.of(1),
            "left" to ObjectNode.of(
                "value" to NumberNode.of(2),
                "leaf" to ObjectNode.of("label" to StringNode.of("leaf-2")),
            ),
        )
        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java).asObject())
    }

    @Test
    fun descriptorSchemaDescriptorRoundTrip() {
        val schema = richSchema()
        val descriptor = schema.toDescriptor()
        val restored = ProtobufSchema.fromDescriptor(descriptor)

        assertEquals("Profile", restored.messageName)
        assertEquals("schema.test", restored.packageName)
        assertEquals(2, restored.nestedMessages.size)
        assertEquals(1, restored.enums.size)
        assertEquals(10, restored.protobufFields.size)

        val address = assertNotNull(restored.nestedMessage("Address"))
        assertEquals(listOf("city", "zip"), address.fields.map { it.name })
        val preferences = assertNotNull(restored.nestedMessage("Preferences"))
        assertEquals(listOf("Display"), preferences.nestedMessages.map { it.name })

        assertEquals(
            ProtobufTypeReference.MessageType("Address"),
            assertNotNull(restored.protobufField("address")).typeReference,
        )
        assertEquals(
            ProtobufTypeReference.EnumType("Status"),
            assertNotNull(restored.protobufField("status")).typeReference,
        )
        assertEquals(
            ProtobufTypeReference.MapType(ProtobufType.STRING, ProtobufType.INT32),
            assertNotNull(restored.protobufField("counts")).typeReference,
        )
        assertEquals(
            ProtobufTypeReference.MapType(
                ProtobufType.STRING,
                ProtobufType.MESSAGE,
                ProtobufTypeReference.MessageType("Address"),
            ),
            assertNotNull(restored.protobufField("addressMap")).typeReference,
        )
        assertEquals(
            ProtobufTypeReference.MapType(
                ProtobufType.STRING,
                ProtobufType.ENUM,
                ProtobufTypeReference.EnumType("Status"),
            ),
            assertNotNull(restored.protobufField("statusMap")).typeReference,
        )
        assertEquals("contact_info", assertNotNull(restored.protobufField("contact")).oneofName)
        assertEquals("contact_info", assertNotNull(restored.protobufField("email")).oneofName)
        assertEquals(
            ProtobufType.STRING,
            assertNotNull(assertNotNull(restored.nestedMessage("Address")).field("city")).protobufType,
        )

        val regenerated = restored.toDescriptor()
        assertEquals(descriptor.toProto(), regenerated.toProto())

        val mapper = ProtobufMapper(regenerated)
        val node = richNode()
        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java).asObject())
    }

    @Test
    fun jsonDescriptionRoundTripsDefinitionsAndTypeReferences() {
        val schema = richSchema()
        val description = schema.toJsonDescription()
        val restored = ProtobufSchema.fromJsonDescription(description)

        assertEquals(description, restored.toJsonDescription())
        assertEquals(schema.toDescriptor().toProto(), restored.toDescriptor().toProto())
    }

    @Test
    fun externalTypeCanBeReferencedThroughDependencyDescriptor() {
        val external = DescriptorProto.newBuilder()
            .setName("External")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("code")
                    .setNumber(1)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING),
            )
            .build()
        val externalEnum = EnumDescriptorProto.newBuilder()
            .setName("Color")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("RED").setNumber(0))
            .addValue(EnumValueDescriptorProto.newBuilder().setName("GREEN").setNumber(1))
            .build()
        val externalFile = FileDescriptor.buildFrom(
            FileDescriptorProto.newBuilder()
                .setName("ext/external.proto")
                .setPackage("ext")
                .addMessageType(external)
                .addEnumType(externalEnum)
                .build(),
            emptyArray(),
        )
        val externalDescriptor = assertNotNull(externalFile.findMessageTypeByName("External"))
        val externalEnumDescriptor = assertNotNull(externalFile.findEnumTypeByName("Color"))
        val schema = ProtobufSchema.builder("Root", "app")
            .field("id", 1, ProtobufType.INT32, required = false)
            .messageField("external", 2, externalDescriptor, required = false)
            .enumField("color", 3, externalEnumDescriptor, required = false)
            .build()
        val descriptor = schema.toDescriptor(arrayOf(externalFile))
        val externalField = assertNotNull(descriptor.findFieldByName("external"))
        assertEquals("ext.External", externalField.messageType.fullName)
        val colorField = assertNotNull(descriptor.findFieldByName("color"))
        assertEquals("ext.Color", colorField.enumType.fullName)

        val mapper = ProtobufMapper(descriptor)
        val node = ObjectNode.of(
            "id" to NumberNode.of(1),
            "external" to ObjectNode.of("code" to StringNode.of("x")),
            "color" to StringNode.of("GREEN"),
        )
        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java).asObject())
    }

    @Test
    fun invalidDefinitionsAreRejected() {
        assertFailsWith<ProtobufException> {
            ProtobufSchema.builder("Bad")
                .field("status", 1, ProtobufType.ENUM)
                .build()
                .toDescriptor()
        }
        assertFailsWith<ProtobufException> {
            ProtobufSchema.builder("Bad")
                .field("child", 1, ProtobufType.MESSAGE)
                .build()
                .toDescriptor()
        }
        assertFailsWith<ProtobufException> {
            ProtobufSchema.builder("Bad")
                .field("entries", 1, ProtobufType.MAP)
                .build()
                .toDescriptor()
        }
        assertFailsWith<ProtobufException> {
            ProtobufSchema.builder("Bad")
                .messageField("child", 1, "Missing")
                .build()
                .toDescriptor()
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.builder("Bad")
                .mapField("entries", 1, ProtobufType.DOUBLE, ProtobufType.STRING)
                .build()
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.builder("Bad")
                .field("tags", 1, ProtobufType.STRING, repeated = true, required = false, oneofName = "o")
                .build()
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.builder("Bad")
                .field("id", 1, ProtobufType.INT32, required = true, oneofName = "o")
                .build()
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.builder("Bad")
                .enumeration("Empty")
                .build()
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.builder("Bad")
                .nestedMessage("Same") { field("id", 1, ProtobufType.INT32, required = false) }
                .enumeration("Same", "UNKNOWN" to 0)
                .build()
        }
    }

    @Test
    fun descriptorFieldLabelsAndTypesMatchSchema() {
        val descriptor = richSchema().toDescriptor()
        assertTrue(assertNotNull(descriptor.findFieldByName("id")).isRequired)
        assertTrue(assertNotNull(descriptor.findFieldByName("address")).isOptional)
        assertTrue(assertNotNull(descriptor.findFieldByName("tags")).isRepeated)
        assertEquals(FieldDescriptor.JavaType.LONG, assertNotNull(descriptor.findFieldByName("id")).javaType)
        assertEquals(10, descriptor.fields.size)
        assertNotNull(descriptor.findFieldByName("address")?.messageType)
    }

    @Test
    fun enumAliasesGenerateAllowAlias() {
        val schema = ProtobufSchema.builder("Alias", "schema.alias")
            .enumeration("Level", "LOW" to 0, "MIN" to 0, "HIGH" to 1)
            .enumField("level", 1, "Level", required = false)
            .build()
        val descriptor = schema.toDescriptor()
        assertTrue(assertNotNull(descriptor.findEnumTypeByName("Level")).options.allowAlias)

        val restored = ProtobufSchema.fromDescriptor(descriptor)
        assertEquals(descriptor.toProto(), restored.toDescriptor().toProto())

        val mapper = ProtobufMapper(descriptor)
        val node = ObjectNode.of("level" to StringNode.of("HIGH"))
        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java).asObject())
    }

    private fun richSchema(): ProtobufSchema = ProtobufSchema.builder("Profile", "schema.test")
        .field("id", 1, ProtobufType.INT64)
        .nestedMessage("Address") {
            field("city", 1, ProtobufType.STRING, required = false)
            field("zip", 2, ProtobufType.INT32, required = false)
        }
        .nestedMessage("Preferences") {
            field("theme", 1, ProtobufType.STRING, required = false)
            nestedMessage("Display") {
                field("scale", 1, ProtobufType.DOUBLE, required = false)
            }
            messageField("display", 2, "Display", required = false)
        }
        .enumeration("Status", "UNKNOWN" to 0, "ACTIVE" to 1, "BLOCKED" to 2)
        .messageField("address", 2, "Address", required = false)
        .enumField("status", 3, "Status", required = false)
        .mapField("counts", 4, ProtobufType.STRING, ProtobufType.INT32)
        .mapField(
            "addressMap",
            5,
            ProtobufType.STRING,
            ProtobufType.MESSAGE,
            ProtobufTypeReference.MessageType("Address"),
        )
        .mapField(
            "statusMap",
            10,
            ProtobufType.STRING,
            ProtobufType.ENUM,
            ProtobufTypeReference.EnumType("Status"),
        )
        .messageField("preferences", 6, "Preferences", required = false)
        .field("tags", 7, ProtobufType.STRING, repeated = true)
        .field("contact", 8, ProtobufType.STRING, required = false, oneofName = "contact_info")
        .field("email", 9, ProtobufType.STRING, required = false, oneofName = "contact_info")
        .build()

    private fun richNode(): ObjectNode = ObjectNode.of(
        "id" to NumberNode.of(1),
        "address" to ObjectNode.of(
            "city" to StringNode.of("杭州"),
            "zip" to NumberNode.of(310_000),
        ),
        "status" to StringNode.of("ACTIVE"),
        "counts" to ObjectNode.of("a" to NumberNode.of(1), "b" to NumberNode.of(2)),
        "addressMap" to ObjectNode.of("home" to ObjectNode.of("city" to StringNode.of("家"))),
        "statusMap" to ObjectNode.of("a" to StringNode.of("ACTIVE")),
        "preferences" to ObjectNode.of(
            "theme" to StringNode.of("dark"),
            "display" to ObjectNode.of("scale" to NumberNode.of(1.5)),
        ),
        "tags" to ArrayNode.of(StringNode.of("x"), StringNode.of("y")),
        "contact" to StringNode.of("x@y"),
    )
}
