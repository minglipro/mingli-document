package com.mingliqiye.document.openapi

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.openapi.exception.OpenApiSchemaException
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import java.util.LinkedHashMap

private const val REFERENCE_KEY = "\$ref"

/**
 * 将 [DocumentSchema] 映射为 OpenAPI Schema Object。
 *
 * 未注册类型覆盖时，复合类型按固定约定映射：
 *
 * - [SchemaType.ARRAY] -> `{"type": "array", "items": {"type": "string"}}`
 * - [SchemaType.MAP] -> `{"type": "object", "additionalProperties": {"type": "string"}}`
 * - [SchemaType.OBJECT] -> `{"type": "object"}`（自由对象）
 *
 * 字段的 [SchemaField.nullable] 输出为 `nullable: true`；若字段是 `$ref`，
 * 则按 OpenAPI 3.0 约定包装为 `{"allOf": [{"$ref": ...}], "nullable": true}`，
 * 避免 `$ref` 的同级字段被忽略。
 */
internal class OpenApiSchemaMapper(
    private val config: OpenApiConfig,
) {

    /**
     * 映射单个 Schema。
     */
    fun mapSchema(schema: DocumentSchema): ObjectNode {
        val properties = LinkedHashMap<String, Node>()
        val required = ArrayList<Node>()
        for (field in schema.fields) {
            properties[field.name] = mapField(schema.name, field)
            if (field.required) {
                required.add(StringNode.of(field.name))
            }
        }
        val schemaNode = LinkedHashMap<String, Node>()
        schemaNode["type"] = StringNode.of("object")
        schemaNode["properties"] = ObjectNode.from(properties)
        if (required.isNotEmpty()) {
            schemaNode["required"] = ArrayNode.from(required)
        }
        return ObjectNode.from(schemaNode)
    }

    private fun mapField(schemaName: String, field: SchemaField): ObjectNode {
        val location = "field '$schemaName.${field.name}'"
        val override = config.fieldTypes[OpenApiFieldKey(schemaName, field.name)]
        val base = if (override == null) {
            mapPrimitive(location, field.type)
        } else {
            mapOverride(location, override)
        }
        return applyMetadata(base, field)
    }

    private fun applyMetadata(base: ObjectNode, field: SchemaField): ObjectNode {
        val nullable = field.nullable
        val description = field.description?.takeIf { it.isNotBlank() }
        if (!nullable && description == null) {
            return base
        }
        val result = if (base.containsKey(REFERENCE_KEY)) {
            val wrapper = LinkedHashMap<String, Node>()
            wrapper["allOf"] = ArrayNode.of(base)
            wrapper
        } else {
            LinkedHashMap(base.asMap())
        }
        if (nullable) {
            result["nullable"] = BooleanNode.of(true)
        }
        if (description != null) {
            result["description"] = StringNode.of(description)
        }
        return ObjectNode.from(result)
    }

    private fun mapOverride(location: String, type: OpenApiFieldType): ObjectNode = when (type) {
        is OpenApiPrimitiveType -> mapPrimitive(location, type.type)
        is OpenApiReferenceType -> openApiReferenceNode(type.schemaName)
        is OpenApiArrayType -> ObjectNode.of(
            "type" to StringNode.of("array"),
            "items" to mapOverride("items of $location", type.items),
        )
        is OpenApiMapType -> ObjectNode.of(
            "type" to StringNode.of("object"),
            "additionalProperties" to mapOverride("values of $location", type.values),
        )
        is OpenApiEnumType -> ObjectNode.of(
            "type" to StringNode.of("string"),
            "enum" to ArrayNode.from(type.values.map(StringNode::of)),
        )
    }

    private fun mapPrimitive(location: String, type: SchemaType): ObjectNode = when (type) {
        SchemaType.STRING -> ObjectNode.of("type" to StringNode.of("string"))
        SchemaType.INT -> integerNode("int32")
        SchemaType.LONG -> integerNode("int64")
        SchemaType.DOUBLE -> ObjectNode.of(
            "type" to StringNode.of("number"),
            "format" to StringNode.of("double"),
        )
        SchemaType.DECIMAL -> ObjectNode.of("type" to StringNode.of("number"))
        SchemaType.BOOLEAN -> ObjectNode.of("type" to StringNode.of("boolean"))
        SchemaType.BYTES -> ObjectNode.of(
            "type" to StringNode.of("string"),
            "format" to StringNode.of("byte"),
        )
        SchemaType.ARRAY -> ObjectNode.of(
            "type" to StringNode.of("array"),
            "items" to ObjectNode.of("type" to StringNode.of("string")),
        )
        SchemaType.MAP -> ObjectNode.of(
            "type" to StringNode.of("object"),
            "additionalProperties" to ObjectNode.of("type" to StringNode.of("string")),
        )
        SchemaType.OBJECT -> ObjectNode.of("type" to StringNode.of("object"))
        SchemaType.NULL -> throw OpenApiSchemaException(
            "$location uses SchemaType.NULL, which OpenAPI 3.0 cannot express; " +
                "mark the field nullable instead or configure a field type override",
        )
    }

    private fun integerNode(format: String): ObjectNode = ObjectNode.of(
        "type" to StringNode.of("integer"),
        "format" to StringNode.of(format),
    )
}

/**
 * 创建指向 `components.schemas` 的 `$ref` 节点。
 *
 * Schema 名称在生成前已校验为 OpenAPI component 合法字符集，可直接拼入 JSON Pointer。
 */
internal fun openApiReferenceNode(schemaName: String): ObjectNode = ObjectNode.of(REFERENCE_KEY to StringNode.of("#/components/schemas/$schemaName"))
