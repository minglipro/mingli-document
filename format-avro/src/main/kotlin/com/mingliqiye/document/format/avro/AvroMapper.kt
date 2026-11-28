package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver
import org.apache.avro.Schema
import java.io.File
import java.nio.file.Path

private val EMPTY_AVRO_CONFIG: AvroConfig = AvroConfig.from(DocumentMapperConfig.empty())

private fun avroConfigOf(config: DocumentMapperConfig): AvroConfig = config as? AvroConfig ?: AvroConfig.from(config)

private fun avroWriterSchemaOf(config: AvroConfig): Schema = requireNotNull(config.schema) {
    "AvroConfig.schema is required to create AvroMapper; use AvroMapper(schema) or AvroConfig.builder().schema(...)"
}

private fun avroReaderSchemaOf(config: AvroConfig): Schema = config.readerSchema ?: avroWriterSchemaOf(config)

/**
 * Avro 二进制文档映射器。
 *
 * 继承 [BinaryDocumentMapper]：`formatBytes` 通过 `GenericDatumWriter` /
 * `BinaryEncoder` 写出裸单根 datum，`parse(ByteArray, ...)` 通过
 * `GenericDatumReader` / `BinaryDecoder` 读入并转换为
 * [com.mingliqiye.document.core.Node]，再交由 `fromNode` 映射成 Java 实体；
 * 文本 API（`parse(String)` / `format` / `formatPretty`）不支持，抛出
 * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]。
 *
 * 除裸单根 API 外还支持 Avro Object Container File（OCF）：
 * - [formatContainerBytes] 用 `DataFileWriter` 把多条记录写成内嵌 Schema 的容器，
 *   默认 codec 取 [AvroConfig.codec]，也可显式传 [AvroCodec]
 *   （[AvroCodec.NULL] / [AvroCodec.DEFLATE] / [AvroCodec.SNAPPY]）
 * - [parseContainerToNodes] / [parseContainer] 用 `DataFileReader` 读取容器全部记录，
 *   codec 由容器头部自动识别
 * - [appendContainer] / [appendContainerBytes] 向已有容器追加记录，保留已有记录与
 *   sync 标记，并沿用容器原 codec
 *
 * Schema 演进通过 [writerSchema] / [readerSchema] 双 Schema 构造器完成：
 * 写出与裸二进制读取以 writer Schema 为写入端，读取时以 reader Schema 为期望
 * Schema，交由 `GenericDatumReader(writer, reader)` 解析；支持新增带默认值字段、
 * 删除字段与类型提升（如 int→long/double），不兼容时抛
 * [com.mingliqiye.document.format.avro.exception.AvroParseException]。
 *
 * 配置对齐 上游实现 Avro：通过 [AvroConfig] / [AvroConfigBuilder] 配置
 * [AvroReadFeature] 与 [AvroWriteFeature]（`AVRO_BUFFERING`、`AVRO_FILE_OUTPUT`、
 * `ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA`），默认值见 [AvroConfig]；也可以直接传普通
 * [DocumentMapperConfig]，此时 Avro 专属选项回退默认值。查询生效配置使用
 * [avroConfig]。
 *
 * 支持的字段类型：null、boolean、int、long、float、double、string、bytes、fixed、
 * enum、array、map、record 以及 union（含 null union）。由于通用
 * [com.mingliqiye.document.core.NumberNode] 只保存十进制值，Avro `float`/`double`
 * 的 NaN、Infinity 不受支持。
 *
 * 支持 Avro logical type：`date`、`time-millis`、`time-micros`、
 * `timestamp-millis`、`timestamp-micros`、`local-timestamp-millis`、
 * `local-timestamp-micros`、`decimal`（bytes/fixed）、`uuid`、`duration`
 * （fixed size 12）。读取时映射为日期/时间 ISO 字符串、保留 scale 的数字、
 * UUID 字符串或 months/days/millis 对象；写出时按 Schema 反向编码。未知
 * logical type 回退到底层 Avro 类型；已知 logical type 的 Schema 结构非法时
 * 读取抛
 * [com.mingliqiye.document.format.avro.exception.AvroParseException]、写出抛
 * [com.mingliqiye.document.format.avro.exception.AvroException]。
 */
public open class AvroMapper internal constructor(
    /** writer Schema：用于写出与解析裸单根 datum。 */
    public val writerSchema: Schema,
    /** reader Schema：读取时使用的期望 Schema；等于 writer Schema 表示不做演进。 */
    public val readerSchema: Schema,
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : BinaryDocumentMapper(
    FORMAT_NAME,
    { source ->
        AvroFeatureSupport.wireReader(
            writerSchema,
            readerSchema,
            config.maxNodeDepth,
            avroConfigOf(config),
        ).read(source)
    },
    { node, _ -> AvroFeatureSupport.wireWriter(writerSchema, avroConfigOf(config)).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    /** Avro record Schema 别名，等同 [writerSchema]。 */
    public val schema: Schema get() = writerSchema

    /**
     * 生效的 Avro 配置。
     *
     * 以普通 [DocumentMapperConfig] 构造时，返回带默认 Avro 选项的配置视图；
     * 以 [AvroConfig] 构造时原样返回。
     */
    public val avroConfig: AvroConfig = avroConfigOf(config)

    /** 容器记录转换时使用的最大嵌套深度，来自通用配置。 */
    private val maxNodeDepth: Int = config.maxNodeDepth

    init {
        require(writerSchema.type == Schema.Type.RECORD) {
            "AvroMapper requires a record writer schema but was ${writerSchema.type}"
        }
        require(readerSchema.type == Schema.Type.RECORD) {
            "AvroMapper requires a record reader schema but was ${readerSchema.type}"
        }
    }

    /**
     * 使用 Apache Avro record Schema 创建映射器（writer 与 reader 相同）。
     *
     * @param schema Avro record Schema
     * @throws IllegalArgumentException Schema 不是 record 类型时抛出
     */
    public constructor(schema: Schema) : this(
        schema,
        schema,
        emptyMap(),
        emptyMap(),
        null,
        null,
        EMPTY_AVRO_CONFIG,
    )

    /**
     * 使用 Apache Avro record Schema 与通用配置创建映射器。
     *
     * @param schema Avro record Schema（writer）
     * @param config 通用配置；传入 [AvroConfig] 时生效其 reader Schema 与格式特性
     * @throws IllegalArgumentException Schema 不是 record 类型时抛出
     */
    public constructor(schema: Schema, config: DocumentMapperConfig) : this(
        schema,
        avroConfigOf(config).readerSchema ?: schema,
        emptyMap(),
        emptyMap(),
        null,
        null,
        avroConfigOf(config),
    )

    /**
     * 使用 Apache Avro record Schema 与 [AvroConfig] 创建映射器。
     *
     * @param schema Avro record Schema（writer）
     * @param config Avro 配置；配置中的 reader Schema 生效
     * @throws IllegalArgumentException Schema 不是 record 类型时抛出
     */
    public constructor(schema: Schema, config: AvroConfig) : this(
        schema,
        config.readerSchema ?: schema,
        emptyMap(),
        emptyMap(),
        null,
        null,
        config,
    )

    /**
     * 使用 [AvroSchema] 创建映射器（writer 与 reader 相同）。
     *
     * @param schema Avro Schema 视图，内部通过 [AvroSchema.toAvroSchema] 转换
     */
    public constructor(schema: AvroSchema) : this(schema.toAvroSchema(), EMPTY_AVRO_CONFIG)

    /**
     * 使用 [AvroSchema] 与通用配置创建映射器。
     *
     * 转换时按配置的 [AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA] 决定
     * 是否给可空/非必填字段补 `null` 默认值。
     *
     * @param schema Avro Schema 视图
     * @param config 通用配置
     */
    public constructor(schema: AvroSchema, config: DocumentMapperConfig) : this(
        schema.toAvroSchema(avroConfigOf(config).addNullAsDefaultValueInSchema),
        config,
    )

    /**
     * 使用 [AvroSchema] 与 [AvroConfig] 创建映射器。
     *
     * @param schema Avro Schema 视图（writer）
     * @param config Avro 配置；配置中的 reader Schema 与 Schema 生成选项生效
     */
    public constructor(schema: AvroSchema, config: AvroConfig) : this(
        schema.toAvroSchema(config.addNullAsDefaultValueInSchema),
        config,
    )

    /**
     * 使用 writer / reader 双 Schema 创建映射器以支持 Schema 演进。
     *
     * @param writerSchema 数据写出使用并内嵌到 OCF 的 Schema
     * @param readerSchema 读取时使用的期望 Schema
     * @throws IllegalArgumentException 任一 Schema 不是 record 类型时抛出
     */
    public constructor(writerSchema: Schema, readerSchema: Schema) : this(
        writerSchema,
        readerSchema,
        emptyMap(),
        emptyMap(),
        null,
        null,
        EMPTY_AVRO_CONFIG,
    )

    /**
     * 使用 writer / reader 双 Schema 与通用配置创建映射器。
     *
     * @param writerSchema 数据写出使用并内嵌到 OCF 的 Schema
     * @param readerSchema 读取时使用的期望 Schema
     * @param config 通用配置
     * @throws IllegalArgumentException 任一 Schema 不是 record 类型时抛出
     */
    public constructor(writerSchema: Schema, readerSchema: Schema, config: DocumentMapperConfig) : this(
        writerSchema,
        readerSchema,
        emptyMap(),
        emptyMap(),
        null,
        null,
        avroConfigOf(config),
    )

    /**
     * 使用 writer / reader 双 Schema 与 [AvroConfig] 创建映射器。
     *
     * @param writerSchema 数据写出使用并内嵌到 OCF 的 Schema
     * @param readerSchema 读取时使用的期望 Schema
     * @param config Avro 配置
     * @throws IllegalArgumentException 任一 Schema 不是 record 类型时抛出
     */
    public constructor(writerSchema: Schema, readerSchema: Schema, config: AvroConfig) : this(
        writerSchema,
        readerSchema,
        emptyMap(),
        emptyMap(),
        null,
        null,
        config,
    )

    /**
     * 使用 writer / reader 双 [AvroSchema] 创建映射器以支持 Schema 演进。
     *
     * @param writerSchema 数据写出使用的 Schema 视图
     * @param readerSchema 读取时使用的期望 Schema 视图
     */
    public constructor(writerSchema: AvroSchema, readerSchema: AvroSchema) : this(
        writerSchema.toAvroSchema(),
        readerSchema.toAvroSchema(),
    )

    /**
     * 使用 writer / reader 双 [AvroSchema] 与 [AvroConfig] 创建映射器。
     *
     * @param writerSchema 数据写出使用的 Schema 视图
     * @param readerSchema 读取时使用的期望 Schema 视图
     * @param config Avro 配置；Schema 生成选项生效
     */
    public constructor(writerSchema: AvroSchema, readerSchema: AvroSchema, config: AvroConfig) : this(
        writerSchema.toAvroSchema(config.addNullAsDefaultValueInSchema),
        readerSchema.toAvroSchema(config.addNullAsDefaultValueInSchema),
        config,
    )

    /**
     * 使用 [AvroConfig] 创建映射器。
     *
     * writer / reader Schema 来自 [AvroConfig.schema] / [AvroConfig.readerSchema]。
     *
     * @param config Avro 配置，必须包含 [AvroConfig.schema]
     * @throws IllegalArgumentException 配置未提供 writer Schema 时抛出
     */
    public constructor(config: AvroConfig) : this(
        avroWriterSchemaOf(config),
        avroReaderSchemaOf(config),
        emptyMap(),
        emptyMap(),
        null,
        null,
        config,
    )

    /**
     * 将实体写成 Avro 二进制字节。
     *
     * 默认写裸单根 datum；开启 [AvroWriteFeature.AVRO_FILE_OUTPUT] 时改为写单条
     * 记录的 OCF 容器（内嵌 Schema，codec 取 [AvroConfig.codec]），与 上游实现
     * `AVRO_FILE_OUTPUT` 行为一致。
     *
     * @param value Java 实体、集合、Map 或 [com.mingliqiye.document.core.Node]
     * @return Avro 二进制字节
     */
    override fun formatBytes(value: Any?): ByteArray {
        if (avroConfig.avroFileOutput) {
            val writer = AvroContainerWriter(writerSchema, avroConfig.codec)
            return writer.write(listOf(value)) { item -> toNode(item) }
        }
        return super.formatBytes(value)
    }

    /**
     * 将多个值写成 Avro Object Container File（OCF），使用 [AvroConfig.codec]。
     *
     * @param values 记录值集合
     * @return OCF 字节
     */
    public fun formatContainerBytes(values: Iterable<Any?>): ByteArray = formatContainerBytes(values, avroConfig.codec)

    /**
     * 将多个值写成 Avro Object Container File（OCF）。
     *
     * 容器头部内嵌 [writerSchema] 与指定 [codec]，记录顺序与入参一致；传入值可以是
     * Java 实体、`Map` 或 [Node]。空集合会写出只含头部的合法容器。写入后
     * `DataFileReader` 可依据头部 `avro.codec` 自动识别并解压。
     *
     * @param values 记录值集合
     * @param codec OCF 压缩 codec
     * @return OCF 字节
     * @throws com.mingliqiye.document.format.avro.exception.AvroException 记录与
     *   writer Schema 不匹配或写出失败时抛出
     */
    public fun formatContainerBytes(values: Iterable<Any?>, codec: AvroCodec): ByteArray {
        val writer = AvroContainerWriter(writerSchema, codec)
        return writer.write(values) { value -> toNode(value) }
    }

    /**
     * 向 OCF 文件追加记录，文件不存在或为空时新建。
     *
     * 已存在容器时会在其后追加新数据块：保留已有记录与 sync 标记，并自动沿用容器
     * 原有 codec；[codec] 显式给出时必须与容器原 codec 一致，否则抛
     * [com.mingliqiye.document.format.avro.exception.AvroException]。
     *
     * @param file OCF 文件
     * @param values 待追加值
     * @param codec 期望 codec；`null` 表示沿用已有容器 codec（新建时为
     *   [AvroConfig.codec]）
     * @throws com.mingliqiye.document.format.avro.exception.AvroException Schema 与
     *   已有容器不一致、codec 不一致或写出失败时抛出
     */
    public fun appendContainer(file: File, values: Iterable<Any?>, codec: AvroCodec? = null) {
        if (file.exists() && file.length() > 0L) {
            val effectiveCodec = codec ?: AvroContainerReader.readHeader(file).codec
            AvroContainerWriter(writerSchema, effectiveCodec).appendTo(file, values) { value -> toNode(value) }
        } else {
            AvroContainerWriter(writerSchema, codec ?: avroConfig.codec)
                .writeToFile(file, values) { value -> toNode(value) }
        }
    }

    /**
     * 向 OCF 文件追加记录，文件不存在或为空时新建。
     *
     * @param file OCF 文件路径
     * @param values 待追加值
     * @param codec 期望 codec；`null` 表示沿用已有容器 codec
     * @throws com.mingliqiye.document.format.avro.exception.AvroException Schema 与
     *   已有容器不一致、codec 不一致或写出失败时抛出
     */
    public fun appendContainer(file: Path, values: Iterable<Any?>, codec: AvroCodec? = null) {
        appendContainer(file.toFile(), values, codec)
    }

    /**
     * 向已有 OCF 字节追加记录，返回追加后的完整容器字节。
     *
     * 输出以原容器字节开头，新记录使用原 sync 标记写入后续数据块，等价于面向
     * `OutputStream` 的追加语义；[existing] 为空时按新建容器处理（codec 取
     * [AvroConfig.codec]）。
     *
     * @param existing 已有 OCF 字节
     * @param values 待追加值
     * @param codec 期望 codec；`null` 表示沿用已有容器 codec
     * @return 追加后的 OCF 字节
     * @throws com.mingliqiye.document.format.avro.exception.AvroException Schema 与
     *   已有容器不一致、codec 不一致或写出失败时抛出
     */
    public fun appendContainerBytes(
        existing: ByteArray,
        values: Iterable<Any?>,
        codec: AvroCodec? = null,
    ): ByteArray {
        if (existing.isEmpty()) {
            return formatContainerBytes(values, codec ?: avroConfig.codec)
        }
        val effectiveCodec = codec ?: AvroContainerReader.readHeader(existing).codec
        return AvroContainerWriter(writerSchema, effectiveCodec).appendTo(existing, values) { value -> toNode(value) }
    }

    /**
     * 读取 OCF 内全部记录并转换为 [Node] 列表。
     *
     * writer Schema 取自容器头部，reader Schema 使用 [readerSchema] 做演进。
     *
     * @param source OCF 字节
     * @return 文档树列表，保持容器内记录顺序
     * @throws com.mingliqiye.document.format.avro.exception.AvroParseException
     *   容器损坏或 Schema 无法演进时抛出
     */
    public fun parseContainerToNodes(source: ByteArray): List<Node> = AvroContainerReader(readerSchema, maxNodeDepth).read(source)

    /**
     * 读取 OCF 内全部记录并映射为指定 Java 类型。
     *
     * @param source OCF 字节
     * @param targetType 目标类型
     * @return Java 实体列表
     * @throws com.mingliqiye.document.format.avro.exception.AvroParseException
     *   容器损坏或 Schema 无法演进时抛出
     */
    public fun <T> parseContainer(source: ByteArray, targetType: Class<T>): List<T> = parseContainerToNodes(source).map { fromNode(it, targetType) }

    /**
     * 读取 OCF 内全部记录并映射为指定泛型类型。
     *
     * @param source OCF 字节
     * @param targetType 目标类型引用
     * @return Java 实体列表
     * @throws com.mingliqiye.document.format.avro.exception.AvroParseException
     *   容器损坏或 Schema 无法演进时抛出
     */
    public fun <T> parseContainer(source: ByteArray, targetType: DocumentTypeReference<T>): List<T> = parseContainerToNodes(source).map { fromNode(it, targetType) }

    public companion object {

        /** 格式名称。 */
        public const val FORMAT_NAME: String = "avro"

        /**
         * 创建不带 Schema 的 Avro Mapper 构建器；Schema 可通过
         * [AvroMapperBuilder.schema] / [AvroMapperBuilder.readerSchema] 或
         * [AvroConfig.schema] 提供。
         *
         * @return 构建器
         */
        @JvmStatic
        public fun builder(): AvroMapperBuilder = AvroMapperBuilder(null, null, null, null, null)

        /**
         * 创建 Avro Mapper 构建器（writer 与 reader 相同）。
         *
         * @param schema Avro record Schema
         * @return 构建器
         */
        @JvmStatic
        public fun builder(schema: Schema): AvroMapperBuilder = AvroMapperBuilder(schema, schema, null, null, null)

        /**
         * 使用 [AvroSchema] 创建 Avro Mapper 构建器（writer 与 reader 相同）。
         *
         * @param schema Avro Schema 视图
         * @return 构建器
         */
        @JvmStatic
        public fun builder(schema: AvroSchema): AvroMapperBuilder = AvroMapperBuilder(null, null, schema, schema, null)

        /**
         * 创建 Avro Mapper 构建器（writer / reader 双 Schema）。
         *
         * @param writerSchema writer Schema
         * @param readerSchema reader Schema
         * @return 构建器
         */
        @JvmStatic
        public fun builder(writerSchema: Schema, readerSchema: Schema): AvroMapperBuilder = AvroMapperBuilder(writerSchema, readerSchema, null, null, null)

        /**
         * 使用双 [AvroSchema] 创建 Avro Mapper 构建器（writer / reader）。
         *
         * @param writerSchema writer Schema 视图
         * @param readerSchema reader Schema 视图
         * @return 构建器
         */
        @JvmStatic
        public fun builder(writerSchema: AvroSchema, readerSchema: AvroSchema): AvroMapperBuilder = AvroMapperBuilder(null, null, writerSchema, readerSchema, null)

        /**
         * 使用 [AvroConfig] 创建 Avro Mapper 构建器。
         *
         * Schema 取 [AvroConfig.schema] / [AvroConfig.readerSchema]；也可后续用
         * [AvroMapperBuilder.schema] 覆盖。
         *
         * @param config Avro 配置
         * @return 构建器
         */
        @JvmStatic
        public fun builder(config: AvroConfig): AvroMapperBuilder = AvroMapperBuilder(config.schema, config.readerSchema, null, null, config)
    }
}
