package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.smile.exception.SmileParseException
import com.mingliqiye.document.serialization.DocumentTokenType
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * [SmileTokenReader] Token 序列测试。
 */
class SmileTokenReaderTest {

    @Test
    fun readsObjectTokenSequence() {
        val bytes = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "age" to NumberNode.of(18),
            "active" to BooleanNode.of(true),
            "missing" to NullNode.INSTANCE,
        ).toSmileBytes()

        val reader = SmileTokenReader(bytes)

        assertNull(reader.currentToken)
        assertNull(reader.textValue)
        assertNull(reader.booleanValue)
        assertNull(reader.binaryValue)

        assertEquals(DocumentTokenType.START_OBJECT, reader.nextToken())
        assertNull(reader.textValue)

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("name", reader.textValue)
        assertEquals(DocumentTokenType.STRING, reader.nextToken())
        assertEquals("小于", reader.textValue)

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("age", reader.textValue)
        assertEquals(DocumentTokenType.NUMBER, reader.nextToken())
        assertEquals("18", reader.textValue)

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("active", reader.textValue)
        assertEquals(DocumentTokenType.BOOLEAN, reader.nextToken())
        assertEquals(true, reader.booleanValue)
        assertNull(reader.textValue)

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("missing", reader.textValue)
        assertEquals(DocumentTokenType.NULL, reader.nextToken())
        assertNull(reader.booleanValue)

        assertEquals(DocumentTokenType.END_OBJECT, reader.nextToken())
        assertNull(reader.nextToken())
        assertNull(reader.currentToken)
    }

    @Test
    fun readsArrayAndNestedTokenSequence() {
        val bytes = ObjectNode.of(
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "matrix" to ArrayNode.of(ArrayNode.of(NumberNode.of(1), NumberNode.of(2))),
        ).toSmileBytes()

        val reader = SmileTokenReader(bytes)

        assertEquals(DocumentTokenType.START_OBJECT, reader.nextToken())
        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("tags", reader.textValue)
        assertEquals(DocumentTokenType.START_ARRAY, reader.nextToken())
        assertEquals(DocumentTokenType.STRING, reader.nextToken())
        assertEquals("a", reader.textValue)
        assertEquals(DocumentTokenType.STRING, reader.nextToken())
        assertEquals("b", reader.textValue)
        assertEquals(DocumentTokenType.END_ARRAY, reader.nextToken())

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("matrix", reader.textValue)
        assertEquals(DocumentTokenType.START_ARRAY, reader.nextToken())
        assertEquals(DocumentTokenType.START_ARRAY, reader.nextToken())
        assertEquals(DocumentTokenType.NUMBER, reader.nextToken())
        assertEquals("1", reader.textValue)
        assertEquals(DocumentTokenType.NUMBER, reader.nextToken())
        assertEquals("2", reader.textValue)
        assertEquals(DocumentTokenType.END_ARRAY, reader.nextToken())
        assertEquals(DocumentTokenType.END_ARRAY, reader.nextToken())
        assertEquals(DocumentTokenType.END_OBJECT, reader.nextToken())
        assertNull(reader.nextToken())
    }

    @Test
    fun readsNumberRawText() {
        val bytes = ArrayNode.of(
            NumberNode.of(-1),
            NumberNode.of(Long.MAX_VALUE),
            NumberNode.of(BigDecimal("3.14")),
            NumberNode.of(BigInteger("123456789012345678901234567890")),
        ).toSmileBytes()

        val reader = SmileTokenReader(bytes)

        assertEquals(DocumentTokenType.START_ARRAY, reader.nextToken())
        assertEquals(DocumentTokenType.NUMBER, reader.nextToken())
        assertEquals("-1", reader.textValue)
        assertEquals(DocumentTokenType.NUMBER, reader.nextToken())
        assertEquals(Long.MAX_VALUE.toString(), reader.textValue)
        assertEquals(DocumentTokenType.NUMBER, reader.nextToken())
        assertEquals("3.14", reader.textValue)
        assertEquals(DocumentTokenType.NUMBER, reader.nextToken())
        assertEquals("123456789012345678901234567890", reader.textValue)
        assertEquals(DocumentTokenType.END_ARRAY, reader.nextToken())
    }

    @Test
    fun readsBinaryToken() {
        val payload = ByteArray(16) { (it * 17).toByte() }
        val bytes = ObjectNode.of("data" to BinaryNode.of(payload)).toSmileBytes()

        val reader = SmileTokenReader(bytes)

        assertEquals(DocumentTokenType.START_OBJECT, reader.nextToken())
        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("data", reader.textValue)
        assertEquals(DocumentTokenType.BINARY, reader.nextToken())
        assertContentEquals(payload, reader.binaryValue)
        assertNull(reader.textValue)
        assertNull(reader.booleanValue)
        assertEquals(DocumentTokenType.END_OBJECT, reader.nextToken())
        assertNull(reader.nextToken())
    }

    @Test
    fun invalidBytesThrowParseException() {
        assertFailsWith<SmileParseException> {
            SmileTokenReader(byteArrayOf(1, 2, 3, 4))
        }
        assertFailsWith<SmileParseException> {
            SmileTokenReader("""{"name":"x"}""".toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun truncatedBytesThrowParseException() {
        val bytes = ObjectNode.of("name" to StringNode.of("小于")).toSmileBytes()
        val reader = SmileTokenReader(bytes.copyOf(bytes.size - 1))

        assertFailsWith<SmileParseException> {
            while (reader.nextToken() != null) {
                // 持续读取直到损坏的末尾触发解析异常。
            }
        }
    }
}
