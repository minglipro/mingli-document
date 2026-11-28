package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.cbor.exception.CborException
import com.mingliqiye.document.format.cbor.exception.CborParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CBOR 配置对齐 上游实现：读取/写出特性、默认值、Builder 便捷方法与 Mapper 集成。
 */
class CborFeatureTest {

    // ---------------------------------------------------------------------
    // 默认值对齐 上游实现
    // ---------------------------------------------------------------------

    @Test
    fun defaultsAlignJackson() {
        assertTrue(CborConfig.DEFAULT_READ_FEATURES.isEmpty())
        assertEquals(setOf(CborWriteFeature.WRITE_MINIMAL_INTS), CborConfig.DEFAULT_WRITE_FEATURES)

        val config = CborConfig.builder().build()
        assertFalse(config.isEnabled(CborReadFeature.STRICT_DUPLICATE_DETECTION))
        assertFalse(config.isEnabled(CborReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING))
        assertTrue(config.isEnabled(CborWriteFeature.WRITE_MINIMAL_INTS))
        assertFalse(config.isEnabled(CborWriteFeature.WRITE_TYPE_HEADER))
        assertFalse(config.isEnabled(CborWriteFeature.STRINGREF))

        // 默认字节行为不变：最小整数、无自描述头、小数仍写十进制分数。
        assertEquals("05", NumberNode.of(5).toCborBytes().toHex())
        assertEquals("21", NumberNode.of(-2).toCborBytes().toHex())
        assertEquals("c4822119013a", NumberNode.ofRaw("3.14").toCborBytes().toHex())
        // 默认读取行为不变：undefined -> null、simple value -> 数字、负数 bignum 用历史解码。
        assertTrue(hex("f7").toCborNode().isNull)
        assertEquals(NumberNode.of(16), hex("f0").toCborNode())
        assertEquals(NumberNode.ofRaw("0"), hex("c34100").toCborNode())
        assertEquals(NumberNode.ofRaw("-18446744073709551616"), hex("c349010000000000000000").toCborNode())
    }

    // ---------------------------------------------------------------------
    // 读取特性
    // ---------------------------------------------------------------------

    @Test
    fun standardNegativeBigIntDecoding() {
        val features = setOf(CborReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)

        // tag 3 + 1 字节 0x00：规范解码为 -1，历史解码为 0。
        assertEquals(
            NumberNode.ofRaw("-1"),
            CborReader(hex("c34100"), readFeatures = features).read(),
        )
        assertEquals(NumberNode.ofRaw("0"), CborReader(hex("c34100")).read())

        // 规范编码的 -2^64 与历史编码的 -2^64。
        assertEquals(
            NumberNode.ofRaw("-18446744073709551616"),
            CborReader(hex("c34900ffffffffffffffff"), readFeatures = features).read(),
        )
        assertEquals(
            NumberNode.ofRaw("-18446744073709551617"),
            CborReader(hex("c349010000000000000000"), readFeatures = features).read(),
        )
    }

    @Test
    fun standardNegativeBigIntEncodingRoundTrip() {
        val write = setOf(CborWriteFeature.ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
        val read = setOf(CborReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
        val value = NumberNode.ofRaw("-18446744073709551616")

        assertEquals("c349010000000000000000", value.toCborBytes().toHex())
        assertEquals("c34900ffffffffffffffff", value.toCborBytes(writeFeatures = write).toHex())

        val encoded = value.toCborBytes(writeFeatures = write)
        assertEquals(value, CborReader(encoded, readFeatures = read).read())
        // 规范读取 + 规范写出：字节级往返一致。
        assertContentEquals(encoded, CborReader(encoded, readFeatures = read).read().toCborBytes(writeFeatures = write))
        // -1 这类小负数仍写成普通整数，不受 bignum 特性影响。
        assertEquals(
            "20",
            NumberNode.of(-1).toCborBytes(writeFeatures = CborConfig.DEFAULT_WRITE_FEATURES + write).toHex(),
        )
    }

    @Test
    fun undefinedAsEmbeddedObject() {
        val features = setOf(CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT)
        val expected = ObjectNode.of(CborConfig.UNDEFINED_FIELD to BooleanNode.of(true))

        assertTrue(hex("f7").toCborNode().isNull)
        assertEquals(expected, CborReader(hex("f7"), readFeatures = features).read())
        assertEquals(expected, hex("f7").toCborNode(readFeatures = features))

        val mapper = CborMapper(CborConfig.builder().enable(CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT).build())
        assertEquals(expected, mapper.parse(hex("f7"), Node::class.java))
        // 与真实 null 可区分（null 仍读取为 NullNode；Node 目标经基类会返回 null）。
        assertTrue(CborReader(hex("f6"), readFeatures = features).read().isNull)
    }

    @Test
    fun simpleValueAsEmbeddedObject() {
        val features = setOf(CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT)

        assertEquals(NumberNode.of(16), hex("f0").toCborNode())
        assertEquals(
            ObjectNode.of(CborConfig.SIMPLE_VALUE_FIELD to NumberNode.of(16)),
            CborReader(hex("f0"), readFeatures = features).read(),
        )
        assertEquals(
            ObjectNode.of(CborConfig.SIMPLE_VALUE_FIELD to NumberNode.of(32)),
            CborReader(hex("f820"), readFeatures = features).read(),
        )

        val mapper = CborMapper(CborConfig.builder().enable(CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT).build())
        assertEquals(
            ObjectNode.of(CborConfig.SIMPLE_VALUE_FIELD to NumberNode.of(16)),
            mapper.parse(hex("f0"), Node::class.java),
        )
    }

    @Test
    fun strictDuplicateDetectionOnRead() {
        // {"a":1,"a":2}
        val duplicate = hex("a2616101616102")

        assertEquals(ObjectNode.of("a" to NumberNode.of(2)), CborReader(duplicate).read())
        assertFailsWith<CborParseException> {
            CborReader(
                duplicate,
                readFeatures = setOf(CborReadFeature.STRICT_DUPLICATE_DETECTION),
            ).read()
        }

        val mapper = CborMapper(CborConfig.builder().enable(CborReadFeature.STRICT_DUPLICATE_DETECTION).build())
        assertFailsWith<CborParseException> {
            mapper.parse(duplicate, Node::class.java)
        }
        assertFailsWith<CborParseException> {
            mapper.parse(duplicate, DuplicateBean::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // 写出特性
    // ---------------------------------------------------------------------

    @Test
    fun minimalIntsCanBeDisabled() {
        val fixed = emptySet<CborWriteFeature>()

        assertEquals("05", NumberNode.of(5).toCborBytes().toHex())
        assertEquals("1a00000005", NumberNode.of(5).toCborBytes(writeFeatures = fixed).toHex())
        assertEquals("21", NumberNode.of(-2).toCborBytes().toHex())
        assertEquals("3a00000001", NumberNode.of(-2).toCborBytes(writeFeatures = fixed).toHex())
        assertEquals("1b0000000100000000", NumberNode.of(4294967296L).toCborBytes(writeFeatures = fixed).toHex())
        // 超出 long 的值仍写 bignum，与最小整数编码特性无关。
        assertEquals(
            "c249010000000000000000",
            NumberNode.ofRaw("18446744073709551616").toCborBytes(writeFeatures = fixed).toHex(),
        )

        val mapper = CborMapper(CborConfig.builder().disable(CborWriteFeature.WRITE_MINIMAL_INTS).build())
        assertEquals("1a00000005", mapper.formatCanonicalBytes(NumberNode.of(5)).toHex())
        assertEquals(NumberNode.of(5), CborReader(hex("1a00000005")).read())
    }

    @Test
    fun writeTypeHeader() {
        val features = setOf(
            CborWriteFeature.WRITE_MINIMAL_INTS,
            CborWriteFeature.WRITE_TYPE_HEADER,
        )
        assertEquals("d9d9f705", NumberNode.of(5).toCborBytes(writeFeatures = features).toHex())
        // sequence 只在文档开头写一次自描述 tag。
        assertEquals(
            "d9d9f70102",
            listOf(NumberNode.of(1), NumberNode.of(2)).toCborSequenceBytes(writeFeatures = features).toHex(),
        )
        // 默认读取忽略自描述 tag。
        assertEquals(NumberNode.of(5), CborReader(hex("d9d9f705")).read())

        val mapper = CborMapper(CborConfig.builder().enable(CborWriteFeature.WRITE_TYPE_HEADER).build())
        val value = HeaderBean().apply { name = "x" }
        val bytes = mapper.formatBytes(value)
        assertTrue(bytes.toHex().startsWith("d9d9f7"))
        assertEquals("x", mapper.parse(bytes, HeaderBean::class.java).name)

        // 流式写出与内存写出一致（都带自描述 tag）。
        val output = ByteArrayOutputStream()
        mapper.format(value, output)
        assertContentEquals(bytes, output.toByteArray())
    }

    @Test
    fun lenientUtfEncoding() {
        val value = StringNode.of("a\uD800b")

        assertFailsWith<CborException> {
            value.toCborBytes()
        }
        val lenient = value.toCborBytes(writeFeatures = CborConfig.DEFAULT_WRITE_FEATURES + CborWriteFeature.LENIENT_UTF_ENCODING)
        assertEquals("6561efbfbd62", lenient.toHex())
        assertEquals("a\uFFFDb", CborReader(lenient).read().asString())
    }

    @Test
    fun stringReferences() {
        val node = ObjectNode.of(
            "a" to StringNode.of("hello world"),
            "b" to StringNode.of("hello world"),
        )
        val defaultBytes = node.toCborBytes()
        val refBytes = node.toCborBytes(writeFeatures = CborConfig.DEFAULT_WRITE_FEATURES + CborWriteFeature.STRINGREF)

        assertFalse(defaultBytes.contentEquals(refBytes))
        assertTrue(refBytes.toHex().startsWith("d90100"))
        assertEquals(node, CborReader(refBytes).read())

        // Mapper 的 Token 直写路径同样生效：d90100（stringref 命名空间 tag）+ 不定长 map。
        val mapper = CborMapper(CborConfig.builder().enable(CborWriteFeature.STRINGREF).build())
        val bytes = mapper.formatBytes(node)
        assertTrue(bytes.toHex().startsWith("d90100"))
        assertEquals(node, mapper.parse(bytes, Node::class.java))
    }

    @Test
    fun writeMinimalDoubles() {
        val defaultBytes = CborMapper().formatBytes(DoubleBean().apply { value = 1.5 })
        assertTrue(defaultBytes.toHex().contains("c482200f"), "default double 应为十进制分数")

        val mapper = CborMapper(CborConfig.builder().enable(CborWriteFeature.WRITE_MINIMAL_DOUBLES).build())
        val bytes = mapper.formatBytes(DoubleBean().apply { value = 1.5 })
        assertTrue(bytes.toHex().contains("fa3fc00000"), "1.5 应写成 float32")
        assertEquals(1.5, mapper.parse(bytes, DoubleBean::class.java).value)

        // 无法无损转 float32 时写 float64。
        val pi = mapper.formatBytes(DoubleBean().apply { value = 3.14 })
        assertTrue(pi.toHex().contains("fb40091eb851eb851f"))

        // NaN / Infinity 仍按模块规则拒绝。
        assertFailsWith<CborException> {
            mapper.formatBytes(DoubleBean().apply { value = Double.NaN })
        }
    }

    @Test
    fun writeStrictDuplicateDetection() {
        val writer = CborTokenWriter(
            writeFeatures = CborConfig.DEFAULT_WRITE_FEATURES + CborWriteFeature.STRICT_DUPLICATE_DETECTION,
        )
        try {
            assertFailsWith<CborException> {
                writer.startObject()
                writer.fieldName("a")
                writer.writeNumber("1")
                writer.fieldName("a")
                writer.writeNumber("2")
                writer.endObject()
            }
        } finally {
            runCatching { writer.close() }
        }

        // 关闭时允许（后者覆盖前者，读取时 last wins）。
        val lenientWriter = CborTokenWriter()
        val lenientBytes = try {
            lenientWriter.startObject()
            lenientWriter.fieldName("a")
            lenientWriter.writeNumber("1")
            lenientWriter.fieldName("a")
            lenientWriter.writeNumber("2")
            lenientWriter.endObject()
            lenientWriter.toByteArray()
        } finally {
            lenientWriter.close()
        }
        assertEquals(
            ObjectNode.of("a" to NumberNode.of(2)),
            CborReader(lenientBytes).read(),
        )
    }

    // ---------------------------------------------------------------------
    // Builder、通用便捷方法与 Mapper 集成
    // ---------------------------------------------------------------------

    @Test
    fun builderFeaturesAndConvenienceMethods() {
        val config = CborConfig.builder()
            .enable(CborReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT)
            .disable(CborWriteFeature.WRITE_MINIMAL_INTS)
            .configure(CborWriteFeature.WRITE_TYPE_HEADER, true)
            .writeNulls(false)
            .maxNodeDepth(3)
            .failOnUnknownFields(true)
            .missingFieldPolicy(MissingFieldPolicy.FAIL)
            .build()

        assertTrue(config.isEnabled(CborReadFeature.STRICT_DUPLICATE_DETECTION))
        assertTrue(config.isEnabled(CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT))
        assertFalse(config.isEnabled(CborWriteFeature.WRITE_MINIMAL_INTS))
        assertTrue(config.isEnabled(CborWriteFeature.WRITE_TYPE_HEADER))
        assertFalse(config.writeNulls)
        assertEquals(3, config.maxNodeDepth)
        assertTrue(config.failOnUnknownFields)
        assertEquals(MissingFieldPolicy.FAIL, config.missingFieldPolicy)

        // maxNodeDepth 在 Node 路径生效（深度 4 > 3）。
        val deep = ArrayNode.of(ArrayNode.of(ArrayNode.of(ArrayNode.of(NumberNode.of(1)))))
        assertFailsWith<CborParseException> {
            CborMapper(config).parse(deep.toCborBytes(), Node::class.java)
        }

        // base(config) 与便捷方法互斥。
        val baseConfig = DocumentMapperConfig.builder().maxNodeDepth(1).build()
        assertEquals(1, CborConfig.builder().base(baseConfig).build().maxNodeDepth)
        assertFailsWith<IllegalStateException> {
            CborConfig.builder().base(baseConfig).writeNulls(false).build()
        }

        // 整体设置特性集合。
        val bulk = CborConfig.builder()
            .readFeatures(setOf(CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT))
            .writeFeatures(setOf(CborWriteFeature.WRITE_TYPE_HEADER))
            .build()
        assertEquals(setOf(CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT), bulk.readFeatures)
        assertEquals(setOf(CborWriteFeature.WRITE_TYPE_HEADER), bulk.writeFeatures)
        assertFalse(bulk.isEnabled(CborWriteFeature.WRITE_MINIMAL_INTS))
    }

    @Test
    fun featureWriteNullsAndMissingFieldsTakeEffect() {
        val mapper = CborMapper(
            CborConfig.builder()
                .writeNulls(false)
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )
        val bytes = mapper.formatBytes(NullableBean())
        val node = CborReader(bytes).read().asObject()
        assertFalse(node.containsKey("name"))

        assertFailsWith<DocumentSerializationException> {
            mapper.parse(bytes, NullableBean::class.java)
        }
    }

    @Test
    fun tokenPathOnlyDisabledForNodeOnlyReadFeatures() {
        val plain = RecordingMapper(CborConfig.builder().build())
        plain.parse(hex("a1646e616d656178"), HeaderBean::class.java)
        assertEquals(1, plain.nonNullTokenReaders)

        // 负数 bignum 规范解码、重复 key 检测可在 Token 路径直接生效。
        val decoded = RecordingMapper(
            CborConfig.builder().enable(CborReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING).build(),
        )
        decoded.parse(hex("a1646e616d656178"), HeaderBean::class.java)
        assertEquals(1, decoded.nonNullTokenReaders)
        assertEquals(NumberNode.ofRaw("-1"), decoded.parse(hex("c34100"), Node::class.java))

        // undefined / simple value 需要 Node 标记对象，Token 快路径被禁用。
        val undefinedMapper = RecordingMapper(
            CborConfig.builder().enable(CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT).build(),
        )
        assertEquals(
            ObjectNode.of(CborConfig.UNDEFINED_FIELD to BooleanNode.of(true)),
            undefinedMapper.parse(hex("f7"), Node::class.java),
        )
        assertEquals(0, undefinedMapper.nonNullTokenReaders)
    }

    // ---------------------------------------------------------------------
    // 辅助
    // ---------------------------------------------------------------------

    private class RecordingMapper(config: CborConfig) : CborMapper(config) {
        var nonNullTokenReaders: Int = 0
            private set

        override fun createTokenReader(source: ByteArray): DocumentTokenReader? {
            val reader = super.createTokenReader(source)
            if (reader != null) {
                nonNullTokenReaders++
            }
            return reader
        }
    }

    private class HeaderBean {
        var name: String? = null
    }

    private class DuplicateBean {
        var a: Int = 0
    }

    private class NullableBean {
        var name: String? = null
    }

    private class DoubleBean {
        var value: Double = 0.0
    }

    private fun hex(value: String): ByteArray {
        val normalized = value.replace(" ", "")
        check(normalized.length % 2 == 0) { "Hex string must have an even length" }
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}
