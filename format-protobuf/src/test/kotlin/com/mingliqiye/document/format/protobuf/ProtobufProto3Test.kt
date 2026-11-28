package com.mingliqiye.document.format.protobuf

import com.google.protobuf.DynamicMessage
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * proto3 语义测试。
 *
 * 覆盖默认值省略、proto3 `optional` presence、string/bytes 校验、packed repeated 与未知字段
 * `PRESERVE` 策略（含嵌套消息未知字段）。
 */
class ProtobufProto3Test {

    private val sample = ProtobufTestFixtures.proto3Sample

    private fun field(name: String) = assertNotNull(sample.findFieldByName(name))

    @Test
    fun defaultValuesAreOmittedOnWrite() {
        val mapper = ProtobufMapper(sample)
        val defaults = ObjectNode.of(
            "age" to NumberNode.of(0),
            "name" to StringNode.of(""),
            "data" to BinaryNode.of(ByteArray(0)),
            "active" to BooleanNode.of(false),
            "score" to NumberNode.of(0.0),
            "nums" to ArrayNode.of(),
            "counts" to ObjectNode.of(),
            "status" to StringNode.of("UNKNOWN"),
        )
        assertContentEquals(ByteArray(0), mapper.formatBytes(defaults))
        assertEquals(ObjectNode.EMPTY, mapper.parse(ByteArray(0), Node::class.java).asObject())
    }

    @Test
    fun nonDefaultValuesAreWrittenAndRoundTrip() {
        val mapper = ProtobufMapper(sample)
        val node = ObjectNode.of(
            "age" to NumberNode.of(1),
            "name" to StringNode.of("n"),
            "data" to BinaryNode.of(byteArrayOf(1, 2)),
            "active" to BooleanNode.of(true),
            "score" to NumberNode.of(1.5),
            "nums" to ArrayNode.of(NumberNode.of(0), NumberNode.of(2)),
            "counts" to ObjectNode.of("a" to NumberNode.of(0), "b" to NumberNode.of(3)),
            "status" to StringNode.of("ACTIVE"),
            "child" to ObjectNode.of("label" to StringNode.of("c")),
        )
        val bytes = mapper.formatBytes(node)
        val message = DynamicMessage.parseFrom(sample, bytes)
        assertEquals(1, message.getField(field("age")))
        assertEquals("n", message.getField(field("name")))
        assertEquals("ACTIVE", (message.getField(field("status")) as com.google.protobuf.Descriptors.EnumValueDescriptor).name)
        assertEquals(node, mapper.parse(bytes, Node::class.java).asObject())
        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java).asObject())
    }

    @Test
    fun packedRepeatedEncodingAndParsing() {
        val mapper = ProtobufMapper(sample)
        // proto3 中可 packed 的 repeated 标量默认 packed：tag(6,2)=0x32 + 长度 + 元素。
        assertContentEquals(
            byteArrayOf(0x32, 0x02, 0x01, 0x02),
            mapper.formatBytes(ObjectNode.of("nums" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)))),
        )
        assertEquals(
            ObjectNode.of("nums" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2))),
            mapper.parse(byteArrayOf(0x32, 0x02, 0x01, 0x02), Node::class.java).asObject(),
        )
    }

    @Test
    fun proto3OptionalPreservesPresence() {
        val optionalField = field("opt_age")
        assertTrue(optionalField.hasPresence())
        assertNull(optionalField.realContainingOneof)

        val mapper = ProtobufMapper(sample)
        assertContentEquals(ByteArray(0), mapper.formatBytes(ObjectNode.of()))
        // 显式设置为默认值 0 时必须保留 presence：tag(10,0)=0x50 + 0。
        assertContentEquals(
            byteArrayOf(0x50, 0x00),
            mapper.formatBytes(ObjectNode.of("opt_age" to NumberNode.of(0))),
        )
        assertEquals(
            ObjectNode.of("opt_age" to NumberNode.of(0)),
            mapper.parse(byteArrayOf(0x50, 0x00), Node::class.java).asObject(),
        )
        // next 字段没有 presence 时 0 被省略，不会保留 presence。
        assertContentEquals(
            ByteArray(0),
            mapper.formatBytes(ObjectNode.of("age" to NumberNode.of(0))),
        )
    }

    @Test
    fun proto2PresenceIsNotAffectedByProto3DefaultOmission() {
        val mapper = ProtobufMapper(ProtobufTestFixtures.proto2Required)
        assertContentEquals(
            byteArrayOf(0x08, 0x00),
            mapper.formatBytes(ObjectNode.of("id" to NumberNode.of(0))),
        )
        assertEquals(
            ObjectNode.of("id" to NumberNode.of(0)),
            mapper.parse(byteArrayOf(0x08, 0x00), Node::class.java).asObject(),
        )
        assertFailsWith<ProtobufException> { mapper.formatBytes(ObjectNode.of()) }
    }

    @Test
    fun stringAndBytesValuesAreValidated() {
        val mapper = ProtobufMapper(sample)
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("name" to StringNode.of("\uD800")))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("child" to ObjectNode.of("label" to StringNode.of("\uDC00"))))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(
                ObjectNode.of("counts" to ObjectNode.of("\uD800" to NumberNode.of(1))),
            )
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("name" to BinaryNode.of(byteArrayOf(1))))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("data" to NumberNode.of(1)))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("data" to StringNode.of("!!!")))
        }
        // bytes 字段接受合法 Base64 字符串。
        assertEquals(
            ObjectNode.of("data" to BinaryNode.of(byteArrayOf(0, 1, 2))),
            mapper.parse(
                mapper.formatBytes(ObjectNode.of("data" to StringNode.of("AAEC"))),
                Node::class.java,
            ).asObject(),
        )
    }

    @Test
    fun invalidUtf8WireStringsAreRejectedOrReplaced() {
        // proto3 string 必须合法 UTF-8：字段 2 长度 2，内容 0xFF 0xFE 非法。
        assertFailsWith<ProtobufParseException> {
            ProtobufMapper(sample).parse(byteArrayOf(0x12, 0x02, 0xFF.toByte(), 0xFE.toByte()), Node::class.java)
        }
        assertFailsWith<ProtobufParseException> {
            ProtobufMapper(sample).parse(byteArrayOf(0x12, 0x01, 0xC0.toByte()), Node::class.java)
        }
        // proto2 string 不强制 UTF-8：非法字节由 protobuf-java 按替换字符解码。
        val proto2Node = ProtobufMapper(ProtobufTestFixtures.proto2Required)
            .parse(byteArrayOf(0x08, 0x01, 0x12, 0x01, 0xFF.toByte()), Node::class.java)
            .asObject()
        assertEquals(NumberNode.of(1), proto2Node["id"])
        assertEquals(StringNode.of("\uFFFD"), proto2Node["name"])
    }

    @Test
    fun unknownFieldsArePreservedAndRoundTrip() {
        val unknownFields = ProtobufTestFixtures.mergeUnknown(
            ProtobufTestFixtures.varintUnknown(99, 1L, 2L),
            ProtobufTestFixtures.fixedUnknown(100, -1),
            ProtobufTestFixtures.fixed64Unknown(101, -1L),
            ProtobufTestFixtures.lengthDelimitedUnknown(102, byteArrayOf(0x61)),
            ProtobufTestFixtures.groupUnknown(103, ProtobufTestFixtures.varintUnknown(1, 5L)),
        )
        val bytes = ProtobufTestFixtures.bytesWithUnknownFields(sample, mapOf("age" to 1), unknownFields)
        val preserved = ProtobufMapper(sample, ProtobufUnknownFieldPolicy.PRESERVE)
        val node = preserved.parse(bytes, Node::class.java).asObject()
        assertEquals(NumberNode.of(1), node["age"])
        val unknownNode = assertNotNull(node[ProtobufUnknownFields.FIELD_NAME]).asObject()
        assertEquals(
            ObjectNode.of(
                "99" to ObjectNode.of(
                    ProtobufUnknownFields.VARINT to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
                ),
                "100" to ObjectNode.of(
                    ProtobufUnknownFields.FIXED32 to NumberNode.of(4_294_967_295L),
                ),
                "101" to ObjectNode.of(
                    ProtobufUnknownFields.FIXED64 to NumberNode.ofRaw("18446744073709551615"),
                ),
                "102" to ObjectNode.of(
                    ProtobufUnknownFields.LENGTH_DELIMITED to BinaryNode.of(byteArrayOf(0x61)),
                ),
                "103" to ObjectNode.of(
                    ProtobufUnknownFields.GROUPS to ObjectNode.of(
                        "1" to ObjectNode.of(ProtobufUnknownFields.VARINT to NumberNode.of(5)),
                    ),
                ),
            ),
            unknownNode,
        )
        assertEquals(node, preserved.parse(preserved.formatBytes(node), Node::class.java).asObject())

        assertFailsWith<ProtobufParseException> {
            ProtobufMapper(sample).parse(bytes, Node::class.java)
        }
        assertEquals(
            ObjectNode.of("age" to NumberNode.of(1)),
            ProtobufMapper(sample, ProtobufUnknownFieldPolicy.IGNORE).parse(bytes, Node::class.java).asObject(),
        )
    }

    @Test
    fun nestedUnknownFieldsArePreserved() {
        val childField = field("child")
        val childDescriptor = childField.messageType
        val childBytes = ProtobufTestFixtures.bytesWithUnknownFields(
            childDescriptor,
            mapOf("label" to "c"),
            ProtobufTestFixtures.varintUnknown(200, 7L),
        )
        val childMessage = DynamicMessage.parseFrom(childDescriptor, childBytes)
        val bytes = DynamicMessage.newBuilder(sample).setField(childField, childMessage).build().toByteArray()

        val preserved = ProtobufMapper(sample, ProtobufUnknownFieldPolicy.PRESERVE)
        val node = preserved.parse(bytes, Node::class.java).asObject()
        assertEquals(
            ObjectNode.of(
                "label" to StringNode.of("c"),
                ProtobufUnknownFields.FIELD_NAME to ObjectNode.of(
                    "200" to ObjectNode.of(ProtobufUnknownFields.VARINT to NumberNode.of(7)),
                ),
            ),
            node["child"],
        )
        assertEquals(node, preserved.parse(preserved.formatBytes(node), Node::class.java).asObject())
        assertFailsWith<ProtobufParseException> { ProtobufMapper(sample).parse(bytes, Node::class.java) }
    }

    @Test
    fun unknownFieldNodeIsOnlyAcceptedInPreserveMode() {
        val node = ObjectNode.of(
            "age" to NumberNode.of(1),
            ProtobufUnknownFields.FIELD_NAME to ObjectNode.of(
                "99" to ObjectNode.of(ProtobufUnknownFields.VARINT to NumberNode.of(1)),
            ),
        )
        assertFailsWith<ProtobufException> { ProtobufMapper(sample).formatBytes(node) }
        assertFailsWith<ProtobufException> {
            ProtobufMapper(sample, ProtobufUnknownFieldPolicy.IGNORE).formatBytes(node)
        }
        assertContentEquals(
            byteArrayOf(0x08, 0x01, 0x98.toByte(), 0x06, 0x01),
            ProtobufMapper(sample, ProtobufUnknownFieldPolicy.PRESERVE).formatBytes(node),
        )
    }

    @Test
    fun invalidUnknownFieldNodesAreRejected() {
        val preserved = ProtobufMapper(sample, ProtobufUnknownFieldPolicy.PRESERVE)
        fun reject(unknown: Node) {
            assertFailsWith<ProtobufException> {
                preserved.formatBytes(ObjectNode.of(ProtobufUnknownFields.FIELD_NAME to unknown))
            }
        }
        reject(StringNode.of("x"))
        reject(ObjectNode.of("0" to ObjectNode.of("varint" to NumberNode.of(1))))
        reject(ObjectNode.of("abc" to ObjectNode.of("varint" to NumberNode.of(1))))
        reject(ObjectNode.of("536870912" to ObjectNode.of("varint" to NumberNode.of(1))))
        reject(ObjectNode.of("99" to NumberNode.of(1)))
        reject(ObjectNode.of("99" to ObjectNode.of()))
        reject(ObjectNode.of("99" to ObjectNode.of("wire9" to NumberNode.of(1))))
        reject(ObjectNode.of("99" to ObjectNode.of("varint" to NumberNode.of(-1))))
        reject(ObjectNode.of("99" to ObjectNode.of("fixed32" to NumberNode.ofRaw("4294967296"))))
        reject(ObjectNode.of("99" to ObjectNode.of("lengthDelimited" to NumberNode.of(1))))
        reject(ObjectNode.of("99" to ObjectNode.of("groups" to StringNode.of("x"))))
        reject(ObjectNode.of("99" to ObjectNode.of("varint" to ArrayNode.of())))
    }

    @Test
    fun emptyMessageWithPresenceIsWritten() {
        val mapper = ProtobufMapper(sample)
        val bytes = mapper.formatBytes(ObjectNode.of("child" to ObjectNode.of()))
        assertContentEquals(byteArrayOf(0x4A, 0x00), bytes)
        assertEquals(
            ObjectNode.of("child" to ObjectNode.of()),
            mapper.parse(bytes, Node::class.java).asObject(),
        )
    }

    @Test
    fun unknownFieldPreserveAcceptsBase64LengthDelimited() {
        val mapper = ProtobufMapper(sample, ProtobufUnknownFieldPolicy.PRESERVE)
        val node = ObjectNode.of(
            ProtobufUnknownFields.FIELD_NAME to ObjectNode.of(
                "102" to ObjectNode.of(
                    ProtobufUnknownFields.LENGTH_DELIMITED to StringNode.of("YQ=="),
                ),
            ),
        )
        val bytes = mapper.formatBytes(node)
        val decoded = mapper.parse(bytes, Node::class.java).asObject()
        assertEquals(
            ObjectNode.of(
                ProtobufUnknownFields.FIELD_NAME to ObjectNode.of(
                    "102" to ObjectNode.of(
                        ProtobufUnknownFields.LENGTH_DELIMITED to BinaryNode.of(byteArrayOf(0x61)),
                    ),
                ),
            ),
            decoded,
        )
    }
}
