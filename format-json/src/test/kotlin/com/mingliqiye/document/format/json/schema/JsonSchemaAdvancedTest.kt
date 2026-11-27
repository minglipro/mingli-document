package com.mingliqiye.document.format.json.schema

import com.mingliqiye.document.format.json.JsonReader
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonSchemaAdvancedTest {

    @Test
    fun formats() {
        val schema = JsonSchema.compile(
            """
            {
              "type": "object",
              "properties": {
                "date": {"type": "string", "format": "date"},
                "email": {"type": "string", "format": "email"},
                "ip": {"type": "string", "format": "ipv4"},
                "id": {"type": "string", "format": "uuid"}
              }
            }
            """.trimIndent(),
        )
        assertTrue(
            schema.isValid(
                JsonReader.parse(
                    """
                    {
                      "date": "2026-11-27",
                      "email": "a@example.com",
                      "ip": "127.0.0.1",
                      "id": "123e4567-e89b-12d3-a456-426614174000"
                    }
                    """.trimIndent(),
                ),
            ),
        )
        assertFalse(
            schema.isValid(
                JsonReader.parse(
                    """{"date":"2026-13-01","email":"bad","ip":"999.1.1.1","id":"x"}""",
                ),
            ),
        )
    }

    @Test
    fun ifThenElseAndContains() {
        val schema = JsonSchema.compile(
            """
            {
              "type": "object",
              "properties": {
                "kind": {"type": "string"},
                "items": {
                  "type": "array",
                  "contains": {"type": "integer", "minimum": 10},
                  "minContains": 2
                }
              },
              "if": {"properties": {"kind": {"const": "special"}}},
              "then": {"required": ["items"]},
              "else": {"required": ["kind"]}
            }
            """.trimIndent(),
        )
        assertTrue(schema.isValid(JsonReader.parse("""{"kind":"normal"}""")))
        assertFalse(schema.isValid(JsonReader.parse("""{"kind":"special"}""")))
        assertTrue(schema.isValid(JsonReader.parse("""{"kind":"special","items":[10,11]}""")))
        assertFalse(schema.isValid(JsonReader.parse("""{"kind":"special","items":[10,1]}""")))
    }

    @Test
    fun dependentRequiredAndPropertyNames() {
        val schema = JsonSchema.compile(
            """
            {
              "type": "object",
              "dependentRequired": {"credit_card": ["billing_address"]},
              "propertyNames": {"pattern": "^[a-z_]+$"}
            }
            """.trimIndent(),
        )
        assertTrue(schema.isValid(JsonReader.parse("""{"credit_card":"x","billing_address":"y"}""")))
        assertFalse(schema.isValid(JsonReader.parse("""{"credit_card":"x"}""")))
        assertFalse(schema.isValid(JsonReader.parse("""{"BadName":1}""")))
    }
}
