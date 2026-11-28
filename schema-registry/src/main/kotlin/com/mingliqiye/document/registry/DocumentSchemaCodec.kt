package com.mingliqiye.document.registry

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.registry.exception.SchemaSerializationException
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import java.util.LinkedHashMap

private const val TITLE = "title"
private const val TYPE = "type"
private const val PROPERTIES = "properties"
private const val REQUIRED = "required"
private const val FORMAT = "format"
private const val CONTENT_ENCODING = "contentEncoding"
private const val ADDITIONAL_PROPERTIES = "additionalProperties"
private const val DESCRIPTION = "description"

private const val TYPE_OBJECT = "object"
private const val TYPE_NULL = "null"
private const val TYPE_STRING = "string"
private const val TYPE_INTEGER = "integer"
private const val TYPE_NUMBER = "number"
private const val TYPE_BOOLEAN = "boolean"
private const val TYPE_ARRAY = "array"

private const val FORMAT_INT32 = "int32"
private const val FORMAT_INT64 = "int64"
private const val FORMAT_DOUBLE = "double"
private const val ENCODING_BASE64 = "base64"

/**
 * [DocumentSchema] 与规范化 JSON Schema 节点之间的编解码器。
 *
 * 编码结果是一个 JSON-compatible `ObjectNode`（draft-07 子集），本地 registry
 * 以该节点作为 Schema 负载；需要 JSON 文本时可配合 `format-json` 的
 * `JsonMapper` 使用：
 *
 * ```kotlin
 * val node = DocumentSchemaCodec.encode(schema)
 * val json = JsonMapper().format(node)
 * val restored = DocumentSchemaCodec.decode(JsonMapper().parse(json, Node::class.java))
 * ```
 *
 * 编码规则：
 *
 * - Schema 名 -> `title`；固定 `type = "object"`；
 * - 字段按声明顺序写入 `properties`，`required = true` 的字段名写入 `required` 数组；
 * - `SchemaField.description` 原样写入 `description`；
 * - `nullable = true` 的字段类型写成 `[<基础类型>, "null"]`（`NULL` 类型除外，本身即可空）；
 * - `INT`/`LONG` 使用 `integer` + `int32`/`int64`；`DOUBLE` 使用 `number` + `double`，
 *   `DECIMAL` 使用无 format 的 `number`；`BYTES` 使用 `string` + `contentEncoding = base64`；
 *   `MAP` 使用带 `additionalProperties` 的 `object`，`OBJECT` 使用裸 `object`；
 *   `ARRAY` 使用裸 `array`（通用类型不携带元素类型）。
 *
 * 反解时忽略未知 JSON Schema 关键字（例如 `items`）；无法映射的结构会抛
 * [SchemaSerializationException]。
 */
public object DocumentSchemaCodec {

    /**
     * 把 [DocumentSchema] 编码为规范化 JSON Schema 节点。
     *
     * @throws SchemaSerializationException Schema 名或字段名非法（空白/重复）时抛出
     */
    @JvmStatic
    public fun encode(schema: DocumentSchema): ObjectNode {
        if (schema.name.isBlank()) {
            throw SchemaSerializationException("DocumentSchema name must not be blank")
        }
        val fieldNames = HashSet<String>()
        val properties = LinkedHashMap<String, Node>()
        val required = ArrayList<Node>()
        for (field in schema.fields) {
            if (field.name.isBlank()) {
                throw SchemaSerializationException("Schema '${schema.name}' contains a blank field name")
            }
            if (!fieldNames.add(field.name)) {
                throw SchemaSerializationException("Schema '${schema.name}' contains duplicate field '${field.name}'")
            }
            properties[field.name] = encodeField(field)
            if (field.required) {
                required.add(StringNode.of(field.name))
            }
        }
        val node = LinkedHashMap<String, Node>()
        node[TITLE] = StringNode.of(schema.name)
        node[TYPE] = StringNode.of(TYPE_OBJECT)
        node[PROPERTIES] = ObjectNode.from(properties)
        if (required.isNotEmpty()) {
            node[REQUIRED] = ArrayNode.from(required)
        }
        return ObjectNode.from(node)
    }

    /**
     * 把规范化 JSON Schema 节点反解为 [DocumentSchema]。
     *
     * @param node 规范化节点
     * @param name `title` 缺失或为空白时使用的 Schema 名；为 `null` 时要求节点带 `title`
     * @throws SchemaSerializationException 节点结构非法或字段类型无法映射时抛出
     */
    @JvmStatic
    public fun decode(node: Node, name: String? = null): DocumentSchema {
        val objectNode = node as? ObjectNode
            ?: throw SchemaSerializationException("DocumentSchema must be encoded as an object node but was ${node.type}")
        objectNode[TYPE]?.let { typeNode ->
            val schemaType = typeNode.asStringOrNull()
                ?: throw SchemaSerializationException("DocumentSchema node '$TYPE' must be a string")
            if (schemaType != TYPE_OBJECT) {
                throw SchemaSerializationException("DocumentSchema node must have '$TYPE' = '$TYPE_OBJECT' but was '$schemaType'")
            }
        }
        val title = objectNode[TITLE]?.let { titleNode ->
            val value = titleNode.asStringOrNull()
                ?: throw SchemaSerializationException("DocumentSchema node '$TITLE' must be a string")
            value.takeIf { it.isNotBlank() }
        }
        val schemaName = title ?: name ?: throw SchemaSerializationException(
            "DocumentSchema node has no '$TITLE' and no fallback name was provided",
        )
        val fields = ArrayList<SchemaField>()
        objectNode[PROPERTIES]?.let { propertiesNode ->
            val properties = propertiesNode as? ObjectNode
                ?: throw SchemaSerializationException("DocumentSchema node '$PROPERTIES' must be an object")
            val requiredNames = readRequiredNames(objectNode[REQUIRED])
            for ((fieldName, fieldNode) in properties.asMap()) {
                fields.add(decodeField(fieldName, fieldNode, requiredNames))
            }
        }
        return DocumentSchema.of(schemaName, *fields.toTypedArray())
    }

    private fun encodeField(field: SchemaField): ObjectNode {
        val node = LinkedHashMap<String, Node>()
        val baseType = jsonType(field.type)
        node[TYPE] = if (field.nullable && field.type != SchemaType.NULL) {
            ArrayNode.of(StringNode.of(baseType), StringNode.of(TYPE_NULL))
        } else {
            StringNode.of(baseType)
        }
        when (field.type) {
            SchemaType.INT -> node[FORMAT] = StringNode.of(FORMAT_INT32)
            SchemaType.LONG -> node[FORMAT] = StringNode.of(FORMAT_INT64)
            SchemaType.DOUBLE -> node[FORMAT] = StringNode.of(FORMAT_DOUBLE)
            SchemaType.BYTES -> node[CONTENT_ENCODING] = StringNode.of(ENCODING_BASE64)
            SchemaType.MAP -> node[ADDITIONAL_PROPERTIES] = ObjectNode.EMPTY
            else -> Unit
        }
        field.description?.let { node[DESCRIPTION] = StringNode.of(it) }
        return ObjectNode.from(node)
    }

    private fun decodeField(fieldName: String, fieldNode: Node, requiredNames: Set<String>): SchemaField {
        if (fieldName.isBlank()) {
            throw SchemaSerializationException("DocumentSchema contains a blank field name")
        }
        val node = fieldNode as? ObjectNode
            ?: throw SchemaSerializationException("Property '$fieldName' must be an object node")
        val typeNode = node[TYPE]
            ?: throw SchemaSerializationException("Property '$fieldName' is missing '$TYPE'")
        val (typeName, nullable) = readFieldType(fieldName, typeNode)
        val format = readOptionalString(node[FORMAT], "Property '$fieldName' '$FORMAT'")
        val contentEncoding = readOptionalString(node[CONTENT_ENCODING], "Property '$fieldName' '$CONTENT_ENCODING'")
        val description = readOptionalString(node[DESCRIPTION], "Property '$fieldName' '$DESCRIPTION'")
        val schemaType = resolveType(fieldName, typeName, format, contentEncoding, node[ADDITIONAL_PROPERTIES])
        return SchemaField(
            name = fieldName,
            type = schemaType,
            required = fieldName in requiredNames,
            nullable = nullable,
            description = description,
        )
    }

    private fun readFieldType(fieldName: String, typeNode: Node): Pair<String, Boolean> {
        typeNode.asStringOrNull()?.let { return it to false }
        val array = typeNode as? ArrayNode
            ?: throw SchemaSerializationException("Property '$fieldName' has invalid '$TYPE': $typeNode")
        val names = array.map { element ->
            element.asStringOrNull()
                ?: throw SchemaSerializationException("Property '$fieldName' '$TYPE' entries must be strings")
        }
        if (names.size != 2 || names.count { it == TYPE_NULL } != 1) {
            throw SchemaSerializationException(
                "Property '$fieldName' '$TYPE' must be either a string or [<type>, \"null\"] but was $names",
            )
        }
        return names.first { it != TYPE_NULL } to true
    }

    private fun resolveType(
        fieldName: String,
        typeName: String,
        format: String?,
        contentEncoding: String?,
        additionalProperties: Node?,
    ): SchemaType = when (typeName) {
        TYPE_STRING -> if (contentEncoding == ENCODING_BASE64) SchemaType.BYTES else SchemaType.STRING
        TYPE_INTEGER -> when (format) {
            null, FORMAT_INT32 -> SchemaType.INT
            FORMAT_INT64 -> SchemaType.LONG
            else -> throw SchemaSerializationException("Property '$fieldName' has unsupported integer format '$format'")
        }

        TYPE_NUMBER -> when (format) {
            null -> SchemaType.DECIMAL
            FORMAT_DOUBLE -> SchemaType.DOUBLE
            else -> throw SchemaSerializationException("Property '$fieldName' has unsupported number format '$format'")
        }

        TYPE_BOOLEAN -> SchemaType.BOOLEAN
        TYPE_NULL -> SchemaType.NULL
        TYPE_ARRAY -> SchemaType.ARRAY
        TYPE_OBJECT -> when (additionalProperties) {
            is ObjectNode -> SchemaType.MAP
            null, is BooleanNode -> SchemaType.OBJECT
            else -> throw SchemaSerializationException(
                "Property '$fieldName' has unsupported '$ADDITIONAL_PROPERTIES': $additionalProperties",
            )
        }

        else -> throw SchemaSerializationException("Property '$fieldName' has unsupported type '$typeName'")
    }

    private fun readRequiredNames(requiredNode: Node?): Set<String> {
        if (requiredNode == null) {
            return emptySet()
        }
        val array = requiredNode as? ArrayNode
            ?: throw SchemaSerializationException("DocumentSchema node '$REQUIRED' must be an array")
        val names = HashSet<String>()
        for (element in array) {
            val name = element.asStringOrNull()
                ?: throw SchemaSerializationException("DocumentSchema node '$REQUIRED' entries must be strings")
            names.add(name)
        }
        return names
    }

    private fun readOptionalString(node: Node?, label: String): String? {
        if (node == null) {
            return null
        }
        return node.asStringOrNull() ?: throw SchemaSerializationException("$label must be a string")
    }

    private fun jsonType(type: SchemaType): String = when (type) {
        SchemaType.STRING, SchemaType.BYTES -> TYPE_STRING
        SchemaType.INT, SchemaType.LONG -> TYPE_INTEGER
        SchemaType.DOUBLE, SchemaType.DECIMAL -> TYPE_NUMBER
        SchemaType.BOOLEAN -> TYPE_BOOLEAN
        SchemaType.ARRAY -> TYPE_ARRAY
        SchemaType.OBJECT, SchemaType.MAP -> TYPE_OBJECT
        SchemaType.NULL -> TYPE_NULL
    }
}

/**
 * 把 [DocumentSchema] 编码为规范化 JSON Schema 节点。
 *
 * 等价于 `DocumentSchemaCodec.encode(this)`。
 */
public fun DocumentSchema.toSchemaNode(): ObjectNode = DocumentSchemaCodec.encode(this)

/**
 * 把规范化 JSON Schema 节点反解为 [DocumentSchema]。
 *
 * 等价于 `DocumentSchemaCodec.decode(this, name)`。
 *
 * @param name `title` 缺失或为空白时使用的 Schema 名
 */
public fun Node.toDocumentSchema(name: String? = null): DocumentSchema = DocumentSchemaCodec.decode(this, name)
