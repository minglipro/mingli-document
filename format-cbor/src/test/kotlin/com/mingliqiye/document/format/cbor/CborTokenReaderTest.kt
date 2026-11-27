package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.format.cbor.exception.CborParseException
import com.mingliqiye.document.serialization.DocumentTokenType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CborTokenReader] 测试。
 */
class CborTokenReaderTest {

    @Test
    fun readsObjectTokens() {
        // {"name":"a","age":24,"act":true,"mis":null}
        val reader = CborTokenReader(hex("a4646e616d65616163616765181863616374f5636d6973f6"))

        assertNull(reader.currentToken)
        assertEquals(DocumentTokenType.START_OBJECT, reader.nextToken())
        assertNull(reader.textValue)

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("name", reader.textValue)
        assertEquals(DocumentTokenType.STRING, reader.nextToken())
        assertEquals("a", reader.textValue)

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("age", reader.textValue)
        assertEquals(DocumentTokenType.NUMBER, reader.nextToken())
        assertEquals("24", reader.textValue)

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("act", reader.textValue)
        assertEquals(DocumentTokenType.BOOLEAN, reader.nextToken())
        assertEquals(true, reader.booleanValue)
        assertNull(reader.textValue)

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("mis", reader.textValue)
        assertEquals(DocumentTokenType.NULL, reader.nextToken())
        assertNull(reader.booleanValue)

        assertEquals(DocumentTokenType.END_OBJECT, reader.nextToken())
        assertNull(reader.textValue)
        assertNull(reader.nextToken())
        assertNull(reader.currentToken)
    }

    @Test
    fun readsArrayTokens() {
        // [1,"x",true]
        val reader = CborTokenReader(hex("83016178f5"))

        assertEquals(DocumentTokenType.START_ARRAY, reader.nextToken())
        assertEquals(DocumentTokenType.NUMBER, reader.nextToken())
        assertEquals("1", reader.textValue)
        assertEquals(DocumentTokenType.STRING, reader.nextToken())
        assertEquals("x", reader.textValue)
        assertEquals(DocumentTokenType.BOOLEAN, reader.nextToken())
        assertEquals(true, reader.booleanValue)
        assertEquals(DocumentTokenType.END_ARRAY, reader.nextToken())
        assertNull(reader.nextToken())
    }

    @Test
    fun readsNumberRawText() {
        // [31, 1000, 18446744073709551615, 18446744073709551616, -1, 1.5]
        val encoded = "86181f1903e81bffffffffffffffffc24901000000000000000020fb3ff8000000000000"
        val reader = CborTokenReader(hex(encoded))

        assertEquals(DocumentTokenType.START_ARRAY, reader.nextToken())
        val raws = ArrayList<String>()
        while (true) {
            val token = reader.nextToken()
            if (token != DocumentTokenType.NUMBER) {
                assertEquals(DocumentTokenType.END_ARRAY, token)
                break
            }
            raws.add(requireNotNull(reader.textValue))
        }
        assertEquals(
            listOf(
                "31",
                "1000",
                "18446744073709551615",
                "18446744073709551616",
                "-1",
                "1.5",
            ),
            raws,
        )
        assertNull(reader.nextToken())
    }

    @Test
    fun readsFloatAndDecimalFractionTokens() {
        val float16 = CborTokenReader(hex("f93e00"))
        assertEquals(DocumentTokenType.NUMBER, float16.nextToken())
        assertEquals("1.5", float16.textValue)
        assertNull(float16.nextToken())

        // tag 4 十进制分数：314 × 10^-2 = 3.14
        val decimal = CborTokenReader(hex("c4822119013a"))
        assertEquals(DocumentTokenType.NUMBER, decimal.nextToken())
        assertEquals("3.14", decimal.textValue)
        assertNull(decimal.nextToken())
    }

    @Test
    fun readsBinaryTokens() {
        val reader = CborTokenReader(hex("43010203"))

        assertEquals(DocumentTokenType.BINARY, reader.nextToken())
        assertNull(reader.textValue)
        assertContentEquals(byteArrayOf(1, 2, 3), reader.binaryValue)
        assertNull(reader.nextToken())

        val empty = CborTokenReader(hex("40"))
        assertEquals(DocumentTokenType.BINARY, empty.nextToken())
        assertContentEquals(ByteArray(0), empty.binaryValue)
    }

    @Test
    fun readsBinaryFieldValue() {
        // {"b": h'010203'}
        val reader = CborTokenReader(hex("a1616243010203"))

        assertEquals(DocumentTokenType.START_OBJECT, reader.nextToken())
        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("b", reader.textValue)
        assertEquals(DocumentTokenType.BINARY, reader.nextToken())
        assertContentEquals(byteArrayOf(1, 2, 3), reader.binaryValue)
        assertEquals(DocumentTokenType.END_OBJECT, reader.nextToken())
    }

    @Test
    fun readsUndefinedAsNull() {
        val reader = CborTokenReader(hex("f7"))

        assertEquals(DocumentTokenType.NULL, reader.nextToken())
        assertNull(reader.nextToken())
    }

    @Test
    fun readsIntegerMapKeysAsFieldNames() {
        // {1: "a"}
        val reader = CborTokenReader(hex("a1016161"))

        assertEquals(DocumentTokenType.START_OBJECT, reader.nextToken())
        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("1", reader.textValue)
        assertEquals(DocumentTokenType.STRING, reader.nextToken())
        assertEquals("a", reader.textValue)
        assertEquals(DocumentTokenType.END_OBJECT, reader.nextToken())
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
                CborTokenReader(hex(encoded)).nextToken()
            }
        }
    }

    @Test
    fun invalidBytesThrowParseException() {
        val encodedValues = listOf("ff", "1c", "8201", "6261")
        for (encoded in encodedValues) {
            assertFailsWith<CborParseException> {
                val reader = CborTokenReader(hex(encoded))
                while (reader.nextToken() != null) {
                    // 持续读取，在损坏的位置触发解析异常。
                }
            }
        }
    }

    @Test
    fun emptyInputHasNoTokens() {
        val reader = CborTokenReader(ByteArray(0))

        assertNull(reader.nextToken())
        assertNull(reader.currentToken)
    }

    @Test
    fun tokenReaderExposesSingleDocumentStream() {
        val reader = CborTokenReader(hex("a26161016162820203"))

        val tokens = ArrayList<DocumentTokenType>()
        while (true) {
            val token = reader.nextToken() ?: break
            tokens.add(token)
            if (tokens.size > 32) {
                break
            }
        }
        assertTrue(tokens.isNotEmpty())
        assertEquals(DocumentTokenType.START_OBJECT, tokens.first())
        assertEquals(DocumentTokenType.END_OBJECT, tokens.last())
    }

    private fun hex(value: String): ByteArray {
        val normalized = value.replace(" ", "")
        check(normalized.length % 2 == 0) { "Hex string must have an even length" }
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
