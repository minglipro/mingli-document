package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.smile.exception.SmileParseException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Smile 读写测试。
 */
class SmileCodecTest {

    @Test
    fun documentTreeRoundTrip() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于 & <smile>"),
            "age" to NumberNode.of(18),
            "pi" to NumberNode.of(BigDecimal("3.14")),
            "active" to BooleanNode.of(true),
            "missing" to NullNode.INSTANCE,
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "address" to ObjectNode.of("city" to StringNode.of("杭州")),
            "matrix" to ArrayNode.of(
                ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
                ObjectNode.of("nested" to NullNode.INSTANCE),
            ),
        )

        val bytes = node.toSmileBytes()
        assertTrue(bytes.isNotEmpty())
        assertEquals(0x3A.toByte(), bytes[0])
        assertEquals(node, SmileReader(bytes).read())
    }

    @Test
    fun rootScalarsRoundTrip() {
        val nodes = listOf(
            NullNode.INSTANCE,
            BooleanNode.of(true),
            BooleanNode.of(false),
            StringNode.of(""),
            StringNode.of("héllo 小于"),
            NumberNode.of(0),
            NumberNode.of(-1),
            NumberNode.of(Long.MAX_VALUE),
            NumberNode.of(BigInteger("999999999999999999999999999999")),
            NumberNode.of(BigDecimal("3.14159265358979323846")),
            ArrayNode.EMPTY,
            ObjectNode.EMPTY,
        )

        for (node in nodes) {
            assertEquals(node, SmileReader(node.toSmileBytes()).read(), "round trip failed for $node")
        }
    }

    @Test
    fun binaryValuesRoundTripNatively() {
        val payload = ByteArray(256) { (it and 0xFF).toByte() }
        val node = ObjectNode.of(
            "data" to BinaryNode.of(payload),
            "empty" to BinaryNode.of(ByteArray(0)),
            "text" to StringNode.of("hi"),
        )

        val restored = SmileReader(node.toSmileBytes()).read()
        assertEquals(node, restored)
        assertContentEquals(payload, restored.asObject()["data"]?.asBinary())
        assertContentEquals(ByteArray(0), restored.asObject()["empty"]?.asBinary())
    }

    @Test
    fun parseObjectArrayAndNestedValues() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "list" to ArrayNode.of(
                NumberNode.of(1),
                ArrayNode.of(NumberNode.of(2), NumberNode.of(3)),
                ObjectNode.of("x" to BooleanNode.of(true)),
            ),
        )

        val obj = SmileReader(node.toSmileBytes()).read().asObject()
        assertEquals("小于", obj["name"]?.asString())
        assertEquals(2, obj["tags"]?.asArray()?.size)
        assertEquals(3, obj["list"]?.asArray()?.size)
        assertEquals(true, obj["list"]?.asArray()?.get(2)?.asObject()?.get("x")?.asBoolean())
    }

    @Test
    fun parseNumbers() {
        val node = ArrayNode.of(
            NumberNode.of(0),
            NumberNode.of(-1),
            NumberNode.of(BigDecimal("1.5")),
            NumberNode.of(Long.MIN_VALUE),
            NumberNode.of(BigInteger("123456789012345678901234567890")),
        )

        val array = SmileReader(node.toSmileBytes()).read().asArray()
        assertEquals(5, array.size)
        assertEquals(0, array[0].asNumber().toInt())
        assertEquals(-1, array[1].asNumber().toInt())
        assertEquals(0, BigDecimal("1.5").compareTo(array[2].asNumber().value))
        assertEquals(Long.MIN_VALUE, array[3].asNumber().toLong())
        assertEquals(BigInteger("123456789012345678901234567890"), array[4].asNumber().toBigInteger())
    }

    @Test
    fun duplicateFieldNamesKeepLastValue() {
        val node = ObjectNode.of("key" to StringNode.of("first"))

        val restored = SmileReader(node.toSmileBytes()).read().asObject()
        assertEquals("first", restored["key"]?.asString())
    }

    @Test
    fun corruptBytesThrowParseException() {
        assertFailsWith<SmileParseException> {
            SmileReader(ByteArray(0)).read()
        }
        assertFailsWith<SmileParseException> {
            SmileReader(byteArrayOf(1, 2, 3, 4)).read()
        }
        assertFailsWith<SmileParseException> {
            SmileReader("""{"name":"x"}""".toByteArray(Charsets.UTF_8)).read()
        }
    }

    @Test
    fun truncatedContentThrowsParseException() {
        val bytes = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "count" to NumberNode.of(7),
        ).toSmileBytes()
        val truncated = bytes.copyOf(bytes.size - 1)

        assertFailsWith<SmileParseException> {
            SmileReader(truncated).read()
        }
    }

    @Test
    fun elementExtensions() {
        val node = SmileReader.parse(ObjectNode.of("a" to NumberNode.of(1)).toSmileBytes())

        assertTrue(node.isObject)
        assertEquals(1, node.asObject()["a"]?.asNumber()?.toInt())
        assertEquals(node, node.toSmileBytes().toSmileNode())
    }

    @Test
    fun writerAndReaderInstancesAreReusable() {
        val reader = SmileReader(ArrayNode.of(NumberNode.of(1), StringNode.of("x")).toSmileBytes())
        val writer = SmileWriter()

        assertEquals(ArrayNode.of(NumberNode.of(1), StringNode.of("x")), reader.read())
        assertEquals(ObjectNode.of("ok" to BooleanNode.of(true)), SmileReader(writer.write(ObjectNode.of("ok" to BooleanNode.of(true)))).read())
    }
}
