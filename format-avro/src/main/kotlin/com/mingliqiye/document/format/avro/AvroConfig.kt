package com.mingliqiye.document.format.avro

import com.mingliqiye.document.serialization.DocumentMapperConfig
import org.apache.avro.Schema

/**
 * Avro Mapper 配置。
 *
 * 在通用字段/读写配置（[DocumentMapperConfig]）基础上增加 Avro 专属选项：
 *
 * - [schema] / [readerSchema]：writer / reader Schema；因此可以直接用
 *   `AvroMapper(config)` 创建映射器（无需再单独传 Schema）；
 * - [codec]：OCF 容器默认 codec（`NULL` / `DEFLATE` / `SNAPPY`），
 *   [AvroMapper.formatContainerBytes] 与新建容器的 `appendContainer` 使用；
 * - [readFeatures] / [writeFeatures]：对齐 上游实现 Avro 的 parser / generator
 *   格式特性，见 [AvroReadFeature] 与 [AvroWriteFeature]。
 *
 * 默认值与 上游实现 对齐：
 * - 读取：[DEFAULT_READ_FEATURES] 只含 [AvroReadFeature.AVRO_BUFFERING]（开启）；
 * - 写出：[DEFAULT_WRITE_FEATURES] 含 [AvroWriteFeature.AVRO_BUFFERING]（开启）、
 *   [AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA]（本项目历史兼容默认开启，
 *   上游实现 3.x 默认关闭）、[AvroWriteFeature.AVRO_FILE_OUTPUT] 默认关闭。
 *
 * 通过 [AvroConfigBuilder] 构建；也可以把普通 [DocumentMapperConfig] 传给
 * `AvroMapper`，此时 Avro 专属选项回退上述默认值。
 */
public class AvroConfig internal constructor(
    base: DocumentMapperConfig,
    /** writer Schema；`null` 表示由 Mapper 构造器另行提供。 */
    public val schema: Schema? = null,
    /** reader Schema；`null` 表示与 writer Schema 相同（不做演进）。 */
    public val readerSchema: Schema? = null,
    /** OCF 容器默认 codec，默认 [AvroCodec.NULL]。 */
    public val codec: AvroCodec = AvroCodec.NULL,
    /** 显式开启的 Avro 读取特性，默认 [DEFAULT_READ_FEATURES]。 */
    public val readFeatures: Set<AvroReadFeature> = DEFAULT_READ_FEATURES,
    /** 显式开启的 Avro 写出特性，默认 [DEFAULT_WRITE_FEATURES]。 */
    public val writeFeatures: Set<AvroWriteFeature> = DEFAULT_WRITE_FEATURES,
) : DocumentMapperConfig(base) {

    /** 读取端是否启用 Avro codec 缓冲（[AvroReadFeature.AVRO_BUFFERING]）。 */
    public val avroBufferingOnRead: Boolean
        get() = AvroReadFeature.AVRO_BUFFERING in readFeatures

    /** 写出端是否启用 Avro codec 缓冲（[AvroWriteFeature.AVRO_BUFFERING]）。 */
    public val avroBufferingOnWrite: Boolean
        get() = AvroWriteFeature.AVRO_BUFFERING in writeFeatures

    /** 是否以 OCF 文件格式写出（[AvroWriteFeature.AVRO_FILE_OUTPUT]）。 */
    public val avroFileOutput: Boolean
        get() = AvroWriteFeature.AVRO_FILE_OUTPUT in writeFeatures

    /**
     * 生成 Schema 时是否给「无真实默认值且 `null` 合法」的字段补 `null` 默认值
     * （[AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA]）。
     */
    public val addNullAsDefaultValueInSchema: Boolean
        get() = AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA in writeFeatures

    /**
     * 指定 Avro 读取特性是否开启。
     *
     * @param feature 读取特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: AvroReadFeature): Boolean = feature in readFeatures

    /**
     * 指定 Avro 写出特性是否开启。
     *
     * @param feature 写出特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: AvroWriteFeature): Boolean = feature in writeFeatures

    public companion object {

        /**
         * 默认读取特性集合：与 上游实现 一致，只开启
         * [AvroReadFeature.AVRO_BUFFERING]。
         */
        public val DEFAULT_READ_FEATURES: Set<AvroReadFeature> = setOf(AvroReadFeature.AVRO_BUFFERING)

        /**
         * 默认写出特性集合。
         *
         * 与 上游实现 一致开启 [AvroWriteFeature.AVRO_BUFFERING]、
         * 关闭 [AvroWriteFeature.AVRO_FILE_OUTPUT]；另为保持本项目历史
         * `AvroSchema.toAvroSchema()` 输出（可空/非必填字段带 `null` 默认值），
         * 默认开启 [AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA]
         * （上游实现 3.x 默认关闭，见该特性说明）。
         */
        public val DEFAULT_WRITE_FEATURES: Set<AvroWriteFeature> = setOf(
            AvroWriteFeature.AVRO_BUFFERING,
            AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA,
        )

        /**
         * 创建 Avro 配置构建器。
         */
        @JvmStatic
        public fun builder(): AvroConfigBuilder = AvroConfigBuilder()

        /**
         * 把任意通用配置归一为 [AvroConfig]；已是 [AvroConfig] 时原样返回。
         *
         * @param config 通用 Mapper 配置
         * @return Avro 配置
         */
        internal fun from(config: DocumentMapperConfig): AvroConfig = config as? AvroConfig ?: AvroConfig(config)
    }
}
