package com.mingliqiye.document.format.avro

/**
 * Avro 读取特性，对齐 `Avro 底层库` 的读取端格式特性。
 *
 * - 上游实现 2.x：`com.fasterxml.上游库.dataformat.avro.AvroParser.Feature`
 * - 上游实现 3.x：`tools.上游库.dataformat.avro.AvroReadFeature`
 *
 * 官方枚举只有 [AVRO_BUFFERING] 一项，默认开启。通过 [AvroConfigBuilder.enable] /
 * [AvroConfigBuilder.disable] / [AvroConfigBuilder.configure] 或
 * [AvroConfig.isEnabled] 配置与查询。
 */
public enum class AvroReadFeature {

    /**
     * 是否让底层 Avro codec 尽可能多地缓冲数据，默认开启。
     *
     * 对应 上游实现 `AVRO_BUFFERING`。开启时使用 `DecoderFactory.binaryDecoder`
     * （缓冲解码器）；关闭时使用 `DecoderFactory.directBinaryDecoder`（尽量只读取
     * 最少字节、不额外缓冲）。两种模式解码结果与尾随字节校验一致，仅内部缓冲策略不同；
     * OCF 容器读取由 Apache Avro 的 `DataFileReader` 自行管理缓冲，不受本特性影响。
     */
    AVRO_BUFFERING,
}
