package com.mingliqiye.document.openapi

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.schema.DocumentSchema

/**
 * 将当前 [DocumentSchema] 生成为 OpenAPI 3.0 文档树。
 *
 * 等价于 `OpenApiGenerator.of(config).addSchema(this).generate()`。
 *
 * @param config 生成配置
 */
public fun DocumentSchema.toOpenApi(config: OpenApiConfig): ObjectNode = OpenApiGenerator.of(config).addSchema(this).generate()

/**
 * 将多个 [DocumentSchema] 生成为 OpenAPI 3.0 文档树。
 *
 * Schema 按迭代顺序写入 `components.schemas`；
 * 生成文档包含 `components.schemas` 与 `paths` 两个核心部分。
 *
 * @param config 生成配置
 */
public fun Iterable<DocumentSchema>.toOpenApi(config: OpenApiConfig): ObjectNode = OpenApiGenerator.of(config).addSchemas(this).generate()
