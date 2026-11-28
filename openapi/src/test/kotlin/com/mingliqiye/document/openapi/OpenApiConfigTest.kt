package com.mingliqiye.document.openapi

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.openapi.exception.OpenApiConfigException
import com.mingliqiye.document.openapi.exception.OpenApiSchemaException
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * OpenApiConfig、字段类型覆盖与 paths 生成测试。
 */
class OpenApiConfigTest {

    @Test
    fun descriptionAndServersAreMapped() {
        val config = OpenApiConfig.builder("API", "2.0.0")
            .description("演示 API")
            .server("https://api.example.com", "生产")
            .server("http://localhost:8080")
            .build()

        val document = OpenApiGenerator.of(config).addSchema(userSchema()).generate()

        assertEquals("演示 API", assertNotNull(document["info"]).asObject()["description"]?.asString())
        val servers = assertNotNull(document["servers"]).asArray()
        assertEquals(2, servers.size)
        val first = servers[0].asObject()
        assertEquals("https://api.example.com", first["url"]?.asString())
        assertEquals("生产", first["description"]?.asString())
        assertNull(servers[1].asObject()["description"])
    }

    @Test
    fun blankTitleAndVersionAreRejected() {
        assertFailsWith<OpenApiConfigException> { OpenApiConfig.builder(" ", "1.0.0") }
        assertFailsWith<OpenApiConfigException> { OpenApiConfig.builder("API", "") }
    }

    @Test
    fun blankServerUrlIsRejected() {
        assertFailsWith<OpenApiConfigException> {
            OpenApiConfig.builder("API", "1.0.0").server(" ")
        }
    }

    @Test
    fun enumOverrideIsMapped() {
        val schema = DocumentSchemaBuilder("Status").field("status", SchemaType.STRING).build()
        val config = OpenApiConfig.builder("API", "1.0.0")
            .enumOf("Status", "status", "ACTIVE", "DISABLED")
            .build()

        val document = OpenApiGenerator.of(config).addSchema(schema).generate()

        val status = document.property("Status", "status")
        assertEquals("string", status["type"]?.asString())
        assertEquals(
            listOf("ACTIVE", "DISABLED"),
            assertNotNull(status["enum"]).asArray().map { it.asString() },
        )
    }

    @Test
    fun emptyOrDuplicateEnumValuesAreRejected() {
        assertFailsWith<OpenApiConfigException> {
            OpenApiConfig.builder("API", "1.0.0").enumOf("User", "status")
        }
        assertFailsWith<OpenApiConfigException> {
            OpenApiConfig.builder("API", "1.0.0").enumOf("User", "status", "A", "A")
        }
    }

    @Test
    fun duplicateFieldOverrideIsRejected() {
        val builder = OpenApiConfig.builder("API", "1.0.0").enumOf("User", "status", "A")

        assertFailsWith<OpenApiConfigException> { builder.refOf("User", "status", "Other") }
    }

    @Test
    fun arrayOverrideNestsItemReference() {
        val order = DocumentSchemaBuilder("Order").field("lines", SchemaType.ARRAY).build()
        val line = DocumentSchemaBuilder("Line").field("sku", SchemaType.STRING).build()
        val config = OpenApiConfig.builder("API", "1.0.0")
            .arrayOf("Order", "lines", OpenApiFieldType.reference("Line"))
            .build()

        val document = OpenApiGenerator.of(config).addSchema(order).addSchema(line).generate()

        val lines = document.property("Order", "lines")
        assertEquals("array", lines["type"]?.asString())
        assertEquals(
            "#/components/schemas/Line",
            assertNotNull(assertNotNull(lines["items"]).asObject()["\$ref"]).asString(),
        )
    }

    @Test
    fun mapOverrideNestsEnumValues() {
        val schema = DocumentSchemaBuilder("Doc").field("labels", SchemaType.MAP).build()
        val config = OpenApiConfig.builder("API", "1.0.0")
            .mapOf("Doc", "labels", OpenApiFieldType.enumOf("A", "B"))
            .build()

        val document = OpenApiGenerator.of(config).addSchema(schema).generate()

        val labels = document.property("Doc", "labels")
        assertEquals("object", labels["type"]?.asString())
        val values = assertNotNull(labels["additionalProperties"]).asObject()
        assertEquals("string", values["type"]?.asString())
        assertEquals(listOf("A", "B"), assertNotNull(values["enum"]).asArray().map { it.asString() })
    }

    @Test
    fun primitiveOverrideReplacesDeclaredType() {
        val schema = DocumentSchemaBuilder("Doc").field("code", SchemaType.INT).build()
        val config = OpenApiConfig.builder("API", "1.0.0")
            .fieldType("Doc", "code", OpenApiFieldType.primitive(SchemaType.STRING))
            .build()

        val document = OpenApiGenerator.of(config).addSchema(schema).generate()

        val code = document.property("Doc", "code")
        assertEquals("string", code["type"]?.asString())
        assertNull(code["format"])
    }

    @Test
    fun nullableReferenceUsesAllOfWrapper() {
        val schema = DocumentSchemaBuilder("User")
            .field("address", SchemaType.OBJECT, required = false, nullable = true, description = "地址")
            .build()
        val address = DocumentSchema.of("Address")
        val config = OpenApiConfig.builder("API", "1.0.0")
            .refOf("User", "address", "Address")
            .build()

        val document = OpenApiGenerator.of(config).addSchema(schema).addSchema(address).generate()

        val addressNode = document.property("User", "address")
        assertNull(addressNode["\$ref"])
        assertEquals(true, addressNode["nullable"]?.asBoolean())
        assertEquals("地址", addressNode["description"]?.asString())
        val allOf = assertNotNull(addressNode["allOf"]).asArray()
        assertEquals(1, allOf.size)
        assertEquals(
            "#/components/schemas/Address",
            assertNotNull(allOf[0].asObject()["\$ref"]).asString(),
        )
    }

    @Test
    fun bareReferenceHasNoSiblingFields() {
        val schema = DocumentSchemaBuilder("User").field("address", SchemaType.OBJECT).build()
        val config = OpenApiConfig.builder("API", "1.0.0")
            .refOf("User", "address", "Address")
            .build()

        val document = OpenApiGenerator.of(config).addSchema(schema).addSchema(DocumentSchema.of("Address")).generate()

        val addressNode = document.property("User", "address")
        assertEquals(listOf("\$ref"), addressNode.keys.toList())
        assertEquals("#/components/schemas/Address", addressNode["\$ref"]?.asString())
    }

    @Test
    fun referenceToUnknownSchemaIsRejected() {
        val schema = DocumentSchemaBuilder("User").field("address", SchemaType.OBJECT).build()
        val config = OpenApiConfig.builder("API", "1.0.0")
            .refOf("User", "address", "Missing")
            .build()

        assertFailsWith<OpenApiSchemaException> {
            OpenApiGenerator.of(config).addSchema(schema).generate()
        }
    }

    @Test
    fun overrideForUnknownFieldIsRejected() {
        val config = OpenApiConfig.builder("API", "1.0.0").enumOf("User", "missing", "A").build()

        assertFailsWith<OpenApiSchemaException> {
            OpenApiGenerator.of(config).addSchema(userSchema()).generate()
        }
    }

    @Test
    fun overrideForUnknownSchemaIsRejected() {
        val config = OpenApiConfig.builder("API", "1.0.0").enumOf("Missing", "status", "A").build()

        assertFailsWith<OpenApiSchemaException> {
            OpenApiGenerator.of(config).addSchema(userSchema()).generate()
        }
    }

    @Test
    fun primitiveNullOverrideIsRejected() {
        val config = OpenApiConfig.builder("API", "1.0.0")
            .fieldType("User", "name", OpenApiFieldType.primitive(SchemaType.NULL))
            .build()

        assertFailsWith<OpenApiSchemaException> {
            OpenApiGenerator.of(config).addSchema(userSchema()).generate()
        }
    }

    @Test
    fun operationsGeneratePaths() {
        val config = OpenApiConfig.builder("API", "1.0.0")
            .operation(
                path = "/users/{id}",
                method = "get",
                schemaName = "User",
                summary = "查询用户",
                operationId = "getUser",
                tags = listOf("user"),
            )
            .build()

        val document = OpenApiGenerator.of(config).addSchema(userSchema()).generate()

        val paths = assertNotNull(document["paths"]).asObject()
        val get = assertNotNull(assertNotNull(paths["/users/{id}"]).asObject()["get"]).asObject()
        assertEquals("查询用户", get["summary"]?.asString())
        assertEquals("getUser", get["operationId"]?.asString())
        assertEquals(listOf("user"), assertNotNull(get["tags"]).asArray().map { it.asString() })
        assertNull(get["requestBody"])
        val response = assertNotNull(assertNotNull(get["responses"]).asObject()["200"]).asObject()
        assertEquals("OK", response["description"]?.asString())
        assertEquals(
            "#/components/schemas/User",
            responseReference(response),
        )
    }

    @Test
    fun bodyMethodsGenerateRequestBody() {
        val config = OpenApiConfig.builder("API", "1.0.0")
            .operation("/users", "POST", "User")
            .operation("/users", "PATCH", "User")
            .build()

        val document = OpenApiGenerator.of(config).addSchema(userSchema()).generate()

        val pathItem = assertNotNull(assertNotNull(document["paths"]).asObject()["/users"]).asObject()
        for (method in listOf("post", "patch")) {
            val operation = assertNotNull(pathItem[method]).asObject()
            val requestBody = assertNotNull(operation["requestBody"]).asObject()
            assertEquals(true, requestBody["required"]?.asBoolean())
            assertEquals("#/components/schemas/User", requestBodyReference(requestBody))
            val response = assertNotNull(assertNotNull(operation["responses"]).asObject()["200"]).asObject()
            assertEquals("#/components/schemas/User", responseReference(response))
        }
    }

    @Test
    fun invalidOperationDefinitionsAreRejected() {
        assertFailsWith<OpenApiConfigException> { OpenApiOperation("users", "GET", "User") }
        assertFailsWith<OpenApiConfigException> { OpenApiOperation("/users", "FETCH", "User") }
        assertFailsWith<OpenApiConfigException> { OpenApiOperation("/users", "GET", " ") }
        assertFailsWith<OpenApiConfigException> {
            OpenApiConfig.builder("API", "1.0.0")
                .operation("/users", "GET", "User")
                .operation("/users", "get", "User")
        }
    }

    @Test
    fun methodIsNormalizedToUpperCase() {
        val operation = OpenApiOperation("/users", " get ", "User")

        assertEquals("GET", operation.method)
    }

    @Test
    fun operationReferencingUnknownSchemaIsRejected() {
        val config = OpenApiConfig.builder("API", "1.0.0")
            .operation("/users", "GET", "Missing")
            .build()

        assertFailsWith<OpenApiSchemaException> {
            OpenApiGenerator.of(config).addSchema(userSchema()).generate()
        }
    }

    private fun responseReference(response: ObjectNode): String? {
        val content = assertNotNull(response["content"]).asObject()
        val mediaType = assertNotNull(content["application/json"]).asObject()
        return assertNotNull(mediaType["schema"]).asObject()["\$ref"]?.asString()
    }

    private fun requestBodyReference(requestBody: ObjectNode): String? {
        val content = assertNotNull(requestBody["content"]).asObject()
        val mediaType = assertNotNull(content["application/json"]).asObject()
        return assertNotNull(mediaType["schema"]).asObject()["\$ref"]?.asString()
    }
}
