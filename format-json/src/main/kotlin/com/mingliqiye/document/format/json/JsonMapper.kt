package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenWriter
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.nio.charset.Charset

private const val LINE_SEPARATOR: Int = '\n'.code

/**
 * JSON 文档映射器。
 *
 * 与 上游实现 的 `JsonMapper` 类似：继承通用 [DocumentMapper]，内置 JSON 编解码能力。
 * 外部只使用这个 Mapper，不需要接触内部 Codec SPI。
 *
 * - `parse(String)` 走 [JsonTokenReader] Token 直读路径；
 * - `parse(ByteArray)` 先按严格 UTF-8 解码，再走同一条 Token 路径；
 * - [format]/[formatPretty] 输出 JSON 文本，[formatBytes] 输出 UTF-8 字节；
 * - [parseLines]/[parseArray] 与 [formatLines]/[formatArray] 提供大文件流式读写，
 *   任意时刻只持有当前记录/元素，不把整个文件读入内存。
 */
public open class JsonMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "json",
    { source -> JsonReader(source).read() },
    { node, pretty -> JsonWriter(pretty).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    private val jsonConfig: JsonConfig? = config as? JsonConfig
    private val allowComments: Boolean = jsonConfig?.allowComments ?: true
    private val duplicateKeyPolicy: DuplicateKeyPolicy = jsonConfig?.duplicateKeyPolicy ?: DuplicateKeyPolicy.LAST_WINS
    private val maxDepth: Int = jsonConfig?.maxDepth ?: JsonConfig.DEFAULT_MAX_DEPTH
    private val prettyOutput: Boolean = jsonConfig?.pretty ?: false
    private val readFeatures: Set<JsonReadFeature> = jsonConfig?.readFeatures ?: JsonConfig.DEFAULT_READ_FEATURES
    private val writeOptions: JsonWriteOptions = JsonWriteOptions(jsonConfig?.writeFeatures ?: JsonConfig.DEFAULT_WRITE_FEATURES)
    private val fileCharset: Charset = config.fileCharset

    /**
     * 创建默认 JSON 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用字段配置创建 JSON 映射器。
     *
     * @param config Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    override fun <T> parse(source: String, targetType: Class<T>): T = parseText(source, DocumentTypeReference.of(targetType))

    override fun <T> parse(source: String, targetType: DocumentTypeReference<T>): T = parseText(source, targetType)

    private fun <T> parseText(source: String, targetType: DocumentTypeReference<T>): T {
        checkInputSize(source.length.toLong())
        return parseDirect(source, targetType)
    }

    /**
     * 创建字节输入的 Token 读取器，供基类 `parse(ByteArray, ...)` 使用。
     *
     * 字节按严格 UTF-8 解码；非法 UTF-8 抛
     * [com.mingliqiye.document.format.json.exception.JsonParseException]。
     */
    protected override fun createTokenReader(source: ByteArray): DocumentTokenReader = createJsonTokenReader(source)

    override fun format(value: Any?): String = writeJson(value, pretty = prettyOutput)

    override fun formatPretty(value: Any?): String = writeJson(value, pretty = true)

    override fun prettyOutputEnabled(): Boolean = prettyOutput

    override fun createStreamingTokenWriter(output: OutputStream, pretty: Boolean): DocumentTokenWriter = JsonUtf8StreamWriter(output, pretty, writeOptions)

    /**
     * 将任意值序列化为 UTF-8 编码的 JSON 字节数组。
     *
     * 等价于 [format] 结果的 UTF-8 字节；`pretty` 配置同样生效。
     */
    override fun formatBytes(value: Any?): ByteArray {
        if (prettyOutput) {
            return writeJson(value, pretty = true).toByteArray(Charsets.UTF_8)
        }
        val writer = JsonUtf8StreamWriter(options = writeOptions)
        writeTo(value, writer)
        return writer.toByteArray()
    }

    /**
     * 逐行解析 JSON Lines / NDJSON 输入，每解析出一个 Bean 立即回调。
     *
     * 逐行读取：任意时刻内存中只有当前行的文本与解析结果，不把整个输入读成 String。
     * 空行与纯空白行被跳过；首个字符为 UTF-8 BOM 时会被忽略；每行必须是完整的单个
     * JSON 值（JSONC 注释只允许出现在行内，不支持整行注释）。
     *
     * 解析失败时抛出 [JsonParseException]（消息包含行号）；Bean 反序列化失败时抛出
     * [DocumentSerializationException]（消息包含行号）；`consumer` 抛出的异常原样传播。
     * 行内容为 JSON `null` 时以 `null` 回调（目标类型为非可空 Bean 时同样如此）。
     * 本方法只读取，不关闭调用方传入的流。
     *
     * @param input 输入流；按 [DocumentMapperConfig.fileCharset]（默认 UTF-8）严格解码
     * @param targetType 每行 Bean 的目标类型
     * @param consumer 单行解析结果回调
     * @throws JsonParseException 某行不是合法 JSON 或输入不是合法字符集编码时抛出
     * @throws DocumentSerializationException 反序列化失败、或累计输入超过 `maxInputSize` 时抛出
     */
    public fun <T> parseLines(input: InputStream, targetType: Class<T>, consumer: (T?) -> Unit) {
        parseLines(input, DocumentTypeReference.of(targetType), consumer)
    }

    /**
     * 逐行解析 JSON Lines / NDJSON 输入，每解析出一个 Bean 立即回调。
     *
     * @param input 输入流；按 [DocumentMapperConfig.fileCharset]（默认 UTF-8）严格解码
     * @param targetType 每行 Bean 的目标类型引用
     * @param consumer 单行解析结果回调
     * @see parseLines
     */
    public fun <T> parseLines(input: InputStream, targetType: DocumentTypeReference<T>, consumer: (T?) -> Unit) {
        val counting = CountingInputStream(input)
        val reader = decodingReader(counting, fileCharset)
        parseLinesInternal(bufferedReader(reader), { counting.count }, targetType, consumer)
    }

    /**
     * 逐行解析 JSON Lines / NDJSON 输入，每解析出一个 Bean 立即回调。
     *
     * @param reader 字符流；调用方负责关闭
     * @param targetType 每行 Bean 的目标类型
     * @param consumer 单行解析结果回调
     * @see parseLines
     */
    public fun <T> parseLines(reader: Reader, targetType: Class<T>, consumer: (T?) -> Unit) {
        parseLines(reader, DocumentTypeReference.of(targetType), consumer)
    }

    /**
     * 逐行解析 JSON Lines / NDJSON 输入，每解析出一个 Bean 立即回调。
     *
     * @param reader 字符流；调用方负责关闭
     * @param targetType 每行 Bean 的目标类型引用
     * @param consumer 单行解析结果回调
     * @see parseLines
     */
    public fun <T> parseLines(reader: Reader, targetType: DocumentTypeReference<T>, consumer: (T?) -> Unit) {
        val counting = CountingReader(reader)
        parseLinesInternal(bufferedReader(counting), { counting.count }, targetType, consumer)
    }

    private fun <T> parseLinesInternal(
        buffered: BufferedReader,
        consumedSize: () -> Long,
        targetType: DocumentTypeReference<T>,
        consumer: (T?) -> Unit,
    ) {
        var lineNumber = 0L
        while (true) {
            val rawLine = buffered.readLine() ?: break
            lineNumber++
            val line = if (lineNumber == 1L) rawLine.removePrefix("\uFEFF") else rawLine
            checkInputSize(consumedSize())
            if (line.isBlank()) {
                continue
            }
            val value = try {
                parseText(line, targetType)
            } catch (e: JsonParseException) {
                throw JsonParseException("Invalid JSON on line $lineNumber: ${e.message}", e)
            } catch (e: DocumentSerializationException) {
                throw DocumentSerializationException("Invalid JSON on line $lineNumber: ${e.message}", e)
            }
            consumer(value)
        }
    }

    /**
     * 流式写出 JSON Lines / NDJSON：每个值写出为一行紧凑 JSON。
     *
     * 逐值写出，不构造整个输出字符串；始终使用紧凑格式（即使 `pretty` 配置为 `true`），
     * 保证每个值独占一行。本方法只 flush，不关闭调用方传入的流。
     *
     * @param values 待写出的值序列
     * @param output 目标输出流；按 UTF-8 写出
     * @throws DocumentSerializationException 序列化失败时抛出（此时输出可能只写入了一部分）
     */
    public fun formatLines(values: Iterable<Any?>, output: OutputStream) {
        val writer = JsonUtf8StreamWriter(output, pretty = false, options = writeOptions)
        for (value in values) {
            writeTo(value, writer)
            output.write(LINE_SEPARATOR)
        }
        writer.flush()
        output.flush()
    }

    /**
     * 流式写出 JSON Lines / NDJSON：每个值写出为一行紧凑 JSON。
     *
     * 逐值写出，不构造整个输出字符串；始终使用紧凑格式。本方法只 flush，不关闭传入的 Writer。
     *
     * @param values 待写出的值序列
     * @param writer 目标字符流；调用方负责关闭
     * @throws DocumentSerializationException 序列化失败时抛出（此时输出可能只写入了一部分）
     */
    public fun formatLines(values: Iterable<Any?>, writer: Writer) {
        for (value in values) {
            writer.write(writeJson(value, pretty = false))
            writer.write(LINE_SEPARATOR)
        }
        writer.flush()
    }

    /**
     * 流式解析 JSON 顶层数组，每解析出一个元素立即回调。
     *
     * 逐字符扫描元素边界，每次只保留当前元素的文本；不把整个数组读成 String。
     * 支持任意 JSON 值元素、嵌套结构、JSONC 注释（[JsonConfig.allowComments]）与扩展空白；
     * 空数组不产生任何回调。除数组语法外，单个元素的解析规则与 [parse] 完全一致。
     *
     * 输入为空、顶层不是数组、元素畸形或数组未闭合时抛出 [JsonParseException]；
     * 元素为 JSON `null` 时以 `null` 回调；`consumer` 抛出的异常原样传播；
     * 本方法不关闭调用方传入的流。嵌套深度限制按单个元素统计（不计最外层数组）。
     *
     * 注意：与 [parse] 一样，每个元素独立反序列化 —— 元素之间不共享 `$ref` 解析上下文，
     * 元素内部的本地 `$ref` 仍可在该元素节点内解析。
     *
     * @param input 输入流；按 [DocumentMapperConfig.fileCharset]（默认 UTF-8）严格解码
     * @param targetType 每个数组元素 Bean 的目标类型
     * @param consumer 单元素解析结果回调
     * @throws JsonParseException 输入不是合法 JSON 数组或编码非法时抛出
     * @throws DocumentSerializationException 反序列化失败、或累计输入超过 `maxInputSize` 时抛出
     */
    public fun <T> parseArray(input: InputStream, targetType: Class<T>, consumer: (T?) -> Unit) {
        parseArray(input, DocumentTypeReference.of(targetType), consumer)
    }

    /**
     * 流式解析 JSON 顶层数组，每解析出一个元素立即回调。
     *
     * @param input 输入流；按 [DocumentMapperConfig.fileCharset]（默认 UTF-8）严格解码
     * @param targetType 每个数组元素 Bean 的目标类型引用
     * @param consumer 单元素解析结果回调
     * @see parseArray
     */
    public fun <T> parseArray(input: InputStream, targetType: DocumentTypeReference<T>, consumer: (T?) -> Unit) {
        val counting = CountingInputStream(input)
        val reader = decodingReader(counting, fileCharset)
        parseArrayInternal(reader, { counting.count }, targetType, consumer)
    }

    /**
     * 流式解析 JSON 顶层数组，每解析出一个元素立即回调。
     *
     * @param reader 字符流；调用方负责关闭
     * @param targetType 每个数组元素 Bean 的目标类型
     * @param consumer 单元素解析结果回调
     * @see parseArray
     */
    public fun <T> parseArray(reader: Reader, targetType: Class<T>, consumer: (T?) -> Unit) {
        parseArray(reader, DocumentTypeReference.of(targetType), consumer)
    }

    /**
     * 流式解析 JSON 顶层数组，每解析出一个元素立即回调。
     *
     * @param reader 字符流；调用方负责关闭
     * @param targetType 每个数组元素 Bean 的目标类型引用
     * @param consumer 单元素解析结果回调
     * @see parseArray
     */
    public fun <T> parseArray(reader: Reader, targetType: DocumentTypeReference<T>, consumer: (T?) -> Unit) {
        val counting = CountingReader(reader)
        parseArrayInternal(counting, { counting.count }, targetType, consumer)
    }

    private fun <T> parseArrayInternal(
        reader: Reader,
        consumedSize: () -> Long,
        targetType: DocumentTypeReference<T>,
        consumer: (T?) -> Unit,
    ) {
        val parser = JsonArrayStreamParser(reader, allowComments, duplicateKeyPolicy, maxDepth, readFeatures)
        parser.forEachValue(
            sizeProbe = consumedSize,
            checkSize = { size -> checkInputSize(size) },
        ) { node ->
            consumer(fromNode(node, targetType))
        }
    }

    /**
     * 流式写出 JSON 顶层数组：逐个元素写出，不构建完整数组节点/String。
     *
     * 始终使用紧凑格式（即使 `pretty` 配置为 `true`）。本方法只 flush，不关闭输出流。
     *
     * @param values 数组元素序列
     * @param output 目标输出流；按 UTF-8 写出
     * @throws DocumentSerializationException 序列化失败时抛出（此时输出可能只写入了一部分）
     */
    public fun formatArray(values: Iterable<Any?>, output: OutputStream) {
        output.write('['.code)
        val writer = JsonUtf8StreamWriter(output, pretty = false, options = writeOptions)
        var first = true
        for (value in values) {
            if (!first) {
                output.write(','.code)
            }
            first = false
            writeTo(value, writer)
        }
        output.write(']'.code)
        writer.flush()
        output.flush()
    }

    /**
     * 流式写出 JSON 顶层数组：逐个元素写出，不构建完整数组节点/String。
     *
     * 始终使用紧凑格式。本方法只 flush，不关闭传入的 Writer。
     *
     * @param values 数组元素序列
     * @param writer 目标字符流；调用方负责关闭
     * @throws DocumentSerializationException 序列化失败时抛出（此时输出可能只写入了一部分）
     */
    public fun formatArray(values: Iterable<Any?>, writer: Writer) {
        writer.write("[")
        var first = true
        for (value in values) {
            if (!first) {
                writer.write(",")
            }
            first = false
            writer.write(writeJson(value, pretty = false))
        }
        writer.write("]")
        writer.flush()
    }

    private fun <T> parseDirect(source: String, targetType: DocumentTypeReference<T>): T = fromTokens(createJsonTokenReader(source), targetType)

    private fun createJsonTokenReader(source: String): JsonTokenReader = JsonTokenReader(source, allowComments, duplicateKeyPolicy, maxDepth, readFeatures)

    private fun createJsonTokenReader(source: ByteArray): JsonTokenReader = JsonTokenReader(decodeUtf8(source), allowComments, duplicateKeyPolicy, maxDepth, readFeatures)

    private fun writeJson(value: Any?, pretty: Boolean): String {
        val writer = JsonStreamWriter(pretty, writeOptions)
        writeTo(value, writer)
        return writer.toString()
    }

    public companion object {

        /**
         * 创建 JSON Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): JsonMapperBuilder = JsonMapperBuilder()
    }
}
