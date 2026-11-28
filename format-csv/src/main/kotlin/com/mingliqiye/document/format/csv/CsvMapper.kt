package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.NodeTokenReader
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.InputStream
import java.io.Reader
import java.nio.charset.Charset

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
 *
 * 配置了 [CsvConfig.nestedMode] 时启用嵌套单元格（JSON 文本或点分列名）；Schema 中通过
 * [CsvSchemaBuilder.objectField] / [CsvSchemaBuilder.arrayField] 声明的嵌套子 Schema 总是启用。
 * 默认（`nestedMode` 为 `null` 且未声明子 Schema）保持旧行为：嵌套单元格报错。
 *
 * 大文件流式读取使用 [parseStream]：逐行解析并逐条回调，不把整个输入读成 String，
 * 表头、类型推断、嵌套与 Schema 规则与整段解析一致。
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
    private val fileCharset: Charset = config.fileCharset

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
        val csvConfig = mapperConfig as? CsvConfig
        if (csvConfig?.schema != null || csvConfig?.nestedMode != null) {
            val node = parseCsv(source, mapperConfig)
            validateNodeLimits(node)
            return NodeTokenReader(node)
        }
        return CsvTokenReader(
            source,
            csvConfig?.delimiter ?: DEFAULT_CSV_DELIMITER,
            csvConfig?.header ?: DEFAULT_CSV_HEADER,
        )
    }

    /**
     * 逐行解析 CSV 输入，每解析出一行 Bean 立即回调。
     *
     * 逐行读取：任意时刻内存中只有当前行记录与解析结果，不把整个输入读成 String；
     * 表头、类型推断、嵌套单元格与 [CsvSchema] 规则与整段 [parse] 解析一致。
     *
     * 空输入不产生任何回调；只有表头时也不产生回调。CSV 语法错误、非法字符编码与
     * Schema 校验失败抛出 [CsvParseException]；Bean 反序列化失败抛出
     * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]；
     * `consumer` 抛出的异常原样传播。本方法只读取，不关闭调用方传入的流。
     *
     * @param input 输入流；按 [DocumentMapperConfig.fileCharset]（默认 UTF-8）严格解码
     * @param targetType 每行 Bean 的目标类型
     * @param maxRows 最多回调的数据行数，`-1` 表示不限制；达到上限后停止读取
     * @param consumer 单行解析结果回调
     * @throws CsvParseException CSV 格式错误、编码错误或 Schema 校验失败时抛出
     * @throws IllegalArgumentException `maxRows` 小于 `-1` 时抛出
     */
    public fun <T> parseStream(
        input: InputStream,
        targetType: Class<T>,
        maxRows: Long = -1,
        consumer: (T) -> Unit,
    ) {
        parseStream(input, DocumentTypeReference.of(targetType), maxRows, consumer)
    }

    /**
     * 逐行解析 CSV 输入，每解析出一行 Bean 立即回调。
     *
     * @param input 输入流；按 [DocumentMapperConfig.fileCharset]（默认 UTF-8）严格解码
     * @param targetType 每行 Bean 的目标类型引用
     * @param maxRows 最多回调的数据行数，`-1` 表示不限制
     * @param consumer 单行解析结果回调
     * @see parseStream
     */
    public fun <T> parseStream(
        input: InputStream,
        targetType: DocumentTypeReference<T>,
        maxRows: Long = -1,
        consumer: (T) -> Unit,
    ) {
        val counting = CountingInputStream(input)
        val reader = decodingReader(counting, fileCharset)
        parseStreamInternal(bufferedReader(reader), { counting.count }, targetType, maxRows, consumer)
    }

    /**
     * 逐行解析 CSV 输入，每解析出一行 Bean 立即回调。
     *
     * @param reader 字符流；调用方负责关闭
     * @param targetType 每行 Bean 的目标类型
     * @param maxRows 最多回调的数据行数，`-1` 表示不限制
     * @param consumer 单行解析结果回调
     * @see parseStream
     */
    public fun <T> parseStream(
        reader: Reader,
        targetType: Class<T>,
        maxRows: Long = -1,
        consumer: (T) -> Unit,
    ) {
        parseStream(reader, DocumentTypeReference.of(targetType), maxRows, consumer)
    }

    /**
     * 逐行解析 CSV 输入，每解析出一行 Bean 立即回调。
     *
     * @param reader 字符流；调用方负责关闭
     * @param targetType 每行 Bean 的目标类型引用
     * @param maxRows 最多回调的数据行数，`-1` 表示不限制
     * @param consumer 单行解析结果回调
     * @see parseStream
     */
    public fun <T> parseStream(
        reader: Reader,
        targetType: DocumentTypeReference<T>,
        maxRows: Long = -1,
        consumer: (T) -> Unit,
    ) {
        val counting = CountingReader(reader)
        parseStreamInternal(bufferedReader(counting), { counting.count }, targetType, maxRows, consumer)
    }

    private fun <T> parseStreamInternal(
        reader: Reader,
        consumedSize: () -> Long,
        targetType: DocumentTypeReference<T>,
        maxRows: Long,
        consumer: (T) -> Unit,
    ) {
        require(maxRows >= -1) { "maxRows must be -1 (unlimited) or non-negative, but was $maxRows" }
        val csvConfig = mapperConfig as? CsvConfig
        val delimiter = csvConfig?.delimiter ?: DEFAULT_CSV_DELIMITER
        val schema = csvConfig?.schema
        val format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).build()
        val parser = try {
            CSVParser.parse(reader, format)
        } catch (e: CsvException) {
            throw e
        } catch (e: Exception) {
            throw CsvParseException("Invalid CSV: ${e.message}", e)
        }
        val rowSource = CsvStreamRowSource(
            parser.iterator(),
            header = csvConfig?.header ?: DEFAULT_CSV_HEADER,
            nestedMode = csvConfig?.nestedMode,
            schema = schema,
        )
        var delivered = 0L
        while (maxRows < 0 || delivered < maxRows) {
            val row = rowSource.next() ?: break
            checkInputSize(consumedSize())
            val value = fromNode(row, targetType)
            consumer(value)
            delivered++
        }
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
    val nestedMode = csvConfig?.nestedMode
    val schema = csvConfig?.schema
    return if (schema != null) {
        CsvSchemaReader(source, schema, delimiter, nestedMode).read()
    } else {
        CsvReader(source, delimiter, csvConfig?.header ?: DEFAULT_CSV_HEADER, nestedMode).read()
    }
}

private fun writeCsv(node: Node, config: DocumentMapperConfig): String {
    val csvConfig = config as? CsvConfig
    val delimiter = csvConfig?.delimiter ?: DEFAULT_CSV_DELIMITER
    val nestedMode = csvConfig?.nestedMode
    val schema = csvConfig?.schema
    return if (schema != null) {
        CsvSchemaWriter(schema, delimiter, nestedMode).write(node)
    } else {
        CsvWriter(delimiter, csvConfig?.header ?: DEFAULT_CSV_HEADER, nestedMode).write(node)
    }
}
