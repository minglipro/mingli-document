package com.mingliqiye.document.format.smile

/**
 * Smile 读取特性。
 *
 * 对齐 Smile 底层库 2.15.3 的 `SmileParser.Feature`，并补充 上游实现
 * 通用解析器中与 Smile 解析相关的 `STRICT_DUPLICATE_DETECTION`。
 *
 * 通过 [SmileConfigBuilder.enable] / [SmileConfigBuilder.disable] /
 * [SmileConfigBuilder.configure] 配置，查询使用 [SmileConfig.isEnabled]。
 * 默认值与 上游实现 一致。
 */
public enum class SmileReadFeature {

    /**
     * 输入必须以 Smile 4 字节头部开头，默认开启。
     *
     * 对齐 上游实现 `SmileParser.Feature.REQUIRE_HEADER`。关闭后允许读取没有头部的
     * Smile 字节（通常由关闭 `WRITE_HEADER` 写出），此时解析器按默认设置
     * （共享字段名开启、共享字符串值关闭、二进制 7-bit 编码）解释内容。
     */
    REQUIRE_HEADER,

    /**
     * 对象内出现重复字段名时抛出解析异常，默认关闭。
     *
     * 对齐 上游实现 `JsonParser.Feature.STRICT_DUPLICATE_DETECTION`。关闭时保持既有
     * 行为：重复字段名以后出现的值为准。
     */
    STRICT_DUPLICATE_DETECTION,
}
