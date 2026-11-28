package com.mingliqiye.document.openapi

import com.mingliqiye.document.openapi.exception.OpenApiConfigException
import java.util.LinkedHashMap

/**
 * [OpenApiConfig] 构建器。
 *
 * 通过 [OpenApiConfig.builder] 创建。
 */
public class OpenApiConfigBuilder internal constructor(
    private val title: String,
    private val version: String,
) {

    private var description: String? = null
    private val servers: MutableList<OpenApiServer> = ArrayList()
    private val operations: MutableList<OpenApiOperation> = ArrayList()
    private val fieldTypes: MutableMap<OpenApiFieldKey, OpenApiFieldType> = LinkedHashMap()

    init {
        if (title.isBlank()) {
            throw OpenApiConfigException("OpenAPI title must not be blank")
        }
        if (version.isBlank()) {
            throw OpenApiConfigException("OpenAPI version must not be blank")
        }
    }

    /**
     * 设置文档描述。
     *
     * @param description 文档描述；`null` 表示不输出该字段
     */
    public fun description(description: String?): OpenApiConfigBuilder {
        this.description = description
        return this
    }

    /**
     * 追加一个 server。
     *
     * @param url server URL，不能为空
     * @param description server 描述，可选
     */
    public fun server(url: String, description: String? = null): OpenApiConfigBuilder {
        servers.add(OpenApiServer(url, description))
        return this
    }

    /**
     * 追加多个 server。
     *
     * @param servers server 列表
     */
    public fun servers(servers: Iterable<OpenApiServer>): OpenApiConfigBuilder {
        for (server in servers) {
            this.servers.add(server)
        }
        return this
    }

    /**
     * 追加一个路径操作。
     *
     * @param operation 路径操作
     */
    public fun operation(operation: OpenApiOperation): OpenApiConfigBuilder {
        val duplicate = operations.any { it.path == operation.path && it.method == operation.method }
        if (duplicate) {
            throw OpenApiConfigException("Duplicate operation for ${operation.method} ${operation.path}")
        }
        operations.add(operation)
        return this
    }

    /**
     * 追加一个路径操作。
     *
     * @param path 路径模板，必须以 `/` 开头
     * @param method HTTP 方法
     * @param schemaName 使用的 Schema 名称
     * @param summary 操作摘要，可选
     * @param operationId 操作 id，可选
     * @param tags 标签列表，可选
     */
    public fun operation(
        path: String,
        method: String,
        schemaName: String,
        summary: String? = null,
        operationId: String? = null,
        tags: List<String> = emptyList(),
    ): OpenApiConfigBuilder = operation(OpenApiOperation(path, method, schemaName, summary, operationId, tags))

    /**
     * 覆盖字段的 OpenAPI 类型。
     *
     * 用于表达通用 `SchemaType` 无法描述的元素类型、枚举取值与 `$ref` 嵌套。
     *
     * @param schemaName Schema 名称
     * @param fieldName 字段名
     * @param type 字段类型
     */
    public fun fieldType(schemaName: String, fieldName: String, type: OpenApiFieldType): OpenApiConfigBuilder {
        val key = OpenApiFieldKey(requireSchemaName(schemaName), requireFieldName(fieldName))
        if (fieldTypes.containsKey(key)) {
            throw OpenApiConfigException("Field type already configured for ${key.schemaName}.${key.fieldName}")
        }
        fieldTypes[key] = type
        return this
    }

    /**
     * 将字段映射为字符串枚举。
     *
     * @param schemaName Schema 名称
     * @param fieldName 字段名
     * @param values 枚举取值，不能为空且不能重复
     */
    public fun enumOf(schemaName: String, fieldName: String, vararg values: String): OpenApiConfigBuilder = fieldType(schemaName, fieldName, OpenApiEnumType(values.toList()))

    /**
     * 将字段映射为对另一个 Schema 的 `$ref`。
     *
     * @param schemaName Schema 名称
     * @param fieldName 字段名
     * @param targetSchemaName 目标 Schema 名称
     */
    public fun refOf(schemaName: String, fieldName: String, targetSchemaName: String): OpenApiConfigBuilder = fieldType(schemaName, fieldName, OpenApiReferenceType(targetSchemaName))

    /**
     * 将字段映射为指定元素类型的数组。
     *
     * @param schemaName Schema 名称
     * @param fieldName 字段名
     * @param items 元素类型
     */
    public fun arrayOf(schemaName: String, fieldName: String, items: OpenApiFieldType): OpenApiConfigBuilder = fieldType(schemaName, fieldName, OpenApiArrayType(items))

    /**
     * 将字段映射为指定值类型的 Map。
     *
     * @param schemaName Schema 名称
     * @param fieldName 字段名
     * @param values 值类型
     */
    public fun mapOf(schemaName: String, fieldName: String, values: OpenApiFieldType): OpenApiConfigBuilder = fieldType(schemaName, fieldName, OpenApiMapType(values))

    /**
     * 构建配置。
     */
    public fun build(): OpenApiConfig = OpenApiConfig(
        title = title,
        version = version,
        description = description,
        servers = servers.toList(),
        operations = operations.toList(),
        fieldTypes = fieldTypes.toMap(),
    )

    private fun requireSchemaName(schemaName: String): String {
        if (schemaName.isBlank()) {
            throw OpenApiConfigException("Schema name of field type override must not be blank")
        }
        return schemaName
    }

    private fun requireFieldName(fieldName: String): String {
        if (fieldName.isBlank()) {
            throw OpenApiConfigException("Field name of field type override must not be blank")
        }
        return fieldName
    }
}
