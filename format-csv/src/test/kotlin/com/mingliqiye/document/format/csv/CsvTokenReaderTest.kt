package com.mingliqiye.document.format.csv

import com.mingliqiye.document.serialization.DocumentTokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvTokenReaderTest {

    @Test
    fun headerRowsEmitNativeTokens() {
        val reader = CsvTokenReader("name,age\n小于,18", ',', true)
        val types = mutableListOf<DocumentTokenType>()
        val values = mutableListOf<String?>()
        while (true) {
            val token = reader.nextToken() ?: break
            types.add(token)
            values.add(reader.textValue)
        }

        assertEquals(
            listOf(
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.STRING,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NUMBER,
                DocumentTokenType.END_OBJECT,
                DocumentTokenType.END_ARRAY,
            ),
            types,
        )
        assertEquals(
            listOf(null, null, "name", "小于", "age", "18", null, null),
            values,
        )
    }

    @Test
    fun headerlessAndEmptyInputs() {
        val headerless = CsvTokenReader("1,true", ',', false)
        assertEquals(DocumentTokenType.START_ARRAY, headerless.nextToken())
        assertEquals(DocumentTokenType.START_OBJECT, headerless.nextToken())
        assertEquals(DocumentTokenType.FIELD_NAME, headerless.nextToken())
        assertEquals("column1", headerless.textValue)
        assertEquals(DocumentTokenType.NUMBER, headerless.nextToken())

        val empty = CsvTokenReader("", ',', true)
        assertEquals(DocumentTokenType.START_ARRAY, empty.nextToken())
        assertEquals(DocumentTokenType.END_ARRAY, empty.nextToken())
        assertNull(empty.nextToken())
    }
}
