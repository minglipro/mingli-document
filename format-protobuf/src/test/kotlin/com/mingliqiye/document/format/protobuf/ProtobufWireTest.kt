package com.mingliqiye.document.format.protobuf

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.MessageOptions
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.EnumValueDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.DynamicMessage
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.protobuf.exception.ProtobufException
import com.mingliqiye.document.format.protobuf.exception.ProtobufParseException
import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [ProtobufMapper] 与 wire 编解码测试。
 *
 * Descriptor 直接通过 DescriptorProto 构造，不依赖 protoc 与生成代码。
 */
class ProtobufWireTest {

    @Test
    fun formatBytesWritesWireAndParseRestoresNode() {
        val mapper = ProtobufMapper(personDescriptor)
        assertEquals("protobuf", mapper.formatName)
        assertEquals(ProtobufUnknownFieldPolicy.FAIL, mapper.unknownFieldPolicy)
        assertEquals(personDescriptor, mapper.descriptor)

        val node = fullNode()
        val bytes = mapper.formatBytes(node)
        assertTrue(bytes.isNotEmpty())

        val message = DynamicMessage.parseFrom(personDescriptor, bytes)
        assertEquals("小于", message.getField(field("name")))
        assertEquals(18, message.getField(field("age")))
        assertEquals(-99L, message.getField(field("balance")))
        assertEquals(-1, message.getField(field("flags")))
        assertEquals(-1L, message.getField(field("big")))
        assertEquals(-7, message.getField(field("delta")))
        assertEquals(-123, message.getField(field("offset")))
        assertEquals(3.5, message.getField(field("score")))
        assertEquals(0.25f, message.getField(field("ratio")))
        assertEquals(true, message.getField(field("active")))
        assertEquals("ACTIVE", (message.getField(field("status")) as EnumValueDescriptor).name)
        assertEquals(2, (message.getField(field("addresses")) as List<*>).size)
        assertEquals(2, (message.getField(field("counts")) as List<*>).size)
        assertEquals(1, (message.getField(field("addressMap")) as List<*>).size)

        val decoded = mapper.parse(bytes, Node::class.java).asObject()
        assertEquals(node, decoded)
    }

    @Test
    fun formatBytesAcceptsBeanMapAndNodeInputs() {
        val mapper = ProtobufMapper(personDescriptor)
        val node = ObjectNode.of("age" to NumberNode.of(1), "name" to StringNode.of("node"))
        val nodeBytes = mapper.formatBytes(node)
        val mapBytes = mapper.formatBytes(mapOf("age" to 1, "name" to "node"))

        assertContentEquals(nodeBytes, mapBytes)
        assertEquals(node, mapper.parse(mapBytes, Node::class.java).asObject())

        val beanBytes = mapper.formatBytes(
            PersonBean().apply {
                age = 1
                name = "node"
            },
        )
        assertEquals(node, mapper.parse(beanBytes, Node::class.java).asObject())
    }

    @Test
    fun parseReadsExternallyBuiltDynamicMessage() {
        val statusField = field("status")
        val message = DynamicMessage.newBuilder(personDescriptor)
            .setField(field("name"), "external")
            .setField(field("age"), 7)
            .setField(statusField, statusField.enumType.findValueByName("ACTIVE"))
            .build()

        val decoded = ProtobufMapper(personDescriptor)
            .parse(message.toByteArray(), Node::class.java)
            .asObject()
        assertEquals(
            ObjectNode.of(
                "name" to StringNode.of("external"),
                "age" to NumberNode.of(7),
                "status" to StringNode.of("ACTIVE"),
            ),
            decoded,
        )
    }

    @Test
    fun parseSupportsDocumentTypeReference() {
        val mapper = ProtobufMapper(personDescriptor)
        val bean = PersonBean().apply {
            name = "type-ref"
            age = 6
        }
        val restored = mapper.parse(
            mapper.formatBytes(bean),
            DocumentTypeReference.of<PersonBean>(),
        )
        assertEquals("type-ref", restored.name)
        assertEquals(6, restored.age)
    }

    @Test
    fun nestedMessageRepeatedAndMapRoundTrip() {
        val mapper = ProtobufMapper(personDescriptor)
        val node = ObjectNode.of(
            "address" to ObjectNode.of(
                "city" to StringNode.of("上海"),
                "zip" to NumberNode.of(200_000),
            ),
            "addresses" to ArrayNode.of(
                ObjectNode.of("city" to StringNode.of("北京")),
                ObjectNode.of("city" to StringNode.of("广州"), "zip" to NumberNode.of(1)),
            ),
            "tags" to ArrayNode.of(StringNode.of("x"), StringNode.of("y")),
            "nums" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2), NumberNode.of(3)),
            "counts" to ObjectNode.of("a" to NumberNode.of(1), "b" to NumberNode.of(2)),
            "addressMap" to ObjectNode.of(
                "home" to ObjectNode.of("city" to StringNode.of("家")),
                "work" to ObjectNode.of("city" to StringNode.of("公司"), "zip" to NumberNode.of(9)),
            ),
            "int64Counts" to ObjectNode.of("5" to StringNode.of("five"), "-3" to StringNode.of("minus")),
        )

        val decoded = mapper.parse(mapper.formatBytes(node), Node::class.java).asObject()
        assertEquals(node, decoded)
    }

    @Test
    fun optionalFieldsUsePresenceAndRequiredFieldsAreValidated() {
        val mapper = ProtobufMapper(legacyDescriptor)

        val requiredOnly = ObjectNode.of("id" to NumberNode.of(1))
        val decoded = mapper.parse(mapper.formatBytes(requiredOnly), Node::class.java).asObject()
        assertEquals(setOf("id"), decoded.keys)
        assertEquals(NumberNode.of(1), decoded["id"])

        val withOptional = ObjectNode.of(
            "id" to NumberNode.of(2),
            "name" to StringNode.of("legacy"),
            "values" to ArrayNode.of(NumberNode.of(10), NumberNode.of(20)),
        )
        val decodedOptional = mapper.parse(mapper.formatBytes(withOptional), Node::class.java).asObject()
        assertEquals(withOptional, decodedOptional)

        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("name" to StringNode.of("missing-id")))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("id" to NullNode.INSTANCE))
        }
        assertFailsWith<ProtobufParseException> {
            mapper.parse(byteArrayOf(0x12, 0x01, 0x78), Node::class.java)
        }
    }

    @Test
    fun enumMapsByNameAndNumberAndOpenEnumValues() {
        val mapper = ProtobufMapper(personDescriptor)
        val statusField = field("status")

        val bytes = mapper.formatBytes(ObjectNode.of("status" to StringNode.of("BLOCKED")))
        assertEquals("BLOCKED", (DynamicMessage.parseFrom(personDescriptor, bytes).getField(statusField) as EnumValueDescriptor).name)
        assertEquals(
            ObjectNode.of("status" to StringNode.of("BLOCKED")),
            mapper.parse(bytes, Node::class.java).asObject(),
        )

        val byNumber = mapper.formatBytes(ObjectNode.of("status" to NumberNode.of(2)))
        assertEquals(
            ObjectNode.of("status" to StringNode.of("BLOCKED")),
            mapper.parse(byNumber, Node::class.java).asObject(),
        )

        val unknownNumber = mapper.formatBytes(ObjectNode.of("status" to NumberNode.of(42)))
        assertEquals(
            ObjectNode.of("status" to NumberNode.of(42)),
            mapper.parse(unknownNumber, Node::class.java).asObject(),
        )

        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("status" to StringNode.of("MISSING")))
        }
    }

    @Test
    fun bytesAcceptBinaryNodeAndBase64String() {
        val mapper = ProtobufMapper(personDescriptor)
        val payload = byteArrayOf(0, 1, 2, 127, -1)
        val binaryBytes = mapper.formatBytes(ObjectNode.of("data" to BinaryNode.of(payload)))
        assertEquals(
            ObjectNode.of("data" to BinaryNode.of(payload)),
            mapper.parse(binaryBytes, Node::class.java).asObject(),
        )

        val base64Bytes = mapper.formatBytes(ObjectNode.of("data" to StringNode.of("AAECf/8=")))
        assertEquals(
            ObjectNode.of("data" to BinaryNode.of(payload)),
            mapper.parse(base64Bytes, Node::class.java).asObject(),
        )

        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("data" to StringNode.of("not-base64!")))
        }
    }

    @Test
    fun beanRoundTripsThroughWire() {
        val mapper = ProtobufMapper(personDescriptor)
        val bean = PersonBean().apply {
            name = "小于"
            age = 18
            balance = -99L
            flags = 4_294_967_295L
            big = BigInteger("18446744073709551615")
            delta = -7
            hash = 1_234_567_890_123_456_789L
            offset = -123
            score = 3.5
            ratio = 0.25f
            active = true
            data = byteArrayOf(0, 1, 2, 127, -1)
            status = "ACTIVE"
            address = AddressBean().apply {
                city = "杭州"
                zip = 310_000
            }
            tags = listOf("a", "b")
            addresses = listOf(
                AddressBean().apply { city = "A" },
                AddressBean().apply {
                    city = "B"
                    zip = 1
                },
            )
            nums = listOf(1, 2, 3)
            counts = linkedMapOf("a" to 1, "b" to 2)
            addressMap = linkedMapOf(
                "home" to AddressBean().apply { city = "家" },
            )
            int64Counts = linkedMapOf(5L to "five", -3L to "minus")
        }

        val restored = mapper.parse(mapper.formatBytes(bean), PersonBean::class.java)
        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertEquals(-99L, restored.balance)
        assertEquals(4_294_967_295L, restored.flags)
        assertEquals(BigInteger("18446744073709551615"), restored.big)
        assertEquals(-7, restored.delta)
        assertEquals(1_234_567_890_123_456_789L, restored.hash)
        assertEquals(-123, restored.offset)
        assertEquals(3.5, restored.score)
        assertEquals(0.25f, restored.ratio)
        assertEquals(true, restored.active)
        assertContentEquals(byteArrayOf(0, 1, 2, 127, -1), restored.data)
        assertEquals("ACTIVE", restored.status)
        assertEquals("杭州", restored.address?.city)
        assertEquals(310_000, restored.address?.zip)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals(listOf("A", "B"), restored.addresses.map { it.city })
        assertEquals(listOf(0, 1), restored.addresses.map { it.zip })
        assertEquals(listOf(1, 2, 3), restored.nums)
        assertEquals(mapOf("a" to 1, "b" to 2), restored.counts)
        assertEquals("家", restored.addressMap["home"]?.city)
        assertEquals(mapOf(5L to "five", -3L to "minus"), restored.int64Counts)
    }

    @Test
    fun beanWithUndeclaredFieldIsRejected() {
        assertFailsWith<ProtobufException> {
            ProtobufMapper(personDescriptor).formatBytes(UnknownFieldBean())
        }
    }

    @Test
    fun malformedBytesThrowProtobufParseException() {
        val mapper = ProtobufMapper(personDescriptor)
        assertFailsWith<ProtobufParseException> {
            mapper.parse(byteArrayOf(0x0A, 0x05, 0x01), Node::class.java)
        }
        assertFailsWith<ProtobufParseException> {
            mapper.parse(byteArrayOf(0x00), Node::class.java)
        }
        assertFailsWith<ProtobufParseException> {
            mapper.parse(byteArrayOf(0xFF.toByte()), Node::class.java)
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("age" to NumberNode.ofRaw("not-a-number")))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("age" to NumberNode.ofRaw("3000000000")))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("big" to NumberNode.of(-1)))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("flags" to NumberNode.of(-1)))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("data" to NumberNode.of(1)))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("tags" to StringNode.of("not-array")))
        }
    }

    @Test
    fun nonFiniteWireNumbersCannotBeMappedToNode() {
        val mapper = ProtobufMapper(personDescriptor)
        // 字段 9（score，double）tag=(9<<3)|1=0x49，后接 0x7FF8000000000000 的小端 NaN。
        val nanBytes = byteArrayOf(0x49, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF8.toByte(), 0x7F)
        assertFailsWith<ProtobufParseException> {
            mapper.parse(nanBytes, Node::class.java)
        }
    }

    @Test
    fun unknownWireFieldsFollowPolicy() {
        val strictMapper = ProtobufMapper(personDescriptor)
        val baseBytes = strictMapper.formatBytes(ObjectNode.of("age" to NumberNode.of(1)))
        // 未知字段编号 99，wire type 0：key=(99<<3)|0=792 -> varint 0x98 0x06，value=1。
        val bytesWithUnknown = baseBytes + byteArrayOf(0x98.toByte(), 0x06, 0x01)
        assertFailsWith<ProtobufParseException> {
            strictMapper.parse(bytesWithUnknown, Node::class.java)
        }

        val lenientMapper = ProtobufMapper(personDescriptor, ProtobufUnknownFieldPolicy.IGNORE)
        assertEquals(
            ObjectNode.of("age" to NumberNode.of(1)),
            lenientMapper.parse(bytesWithUnknown, Node::class.java).asObject(),
        )
    }

    @Test
    fun textApisAreNotSupported() {
        val mapper: BinaryDocumentMapper = ProtobufMapper(personDescriptor)
        assertFailsWith<DocumentSerializationException> { mapper.format(ObjectNode.EMPTY) }
        assertFailsWith<DocumentSerializationException> { mapper.formatPretty(ObjectNode.EMPTY) }
        assertFailsWith<DocumentSerializationException> { mapper.parse("{}", Node::class.java) }
    }

    @Test
    fun schemaToDescriptorAndMapperRoundTrip() {
        val schema = ProtobufSchema.builder("Record", "wiretest3")
            .field("id", 1, ProtobufType.INT64)
            .field("title", 2, ProtobufType.STRING)
            .field("tags", 3, ProtobufType.STRING, repeated = true)
            .field("payload", 4, ProtobufType.BYTES)
            .field("score", 5, ProtobufType.DOUBLE, required = false)
            .field("note", 6, ProtobufType.STRING, required = false)
            .build()

        val descriptor = schema.toDescriptor()
        assertEquals("wiretest3.Record", descriptor.fullName)
        assertEquals(6, descriptor.fields.size)

        val node = ObjectNode.of(
            "id" to NumberNode.of(9),
            "title" to StringNode.of("title"),
            "tags" to ArrayNode.of(StringNode.of("t1"), StringNode.of("t2")),
            "payload" to BinaryNode.of(byteArrayOf(1, 2, 3)),
            "score" to NumberNode.of(1.5),
            "note" to StringNode.of("note"),
        )
        val schemaMapper = ProtobufMapper(schema)
        val descriptorMapper = ProtobufMapper(descriptor)
        assertContentEquals(schemaMapper.formatBytes(node), descriptorMapper.formatBytes(node))
        assertEquals(node, schemaMapper.parse(schemaMapper.formatBytes(node), Node::class.java).asObject())

        val lenient = ProtobufMapper(schema, ProtobufUnknownFieldPolicy.IGNORE)
        assertEquals(node, lenient.parse(lenient.formatBytes(node), Node::class.java).asObject())

        val restored = ProtobufSchema.fromDescriptor(descriptor)
        assertEquals("Record", restored.messageName)
        assertEquals("wiretest3", restored.packageName)
        assertEquals("wiretest3.Record", restored.messageFullName)
        assertEquals(6, restored.protobufFields.size)
        assertEquals(ProtobufType.INT64, assertNotNull(restored.protobufField("id")).protobufType)
        assertEquals(true, assertNotNull(restored.protobufField("id")).required)
        assertTrue(assertNotNull(restored.protobufField("tags")).repeated)
        assertEquals(false, assertNotNull(restored.protobufField("score")).required)
        assertEquals(true, assertNotNull(restored.protobufField("score")).nullable)

        val nestedSchema = ProtobufSchema.builder("Outer.Inner", "wiretest4")
            .field("id", 1, ProtobufType.INT32)
            .build()
        val nestedDescriptor = nestedSchema.toDescriptor()
        assertEquals("wiretest4.Outer.Inner", nestedDescriptor.fullName)
        assertEquals("wiretest4.Outer.Inner", ProtobufSchema.fromDescriptor(nestedDescriptor).messageFullName)
    }

    @Test
    fun schemaWithCompositeFieldsCannotBuildDescriptor() {
        val messageSchema = ProtobufSchema.builder("Bad")
            .field("child", 1, ProtobufType.MESSAGE)
            .build()
        assertFailsWith<ProtobufException> { messageSchema.toDescriptor() }
        assertFailsWith<ProtobufException> { ProtobufMapper(messageSchema) }

        val mapSchema = ProtobufSchema.builder("BadMap")
            .field("entries", 1, ProtobufType.MAP)
            .build()
        assertFailsWith<ProtobufException> { mapSchema.toDescriptor() }
    }

    @Test
    fun fromDescriptorMapsFieldsOfDescriptor() {
        val schema = ProtobufSchema.fromDescriptor(personDescriptor)
        assertEquals("Person", schema.messageName)
        assertEquals("wiretest", schema.packageName)
        assertEquals("wiretest.Person", schema.messageFullName)
        assertEquals(20, schema.protobufFields.size)

        assertEquals(ProtobufType.STRING, assertNotNull(schema.protobufField("name")).protobufType)
        assertEquals(ProtobufType.UINT32, assertNotNull(schema.protobufField("flags")).protobufType)
        assertEquals(ProtobufType.ENUM, assertNotNull(schema.protobufField("status")).protobufType)
        assertEquals(ProtobufType.MESSAGE, assertNotNull(schema.protobufField("address")).protobufType)
        assertTrue(assertNotNull(schema.protobufField("address")).nullable)
        assertEquals(ProtobufType.MAP, assertNotNull(schema.protobufField("counts")).protobufType)
        assertTrue(assertNotNull(schema.protobufField("tags")).repeated)
        assertEquals(false, assertNotNull(schema.protobufField("age")).repeated)
    }

    private fun field(name: String): FieldDescriptor = assertNotNull(personDescriptor.findFieldByName(name))

    private fun fullNode(): ObjectNode = ObjectNode.of(
        "name" to StringNode.of("小于"),
        "age" to NumberNode.of(18),
        "balance" to NumberNode.of(-99L),
        "flags" to NumberNode.of(4_294_967_295L),
        "big" to NumberNode.ofRaw("18446744073709551615"),
        "delta" to NumberNode.of(-7),
        "hash" to NumberNode.of(1_234_567_890_123_456_789L),
        "offset" to NumberNode.of(-123),
        "score" to NumberNode.of(3.5),
        "ratio" to NumberNode.of(0.25f),
        "active" to BooleanNode.of(true),
        "data" to BinaryNode.of(byteArrayOf(0, 1, 2, 127, -1)),
        "status" to StringNode.of("ACTIVE"),
        "address" to ObjectNode.of(
            "city" to StringNode.of("杭州"),
            "zip" to NumberNode.of(310_000),
        ),
        "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
        "addresses" to ArrayNode.of(
            ObjectNode.of("city" to StringNode.of("A")),
            ObjectNode.of("city" to StringNode.of("B"), "zip" to NumberNode.of(1)),
        ),
        "nums" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
        "counts" to ObjectNode.of("a" to NumberNode.of(1), "b" to NumberNode.of(2)),
        "addressMap" to ObjectNode.of("home" to ObjectNode.of("city" to StringNode.of("家"))),
        "int64Counts" to ObjectNode.of("5" to StringNode.of("five"), "-3" to StringNode.of("minus")),
    )

    private class PersonBean {
        var name: String? = null
        var age: Int = 0
        var balance: Long = 0
        var flags: Long = 0
        var big: BigInteger? = null
        var delta: Int = 0
        var hash: Long = 0
        var offset: Int = 0
        var score: Double = 0.0
        var ratio: Float = 0.0f
        var active: Boolean = false
        var data: ByteArray? = null
        var status: String? = null
        var address: AddressBean? = null
        var tags: List<String> = emptyList()
        var addresses: List<AddressBean> = emptyList()
        var nums: List<Int> = emptyList()
        var counts: Map<String, Int> = emptyMap()
        var addressMap: Map<String, AddressBean> = emptyMap()
        var int64Counts: Map<Long, String> = emptyMap()
    }

    private class AddressBean {
        var city: String? = null
        var zip: Int = 0
    }

    private class UnknownFieldBean {
        var missing: String? = "x"
    }
}

private val labelOptional: FieldDescriptorProto.Label = FieldDescriptorProto.Label.LABEL_OPTIONAL
private val labelRequired: FieldDescriptorProto.Label = FieldDescriptorProto.Label.LABEL_REQUIRED
private val labelRepeated: FieldDescriptorProto.Label = FieldDescriptorProto.Label.LABEL_REPEATED

private fun fieldProto(
    name: String,
    number: Int,
    label: FieldDescriptorProto.Label,
    type: FieldDescriptorProto.Type,
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

private fun enumProto(name: String, vararg values: Pair<String, Int>): EnumDescriptorProto {
    val builder = EnumDescriptorProto.newBuilder().setName(name)
    for ((valueName, valueNumber) in values) {
        builder.addValue(EnumValueDescriptorProto.newBuilder().setName(valueName).setNumber(valueNumber))
    }
    return builder.build()
}

private fun mapEntryProto(name: String, key: FieldDescriptorProto, value: FieldDescriptorProto): DescriptorProto = DescriptorProto.newBuilder()
    .setName(name)
    .setOptions(MessageOptions.newBuilder().setMapEntry(true))
    .addField(key)
    .addField(value)
    .build()

private fun buildFileDescriptor(fileProto: FileDescriptorProto): FileDescriptor = FileDescriptor.buildFrom(fileProto, emptyArray())

private fun buildPersonDescriptor(): Descriptor {
    val address = DescriptorProto.newBuilder()
        .setName("Address")
        .addField(fieldProto("city", 1, labelOptional, FieldDescriptorProto.Type.TYPE_STRING))
        .addField(fieldProto("zip", 2, labelOptional, FieldDescriptorProto.Type.TYPE_INT32))
        .build()
    val countsEntry = mapEntryProto(
        "CountsEntry",
        fieldProto("key", 1, labelOptional, FieldDescriptorProto.Type.TYPE_STRING),
        fieldProto("value", 2, labelOptional, FieldDescriptorProto.Type.TYPE_INT32),
    )
    val addressMapEntry = mapEntryProto(
        "AddressMapEntry",
        fieldProto("key", 1, labelOptional, FieldDescriptorProto.Type.TYPE_STRING),
        fieldProto("value", 2, labelOptional, FieldDescriptorProto.Type.TYPE_MESSAGE, ".wiretest.Person.Address"),
    )
    val int64CountsEntry = mapEntryProto(
        "Int64CountsEntry",
        fieldProto("key", 1, labelOptional, FieldDescriptorProto.Type.TYPE_INT64),
        fieldProto("value", 2, labelOptional, FieldDescriptorProto.Type.TYPE_STRING),
    )
    val person = DescriptorProto.newBuilder()
        .setName("Person")
        .addField(fieldProto("name", 1, labelOptional, FieldDescriptorProto.Type.TYPE_STRING))
        .addField(fieldProto("age", 2, labelOptional, FieldDescriptorProto.Type.TYPE_INT32))
        .addField(fieldProto("balance", 3, labelOptional, FieldDescriptorProto.Type.TYPE_INT64))
        .addField(fieldProto("flags", 4, labelOptional, FieldDescriptorProto.Type.TYPE_UINT32))
        .addField(fieldProto("big", 5, labelOptional, FieldDescriptorProto.Type.TYPE_UINT64))
        .addField(fieldProto("delta", 6, labelOptional, FieldDescriptorProto.Type.TYPE_SINT32))
        .addField(fieldProto("hash", 7, labelOptional, FieldDescriptorProto.Type.TYPE_FIXED64))
        .addField(fieldProto("offset", 8, labelOptional, FieldDescriptorProto.Type.TYPE_SFIXED32))
        .addField(fieldProto("score", 9, labelOptional, FieldDescriptorProto.Type.TYPE_DOUBLE))
        .addField(fieldProto("ratio", 10, labelOptional, FieldDescriptorProto.Type.TYPE_FLOAT))
        .addField(fieldProto("active", 11, labelOptional, FieldDescriptorProto.Type.TYPE_BOOL))
        .addField(fieldProto("data", 12, labelOptional, FieldDescriptorProto.Type.TYPE_BYTES))
        .addField(fieldProto("status", 13, labelOptional, FieldDescriptorProto.Type.TYPE_ENUM, ".wiretest.Person.Status"))
        .addField(fieldProto("address", 14, labelOptional, FieldDescriptorProto.Type.TYPE_MESSAGE, ".wiretest.Person.Address"))
        .addField(fieldProto("tags", 15, labelRepeated, FieldDescriptorProto.Type.TYPE_STRING))
        .addField(fieldProto("addresses", 16, labelRepeated, FieldDescriptorProto.Type.TYPE_MESSAGE, ".wiretest.Person.Address"))
        .addField(fieldProto("nums", 17, labelRepeated, FieldDescriptorProto.Type.TYPE_INT32))
        .addField(fieldProto("counts", 18, labelRepeated, FieldDescriptorProto.Type.TYPE_MESSAGE, ".wiretest.Person.CountsEntry"))
        .addField(
            fieldProto("addressMap", 19, labelRepeated, FieldDescriptorProto.Type.TYPE_MESSAGE, ".wiretest.Person.AddressMapEntry"),
        )
        .addField(
            fieldProto("int64Counts", 20, labelRepeated, FieldDescriptorProto.Type.TYPE_MESSAGE, ".wiretest.Person.Int64CountsEntry"),
        )
        .addNestedType(address)
        .addNestedType(countsEntry)
        .addNestedType(addressMapEntry)
        .addNestedType(int64CountsEntry)
        .addEnumType(enumProto("Status", "UNKNOWN" to 0, "ACTIVE" to 1, "BLOCKED" to 2))
        .build()
    return buildFileDescriptor(
        FileDescriptorProto.newBuilder()
            .setName("wiretest/person.proto")
            .setPackage("wiretest")
            .setSyntax("proto3")
            .addMessageType(person)
            .build(),
    ).findMessageTypeByName("Person")
}

private fun buildLegacyDescriptor(): Descriptor {
    val legacy = DescriptorProto.newBuilder()
        .setName("Legacy")
        .addField(fieldProto("id", 1, labelRequired, FieldDescriptorProto.Type.TYPE_INT32))
        .addField(fieldProto("name", 2, labelOptional, FieldDescriptorProto.Type.TYPE_STRING))
        .addField(fieldProto("values", 3, labelRepeated, FieldDescriptorProto.Type.TYPE_INT64))
        .build()
    return buildFileDescriptor(
        FileDescriptorProto.newBuilder()
            .setName("wiretest/legacy.proto")
            .setPackage("wiretest2")
            .addMessageType(legacy)
            .build(),
    ).findMessageTypeByName("Legacy")
}

private val personDescriptor: Descriptor by lazy { buildPersonDescriptor() }

private val legacyDescriptor: Descriptor by lazy { buildLegacyDescriptor() }
