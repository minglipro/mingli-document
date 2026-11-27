package com.mingliqiye.document.serialization

import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.json.JsonReader
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class StringLengthLimitTest {

    @Test
    fun maxStringLengthRejectsValuesAndKeys() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().maxStringLength(3).build())

        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(JsonReader.parse("""{"name":"long"}"""), Map::class.java)
        }
        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(JsonReader.parse("""{"long-key":"a"}"""), Map::class.java)
        }
        assertFailsWith<DocumentSerializationException> {
            mapper.toNode(mapOf("name" to "long"))
        }
    }

    @Test
    fun limitAppliesToNestedStrings() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().maxStringLength(3).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(JsonReader.parse("""{"a":["ok","long"]}"""), Map::class.java)
        }
    }
}
