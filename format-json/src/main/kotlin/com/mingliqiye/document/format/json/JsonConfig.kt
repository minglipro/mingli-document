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
    /** 对象内重复 key 的处理策略，默认 [DuplicateKeyPolicy.LAST_WINS]。 */
    public val duplicateKeyPolicy: DuplicateKeyPolicy = DuplicateKeyPolicy.LAST_WINS,
    /** 对象/数组最大嵌套深度，必须为正数，默认 [DEFAULT_MAX_DEPTH]。 */
    public val maxDepth: Int = DEFAULT_MAX_DEPTH,
) : DocumentMapperConfig(base) {

    public companion object {

        /**
         * 默认最大嵌套深度。
         *
         * 对象与数组统一计数；`{"a":[1]}` 的深度为 2。
         */
        public const val DEFAULT_MAX_DEPTH: Int = 512

        /**
         * 创建 JSON 配置构建器。
         */
        @JvmStatic
        public fun builder(): JsonConfigBuilder = JsonConfigBuilder()
    }
}
