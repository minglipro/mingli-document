package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver
import org.apache.avro.Schema
import java.io.File
import java.nio.file.Path

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
 *   可选 [AvroCodec]（[AvroCodec.NULL] / [AvroCodec.DEFLATE] / [AvroCodec.SNAPPY]）
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
 * 支持的字段类型：null、boolean、int、long、float、double、string、bytes、fixed、
 * enum、array、map、record 以及 union（含 null union）。由于通用
 * [com.mingliqiye.document.core.NumberNode] 只保存十进制值，Avro `float`/`double`
 * 的 NaN、Infinity 不受支持。
 */
public open class AvroMapper internal constructor(
    /** writer Schema：用于写出与解析裸单根 datum。 */
    public val writerSchema: Schema,
    /** reader Schema：读取时使用的期望 Schema；等于 writer Schema 表示不做演进。 */
    public val readerSchema: Schema,
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : BinaryDocumentMapper(
    FORMAT_NAME,
    { source -> AvroWireReader(writerSchema, readerSchema).read(source) },
    { node, _ -> AvroWireWriter(writerSchema).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    /** Avro record Schema 别名，等同 [writerSchema]。 */
    public val schema: Schema get() = writerSchema

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
        DocumentMapperConfig.empty(),
    )

    /**
     * 使用 Apache Avro record Schema 与通用配置创建映射器（writer 与 reader 相同）。
     *
     * @param schema Avro record Schema
     * @param config 通用 Mapper 配置
     * @throws IllegalArgumentException Schema 不是 record 类型时抛出
     */
    public constructor(schema: Schema, config: DocumentMapperConfig) : this(
        schema,
        schema,
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
    public constructor(schema: AvroSchema) : this(schema.toAvroSchema(), DocumentMapperConfig.empty())

    /**
     * 使用 [AvroSchema] 与通用配置创建映射器（writer 与 reader 相同）。
     *
     * @param schema Avro Schema 视图，内部通过 [AvroSchema.toAvroSchema] 转换
     * @param config 通用 Mapper 配置
     */
    public constructor(schema: AvroSchema, config: DocumentMapperConfig) : this(schema.toAvroSchema(), config)

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
        DocumentMapperConfig.empty(),
    )

    /**
     * 使用 writer / reader 双 Schema 与通用配置创建映射器。
     *
     * @param writerSchema 数据写出使用并内嵌到 OCF 的 Schema
     * @param readerSchema 读取时使用的期望 Schema
     * @param config 通用 Mapper 配置
     * @throws IllegalArgumentException 任一 Schema 不是 record 类型时抛出
     */
    public constructor(writerSchema: Schema, readerSchema: Schema, config: DocumentMapperConfig) : this(
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
     * 将多个值写成 Avro Object Container File（OCF），使用 [AvroCodec.NULL] 不压缩。
     *
     * @param values 记录值集合
     * @return OCF 字节
     */
    public fun formatContainerBytes(values: Iterable<Any?>): ByteArray = formatContainerBytes(values, AvroCodec.NULL)

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
     * @param codec 期望 codec；`null` 表示沿用已有容器 codec（新建时为 [AvroCodec.NULL]）
     * @throws com.mingliqiye.document.format.avro.exception.AvroException Schema 与
     *   已有容器不一致、codec 不一致或写出失败时抛出
     */
    public fun appendContainer(file: File, values: Iterable<Any?>, codec: AvroCodec? = null) {
        if (file.exists() && file.length() > 0L) {
            val effectiveCodec = codec ?: AvroContainerReader.readHeader(file).codec
            AvroContainerWriter(writerSchema, effectiveCodec).appendTo(file, values) { value -> toNode(value) }
        } else {
            AvroContainerWriter(writerSchema, codec ?: AvroCodec.NULL)
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
     * `OutputStream` 的追加语义；[existing] 为空时按新建容器处理。
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
            return formatContainerBytes(values, codec ?: AvroCodec.NULL)
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
    public fun parseContainerToNodes(source: ByteArray): List<Node> = AvroContainerReader(readerSchema).read(source)

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
         * 创建 Avro Mapper 构建器（writer 与 reader 相同）。
         *
         * @param schema Avro record Schema
         * @return 构建器
         */
        @JvmStatic
        public fun builder(schema: Schema): AvroMapperBuilder = AvroMapperBuilder(schema, schema)

        /**
         * 使用 [AvroSchema] 创建 Avro Mapper 构建器（writer 与 reader 相同）。
         *
         * @param schema Avro Schema 视图
         * @return 构建器
         */
        @JvmStatic
        public fun builder(schema: AvroSchema): AvroMapperBuilder = AvroMapperBuilder(schema.toAvroSchema(), schema.toAvroSchema())

        /**
         * 创建 Avro Mapper 构建器（writer / reader 双 Schema）。
         *
         * @param writerSchema writer Schema
         * @param readerSchema reader Schema
         * @return 构建器
         */
        @JvmStatic
        public fun builder(writerSchema: Schema, readerSchema: Schema): AvroMapperBuilder = AvroMapperBuilder(writerSchema, readerSchema)
    }
}
