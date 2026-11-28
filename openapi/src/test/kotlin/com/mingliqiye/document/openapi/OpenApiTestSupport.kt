package com.mingliqiye.document.openapi

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.assertNotNull

/**
 * 测试共用的 Schema 与 OpenAPI 文档树读取辅助。
 */
internal fun testConfig(): OpenApiConfig = OpenApiConfig.builder("Test API", "1.0.0").build()

internal fun generate(vararg schemas: DocumentSchema): ObjectNode = OpenApiGenerator.of(testConfig()).addSchemas(schemas.toList()).generate()

internal fun userSchema(): DocumentSchema = DocumentSchemaBuilder("User")
    .field("id", SchemaType.LONG, description = "用户 id")
    .field("name", SchemaType.STRING, description = "用户名")
    .field("age", SchemaType.INT, required = false, nullable = true)
    .build()

internal fun ObjectNode.schemasNode(): ObjectNode = assertNotNull(assertNotNull(this["components"]).asObject()["schemas"]).asObject()

internal fun ObjectNode.componentSchema(name: String): ObjectNode = assertNotNull(schemasNode()[name]).asObject()

internal fun ObjectNode.property(schemaName: String, fieldName: String): ObjectNode = assertNotNull(
    assertNotNull(componentSchema(schemaName)["properties"]).asObject()[fieldName],
).asObject()

internal fun ObjectNode.requiredFields(schemaName: String): List<String> = componentSchema(schemaName)["required"]?.asArray()?.map { it.asString() } ?: emptyList()

internal fun ObjectNode.propertyNames(schemaName: String): List<String> = assertNotNull(componentSchema(schemaName)["properties"]).asObject().keys.toList()
