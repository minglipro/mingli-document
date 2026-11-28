package com.mingliqiye.document.openapi

import com.mingliqiye.document.openapi.exception.OpenApiSchemaException
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * 多 Schema、跨 Schema `$ref` 与扩展函数测试。
 */
class OpenApiMultiSchemaTest {

    @Test
    fun multipleSchemasKeepInsertionOrder() {
        val user = userSchema()
        val event = DocumentSchemaBuilder("Event").field("name", SchemaType.STRING).build()

        val document = OpenApiGenerator.of(testConfig()).addSchema(user).addSchema(event).generate()

        assertEquals(listOf("User", "Event"), document.schemasNode().keys.toList())
        assertEquals("用户 id", document.property("User", "id")["description"]?.asString())
        assertEquals("string", document.property("Event", "name")["type"]?.asString())
    }

    @Test
    fun crossSchemaReferenceIsGenerated() {
        val user = userSchema()
        val order = DocumentSchemaBuilder("Order")
            .field("id", SchemaType.LONG)
            .field("user", SchemaType.OBJECT)
            .build()
        val config = OpenApiConfig.builder("API", "1.0.0")
            .refOf("Order", "user", "User")
            .build()

        val document = OpenApiGenerator.of(config).addSchema(order).addSchema(user).generate()

        val userNode = document.property("Order", "user")
        assertEquals("#/components/schemas/User", userNode["\$ref"]?.asString())
    }

    @Test
    fun recursiveReferenceIsSupported() {
        val tree = DocumentSchemaBuilder("Tree")
            .field("value", SchemaType.STRING)
            .field("children", SchemaType.ARRAY, required = false)
            .build()
        val config = OpenApiConfig.builder("API", "1.0.0")
            .arrayOf("Tree", "children", OpenApiFieldType.reference("Tree"))
            .build()

        val document = OpenApiGenerator.of(config).addSchema(tree).generate()

        val children = document.property("Tree", "children")
        assertEquals("array", children["type"]?.asString())
        assertEquals(
            "#/components/schemas/Tree",
            assertNotNull(assertNotNull(children["items"]).asObject()["\$ref"]).asString(),
        )
    }

    @Test
    fun duplicateSchemaNameIsRejected() {
        val generator = OpenApiGenerator.of(testConfig()).addSchema(userSchema())

        assertFailsWith<OpenApiSchemaException> { generator.addSchema(userSchema()) }
    }

    @Test
    fun generatorWithoutSchemasIsRejected() {
        assertFailsWith<OpenApiSchemaException> { OpenApiGenerator.of(testConfig()).generate() }
    }

    @Test
    fun documentSchemaExtensionMatchesGenerator() {
        val config = OpenApiConfig.builder("API", "1.0.0").description("描述").build()

        val viaExtension = userSchema().toOpenApi(config)
        val viaGenerator = OpenApiGenerator.of(config).addSchema(userSchema()).generate()

        assertEquals(viaGenerator.asMap(), viaExtension.asMap())
        assertEquals("描述", assertNotNull(viaExtension["info"]).asObject()["description"]?.asString())
    }

    @Test
    fun iterableExtensionGeneratesAllSchemas() {
        val config = OpenApiConfig.builder("API", "1.0.0").build()
        val schemas = listOf(userSchema(), DocumentSchema.of("Event"))

        val document = schemas.toOpenApi(config)

        assertEquals(listOf("User", "Event"), document.schemasNode().keys.toList())
        assertEquals(2, document.schemasNode().size)
    }
}
