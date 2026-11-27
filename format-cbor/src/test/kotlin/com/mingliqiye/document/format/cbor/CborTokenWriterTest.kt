package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.cbor.exception.CborException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [CborTokenWriter] 测试。
 */
class CborTokenWriterTest {

    @Test
    fun writesScalarTokens() {
        assertEquals("f6", write { it.writeNull() }.toHex())
        assertEquals("f4", write { it.writeBoolean(false) }.toHex())
        assertEquals("f5", write { it.writeBoolean(true) }.toHex())
        assertEquals("626869", write { it.writeString("hi") }.toHex())
        assertEquals("182a", write { it.writeInt(42) }.toHex())
        assertEquals("1b0000000100000000", write { it.writeLong(4294967296L) }.toHex())
        assertEquals("c482200f", write { it.writeDouble(1.5) }.toHex())
    }

    @Test
    fun writesNumbersWithNodeStrategy() {
        assertEquals("01", write { it.writeNumber("1") }.toHex())
        assertEquals("20", write { it.writeNumber("-1") }.toHex())
        assertEquals("1818", write { it.writeNumber("24") }.toHex())
        assertEquals("1903e8", write { it.writeNumber("1000") }.toHex())
        // 超出 Long 范围的正整数写成 bignum（tag 2），与 CborWriter 策略一致
        assertEquals("c24900ffffffffffffffff", write { it.writeNumber("18446744073709551615") }.toHex())
        assertEquals("c249010000000000000000", write { it.writeNumber("18446744073709551616") }.toHex())
        // 非整数写成 CBOR 十进制分数（tag 4）
        assertEquals("c4822119013a", write { it.writeNumber("3.14") }.toHex())
        assertEquals("c482200f", write { it.writeNumber("1.5") }.toHex())
    }

    @Test
    fun writesIndefiniteLengthContainers() {
        assertEquals(
            "bfff",
            write {
                it.startObject()
                it.endObject()
            }.toHex(),
        )
        assertEquals(
            "9fff",
            write {
                it.startArray()
                it.endArray()
            }.toHex(),
        )

        val nested = write { writer ->
            writer.startObject()
            writer.fieldName("a")
            writer.startArray()
            writer.writeNumber("1")
            writer.writeString("x")
            writer.endArray()
            writer.fieldName("b")
            writer.startObject()
            writer.fieldName("c")
            writer.writeBoolean(true)
            writer.endObject()
            writer.endObject()
        }
        assertEquals("bf61619f016178ff6162bf6163f5ffff", nested.toHex())

        val node = CborReader(nested).read().asObject()
        assertEquals(1, node["a"]?.asArray()?.get(0)?.asNumber()?.toInt())
        assertEquals("x", node["a"]?.asArray()?.get(1)?.asString())
        assertEquals(true, node["b"]?.asObject()?.get("c")?.asBoolean())
    }

    @Test
    fun writesBinaryTokens() {
        val payload = byteArrayOf(0, 1, 2, 127, -1)
        val bytes = write { it.writeBinary(payload) }

        assertEquals("450001027fff", bytes.toHex())
        val node = CborReader(bytes).read()
        assertTrue(node.isBinary)
        assertContentEquals(payload, node.asBinary())
    }

    @Test
    fun writesBinaryFieldValue() {
        val bytes = write { writer ->
            writer.startObject()
            writer.fieldName("data")
            writer.writeBinary(byteArrayOf(1, 2, 3))
            writer.endObject()
        }

        val node = CborReader(bytes).read().asObject()
        assertContentEquals(byteArrayOf(1, 2, 3), node["data"]?.asBinary())
    }

    @Test
    fun writeNodeDefaultProducesReadableBytes() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "age" to NumberNode.of(18),
            "pi" to NumberNode.of(BigDecimal("3.14")),
            "active" to BooleanNode.of(true),
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "data" to BinaryNode.of(byteArrayOf(1, 2, 3)),
        )
        val bytes = write { it.writeNode(node) }

        assertEquals(node, CborReader(bytes).read())
    }

    @Test
    fun toByteArrayCanBeCalledRepeatedly() {
        val writer = CborTokenWriter()
        try {
            writer.writeString("hi")
            val first = writer.toByteArray()
            val second = writer.toByteArray()
            assertEquals("626869", first.toHex())
            assertContentEquals(first, second)
        } finally {
            writer.close()
        }
    }

    @Test
    fun invalidNumberRawThrowsCborException() {
        val writer = CborTokenWriter()
        try {
            assertFailsWith<CborException> {
                writer.writeNumber("not-a-number")
            }
        } finally {
            writer.close()
        }
    }

    private fun write(block: (CborTokenWriter) -> Unit): ByteArray {
        val writer = CborTokenWriter()
        return try {
            block(writer)
            writer.toByteArray()
        } finally {
            writer.close()
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}
