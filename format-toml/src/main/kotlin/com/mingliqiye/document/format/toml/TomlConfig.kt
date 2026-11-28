package com.mingliqiye.document.format.toml

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * TOML Mapper 配置。
 *
 * 在通用 [DocumentMapperConfig] 基础上增加 TOML 专属配置：
 * - [maxDepth]：解析最大嵌套深度（根表深度为 `1`，默认 [DEFAULT_MAX_DEPTH]）；
 * - [readFeatures] / [writeFeatures]：对齐 上游实现 `TomlReadFeature` / `TomlWriteFeature`；
 * - [commentConfig]：注释保留写出配置，由 [TomlCommentConfigBuilder] 或
 *   [TomlConfigBuilder] 构建并合并进主配置。
 *
 * 默认值与 上游实现 TOML 对齐：所有特性默认关闭；`FAIL_ON_NULL_WRITE` 关闭时
 * `null` 写成 `""`，`PARSE_JAVA_TIME` / `VALIDATE_DATE_TIME` 关闭时日期时间读取为
 * TOML 源码原文（如 `1979-05-27 07:32:00` 归一化为 `1979-05-27T07:32:00`）。
 *
 * 构造器为 internal，请使用 [builder] 或 [TomlMapper] 的构造器。
 */
public class TomlConfig internal constructor(
    base: DocumentMapperConfig,
    /** 解析最大嵌套深度（根表深度为 `1`），必须为正数。 */
    public val maxDepth: Int = DEFAULT_MAX_DEPTH,
    /** TOML 读取特性集合，默认 [DEFAULT_READ_FEATURES]（全部关闭）。 */
    public val readFeatures: Set<TomlReadFeature> = DEFAULT_READ_FEATURES,
    /** TOML 写出特性集合，默认 [DEFAULT_WRITE_FEATURES]（全部关闭）。 */
    public val writeFeatures: Set<TomlWriteFeature> = DEFAULT_WRITE_FEATURES,
    /** 注释保留写出配置，默认 [TomlCommentConfig]。 */
    public val commentConfig: TomlCommentConfig = TomlCommentConfig(),
) : DocumentMapperConfig(base) {

    init {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
    }

    /** 是否开启 `PARSE_JAVA_TIME`。 */
    public val parseJavaTime: Boolean
        get() = isEnabled(TomlReadFeature.PARSE_JAVA_TIME)

    /** 是否开启 `VALIDATE_DATE_TIME`。 */
    public val validateDateTime: Boolean
        get() = isEnabled(TomlReadFeature.VALIDATE_DATE_TIME)

    /** 是否开启 `FAIL_ON_NULL_WRITE`。 */
    public val failOnNullWrite: Boolean
        get() = isEnabled(TomlWriteFeature.FAIL_ON_NULL_WRITE)

    /**
     * 是否开启指定 TOML 读取特性。
     *
     * @param feature 读取特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: TomlReadFeature): Boolean = feature in readFeatures

    /**
     * 是否开启指定 TOML 写出特性。
     *
     * @param feature 写出特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: TomlWriteFeature): Boolean = feature in writeFeatures

    public companion object {

        /** 默认解析最大嵌套深度（根表深度为 `1`）。 */
        public const val DEFAULT_MAX_DEPTH: Int = 512

        /** 默认 TOML 读取特性：全部关闭（上游实现 默认值）。 */
        public val DEFAULT_READ_FEATURES: Set<TomlReadFeature> = emptySet()

        /** 默认 TOML 写出特性：全部关闭（上游实现 默认值）。 */
        public val DEFAULT_WRITE_FEATURES: Set<TomlWriteFeature> = emptySet()

        /**
         * 创建 TOML 配置构建器。
         *
         * @return 配置构建器
         */
        @JvmStatic
        public fun builder(): TomlConfigBuilder = TomlConfigBuilder()

        /**
         * 从通用配置创建 TOML 配置（使用 TOML 默认值与默认特性）。
         *
         * @param base 通用 Mapper 配置
         * @return TOML 配置
         */
        @JvmStatic
        public fun from(base: DocumentMapperConfig): TomlConfig = if (base is TomlConfig) base else TomlConfig(base)
    }
}
