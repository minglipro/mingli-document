package com.mingliqiye.document.format.protobuf

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * Protobuf Mapper 配置。
 *
 * 继承 [DocumentMapperConfig]，在通用字段/读写配置基础上增加 Protobuf 专属选项：
 * - [unknownFieldPolicy]：未知字段策略（`FAIL` / `IGNORE` / `PRESERVE`）；
 * - [maxDepth]：解析最大嵌套深度；
 * - [extensions]：extension 注册表；
 * - [readFeatures] / [writeFeatures]：对齐 上游实现 `Protobuf 底层库` 读取/写出行为。
 *
 * ## 上游实现 对齐
 *
 * 上游实现 `Protobuf 底层库`（2.15.3 / 2.23.0 源码核对）没有格式专属的
 * Parser/Generator Feature 枚举（`ProtobufFactory.getFormatReadFeatureType()` /
 * `getFormatWriteFeatureType()` 均被注释为 "No format-specific configuration, yet"）。
 * 其解析/写出行为由核心 上游实现 特性控制，本模块以同名常量封装其中真正影响 wire 语义的两项：
 * - 读取 [ProtobufReadFeature.IGNORE_UNDEFINED]（上游实现 `StreamReadFeature.IGNORE_UNDEFINED`）；
 * - 写出 [ProtobufWriteFeature.IGNORE_UNKNOWN]（上游实现 `JsonGenerator.Feature.IGNORE_UNKNOWN`）。
 *
 * 默认值均对齐 上游实现（关闭）。其余 上游实现 核心特性（流生命周期、ASCII 数字快速解析等）
 * 在本模块不适用或由通用 Mapper 层统一处理，详见模块 README 与
 * `docs/formats/protobuf.md` 的配置矩阵。
 *
 * 读取时实际生效的未知字段策略见 [effectiveUnknownFieldPolicy]：开启
 * [ProtobufReadFeature.IGNORE_UNDEFINED] 且策略为 [ProtobufUnknownFieldPolicy.FAIL] 时升级为
 * `IGNORE`；[ProtobufUnknownFieldPolicy.PRESERVE] 优先级最高。
 *
 * @property unknownFieldPolicy 未知字段策略，默认 [ProtobufUnknownFieldPolicy.FAIL]（对齐 上游实现）
 * @property maxDepth 解析最大嵌套深度，`0` 表示不限制（默认）
 * @property extensions extension 注册表，默认 [ProtobufExtensionRegistry.EMPTY]
 * @property readFeatures 读取特性集合，默认 [DEFAULT_READ_FEATURES]（空，对齐 上游实现）
 * @property writeFeatures 写出特性集合，默认 [DEFAULT_WRITE_FEATURES]（空，对齐 上游实现）
 */
public class ProtobufConfig internal constructor(
    base: DocumentMapperConfig,
    /** 未知字段策略，默认 [ProtobufUnknownFieldPolicy.FAIL]（对齐 上游实现 未知字段报错）。 */
    public val unknownFieldPolicy: ProtobufUnknownFieldPolicy = ProtobufUnknownFieldPolicy.FAIL,
    /** 解析最大嵌套深度，`0` 表示不限制。 */
    public val maxDepth: Int = DEFAULT_MAX_DEPTH,
    /** extension 注册表。 */
    public val extensions: ProtobufExtensionRegistry = ProtobufExtensionRegistry.EMPTY,
    /** Protobuf 读取特性集合，默认 [DEFAULT_READ_FEATURES]（空，对齐 上游实现）。 */
    public val readFeatures: Set<ProtobufReadFeature> = DEFAULT_READ_FEATURES,
    /** Protobuf 写出特性集合，默认 [DEFAULT_WRITE_FEATURES]（空，对齐 上游实现）。 */
    public val writeFeatures: Set<ProtobufWriteFeature> = DEFAULT_WRITE_FEATURES,
) : DocumentMapperConfig(base) {

    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, but was $maxDepth" }
    }

    /**
     * 指定读取特性是否开启。
     *
     * @param feature 读取特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: ProtobufReadFeature): Boolean = feature in readFeatures

    /**
     * 指定写出特性是否开启。
     *
     * @param feature 写出特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: ProtobufWriteFeature): Boolean = feature in writeFeatures

    /**
     * 读取时实际生效的未知字段策略。
     *
     * - [ProtobufUnknownFieldPolicy.PRESERVE] 优先（保留未知字段，不丢数据）；
     * - 开启 [ProtobufReadFeature.IGNORE_UNDEFINED] 时，[ProtobufUnknownFieldPolicy.FAIL] 升级为
     *   [ProtobufUnknownFieldPolicy.IGNORE]；
     * - 其余情况使用 [unknownFieldPolicy]。
     */
    public val effectiveUnknownFieldPolicy: ProtobufUnknownFieldPolicy
        get() = when {
            unknownFieldPolicy == ProtobufUnknownFieldPolicy.PRESERVE -> ProtobufUnknownFieldPolicy.PRESERVE
            isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED) -> ProtobufUnknownFieldPolicy.IGNORE
            else -> unknownFieldPolicy
        }

    /** 是否忽略未知 wire 字段（[ProtobufReadFeature.IGNORE_UNDEFINED]）。 */
    public val ignoreUndefinedFields: Boolean
        get() = isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED)

    /** 是否忽略写出时未声明的字段名（[ProtobufWriteFeature.IGNORE_UNKNOWN]）。 */
    public val ignoreUnknownFields: Boolean
        get() = isEnabled(ProtobufWriteFeature.IGNORE_UNKNOWN)

    public companion object {

        /** 默认读取特性：空集合，对齐 上游实现（未知字段报错）。 */
        public val DEFAULT_READ_FEATURES: Set<ProtobufReadFeature> = emptySet()

        /** 默认写出特性：空集合，对齐 上游实现（未知字段名报错）。 */
        public val DEFAULT_WRITE_FEATURES: Set<ProtobufWriteFeature> = emptySet()

        /** 默认解析最大嵌套深度：`0`，表示不限制。 */
        public const val DEFAULT_MAX_DEPTH: Int = 0

        /**
         * 创建 Protobuf 配置构建器。
         *
         * @return 构建器
         */
        @JvmStatic
        public fun builder(): ProtobufConfigBuilder = ProtobufConfigBuilder()

        /**
         * 使用默认值创建 Protobuf 配置。
         *
         * @param unknownFieldPolicy 未知字段策略，默认 `FAIL`
         * @param maxDepth 解析最大嵌套深度，默认 `0`（不限制）
         * @param extensions extension 注册表，默认空
         * @return Protobuf 配置
         */
        @JvmStatic
        @JvmOverloads
        public fun of(
            unknownFieldPolicy: ProtobufUnknownFieldPolicy = ProtobufUnknownFieldPolicy.FAIL,
            maxDepth: Int = DEFAULT_MAX_DEPTH,
            extensions: ProtobufExtensionRegistry = ProtobufExtensionRegistry.EMPTY,
        ): ProtobufConfig = ProtobufConfig(DocumentMapperConfig.empty(), unknownFieldPolicy, maxDepth, extensions)

        /**
         * 基于通用 Mapper 配置创建 Protobuf 配置。
         *
         * @param base 通用 Mapper 配置（字段转换、命名策略、Node 限制等）
         * @param unknownFieldPolicy 未知字段策略，默认 `FAIL`
         * @param maxDepth 解析最大嵌套深度，默认 `0`（不限制）
         * @param extensions extension 注册表，默认空
         * @param readFeatures 读取特性集合，默认 [DEFAULT_READ_FEATURES]
         * @param writeFeatures 写出特性集合，默认 [DEFAULT_WRITE_FEATURES]
         * @return Protobuf 配置
         */
        @JvmStatic
        @JvmOverloads
        public fun from(
            base: DocumentMapperConfig,
            unknownFieldPolicy: ProtobufUnknownFieldPolicy = ProtobufUnknownFieldPolicy.FAIL,
            maxDepth: Int = DEFAULT_MAX_DEPTH,
            extensions: ProtobufExtensionRegistry = ProtobufExtensionRegistry.EMPTY,
            readFeatures: Set<ProtobufReadFeature> = DEFAULT_READ_FEATURES,
            writeFeatures: Set<ProtobufWriteFeature> = DEFAULT_WRITE_FEATURES,
        ): ProtobufConfig = ProtobufConfig(base, unknownFieldPolicy, maxDepth, extensions, readFeatures, writeFeatures)
    }
}
