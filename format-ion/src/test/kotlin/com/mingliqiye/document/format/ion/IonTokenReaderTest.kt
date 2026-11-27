package com.mingliqiye.document.format.ion

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.ion.exception.IonParseException
import com.mingliqiye.document.serialization.DocumentTokenType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * [IonTokenReader] 测试。
 */
class IonTokenReaderTest {

    @Test
    fun structTokenSequence() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "age" to NumberNode.of(18),
            "active" to BooleanNode.of(true),
            "missing" to NullNode.INSTANCE,
            "data" to BinaryNode.of(byteArrayOf(1, 2, 3)),
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "address" to ObjectNode.of("city" to StringNode.of("杭州")),
        )

        val tokens = readTokens(node.toIonBytes())
        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.STRING,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NUMBER,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.BOOLEAN,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NULL,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.BINARY,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.STRING,
                DocumentTokenType.STRING,
                DocumentTokenType.END_ARRAY,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.STRING,
                DocumentTokenType.END_OBJECT,
                DocumentTokenType.END_OBJECT,
            ),
            tokens.map { it.first },
        )
        assertEquals(
            listOf("name", "age", "active", "missing", "data", "tags", "address", "city"),
            tokens.filter { it.first == DocumentTokenType.FIELD_NAME }.map { it.second },
        )
        assertEquals("小于", tokens[2].second)
        assertEquals("18", tokens[4].second)
        assertEquals("a", tokens[13].second)
        assertEquals("b", tokens[14].second)
        assertEquals("杭州", tokens[19].second)
    }

    @Test
    fun arrayAndScalarTokenSequence() {
        val array = ArrayNode.of(
            NumberNode.of(1),
            StringNode.of("x"),
            BooleanNode.of(false),
            NullNode.INSTANCE,
        )

        val tokens = readTokens(array.toIonBytes())
        assertEquals(
            listOf(
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.NUMBER,
                DocumentTokenType.STRING,
                DocumentTokenType.BOOLEAN,
                DocumentTokenType.NULL,
                DocumentTokenType.END_ARRAY,
            ),
            tokens.map { it.first },
        )
        assertEquals("1", tokens[1].second)
        assertEquals("x", tokens[2].second)

        val scalarReader = IonTokenReader(NumberNode.of(42).toIonBytes())
        assertEquals(DocumentTokenType.NUMBER, scalarReader.nextToken())
        assertEquals("42", scalarReader.textValue)
        assertNull(scalarReader.nextToken())
        assertNull(scalarReader.currentToken)
    }

    @Test
    fun booleanNullAndBinaryValuesAreExposed() {
        val blob = byteArrayOf(9, 8, 7, -1)
        val node = ObjectNode.of(
            "ok" to BooleanNode.of(true),
            "none" to NullNode.INSTANCE,
            "blob" to BinaryNode.of(blob),
        )

        val reader = IonTokenReader(node.toIonBytes())
        assertNull(reader.currentToken)
        assertEquals(DocumentTokenType.START_OBJECT, reader.nextToken())

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("ok", reader.textValue)
        assertEquals(DocumentTokenType.BOOLEAN, reader.nextToken())
        assertEquals(true, reader.booleanValue)

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertNull(reader.booleanValue)
        assertEquals(DocumentTokenType.NULL, reader.nextToken())
        assertNull(reader.booleanValue)

        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals(DocumentTokenType.BINARY, reader.nextToken())
        assertContentEquals(blob, reader.binaryValue)

        assertEquals(DocumentTokenType.END_OBJECT, reader.nextToken())
        assertNull(reader.nextToken())
        assertNull(reader.currentToken)
        assertNull(reader.textValue)
        assertNull(reader.binaryValue)
    }

    @Test
    fun nestedContainerTokenSequence() {
        val node = ObjectNode.of(
            "matrix" to ArrayNode.of(
                ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
                ObjectNode.of("deep" to ArrayNode.of(StringNode.of("v"))),
            ),
        )

        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.NUMBER,
                DocumentTokenType.NUMBER,
                DocumentTokenType.END_ARRAY,
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.STRING,
                DocumentTokenType.END_ARRAY,
                DocumentTokenType.END_OBJECT,
                DocumentTokenType.END_ARRAY,
                DocumentTokenType.END_OBJECT,
            ),
            readTokens(node.toIonBytes()).map { it.first },
        )
    }

    @Test
    fun textIonBytesAreAutoDetected() {
        val bytes = "{a: 1, b: [true, null], c: 'sym', at: 2024-01-02T03:04:05Z}"
            .toByteArray(Charsets.UTF_8)

        val tokens = readTokens(bytes)
        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NUMBER,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.BOOLEAN,
                DocumentTokenType.NULL,
                DocumentTokenType.END_ARRAY,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.STRING,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.STRING,
                DocumentTokenType.END_OBJECT,
            ),
            tokens.map { it.first },
        )
        assertEquals("1", tokens[2].second)
        assertEquals("sym", tokens[9].second)
        assertEquals("2024-01-02T03:04:05Z", tokens[11].second)
    }

    @Test
    fun emptyInputEndsImmediately() {
        val reader = IonTokenReader(ByteArray(0))

        assertNull(reader.currentToken)
        assertNull(reader.nextToken())
        assertNull(reader.currentToken)
    }

    @Test
    fun invalidBytesThrowParseException() {
        val badBytes = byteArrayOf(0xE0.toByte(), 0x01, 0x00, 0xEA.toByte(), 0xFF.toByte())

        assertFailsWith<IonParseException> {
            IonTokenReader(badBytes).nextToken()
        }
    }

    @Test
    fun mapperFromTokensDecodesNestedBean() {
        val mapper = IonMapper()
        val record = Record().apply {
            name = "小于"
            age = 18
            active = true
            tags = listOf("a", "b")
            address = Address().apply { city = "杭州" }
            data = byteArrayOf(1, 2, -1)
        }

        val restored = mapper.fromTokens(IonTokenReader(mapper.formatBytes(record)), Record::class.java)

        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertEquals(true, restored.active)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals("杭州", restored.address?.city)
        assertContentEquals(byteArrayOf(1, 2, -1), restored.data)
    }

    private fun readTokens(bytes: ByteArray): List<Pair<DocumentTokenType, String?>> {
        val reader = IonTokenReader(bytes)
        val tokens = ArrayList<Pair<DocumentTokenType, String?>>()
        while (true) {
            val token = reader.nextToken() ?: break
            tokens.add(token to reader.textValue)
        }
        return tokens
    }

    private class Record {
        var name: String? = null
        var age: Int = 0
        var active: Boolean = false
        var tags: List<String> = emptyList()
        var address: Address? = null
        var data: ByteArray? = null
    }

    private class Address {
        var city: String? = null
    }
}
