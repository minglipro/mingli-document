package com.mingliqiye.document.format.json.schema

import com.mingliqiye.document.format.json.JsonReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonSchemaTest {

    private val schema = JsonSchema.compile(
        """
        {
          "type": "object",
          "required": ["name"],
          "properties": {
            "name": {"type": "string", "minLength": 1},
            "age": {"type": "integer", "minimum": 0},
            "tags": {"type": "array", "items": {"type": "string"}, "uniqueItems": true},
            "address": {"${'$'}ref": "#/definitions/address"}
          },
          "additionalProperties": false,
          "definitions": {
            "address": {
              "type": "object",
              "required": ["city"],
              "properties": {"city": {"type": "string"}},
              "additionalProperties": false
            }
          }
        }
        """.trimIndent(),
    )

    @Test
    fun validDocumentPasses() {
        val node = JsonReader.parse(
            """{"name":"小于","age":18,"tags":["a","b"],"address":{"city":"Shanghai"}}""",
        )
        assertTrue(schema.isValid(node))
        assertEquals(emptyList(), schema.validate(node))
    }

    @Test
    fun requiredAndTypeErrors() {
        val errors = schema.validate(JsonReader.parse("""{"age":-1}"""))
        assertTrue(errors.any { it.instancePath == "" && it.message.contains("missing required property 'name'") })
        assertTrue(errors.any { it.instancePath == "/age" && it.message.contains("must be >= 0") })
    }

    @Test
    fun nestedPathAndAdditionalProperty() {
        val errors = schema.validate(
            JsonReader.parse("""{"name":"a","address":{"city":1},"extra":true}"""),
        )
        assertTrue(errors.any { it.instancePath == "/address/city" })
        assertTrue(errors.any { it.message.contains("additional property 'extra'") })
    }

    @Test
    fun arrayAndEnumComposition() {
        val validator = JsonSchema.compile(
            """
            {
              "anyOf": [
                {"type": "array", "items": {"enum": ["a", "b"]}, "minItems": 2},
                {"type": "string", "pattern": "^x+$"}
              ]
            }
            """.trimIndent(),
        )
        assertTrue(validator.isValid(JsonReader.parse("""["a","b"]""")))
        assertTrue(validator.isValid(JsonReader.parse(""""xx"""")))
        assertFalse(validator.isValid(JsonReader.parse("""["a","c"]""")))
        assertFalse(validator.isValid(JsonReader.parse("""1""")))
    }

    @Test
    fun booleanSchemaAndNot() {
        val falseSchema = JsonSchema.compile("false")
        assertFalse(falseSchema.isValid(JsonReader.parse("null")))
        assertTrue(falseSchema.validate(JsonReader.parse("null")).isNotEmpty())

        val notSchema = JsonSchema.compile("""{"not":{"type":"string"}}""")
        assertTrue(notSchema.isValid(JsonReader.parse("1")))
        assertFalse(notSchema.isValid(JsonReader.parse(""""text"""")))
    }

    @Test
    fun invalidSchemaKeywordThrows() {
        val invalidType = JsonSchema.compile("""{"type":"unknown"}""")
        assertFailsWith<com.mingliqiye.document.format.json.exception.JsonSchemaException> {
            invalidType.validate(JsonReader.parse("1"))
        }
    }
}
