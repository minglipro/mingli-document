package com.mingliqiye.document.format.smile

/**
 * Smile 写出特性。
 *
 * 对齐 Smile 底层库 2.15.3 的 `SmileGenerator.Feature`，并补充 上游实现
 * 通用生成器中与 Smile 写出相关的 `STRICT_DUPLICATE_DETECTION`。
 *
 * 通过 [SmileConfigBuilder.enable] / [SmileConfigBuilder.disable] /
 * [SmileConfigBuilder.configure] 配置，查询使用 [SmileConfig.isEnabled]。
 * 默认值与 上游实现 一致。
 */
public enum class SmileWriteFeature {

    /**
     * 写出 Smile 4 字节头部，默认开启。
     *
     * 对齐 上游实现 `SmileGenerator.Feature.WRITE_HEADER`。关闭后输出没有头部，
     * 只能由关闭 `REQUIRE_HEADER` 的读取器解析。
     */
    WRITE_HEADER,

    /**
     * 关闭写出器时追加内容结束标记 `0xFF`，默认关闭。
     *
     * 对齐 上游实现 `SmileGenerator.Feature.WRITE_END_MARKER`。开启后字节数组/Node
     * 写出路径在文档末尾追加标记，读取器会把它当作 EOF。
     */
    WRITE_END_MARKER,

    /**
     * 二进制内容使用 7-bit 编码（每 7 字节编码为 8 个字节），默认开启。
     *
     * 对齐 上游实现 `SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT`。开启可保证输出中
     * 不出现 `0xFF`；关闭后写出原始二进制并在头部标记 HAS_RAW_BINARY，体积更小但
     * 输出可能包含 `0xFF`。关闭该特性时要求开启 `WRITE_HEADER`。
     */
    ENCODE_BINARY_AS_7BIT,

    /**
     * 检查并复用重复字段名（反向引用），默认开启。
     *
     * 对齐 上游实现 `SmileGenerator.Feature.CHECK_SHARED_NAMES`。开启后输出更紧凑；
     * 关闭后每个字段名都完整写出。
     */
    CHECK_SHARED_NAMES,

    /**
     * 检查并复用重复短字符串值（反向引用），默认关闭。
     *
     * 对齐 上游实现 `SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES`。开启后输出更
     * 紧凑；关闭该特性时要求开启 `WRITE_HEADER`。
     */
    CHECK_SHARED_STRING_VALUES,

    /**
     * 宽松处理非法 UTF-16 代理项：用替换字符 U+FFFD 代替，而不是写入失败，默认关闭。
     *
     * 对齐 上游实现 `SmileGenerator.Feature.LENIENT_UTF_ENCODING`。关闭时字符串中的
     * 孤立代理项会抛出 [SmileException]。
     */
    LENIENT_UTF_ENCODING,

    /**
     * 写出重复字段名时抛出异常，默认关闭。
     *
     * 对齐 上游实现 `JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION`。Bean/Map 序列化
     * 不会产生重复字段名；该特性主要约束直接使用 Token 写出器的调用方。
     */
    STRICT_DUPLICATE_DETECTION,
}
