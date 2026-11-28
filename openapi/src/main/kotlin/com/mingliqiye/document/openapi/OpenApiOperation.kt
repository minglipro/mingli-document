package com.mingliqiye.document.openapi

import com.mingliqiye.document.openapi.exception.OpenApiConfigException
import java.util.Locale

private val SUPPORTED_HTTP_METHODS: Set<String> =
    linkedSetOf("GET", "PUT", "POST", "DELETE", "OPTIONS", "HEAD", "PATCH", "TRACE")

/**
 * OpenAPI 路径操作定义。
 *
 * 生成的 operation 固定返回 `200` 响应，响应体为 `application/json` 且
 * schema 通过 `$ref` 引用 [schemaName] 对应的 component schema；
 * [REQUEST_BODY_METHODS] 中的方法同时生成 requestBody。
 *
 * @property path 路径模板，必须以 `/` 开头，例如 `/users/{id}`
 * @property schemaName 该操作使用的 Schema 名称，生成时必须已注册
 * @property summary 操作摘要，可选
 * @property operationId 操作 id，可选
 * @property tags 标签列表，可选
 * @property method HTTP 方法，统一转换为大写
 */
public class OpenApiOperation(
    public val path: String,
    method: String,
    public val schemaName: String,
    public val summary: String? = null,
    public val operationId: String? = null,
    tags: List<String> = emptyList(),
) {

    /** HTTP 方法，统一转换为大写。 */
    public val method: String = method.trim().uppercase(Locale.ROOT)

    /** 标签列表，保持声明顺序。 */
    public val tags: List<String> = tags.toList()

    /** 该方法的 operation 是否生成 requestBody。 */
    internal val hasRequestBody: Boolean
        get() = method in REQUEST_BODY_METHODS

    init {
        if (!path.startsWith("/")) {
            throw OpenApiConfigException("Operation path must start with '/': $path")
        }
        if (this.method !in SUPPORTED_HTTP_METHODS) {
            throw OpenApiConfigException("Unsupported HTTP method: $method")
        }
        if (schemaName.isBlank()) {
            throw OpenApiConfigException("Operation schema name must not be blank")
        }
    }

    private companion object {

        /** 生成 requestBody 的 HTTP 方法。 */
        private val REQUEST_BODY_METHODS: Set<String> = linkedSetOf("POST", "PUT", "PATCH")
    }
}
