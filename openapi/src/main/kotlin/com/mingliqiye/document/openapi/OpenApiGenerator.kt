package com.mingliqiye.document.openapi

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.openapi.exception.OpenApiSchemaException
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaType
import java.util.LinkedHashMap
import java.util.Locale

private val COMPONENT_NAME_PATTERN = Regex("[A-Za-z0-9._-]+")

/**
 * OpenAPI 3.0 文档生成器。
 *
 * 将若干 [DocumentSchema] 映射为 OpenAPI 文档树：
 *
 * - 每个 Schema 生成 `components.schemas.<name>` 的 object schema，包含
 *   `properties`、`required` 与字段描述；
 * - [OpenApiConfig.operations] 生成 `paths`；operation 通过 `$ref` 引用对应 Schema；
 * - [OpenApiConfig] 中的字段类型覆盖用于表达枚举、`$ref` 与嵌套数组/Map。
 *
 * 生成结果是格式无关的 [ObjectNode]；需要文本时使用 [generateJson]。
 *
 * ```kotlin
 * val document = OpenApiGenerator.of(config)
 *     .addSchema(userSchema)
 *     .addSchema(orderSchema)
 *     .generate()
 * ```
 */
public class OpenApiGenerator(
    private val config: OpenApiConfig,
) {

    private val schemas: MutableMap<String, DocumentSchema> = LinkedHashMap()

    /**
     * 注册一个 Schema。
     *
     * @throws OpenApiSchemaException 同名 Schema 已注册时抛出
     */
    public fun addSchema(schema: DocumentSchema): OpenApiGenerator {
        if (schemas.containsKey(schema.name)) {
            throw OpenApiSchemaException("Duplicate schema name: ${schema.name}")
        }
        schemas[schema.name] = schema
        return this
    }

    /**
     * 批量注册 Schema。
     */
    public fun addSchemas(schemas: Iterable<DocumentSchema>): OpenApiGenerator {
        for (schema in schemas) {
            addSchema(schema)
        }
        return this
    }

    /**
     * 生成 OpenAPI 文档树。
     *
     * @throws OpenApiSchemaException Schema 非法、字段类型不支持、`$ref` 或
     * operation 指向未知 Schema 时抛出
     */
    public fun generate(): ObjectNode {
        validate()
        val mapper = OpenApiSchemaMapper(config)
        val schemaNodes = LinkedHashMap<String, Node>()
        for (schema in schemas.values) {
            schemaNodes[schema.name] = mapper.mapSchema(schema)
        }
        val document = LinkedHashMap<String, Node>()
        document["openapi"] = StringNode.of(OPENAPI_VERSION)
        document["info"] = buildInfo()
        if (config.servers.isNotEmpty()) {
            document["servers"] = buildServers()
        }
        document["paths"] = buildPaths()
        document["components"] = ObjectNode.of("schemas" to ObjectNode.from(schemaNodes))
        return ObjectNode.from(document)
    }

    /**
     * 生成 OpenAPI 文档 JSON 文本。
     *
     * @param pretty 是否使用两空格缩进的格式化输出
     */
    public fun generateJson(pretty: Boolean = false): String = OpenApiJsonWriter(pretty).write(generate())

    private fun buildInfo(): ObjectNode {
        val info = LinkedHashMap<String, Node>()
        info["title"] = StringNode.of(config.title)
        info["version"] = StringNode.of(config.version)
        config.description?.takeIf { it.isNotBlank() }?.let { info["description"] = StringNode.of(it) }
        return ObjectNode.from(info)
    }

    private fun buildServers(): ArrayNode {
        val servers = ArrayList<Node>()
        for (server in config.servers) {
            val node = LinkedHashMap<String, Node>()
            node["url"] = StringNode.of(server.url)
            server.description?.takeIf { it.isNotBlank() }?.let { node["description"] = StringNode.of(it) }
            servers.add(ObjectNode.from(node))
        }
        return ArrayNode.from(servers)
    }

    private fun buildPaths(): ObjectNode {
        val paths = LinkedHashMap<String, MutableMap<String, Node>>()
        for (operation in config.operations) {
            val pathItem = paths.getOrPut(operation.path) { LinkedHashMap() }
            pathItem[operation.method.lowercase(Locale.ROOT)] = buildOperation(operation)
        }
        val result = LinkedHashMap<String, Node>()
        for ((path, pathItem) in paths) {
            result[path] = ObjectNode.from(pathItem)
        }
        return ObjectNode.from(result)
    }

    private fun buildOperation(operation: OpenApiOperation): ObjectNode {
        val node = LinkedHashMap<String, Node>()
        if (operation.tags.isNotEmpty()) {
            node["tags"] = ArrayNode.from(operation.tags.map(StringNode::of))
        }
        operation.summary?.takeIf { it.isNotBlank() }?.let { node["summary"] = StringNode.of(it) }
        operation.operationId?.takeIf { it.isNotBlank() }?.let { node["operationId"] = StringNode.of(it) }
        val mediaType = ObjectNode.of("schema" to openApiReferenceNode(operation.schemaName))
        val content = ObjectNode.of("application/json" to mediaType)
        if (operation.hasRequestBody) {
            node["requestBody"] = ObjectNode.of(
                "required" to BooleanNode.of(true),
                "content" to content,
            )
        }
        node["responses"] = ObjectNode.of(
            "200" to ObjectNode.of(
                "description" to StringNode.of("OK"),
                "content" to content,
            ),
        )
        return ObjectNode.from(node)
    }

    private fun validate() {
        if (schemas.isEmpty()) {
            throw OpenApiSchemaException("At least one DocumentSchema is required")
        }
        for (schema in schemas.values) {
            validateSchemaName(schema.name)
            validateFields(schema)
        }
        for (key in config.fieldTypes.keys) {
            val schema = schemas[key.schemaName]
                ?: throw OpenApiSchemaException("Field type override references unknown schema '${key.schemaName}'")
            if (schema.fields.none { it.name == key.fieldName }) {
                throw OpenApiSchemaException(
                    "Field type override references unknown field '${key.schemaName}.${key.fieldName}'",
                )
            }
        }
        for (operation in config.operations) {
            if (!schemas.containsKey(operation.schemaName)) {
                throw OpenApiSchemaException(
                    "Operation ${operation.method} ${operation.path} references unknown schema '${operation.schemaName}'",
                )
            }
        }
    }

    private fun validateSchemaName(name: String) {
        if (name.isBlank()) {
            throw OpenApiSchemaException("Schema name must not be blank")
        }
        if (!COMPONENT_NAME_PATTERN.matches(name)) {
            throw OpenApiSchemaException(
                "Invalid schema name '$name': OpenAPI component schema keys must match [A-Za-z0-9._-]+",
            )
        }
    }

    private fun validateFields(schema: DocumentSchema) {
        val fieldNames = HashSet<String>()
        for (field in schema.fields) {
            if (field.name.isBlank()) {
                throw OpenApiSchemaException("Schema '${schema.name}' contains a blank field name")
            }
            if (!fieldNames.add(field.name)) {
                throw OpenApiSchemaException("Schema '${schema.name}' contains duplicate field '${field.name}'")
            }
            val location = "field '${schema.name}.${field.name}'"
            val override = config.fieldTypes[OpenApiFieldKey(schema.name, field.name)]
            if (override == null) {
                if (field.type == SchemaType.NULL) {
                    throw OpenApiSchemaException(
                        "$location uses SchemaType.NULL, which OpenAPI 3.0 cannot express; " +
                            "mark the field nullable instead",
                    )
                }
            } else {
                validateOverride(location, override)
            }
        }
    }

    private fun validateOverride(location: String, type: OpenApiFieldType) {
        when (type) {
            is OpenApiPrimitiveType -> if (type.type == SchemaType.NULL) {
                throw OpenApiSchemaException(
                    "$location maps to SchemaType.NULL, which OpenAPI 3.0 cannot express",
                )
            }
            is OpenApiReferenceType -> if (!schemas.containsKey(type.schemaName)) {
                throw OpenApiSchemaException("$location references unknown schema '${type.schemaName}'")
            }
            is OpenApiArrayType -> validateOverride("items of $location", type.items)
            is OpenApiMapType -> validateOverride("values of $location", type.values)
            is OpenApiEnumType -> Unit
        }
    }

    /** [OpenApiGenerator] 工厂方法。 */
    public companion object {

        /** 生成的 OpenAPI 规范版本。 */
        public const val OPENAPI_VERSION: String = "3.0.3"

        /**
         * 创建生成器。
         *
         * @param config 生成配置
         */
        @JvmStatic
        public fun of(config: OpenApiConfig): OpenApiGenerator = OpenApiGenerator(config)
    }
}
