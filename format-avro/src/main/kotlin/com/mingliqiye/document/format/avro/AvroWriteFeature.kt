package com.mingliqiye.document.format.avro

/**
 * Avro 写出特性，对齐 `Avro 底层库` 的写出端格式特性。
 *
 * - 上游实现 2.15.3：`com.fasterxml.上游库.dataformat.avro.AvroGenerator.Feature`
 *   包含 [AVRO_BUFFERING]、[AVRO_FILE_OUTPUT]；
 * - 上游实现 3.x：更名为 `tools.上游库.dataformat.avro.AvroWriteFeature`，并新增
 *   [ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA]。
 *
 * 通过 [AvroConfigBuilder.enable] / [AvroConfigBuilder.disable] /
 * [AvroConfigBuilder.configure] 或 [AvroConfig.isEnabled] 配置与查询。
 */
public enum class AvroWriteFeature {

    /**
     * 生成 Avro Schema 时，对「没有真实默认值且 `null` 合法（union 含 `null`）」的
     * 字段补充 `null` 作为默认值。
     *
     * 对应 上游实现 3.x `AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA`
     * （上游实现 2.x 无此特性，默认不补充）；上游实现 3.x 默认关闭。本模块为保持历史
     * `AvroSchema.toAvroSchema()` 输出（可空/非必填字段带 `"default": null`）默认
     * **开启**，需要 上游实现 3.x 行为时显式
     * `disable(AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA)`。
     *
     * 只影响由 [AvroSchema] 生成的 Apache Avro Schema（`AvroMapper` 以
     * `AvroSchema` 构造时按本特性转换）；调用方直接传入 `org.apache.avro.Schema`
     * 时 Schema 已由调用方生成，本特性不生效。
     */
    ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA,

    /**
     * 是否让底层 Avro codec 尽可能多地缓冲数据，默认开启。
     *
     * 对应 上游实现 `AVRO_BUFFERING`。开启时使用 `EncoderFactory.binaryEncoder`
     * （缓冲编码器）；关闭时使用 `EncoderFactory.directBinaryEncoder`（直接写出、
     * 不额外缓冲）。两种模式输出的字节完全一致，仅内部缓冲策略不同；OCF 容器写出由
     * Apache Avro 的 `DataFileWriter` 自行管理缓冲，不受本特性影响。
     */
    AVRO_BUFFERING,

    /**
     * 以 Avro 文件格式（Object Container File，OCF）写出，而不是默认的裸 datum
     * （RPC）格式，默认关闭。
     *
     * 对应 上游实现 `AVRO_FILE_OUTPUT`：开启后 `formatBytes` 以及由它驱动的
     * `format(value, OutputStream/File/Path)` 会把单个根值写成内嵌 Schema 的 OCF
     * 容器（单条记录）。容器 codec 取 [AvroConfig.codec]（默认 [AvroCodec.NULL]，
     * 与 上游实现 默认 codec 一致）。
     *
     * 与 上游实现 一致：OCF 的头部不是 100% 可靠可自动识别，读取端必须显式选择容器
     * API，本模块使用 [AvroMapper.parseContainerToNodes] / [AvroMapper.parseContainer]
     * 读取。（`parse(ByteArray)` 只接受裸 datum。）
     */
    AVRO_FILE_OUTPUT,
}
