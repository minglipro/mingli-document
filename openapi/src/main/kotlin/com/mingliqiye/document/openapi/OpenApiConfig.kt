package com.mingliqiye.document.openapi

/**
 * OpenAPI 文档生成配置。
 *
 * 通过 [OpenApiConfig.builder] 构建；[title] 与 [version] 必填，
 * [description]、[servers]、[operations] 可选。
 *
 * @property title 文档标题
 * @property version 文档版本
 * @property description 文档描述，可选
 * @property servers server 列表
 * @property operations 路径操作列表
 */
public class OpenApiConfig internal constructor(
    public val title: String,
    public val version: String,
    public val description: String?,
    public val servers: List<OpenApiServer>,
    public val operations: List<OpenApiOperation>,
    internal val fieldTypes: Map<OpenApiFieldKey, OpenApiFieldType>,
) {

    /** [OpenApiConfig] 工厂方法。 */
    public companion object {

        /**
         * 创建配置构建器。
         *
         * @param title 文档标题，不能为空
         * @param version 文档版本，不能为空
         */
        @JvmStatic
        public fun builder(title: String, version: String): OpenApiConfigBuilder = OpenApiConfigBuilder(title, version)
    }
}
