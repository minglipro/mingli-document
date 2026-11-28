package com.mingliqiye.document.openapi

import com.mingliqiye.document.format.json.JsonReader
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * OpenAPI JSON 文本输出测试。
 *
 * 使用 `:format-json` 的 JsonReader 作为测试依赖，验证生成文本是合法 JSON
 * 且可还原为公开 API 返回的文档树。
 */
class OpenApiJsonOutputTest {

    @Test
    fun compactJsonIsValidAndMatchesGeneratedTree() {
        val config = testConfig()
        val generator = OpenApiGenerator.of(config).addSchema(userSchema())
        val document = generator.generate()

        val json = generator.generateJson()

        assertFalse(json.contains("\n"))
        val parsed = assertNotNull(JsonReader(json).read().asObjectOrNull())
        assertEquals(document.asMap(), parsed.asMap())
    }

    @Test
    fun prettyJsonIsIndentedAndStillParsable() {
        val generator = OpenApiGenerator.of(testConfig()).addSchema(userSchema())

        val json = generator.generateJson(pretty = true)

        assertTrue(json.contains("\n  \""))
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        val parsed = assertNotNull(JsonReader(json).read().asObjectOrNull())
        assertEquals(generator.generate().asMap(), parsed.asMap())
    }

    @Test
    fun specialCharactersAreEscaped() {
        val description = "引号\"反斜杠\\换行\n制表\t控制\u0001"
        val schema = DocumentSchemaBuilder("Escaped")
            .field("value", SchemaType.STRING, description = description)
            .build()

        val json = OpenApiGenerator.of(testConfig()).addSchema(schema).generateJson()

        assertTrue(json.contains("\\\""))
        assertTrue(json.contains("\\\\"))
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("\\t"))
        assertTrue(json.contains("\\u0001"))
        val parsed = assertNotNull(JsonReader(json).read().asObjectOrNull())
        assertEquals(description, parsed.property("Escaped", "value")["description"]?.asString())
    }

    @Test
    fun jsonContainsAllTopLevelSections() {
        val config = OpenApiConfig.builder("API", "1.0.0")
            .description("演示")
            .server("https://api.example.com")
            .operation("/users", "GET", "User")
            .build()

        val json = OpenApiGenerator.of(config).addSchema(userSchema()).generateJson()

        assertTrue(json.contains("\"openapi\":\"3.0.3\""))
        assertTrue(json.contains("\"info\""))
        assertTrue(json.contains("\"servers\""))
        assertTrue(json.contains("\"paths\""))
        assertTrue(json.contains("\"components\""))
        assertTrue(json.contains("\"schemas\""))
        assertTrue(json.contains("\"required\""))
        assertTrue(json.contains("\"nullable\":true"))
        assertTrue(json.contains("\"\$ref\":\"#/components/schemas/User\""))
    }

    @Test
    fun largeDocumentRoundTripsThroughJson() {
        val user = userSchema()
        val order = DocumentSchemaBuilder("Order")
            .field("id", SchemaType.LONG)
            .field("user", SchemaType.OBJECT)
            .field("lines", SchemaType.ARRAY)
            .build()
        val line = DocumentSchema.of("Line", com.mingliqiye.document.schema.SchemaField.of("sku", SchemaType.STRING))
        val config = OpenApiConfig.builder("API", "1.0.0")
            .refOf("Order", "user", "User")
            .arrayOf("Order", "lines", OpenApiFieldType.reference("Line"))
            .build()
        val generator = OpenApiGenerator.of(config).addSchema(user).addSchema(order).addSchema(line)
        val document = generator.generate()

        val parsed = assertNotNull(JsonReader(generator.generateJson(pretty = true)).read().asObjectOrNull())

        assertEquals(document.asMap(), parsed.asMap())
    }
}
