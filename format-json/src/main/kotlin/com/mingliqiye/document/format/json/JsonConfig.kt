package com.mingliqiye.document.format.json

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * JSON Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 JSON 专属选项。
 *
 * JSON 读取/写出特性通过 [readFeatures] / [writeFeatures] 配置，并与历史选项
 * [allowComments] / [duplicateKeyPolicy] 联动（见 [isEnabled] 说明）。
 */
public class JsonConfig internal constructor(
    base: DocumentMapperConfig,
    /** 是否允许 JSONC 注释（等价于开启 [JsonReadFeature.ALLOW_JAVA_COMMENTS]）。 */
    public val allowComments: Boolean,
    /** `format()` 是否默认输出格式化 JSON。 */
    public val pretty: Boolean,
    /** 对象内重复 key 的处理策略，默认 [DuplicateKeyPolicy.LAST_WINS]。 */
    public val duplicateKeyPolicy: DuplicateKeyPolicy = DuplicateKeyPolicy.LAST_WINS,
    /** 对象/数组最大嵌套深度，必须为正数，默认 [DEFAULT_MAX_DEPTH]。 */
    public val maxDepth: Int = DEFAULT_MAX_DEPTH,
    /** 显式开启的 JSON 读取特性集合；ALLOW_JAVA_COMMENTS 与 STRICT_DUPLICATE_DETECTION 由历史选项决定。 */
    public val readFeatures: Set<JsonReadFeature> = DEFAULT_READ_FEATURES,
    /** 显式开启的 JSON 写出特性集合，默认 [DEFAULT_WRITE_FEATURES]。 */
    public val writeFeatures: Set<JsonWriteFeature> = DEFAULT_WRITE_FEATURES,
) : DocumentMapperConfig(base) {

    /**
     * 指定 JSON 读取特性是否生效。
     *
     * - [JsonReadFeature.ALLOW_JAVA_COMMENTS] 由 [allowComments] 决定；
     * - [JsonReadFeature.STRICT_DUPLICATE_DETECTION] 由
     *   [duplicateKeyPolicy] 是否为 [DuplicateKeyPolicy.FAIL] 决定；
     * - 其余特性由 [readFeatures] 决定。
     *
     * @param feature 读取特性
     * @return 生效时返回 `true`
     */
    public fun isEnabled(feature: JsonReadFeature): Boolean = when (feature) {
        JsonReadFeature.ALLOW_JAVA_COMMENTS -> allowComments
        JsonReadFeature.STRICT_DUPLICATE_DETECTION -> duplicateKeyPolicy == DuplicateKeyPolicy.FAIL
        else -> feature in readFeatures
    }

    /**
     * 指定 JSON 写出特性是否生效。
     *
     * @param feature 写出特性
     * @return 生效时返回 `true`
     */
    public fun isEnabled(feature: JsonWriteFeature): Boolean = feature in writeFeatures

    public companion object {

        /**
         * 默认最大嵌套深度。
         *
         * 对象与数组统一计数；`{"a":[1]}` 的深度为 2。
         */
        public const val DEFAULT_MAX_DEPTH: Int = 512

        /**
         * 默认读取特性集合：空集合，即 上游实现 2.15.3 的全部 JSON 读取特性默认关闭。
         *
         * [JsonReadFeature.ALLOW_JAVA_COMMENTS] 例外：本项目为兼容 JSONC 历史行为
         * 通过 [JsonConfig.allowComments] 默认开启；需要严格 JSON 时显式关闭。
         */
        public val DEFAULT_READ_FEATURES: Set<JsonReadFeature> = emptySet()

        /**
         * 默认写出特性集合，与 上游实现 2.15.3 一致。
         *
         * [JsonWriteFeature.QUOTE_FIELD_NAMES]、[JsonWriteFeature.WRITE_NAN_AS_STRINGS]
         * 与 [JsonWriteFeature.WRITE_HEX_UPPER_CASE] 默认开启，其余默认关闭。
         */
        public val DEFAULT_WRITE_FEATURES: Set<JsonWriteFeature> = setOf(
            JsonWriteFeature.QUOTE_FIELD_NAMES,
            JsonWriteFeature.WRITE_NAN_AS_STRINGS,
            JsonWriteFeature.WRITE_HEX_UPPER_CASE,
        )

        /**
         * 创建 JSON 配置构建器。
         */
        @JvmStatic
        public fun builder(): JsonConfigBuilder = JsonConfigBuilder()
    }
}
