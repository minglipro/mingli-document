package com.mingliqiye.document.format.json

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * JSON Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 JSON 专属选项。
 */
public class JsonConfig internal constructor(
    base: DocumentMapperConfig,
    /** 是否允许 JSONC 注释。 */
    public val allowComments: Boolean,
    /** `format()` 是否默认输出格式化 JSON。 */
    public val pretty: Boolean,
) : DocumentMapperConfig(base) {

    public companion object {

        /**
         * 创建 JSON 配置构建器。
         */
        @JvmStatic
        public fun builder(): JsonConfigBuilder = JsonConfigBuilder()
    }
}
