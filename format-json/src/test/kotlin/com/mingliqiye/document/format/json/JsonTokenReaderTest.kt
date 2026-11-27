package com.mingliqiye.document.format.json

import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.serialization.DocumentTokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [JsonTokenReader] 测试。
 */
class JsonTokenReaderTest {

    @Test
    fun emitsTokenSequence() {
        val reader = JsonTokenReader(
            """{"name":"小于","age":18,"ok":true,"tags":[1,null]}""",
        )
        val tokens = ArrayList<DocumentTokenType>()
        while (true) {
            tokens.add(reader.nextToken() ?: break)
        }

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
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.NUMBER,
                DocumentTokenType.NULL,
                DocumentTokenType.END_ARRAY,
                DocumentTokenType.END_OBJECT,
            ),
            tokens,
        )
    }

    @Test
    fun supportsCommentsWhenEnabled() {
        val reader = JsonTokenReader("""{"a":1} // comment""", allowComments = true)
        val tokens = ArrayList<DocumentTokenType>()
        while (true) {
            tokens.add(reader.nextToken() ?: break)
        }
        assertEquals(DocumentTokenType.END_OBJECT, tokens.last())
    }

    @Test
    fun rejectsTrailingContent() {
        val reader = JsonTokenReader("""{"a":1} trailing""", allowComments = false)

        assertFailsWith<JsonParseException> {
            while (reader.nextToken() != null) {
                // drain
            }
        }
    }

    @Test
    fun rejectsMalformedJson() {
        assertFailsWith<JsonParseException> {
            val reader = JsonTokenReader("""{"a":}""")
            while (reader.nextToken() != null) {
                // drain
            }
        }
    }
}
