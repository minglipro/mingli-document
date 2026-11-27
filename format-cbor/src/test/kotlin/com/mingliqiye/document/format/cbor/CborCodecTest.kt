package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.cbor.exception.CborException
import com.mingliqiye.document.format.cbor.exception.CborParseException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CBOR 读写测试。
 */
class CborCodecTest {

    @Test
    fun documentTreeRoundTrip() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于 & <cbor>"),
            "age" to NumberNode.of(18),
            "pi" to NumberNode.of(BigDecimal("3.14")),
            "active" to BooleanNode.of(true),
            "missing" to NullNode.INSTANCE,
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "address" to ObjectNode.of("city" to StringNode.of("杭州")),
            "data" to BinaryNode.of(byteArrayOf(0, 1, 2, -1)),
            "matrix" to ArrayNode.of(
                ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
                ObjectNode.of("nested" to NullNode.INSTANCE),
            ),
        )

        val bytes = node.toCborBytes()
        assertTrue(bytes.isNotEmpty())
        assertEquals(node, CborReader(bytes).read())
    }

    @Test
    fun writerProducesExpectedEncodings() {
        assertEquals("00", NumberNode.of(0).toCborBytes().toHex())
        assertEquals("01", NumberNode.of(1).toCborBytes().toHex())
        assertEquals("20", NumberNode.of(-1).toCborBytes().toHex())
        assertEquals("1818", NumberNode.of(24).toCborBytes().toHex())
        assertEquals("1903e8", NumberNode.of(1000).toCborBytes().toHex())
        assertEquals("f4", BooleanNode.of(false).toCborBytes().toHex())
        assertEquals("f5", BooleanNode.of(true).toCborBytes().toHex())
        assertEquals("f6", NullNode.INSTANCE.toCborBytes().toHex())
        assertEquals("626869", StringNode.of("hi").toCborBytes().toHex())
        assertEquals("43010203", BinaryNode.of(byteArrayOf(1, 2, 3)).toCborBytes().toHex())
        assertEquals("80", ArrayNode.EMPTY.toCborBytes().toHex())
        assertEquals("a0", ObjectNode.EMPTY.toCborBytes().toHex())
        assertEquals("820102", ArrayNode.of(NumberNode.of(1), NumberNode.of(2)).toCborBytes().toHex())
        assertEquals("a1616101", ObjectNode.of("a" to NumberNode.of(1)).toCborBytes().toHex())
        // 非整数按 CBOR 十进制分数（tag 4）写出：314 × 10^-2 = 3.14
        assertEquals("c4822119013a", NumberNode.of(BigDecimal("3.14")).toCborBytes().toHex())
    }

    @Test
    fun readsScalars() {
        assertEquals("1", hex("01").toCborNode().asNumber().raw)
        assertEquals("-1", hex("20").toCborNode().asNumber().raw)
        assertTrue(hex("f5").toCborNode().asBoolean())
        assertFalse(hex("f4").toCborNode().asBoolean())
        assertTrue(hex("f6").toCborNode().isNull)
        assertTrue(hex("f7").toCborNode().isNull)
        assertEquals("héllo", hex("6668c3a96c6c6f").toCborNode().asString())
        assertEquals("小于", StringNode.of("小于").toCborBytes().toCborNode().asString())
    }

    @Test
    fun readsFloatingPointNumbers() {
        assertEquals(0, BigDecimal("1.5").compareTo(hex("f93e00").toCborNode().asNumber().value))
        assertEquals(0, BigDecimal("1.5").compareTo(hex("fa3fc00000").toCborNode().asNumber().value))
        assertEquals("1.5", hex("fb3ff8000000000000").toCborNode().asNumber().raw)

        val negativeZero = hex("fb8000000000000000").toCborNode().asNumber()
        assertEquals(0, BigDecimal.ZERO.compareTo(negativeZero.value))
    }

    @Test
    fun nonFiniteFloatsThrowParseException() {
        val encodedValues = listOf(
            "f97e00",
            "fb7ff8000000000000",
            "fb7ff0000000000000",
            "fbfff0000000000000",
        )
        for (encoded in encodedValues) {
            assertFailsWith<CborParseException> {
                hex(encoded).toCborNode()
            }
        }
    }

    @Test
    fun integersPreserveRawAndSupportBignum() {
        assertEquals("18446744073709551615", hex("1bffffffffffffffff").toCborNode().asNumber().raw)
        assertEquals("18446744073709551616", hex("c249010000000000000000").toCborNode().asNumber().raw)
        assertEquals("-18446744073709551616", hex("c349010000000000000000").toCborNode().asNumber().raw)

        val big = NumberNode.of(BigInteger("18446744073709551616"))
        assertEquals("18446744073709551616", CborReader(big.toCborBytes()).read().asNumber().raw)
    }

    @Test
    fun decimalFractionTagMapsToNumber() {
        val node = hex("c4822119013a").toCborNode()

        assertTrue(node.isNumber)
        assertEquals("3.14", node.asNumber().raw)
        assertEquals(0, BigDecimal("3.14").compareTo(node.asNumber().value))
        assertEquals(node, CborReader(node.toCborBytes()).read())
    }

    @Test
    fun decimalPrecisionIsPreservedThroughRoundTrip() {
        val raw = "123456789012345678901234567890.1234567890123456789"
        val node = NumberNode.of(BigDecimal(raw))

        val restored = CborReader(node.toCborBytes()).read().asNumber()
        assertEquals(node, restored)
        assertEquals(0, BigDecimal(raw).compareTo(restored.value))

        val huge = NumberNode.ofRaw("1e400")
        assertEquals(huge, CborReader(huge.toCborBytes()).read().asNumber())
    }

    @Test
    fun byteStringsMapToBinaryNode() {
        val node = hex("43010203").toCborNode()
        assertTrue(node.isBinary)
        assertFalse(node.isString)
        assertContentEquals(byteArrayOf(1, 2, 3), node.asBinary())
        assertEquals("43010203", node.toCborBytes().toHex())

        assertEquals(0, hex("40").toCborNode().asBinary().size)
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5),
            hex("5f42010243030405ff").toCborNode().asBinary(),
        )
    }

    @Test
    fun readsNestedContainers() {
        val node = hex("a26161016162820203").toCborNode().asObject()

        assertEquals(2, node.size)
        assertEquals(1, node["a"]?.asNumber()?.toInt())
        assertEquals(2, node["b"]?.asArray()?.size)
        assertEquals(3, node["b"]?.asArray()?.get(1)?.asNumber()?.toInt())
        assertEquals(2, hex("9f0102ff").toCborNode().asArray().size)
    }

    @Test
    fun integerMapKeysBecomeStringFields() {
        val node = hex("a1016161").toCborNode().asObject()

        assertEquals(setOf("1"), node.keys)
        assertEquals("a", node["1"]?.asString())
    }

    @Test
    fun invalidBytesThrowParseException() {
        val encodedValues = listOf("ff", "1c", "8201", "6261", "9f")
        for (encoded in encodedValues) {
            assertFailsWith<CborParseException> {
                hex(encoded).toCborNode()
            }
        }
    }

    @Test
    fun emptyInputThrowsParseException() {
        assertFailsWith<CborParseException> {
            CborReader(ByteArray(0)).read()
        }
        assertFailsWith<CborParseException> {
            ByteArray(0).toCborNode()
        }
    }

    @Test
    fun multipleTopLevelValuesThrowParseException() {
        assertFailsWith<CborParseException> {
            hex("0102").toCborNode()
        }
    }

    @Test
    fun writerRejectsInvalidNumberRaw() {
        assertFailsWith<CborException> {
            NumberNode.ofRaw("not-a-number").toCborBytes()
        }
    }

    @Test
    fun readerCompanionAndExtensions() {
        val node = hex("a1616101").toCborNode()

        assertEquals(node, CborReader.parse(hex("a1616101")))
        assertEquals("a1616101", node.toCborBytes().toHex())
        assertEquals(node, CborReader(node.toCborBytes()).read())
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
