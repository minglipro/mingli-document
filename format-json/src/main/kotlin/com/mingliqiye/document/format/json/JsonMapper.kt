package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * JSON 文档映射器。
 *
 * 与 Jackson 的 `JsonMapper` 类似：继承通用 [DocumentMapper]，内置 JSON 编解码能力。
 * 外部只使用这个 Mapper，不需要接触内部 Codec SPI。
 *
 * - `parse(String)` 走 [JsonTokenReader] Token 直读路径；
 * - `parse(ByteArray)` 先按严格 UTF-8 解码，再走同一条 Token 路径；
 * - [format]/[formatPretty] 输出 JSON 文本，[formatBytes] 输出 UTF-8 字节。
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

    override fun <T> parse(source: String, targetType: Class<T>): T = parseDirect(source, DocumentTypeReference.of(targetType))

    override fun <T> parse(source: String, targetType: DocumentTypeReference<T>): T = parseDirect(source, targetType)

    /**
     * 创建字节输入的 Token 读取器，供基类 `parse(ByteArray, ...)` 使用。
     *
     * 字节按严格 UTF-8 解码；非法 UTF-8 抛
     * [com.mingliqiye.document.format.json.exception.JsonParseException]。
     */
    protected override fun createTokenReader(source: ByteArray): DocumentTokenReader = createJsonTokenReader(source)

    override fun format(value: Any?): String = writeJson(value, pretty = prettyOutput)

    override fun formatPretty(value: Any?): String = writeJson(value, pretty = true)

    /**
     * 将任意值序列化为 UTF-8 编码的 JSON 字节数组。
     *
     * 等价于 [format] 结果的 UTF-8 字节；`pretty` 配置同样生效。
     */
    override fun formatBytes(value: Any?): ByteArray = writeJson(value, pretty = prettyOutput).toByteArray(Charsets.UTF_8)

    private fun <T> parseDirect(source: String, targetType: DocumentTypeReference<T>): T = fromTokens(createJsonTokenReader(source), targetType)

    private fun createJsonTokenReader(source: String): JsonTokenReader = JsonTokenReader(source, allowComments, duplicateKeyPolicy, maxDepth)

    private fun createJsonTokenReader(source: ByteArray): JsonTokenReader = JsonTokenReader(decodeUtf8(source), allowComments, duplicateKeyPolicy, maxDepth)

    private fun writeJson(value: Any?, pretty: Boolean): String {
        val writer = JsonStreamWriter(pretty)
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
