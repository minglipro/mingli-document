package com.mingliqiye.document.openapi

import com.mingliqiye.document.openapi.exception.OpenApiConfigException

/**
 * OpenAPI server 定义，对应文档根节点的 `servers` 数组元素。
 *
 * @property url server URL，例如 `https://api.example.com`
 * @property description server 描述，可选
 */
public class OpenApiServer(
    public val url: String,
    public val description: String? = null,
) {

    init {
        if (url.isBlank()) {
            throw OpenApiConfigException("Server url must not be blank")
        }
    }
}
