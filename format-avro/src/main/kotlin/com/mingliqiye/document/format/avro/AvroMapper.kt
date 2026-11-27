package com.mingliqiye.document.format.avro

import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver
import org.apache.avro.Schema

/**
 * Avro 二进制文档映射器。
 *
 * 继承 [BinaryDocumentMapper]：`formatBytes` 通过 `GenericDatumWriter` /
 * `BinaryEncoder` 写出，`parse(ByteArray, ...)` 通过 `GenericDatumReader` /
 * `BinaryDecoder` 读入并转换为 [com.mingliqiye.document.core.Node]，再交由
 * `fromNode` 映射成 Java 实体；文本 API（`parse(String)` / `format` /
 * `formatPretty`）不支持，抛出
 * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]。
 *
 * Avro 二进制不自带 Schema，读写都必须显式提供 Schema，且只支持单根 record：
 * - [formatBytes] 接受 Java 实体、`Map`、[com.mingliqiye.document.core.Node]
 * - [parse] 接受 `ByteArray`，目标类型可以是实体、`Map` 或
 *   [com.mingliqiye.document.core.Node]
 *
 * 支持的字段类型：null、boolean、int、long、float、double、string、bytes、fixed、
 * enum、array、map、record 以及 union（含 null union）。由于通用
 * [com.mingliqiye.document.core.NumberNode] 只保存十进制值，Avro `float`/`double`
 * 的 NaN、Infinity 不受支持。
 */
public open class AvroMapper internal constructor(
    /** Avro record Schema。 */
    public val schema: Schema,
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : BinaryDocumentMapper(
    FORMAT_NAME,
    { source -> AvroWireReader(schema).read(source) },
    { node, _ -> AvroWireWriter(schema).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    init {
        require(schema.type == Schema.Type.RECORD) {
            "AvroMapper requires a record schema but was ${schema.type}"
        }
    }

    /**
     * 使用 Apache Avro record Schema 创建映射器。
     *
     * @param schema Avro record Schema
     * @throws IllegalArgumentException Schema 不是 record 类型时抛出
     */
    public constructor(schema: Schema) : this(
        schema,
        emptyMap(),
        emptyMap(),
        null,
        null,
        DocumentMapperConfig.empty(),
    )

    /**
     * 使用 Apache Avro record Schema 与通用配置创建映射器。
     *
     * @param schema Avro record Schema
     * @param config 通用 Mapper 配置
     * @throws IllegalArgumentException Schema 不是 record 类型时抛出
     */
    public constructor(schema: Schema, config: DocumentMapperConfig) : this(
        schema,
        emptyMap(),
        emptyMap(),
        null,
        null,
        config,
    )

    /**
     * 使用 [AvroSchema] 创建映射器。
     *
     * @param schema Avro Schema 视图，内部通过 [AvroSchema.toAvroSchema] 转换
     */
    public constructor(schema: AvroSchema) : this(schema.toAvroSchema())

    /**
     * 使用 [AvroSchema] 与通用配置创建映射器。
     *
     * @param schema Avro Schema 视图，内部通过 [AvroSchema.toAvroSchema] 转换
     * @param config 通用 Mapper 配置
     */
    public constructor(schema: AvroSchema, config: DocumentMapperConfig) : this(schema.toAvroSchema(), config)

    public companion object {

        /** 格式名称。 */
        public const val FORMAT_NAME: String = "avro"

        /**
         * 创建 Avro Mapper 构建器。
         *
         * @param schema Avro record Schema
         * @return 构建器
         */
        @JvmStatic
        public fun builder(schema: Schema): AvroMapperBuilder = AvroMapperBuilder(schema)

        /**
         * 使用 [AvroSchema] 创建 Avro Mapper 构建器。
         *
         * @param schema Avro Schema 视图
         * @return 构建器
         */
        @JvmStatic
        public fun builder(schema: AvroSchema): AvroMapperBuilder = AvroMapperBuilder(schema.toAvroSchema())
    }
}
