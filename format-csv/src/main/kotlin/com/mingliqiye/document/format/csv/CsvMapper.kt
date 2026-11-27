package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.NodeTokenReader
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * CSV 文档映射器。
 *
 * CSV 只表达平坦表格：根节点为数组，每个元素是一行对象；写出时根对象被当作单行。
 * 分隔符与表头由 [CsvConfig] 控制。
 *
 * 配置了 [CsvSchema] 时按 Schema 读写：列顺序即 Schema 字段顺序，读取时按列名映射
 * （无表头时按顺序对应），单元格按 Schema 类型解析；此时 [CsvSchema.header] 优先于
 * [CsvConfig.header]。可通过 [CsvMapper] 构造器、[CsvConfigBuilder.schema] 或
 * [CsvMapperBuilder.schema] 传入 Schema。
 */
public open class CsvMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "csv",
    { source -> parseCsv(source, config) },
    { node, _ -> writeCsv(node, config) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    private val mapperConfig: DocumentMapperConfig = config

    /**
     * 创建默认 CSV 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用通用 Mapper 配置创建映射器。
     *
     * @param config 通用 Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 使用 CSV 配置创建映射器。
     *
     * @param config CSV 配置
     */
    public constructor(config: CsvConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 按指定 CSV Schema 创建映射器。
     *
     * Schema 的 header 配置优先于默认表头配置；分隔符使用默认值（逗号）。
     *
     * @param schema CSV Schema
     */
    public constructor(schema: CsvSchema) : this(
        emptyMap(),
        emptyMap(),
        null,
        null,
        CsvConfig.builder().schema(schema).build(),
    )

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    override fun createTextTokenReader(source: String): DocumentTokenReader {
        val node = parseCsv(source, mapperConfig)
        validateNodeLimits(node)
        return NodeTokenReader(node)
    }

    public companion object {

        /**
         * 创建 CSV Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): CsvMapperBuilder = CsvMapperBuilder()
    }
}

private fun parseCsv(source: String, config: DocumentMapperConfig): Node {
    val csvConfig = config as? CsvConfig
    val delimiter = csvConfig?.delimiter ?: DEFAULT_CSV_DELIMITER
    val schema = csvConfig?.schema
    return if (schema != null) {
        CsvSchemaReader(source, schema, delimiter).read()
    } else {
        CsvReader(source, delimiter, csvConfig?.header ?: DEFAULT_CSV_HEADER).read()
    }
}

private fun writeCsv(node: Node, config: DocumentMapperConfig): String {
    val csvConfig = config as? CsvConfig
    val delimiter = csvConfig?.delimiter ?: DEFAULT_CSV_DELIMITER
    val schema = csvConfig?.schema
    return if (schema != null) {
        CsvSchemaWriter(schema, delimiter).write(node)
    } else {
        CsvWriter(delimiter, csvConfig?.header ?: DEFAULT_CSV_HEADER).write(node)
    }
}
