package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.smile.exception.SmileException
import com.mingliqiye.document.format.smile.exception.SmileParseException
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Smile 读写特性逐项行为测试。
 *
 * 每个 [SmileReadFeature] / [SmileWriteFeature] 都必须产生可观察的解析/写出差异，
 * 不允许只保存开关而不生效。
 */
class SmileFeatureTest {

    @Test
    fun requireHeaderFeatureControlsReadingHeaderlessInput() {
        val node = ObjectNode.of("a" to NumberNode.of(1))
        val headerless = SmileWriter(
            SmileConfig.builder().disable(SmileWriteFeature.WRITE_HEADER).build(),
        ).write(node)
        assertNotEquals(HEADER_FIRST_BYTE, headerless.first())

        assertFailsWith<SmileParseException> {
            SmileReader(headerless).read()
        }

        val relaxed = SmileConfig.builder().disable(SmileReadFeature.REQUIRE_HEADER).build()
        assertEquals(node, SmileReader(headerless, config = relaxed).read())

        // 带头部输入在关闭 REQUIRE_HEADER 时仍可读取。
        assertEquals(node, SmileReader(node.toSmileBytes(), config = relaxed).read())
    }

    @Test
    fun writeHeaderFeatureControlsHeaderBytes() {
        val node = ObjectNode.of("a" to NumberNode.of(1))

        val withHeader = SmileWriter().write(node)
        assertContentEquals(DEFAULT_HEADER, withHeader.copyOf(DEFAULT_HEADER.size))

        val writeConfig = SmileConfig.builder().disable(SmileWriteFeature.WRITE_HEADER).build()
        val withoutHeader = SmileWriter(writeConfig).write(node)
        assertFalse(withoutHeader.contentEquals(withHeader))
        assertTrue(withoutHeader.size < withHeader.size)

        val readConfig = SmileConfig.builder().disable(SmileReadFeature.REQUIRE_HEADER).build()
        assertEquals(node, SmileReader(withoutHeader, config = readConfig).read())
    }

    @Test
    fun writeEndMarkerFeatureAppendsTerminator() {
        val node = ObjectNode.of("a" to NumberNode.of(1))
        val plain = SmileWriter().write(node)
        assertNotEquals(END_MARKER, plain.last())

        val config = SmileConfig.builder().enable(SmileWriteFeature.WRITE_END_MARKER).build()
        val marked = SmileWriter(config).write(node)
        assertEquals(END_MARKER, marked.last())
        assertContentEquals(plain, marked.copyOf(plain.size))
        assertEquals(node, SmileReader(marked).read())

        // 多消息流同样以结束标记收尾，读取端将其视为 EOF。
        val writer = SmileSequenceWriter(config)
        val stream = writer.writeAll(listOf(node, node))
        assertEquals(END_MARKER, stream.last())
        assertEquals(listOf(node, node), SmileSequenceReader(stream).readAll())
    }

    @Test
    fun encodeBinaryAs7BitFeatureControlsBinaryEncoding() {
        val payload = ByteArray(256) { it.toByte() }
        val node = ObjectNode.of("data" to BinaryNode.of(payload))

        val encoded = SmileWriter().write(node)
        assertTrue(encoded.none { it == END_MARKER })
        assertEquals(node, SmileReader(encoded).read())

        val rawConfig = SmileConfig.builder().disable(SmileWriteFeature.ENCODE_BINARY_AS_7BIT).build()
        val raw = SmileWriter(rawConfig).write(node)
        assertTrue(raw.any { it == END_MARKER })
        assertTrue((raw[HEADER_BIT_INDEX].toInt() and HEADER_BIT_HAS_RAW_BINARY) != 0)
        assertTrue(raw.size < encoded.size)
        assertEquals(node, SmileReader(raw).read())
    }

    @Test
    fun checkSharedNamesFeatureCompactsRepeatedFieldNames() {
        val node = ArrayNode.from((0 until 8).map { ObjectNode.of("value" to NumberNode.of(it)) })

        val shared = SmileWriter().write(node)
        val unshared = SmileWriter(
            SmileConfig.builder().disable(SmileWriteFeature.CHECK_SHARED_NAMES).build(),
        ).write(node)

        assertTrue(unshared.size > shared.size)
        assertTrue((shared[HEADER_BIT_INDEX].toInt() and HEADER_BIT_HAS_SHARED_NAMES) != 0)
        assertTrue((unshared[HEADER_BIT_INDEX].toInt() and HEADER_BIT_HAS_SHARED_NAMES) == 0)
        assertEquals(node, SmileReader(shared).read())
        assertEquals(node, SmileReader(unshared).read())
    }

    @Test
    fun checkSharedStringValuesFeatureCompactsRepeatedStrings() {
        val node = ArrayNode.from((0 until 16).map { StringNode.of("shared-string-value") })

        val plain = SmileWriter().write(node)
        val shared = SmileWriter(
            SmileConfig.builder().enable(SmileWriteFeature.CHECK_SHARED_STRING_VALUES).build(),
        ).write(node)

        assertTrue(shared.size < plain.size)
        assertTrue((shared[HEADER_BIT_INDEX].toInt() and HEADER_BIT_HAS_SHARED_STRING_VALUES) != 0)
        assertEquals(node, SmileReader(shared).read())
        assertEquals(node, SmileReader(plain).read())
    }

    @Test
    fun lenientUtfEncodingFeatureReplacesInvalidSurrogates() {
        val invalid = StringNode.of("\uD800")

        assertFailsWith<SmileException> {
            SmileWriter().write(invalid)
        }

        val config = SmileConfig.builder().enable(SmileWriteFeature.LENIENT_UTF_ENCODING).build()
        val bytes = SmileWriter(config).write(invalid)
        assertEquals("\uFFFD", SmileReader(bytes).read().asString())
        assertEquals("\uFFFD", SmileMapper(config).parse(bytes, String::class.java))
    }

    @Test
    fun strictDuplicateDetectionReadRejectsDuplicateFieldNames() {
        val bytes = duplicateFieldSmileBytes()

        // 默认行为：重复字段名以后出现的值为准。
        assertEquals(2, SmileReader(bytes).read().asObject()["value"]?.asNumber()?.toInt())

        val strict = SmileConfig.builder()
            .enable(SmileReadFeature.STRICT_DUPLICATE_DETECTION)
            .build()
        assertFailsWith<SmileParseException> {
            SmileReader(bytes, config = strict).read()
        }
        assertFailsWith<SmileParseException> {
            SmileSequenceReader(bytes, config = strict).readAll()
        }
        assertFailsWith<SmileParseException> {
            SmileMapper(strict).parse(bytes, Node::class.java)
        }
    }

    @Test
    fun strictDuplicateDetectionWriteRejectsDuplicateFieldNames() {
        // 默认 Token 写出器允许重复字段名（用于构造上面的严格读取输入）。
        duplicateFieldSmileBytes()

        val strict = SmileConfig.builder()
            .enable(SmileWriteFeature.STRICT_DUPLICATE_DETECTION)
            .build()
        val writer = SmileTokenWriter(config = strict)
        try {
            assertFailsWith<SmileException> {
                writer.startObject()
                writer.fieldName("value")
                writer.writeNumber("1")
                writer.fieldName("value")
                writer.writeNumber("2")
                writer.endObject()
            }
        } finally {
            writer.close()
        }
    }

    @Test
    fun mapperConfigAppliesReadAndWriteFeaturesEndToEnd() {
        val config = SmileConfig.builder()
            .disable(SmileWriteFeature.WRITE_HEADER)
            .disable(SmileReadFeature.REQUIRE_HEADER)
            .build()
        val mapper = SmileMapper(config)
        val person = Person().apply {
            name = "小于"
            age = 18
            tags = listOf("a", "b")
            address = Address().apply { city = "杭州" }
        }

        val bytes = mapper.formatBytes(person)
        assertNotEquals(HEADER_FIRST_BYTE, bytes.first())

        val restored = mapper.parse(bytes, Person::class.java)
        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals("杭州", restored.address?.city)

        val stream = mapper.formatAll(listOf(person, person))
        assertEquals(2, mapper.parseAllToNodes(stream).size)
    }

    @Test
    fun streamingOutputHonorsWriteFeatures() {
        val config = SmileConfig.builder().disable(SmileWriteFeature.WRITE_HEADER).build()
        val mapper = SmileMapper(config)
        val value = linkedMapOf("name" to "a", "numbers" to listOf(1, 2, 3))

        val output = ByteArrayOutputStream()
        mapper.format(value, output)
        val streamed = output.toByteArray()
        assertNotEquals(HEADER_FIRST_BYTE, streamed.first())
        assertContentEquals(mapper.formatBytes(value), streamed)
    }

    @Test
    fun commonDocumentMapperConfigOptionsStillApply() {
        val mapper = SmileMapper(
            SmileConfig.builder()
                .writeNulls(false)
                .failOnUnknownFields(true)
                .build(),
        )
        val bytes = mapper.formatBytes(Person().apply { name = "小于" })
        val node = SmileReader(bytes).read().asObject()
        assertFalse(node.containsKey("address"))

        val unknown = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "extra" to NumberNode.of(1),
        ).toSmileBytes()
        assertFailsWith<DocumentSerializationException> {
            mapper.parse(unknown, Person::class.java)
        }
    }

    private fun duplicateFieldSmileBytes(): ByteArray {
        val writer = SmileTokenWriter()
        try {
            writer.startObject()
            writer.fieldName("value")
            writer.writeNumber("1")
            writer.fieldName("value")
            writer.writeNumber("2")
            writer.endObject()
            return writer.toByteArray()
        } finally {
            writer.close()
        }
    }

    private class Person {
        var name: String? = null
        var age: Int = 0
        var tags: List<String> = emptyList()
        var address: Address? = null
    }

    private class Address {
        var city: String? = null
    }

    private companion object {

        private const val HEADER_FIRST_BYTE: Byte = 0x3A

        private val DEFAULT_HEADER: ByteArray = byteArrayOf(0x3A, 0x29, 0x0A, 0x01)

        private const val END_MARKER: Byte = -1

        private const val HEADER_BIT_INDEX: Int = 3

        private const val HEADER_BIT_HAS_SHARED_NAMES: Int = 0x01

        private const val HEADER_BIT_HAS_SHARED_STRING_VALUES: Int = 0x02

        private const val HEADER_BIT_HAS_RAW_BINARY: Int = 0x04
    }
}
