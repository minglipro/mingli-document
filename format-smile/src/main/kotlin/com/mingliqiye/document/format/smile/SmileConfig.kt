package com.mingliqiye.document.format.smile

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.fasterxml.jackson.dataformat.smile.SmileGenerator
import com.fasterxml.jackson.dataformat.smile.SmileParser
import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * Smile Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 Smile 专属读写特性，对齐 Smile 底层库：
 * - 读取特性见 [SmileReadFeature]（`SmileParser.Feature` + `STRICT_DUPLICATE_DETECTION`）；
 * - 写出特性见 [SmileWriteFeature]（`SmileGenerator.Feature` + `STRICT_DUPLICATE_DETECTION`）。
 *
 * 默认值完全对齐 上游实现：读取要求 Smile 头部、不检查重复字段名；写出头部、检查共享
 * 字段名、二进制 7-bit 编码，而共享字符串值、结束标记、宽松 UTF 编码默认关闭。
 *
 * 通过 [SmileConfig.builder] 创建，`enable` / `disable` / `configure` 配置特性；
 * 通用配置项见 [SmileConfigBuilder] 中的便捷方法。
 */
public class SmileConfig internal constructor(
    base: DocumentMapperConfig,
    /** 显式开启的 Smile 读取特性集合。 */
    public val readFeatures: Set<SmileReadFeature> = DEFAULT_READ_FEATURES,
    /** 显式开启的 Smile 写出特性集合。 */
    public val writeFeatures: Set<SmileWriteFeature> = DEFAULT_WRITE_FEATURES,
) : DocumentMapperConfig(base) {

    /**
     * 指定读取特性是否开启。
     *
     * @param feature 读取特性
     * @return 显式开启时返回 `true`
     */
    public fun isEnabled(feature: SmileReadFeature): Boolean = feature in readFeatures

    /**
     * 指定写出特性是否开启。
     *
     * @param feature 写出特性
     * @return 显式开启时返回 `true`
     */
    public fun isEnabled(feature: SmileWriteFeature): Boolean = feature in writeFeatures

    public companion object {

        /**
         * 默认读取特性，与 上游实现 一致。
         *
         * 仅 [SmileReadFeature.REQUIRE_HEADER] 默认开启。
         */
        public val DEFAULT_READ_FEATURES: Set<SmileReadFeature> = setOf(SmileReadFeature.REQUIRE_HEADER)

        /**
         * 默认写出特性，与 上游实现 一致。
         *
         * [SmileWriteFeature.WRITE_HEADER]、[SmileWriteFeature.ENCODE_BINARY_AS_7BIT]、
         * [SmileWriteFeature.CHECK_SHARED_NAMES] 默认开启；
         * [SmileWriteFeature.WRITE_END_MARKER]、
         * [SmileWriteFeature.CHECK_SHARED_STRING_VALUES]、
         * [SmileWriteFeature.LENIENT_UTF_ENCODING]、
         * [SmileWriteFeature.STRICT_DUPLICATE_DETECTION] 默认关闭。
         */
        public val DEFAULT_WRITE_FEATURES: Set<SmileWriteFeature> = setOf(
            SmileWriteFeature.WRITE_HEADER,
            SmileWriteFeature.ENCODE_BINARY_AS_7BIT,
            SmileWriteFeature.CHECK_SHARED_NAMES,
        )

        /**
         * 创建 Smile 配置构建器。
         */
        @JvmStatic
        public fun builder(): SmileConfigBuilder = SmileConfigBuilder()
    }
}

/**
 * 创建应用 Smile 读取特性的 上游实现 工厂；`null` 表示使用 上游实现 默认特性。
 */
internal fun createSmileParserFactory(config: SmileConfig?): SmileFactory {
    val factory = SmileFactory()
    factory.configure(
        SmileParser.Feature.REQUIRE_HEADER,
        config?.isEnabled(SmileReadFeature.REQUIRE_HEADER) ?: true,
    )
    factory.configure(
        JsonParser.Feature.STRICT_DUPLICATE_DETECTION,
        config?.isEnabled(SmileReadFeature.STRICT_DUPLICATE_DETECTION) ?: false,
    )
    return factory
}

/**
 * 创建应用 Smile 写出特性的 上游实现 工厂；`null` 表示使用 上游实现 默认特性。
 */
internal fun createSmileGeneratorFactory(config: SmileConfig?): SmileFactory {
    val factory = SmileFactory()
    factory.configure(
        SmileGenerator.Feature.WRITE_HEADER,
        config?.isEnabled(SmileWriteFeature.WRITE_HEADER) ?: true,
    )
    factory.configure(
        SmileGenerator.Feature.WRITE_END_MARKER,
        config?.isEnabled(SmileWriteFeature.WRITE_END_MARKER) ?: false,
    )
    factory.configure(
        SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT,
        config?.isEnabled(SmileWriteFeature.ENCODE_BINARY_AS_7BIT) ?: true,
    )
    factory.configure(
        SmileGenerator.Feature.CHECK_SHARED_NAMES,
        config?.isEnabled(SmileWriteFeature.CHECK_SHARED_NAMES) ?: true,
    )
    factory.configure(
        SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES,
        config?.isEnabled(SmileWriteFeature.CHECK_SHARED_STRING_VALUES) ?: false,
    )
    factory.configure(
        SmileGenerator.Feature.LENIENT_UTF_ENCODING,
        config?.isEnabled(SmileWriteFeature.LENIENT_UTF_ENCODING) ?: false,
    )
    factory.configure(
        JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION,
        config?.isEnabled(SmileWriteFeature.STRICT_DUPLICATE_DETECTION) ?: false,
    )
    return factory
}
