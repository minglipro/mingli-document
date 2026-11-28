package com.mingliqiye.document.format.protobuf

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.UnknownFieldSet
import com.mingliqiye.document.core.ArrayNode
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
 * Protobuf extension 读写测试。
 *
 * Descriptor 由 [ProtobufTestFixtures] 手工构造（含嵌套作用域 extension），验证注册表命名、
 * 已知 extension 读写、未知 extension 策略与错误路径。
 */
class ProtobufExtensionTest {

    private val base = ProtobufTestFixtures.extendedBase

    private val registry = ProtobufTestFixtures.extendedRegistry

    private fun extension(name: String): FieldDescriptor = assertNotNull(
        ProtobufTestFixtures.extendedExtensions.firstOrNull { it.name == name },
        "找不到 extension: $name",
    )

    @Test
    fun registryMapsExtensionsAndNames() {
        assertEquals(6, registry.extensions.size)
        assertEquals(ProtobufExtensionNaming.FULL_NAME, registry.naming)
        assertEquals("ext.nickname", registry.documentName(extension("nickname")))
        assertEquals(extension("nickname"), registry.extension(base, "ext.nickname"))
        assertEquals(extension("nickname"), registry.extension(base, 100))
        assertEquals(5, registry.extensionsFor(base).size)
        assertNull(registry.extension(base, 999))
        assertEquals(emptyList(), ProtobufExtensionRegistry.EMPTY.extensionsFor(base))
        assertTrue(ProtobufExtensionRegistry.EMPTY.isEmpty)

        val simple = ProtobufExtensionRegistry.builder()
            .addAll(ProtobufTestFixtures.extendedFile.extensions)
            .naming(ProtobufExtensionNaming.SIMPLE_NAME)
            .build()
        assertEquals("nickname", simple.documentName(extension("nickname")))

        val bracketed = ProtobufExtensionRegistry.builder()
            .addAll(registry.extensions)
            .naming(ProtobufExtensionNaming.BRACKETED)
            .build()
        assertEquals("[ext.nickname]", bracketed.documentName(extension("nickname")))
    }

    @Test
    fun registryRejectsInvalidAndConflictingExtensions() {
        assertFailsWith<IllegalArgumentException> {
            ProtobufExtensionRegistry.builder().add(assertNotNull(base.findFieldByName("id"))).build()
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufExtensionRegistry.builder()
                .addAll(registry.extensions)
                .naming(ProtobufExtensionNaming.SIMPLE_NAME)
                .build()
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufExtensionRegistry.of(ProtobufTestFixtures.extendedFile, ProtobufExtensionNaming.SIMPLE_NAME)
        }
    }

    @Test
    fun registryFromFileCollectsNestedExtensions() {
        val fromFile = ProtobufExtensionRegistry.of(ProtobufTestFixtures.extendedFile)
        assertEquals(6, fromFile.extensions.size)
        assertNotNull(fromFile.extension(ProtobufTestFixtures.extendedNestedInner, "ext.Nested.inner_note"))
        assertEquals(
            ProtobufExtensionNaming.BRACKETED,
            ProtobufExtensionRegistry.of(ProtobufTestFixtures.extendedFile, ProtobufExtensionNaming.BRACKETED).naming,
        )
    }

    @Test
    fun formatAndParseKnownExtensions() {
        val mapper = ProtobufMapper(base, registry)
        val node = extendedNode()

        val bytes = mapper.formatBytes(node)
        assertTrue(bytes.isNotEmpty())

        val message = DynamicMessage.parseFrom(base, bytes, registry.protobufRegistry)
        assertEquals("小于", message.getField(extension("nickname")))
        val extra = message.getField(extension("extra")) as DynamicMessage
        assertEquals("v", extra.getField(ProtobufTestFixtures.extendedExtra.findFieldByName("value")))
        assertEquals(2, extra.getField(ProtobufTestFixtures.extendedExtra.findFieldByName("count")))
        assertEquals(listOf(1, 2, 3), message.getField(extension("numbers")))

        val decoded = mapper.parse(bytes, Node::class.java).asObject()
        assertEquals(node, decoded)

        // 不注册 extension 时，这些编号按未知字段处理。
        assertFailsWith<ProtobufParseException> {
            ProtobufMapper(base).parse(bytes, Node::class.java)
        }
        val ignored = ProtobufMapper(base, ProtobufUnknownFieldPolicy.IGNORE)
            .parse(bytes, Node::class.java)
            .asObject()
        assertEquals(setOf("id"), ignored.keys)
    }

    @Test
    fun namingModesMapToDocumentFields() {
        val simple = ProtobufExtensionRegistry.builder()
            .addAll(ProtobufTestFixtures.extendedFile.extensions)
            .naming(ProtobufExtensionNaming.SIMPLE_NAME)
            .build()
        val simpleMapper = ProtobufMapper(base, simple)
        val simpleNode = ObjectNode.of(
            "id" to NumberNode.of(1),
            "nickname" to StringNode.of("simple"),
            "numbers" to ArrayNode.of(NumberNode.of(7)),
        )
        assertEquals(simpleNode, simpleMapper.parse(simpleMapper.formatBytes(simpleNode), Node::class.java).asObject())

        val bracketed = ProtobufExtensionRegistry.builder()
            .addAll(registry.extensions)
            .naming(ProtobufExtensionNaming.BRACKETED)
            .build()
        val bracketedMapper = ProtobufMapper(base, bracketed)
        val bracketedNode = ObjectNode.of(
            "id" to NumberNode.of(1),
            "[ext.nickname]" to StringNode.of("bracketed"),
        )
        assertEquals(
            bracketedNode,
            bracketedMapper.parse(bracketedMapper.formatBytes(bracketedNode), Node::class.java).asObject(),
        )
    }

    @Test
    fun unknownExtensionFollowsPolicy() {
        val mapper = ProtobufMapper(base, registry)
        val knownBytes = mapper.formatBytes(ObjectNode.of("id" to NumberNode.of(1), "ext.nickname" to StringNode.of("x")))
        val unknownBytes = unknownFieldBytes(ProtobufTestFixtures.varintUnknown(105, 7L))
        val bytes = knownBytes + unknownBytes

        assertFailsWith<ProtobufParseException> { mapper.parse(bytes, Node::class.java) }

        val lenient = ProtobufMapper(base, ProtobufUnknownFieldPolicy.IGNORE, registry)
        assertEquals(
            ObjectNode.of("id" to NumberNode.of(1), "ext.nickname" to StringNode.of("x")),
            lenient.parse(bytes, Node::class.java).asObject(),
        )

        val preserved = ProtobufMapper(base, ProtobufUnknownFieldPolicy.PRESERVE, registry)
        val preservedNode = preserved.parse(bytes, Node::class.java).asObject()
        assertEquals(StringNode.of("x"), preservedNode["ext.nickname"])
        assertEquals(
            ObjectNode.of("105" to ObjectNode.of("varint" to NumberNode.of(7))),
            preservedNode[ProtobufUnknownFields.FIELD_NAME],
        )
        assertEquals(
            preservedNode,
            preserved.parse(preserved.formatBytes(preservedNode), Node::class.java).asObject(),
        )
    }

    @Test
    fun extensionsOnNestedMessages() {
        val innerNote = extension("inner_note")
        val inner = DynamicMessage.newBuilder(ProtobufTestFixtures.extendedNestedInner)
            .setField(innerNote, "note")
            .build()
        val nestedField = assertNotNull(ProtobufTestFixtures.extendedNested.findFieldByName("inner"))
        val nested = DynamicMessage.newBuilder(ProtobufTestFixtures.extendedNested)
            .setField(nestedField, inner)
            .build()
        val mapper = ProtobufMapper(ProtobufTestFixtures.extendedNested, registry)
        val node = ObjectNode.of(
            "inner" to ObjectNode.of("ext.Nested.inner_note" to StringNode.of("note")),
        )
        assertEquals(node, mapper.parse(nested.toByteArray(), Node::class.java).asObject())
        assertContentEquals(nested.toByteArray(), mapper.formatBytes(node))

        val restored = DynamicMessage.parseFrom(
            ProtobufTestFixtures.extendedNested,
            mapper.formatBytes(node),
            registry.protobufRegistry,
        )
        val restoredInner = restored.getField(nestedField) as DynamicMessage
        assertEquals("note", restoredInner.getField(innerNote))
    }

    @Test
    fun extensionContainingTypeMismatchIsRejected() {
        val mapper = ProtobufMapper(base, registry)
        val exception = assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("ext.Nested.inner_note" to StringNode.of("x")))
        }
        assertTrue(exception.message.orEmpty().contains("未在消息"))
    }

    @Test
    fun extensionValueTypesAreValidated() {
        val mapper = ProtobufMapper(base, registry)
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("ext.nickname" to NumberNode.of(1)))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("ext.numbers" to StringNode.of("not-array")))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("ext.extra" to NumberNode.of(1)))
        }
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("ext.numbers" to ArrayNode.of(StringNode.of("x"))))
        }
    }

    @Test
    fun descriptorInstanceMismatchIsDiagnosed() {
        val rebuilt = ProtobufTestFixtures.rebuildExtendedFile()
        val rebuiltBase = assertNotNull(rebuilt.findMessageTypeByName("Base"))
        val mapper = ProtobufMapper(rebuiltBase, registry)
        val encodeError = assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of("ext.nickname" to StringNode.of("x")))
        }
        assertTrue(encodeError.message.orEmpty().contains("Descriptor"))

        val originalBytes = ProtobufMapper(base, registry)
            .formatBytes(ObjectNode.of("ext.nickname" to StringNode.of("x")))
        val decodeError = assertFailsWith<ProtobufParseException> {
            mapper.parse(originalBytes, Node::class.java)
        }
        assertTrue(decodeError.message.orEmpty().contains("不是同一个实例"))
    }

    @Test
    fun malformedExtensionWireThrows() {
        val mapper = ProtobufMapper(base, registry)
        // nickname(100) 是 string（wire type 2），这里写成 varint wire type：tag=(100<<3)|0=800。
        assertFailsWith<ProtobufParseException> {
            mapper.parse(byteArrayOf(0xA0.toByte(), 0x06, 0x01), Node::class.java)
        }
        // extra(101) 是 message，payload 内部字符串长度超出剩余字节：tag=(101<<3)|2=810。
        assertFailsWith<ProtobufParseException> {
            mapper.parse(byteArrayOf(0xAA.toByte(), 0x06, 0x03, 0x0A, 0x05, 0x01), Node::class.java)
        }
    }

    @Test
    fun extensionRepeatedAndSingleMutationRoundTrip() {
        val mapper = ProtobufMapper(base, registry)
        val node = ObjectNode.of("ext.numbers" to ArrayNode.of(NumberNode.of(4), NumberNode.of(5)))
        val decoded = mapper.parse(mapper.formatBytes(node), Node::class.java).asObject()
        assertEquals(node, decoded)

        val message = DynamicMessage.parseFrom(base, mapper.formatBytes(node), registry.protobufRegistry)
        assertEquals(listOf(4, 5), message.getField(extension("numbers")))
    }

    private fun extendedNode(): ObjectNode = ObjectNode.of(
        "id" to NumberNode.of(1),
        "ext.nickname" to StringNode.of("小于"),
        "ext.extra" to ObjectNode.of(
            "value" to StringNode.of("v"),
            "count" to NumberNode.of(2),
        ),
        "ext.numbers" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2), NumberNode.of(3)),
    )

    private fun unknownFieldBytes(unknownFields: UnknownFieldSet): ByteArray = UnknownFieldSet.newBuilder()
        .mergeFrom(unknownFields)
        .build()
        .toByteArray()
}
