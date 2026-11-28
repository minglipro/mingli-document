package com.mingliqiye.document.openapi

import com.mingliqiye.document.openapi.exception.OpenApiSchemaException
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * DocumentSchema -> OpenAPI Schema Object 映射测试。
 */
class OpenApiSchemaMappingTest {

    @Test
    fun documentContainsRequiredOpenApiSections() {
        val document = generate(userSchema())

        assertEquals("3.0.3", document["openapi"]?.asString())
        val info = assertNotNull(document["info"]).asObject()
        assertEquals("Test API", info["title"]?.asString())
        assertEquals("1.0.0", info["version"]?.asString())
        assertNull(info["description"])
        assertNull(document["servers"])
        assertEquals(0, assertNotNull(document["paths"]).asObject().size)
        assertEquals(listOf("User"), document.schemasNode().keys.toList())
    }

    @Test
    fun scalarAndCompositeTypesAreMapped() {
        val schema = DocumentSchemaBuilder("mapping")
            .field("text", SchemaType.STRING)
            .field("count", SchemaType.INT)
            .field("big", SchemaType.LONG)
            .field("ratio", SchemaType.DOUBLE)
            .field("amount", SchemaType.DECIMAL)
            .field("active", SchemaType.BOOLEAN)
            .field("payload", SchemaType.BYTES)
            .field("tags", SchemaType.ARRAY)
            .field("attributes", SchemaType.MAP)
            .field("nested", SchemaType.OBJECT)
            .build()

        val document = generate(schema)

        assertEquals("string", document.property("mapping", "text")["type"]?.asString())
        assertNull(document.property("mapping", "text")["format"])

        assertEquals("integer", document.property("mapping", "count")["type"]?.asString())
        assertEquals("int32", document.property("mapping", "count")["format"]?.asString())

        assertEquals("integer", document.property("mapping", "big")["type"]?.asString())
        assertEquals("int64", document.property("mapping", "big")["format"]?.asString())

        assertEquals("number", document.property("mapping", "ratio")["type"]?.asString())
        assertEquals("double", document.property("mapping", "ratio")["format"]?.asString())

        assertEquals("number", document.property("mapping", "amount")["type"]?.asString())
        assertNull(document.property("mapping", "amount")["format"])

        assertEquals("boolean", document.property("mapping", "active")["type"]?.asString())

        assertEquals("string", document.property("mapping", "payload")["type"]?.asString())
        assertEquals("byte", document.property("mapping", "payload")["format"]?.asString())

        val tags = document.property("mapping", "tags")
        assertEquals("array", tags["type"]?.asString())
        assertEquals("string", assertNotNull(tags["items"]).asObject()["type"]?.asString())

        val attributes = document.property("mapping", "attributes")
        assertEquals("object", attributes["type"]?.asString())
        assertEquals(
            "string",
            assertNotNull(attributes["additionalProperties"]).asObject()["type"]?.asString(),
        )

        val nested = document.property("mapping", "nested")
        assertEquals("object", nested["type"]?.asString())
        assertNull(nested["properties"])
    }

    @Test
    fun requiredFieldsKeepDeclarationOrder() {
        val schema = DocumentSchemaBuilder("user")
            .field("id", SchemaType.LONG)
            .field("nick", SchemaType.STRING, required = false)
            .field("email", SchemaType.STRING)
            .field("age", SchemaType.INT, required = false, nullable = true)
            .build()

        val document = generate(schema)

        assertEquals(listOf("id", "email"), document.requiredFields("user"))
        assertEquals(listOf("id", "nick", "email", "age"), document.propertyNames("user"))
        assertNull(document.componentSchema("user")["description"])
    }

    @Test
    fun nullableFieldAddsNullableFlag() {
        val schema = DocumentSchemaBuilder("user")
            .field("name", SchemaType.STRING, nullable = true)
            .field("age", SchemaType.INT, required = false)
            .build()

        val document = generate(schema)

        assertEquals(true, document.property("user", "name")["nullable"]?.asBoolean())
        assertNull(document.property("user", "age")["nullable"])
    }

    @Test
    fun fieldDescriptionsAreMappedAndBlankIgnored() {
        val schema = DocumentSchemaBuilder("user")
            .field("name", SchemaType.STRING, description = "用户 名")
            .field("id", SchemaType.LONG)
            .field("nick", SchemaType.STRING, description = "  ")
            .build()

        val document = generate(schema)

        assertEquals("用户 名", document.property("user", "name")["description"]?.asString())
        assertNull(document.property("user", "id")["description"])
        assertNull(document.property("user", "nick")["description"])
    }

    @Test
    fun emptySchemaGeneratesObjectWithEmptyProperties() {
        val document = generate(DocumentSchema.of("Empty"))

        val schema = document.componentSchema("Empty")
        assertEquals("object", schema["type"]?.asString())
        assertEquals(0, assertNotNull(schema["properties"]).asObject().size)
        assertNull(schema["required"])
    }

    @Test
    fun schemaNameSupportsStandardComponentCharacters() {
        val document = generate(DocumentSchema.of("user.profile-v1_2", SchemaField.of("id", SchemaType.LONG)))

        assertEquals(listOf("user.profile-v1_2"), document.schemasNode().keys.toList())
    }

    @Test
    fun nullSchemaTypeIsRejected() {
        val schema = DocumentSchemaBuilder("bad").field("nothing", SchemaType.NULL).build()

        val error = assertFailsWith<OpenApiSchemaException> { generate(schema) }

        assertEquals(true, error.message.orEmpty().contains("SchemaType.NULL"))
    }

    @Test
    fun blankFieldNameIsRejected() {
        val schema = DocumentSchema.of("bad", SchemaField.of("", SchemaType.STRING))

        assertFailsWith<OpenApiSchemaException> { generate(schema) }
    }

    @Test
    fun duplicateFieldNameIsRejected() {
        val schema = DocumentSchema.of(
            "bad",
            SchemaField.of("id", SchemaType.STRING),
            SchemaField.of("id", SchemaType.INT),
        )

        assertFailsWith<OpenApiSchemaException> { generate(schema) }
    }

    @Test
    fun invalidSchemaNameIsRejected() {
        assertFailsWith<OpenApiSchemaException> {
            generate(DocumentSchema.of("bad name", SchemaField.of("id", SchemaType.STRING)))
        }
        assertFailsWith<OpenApiSchemaException> {
            generate(DocumentSchema.of("bad/name", SchemaField.of("id", SchemaType.STRING)))
        }
        assertFailsWith<OpenApiSchemaException> {
            generate(DocumentSchema.of("", SchemaField.of("id", SchemaType.STRING)))
        }
    }
}
