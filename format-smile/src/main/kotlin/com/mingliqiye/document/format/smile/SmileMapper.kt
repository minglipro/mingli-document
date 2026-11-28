package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenWriter
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver
import java.io.OutputStream

/**
 * Smile 文档映射器。
 *
 * 继承通用 [BinaryDocumentMapper]，以 Smile 二进制作为唯一编解码格式：
 * [formatBytes] / [parse] 的 `ByteArray` 重载负责读写，文本 `format` / `parse(String)`
 * 由基类统一抛出“不支持”异常。
 *
 * `formatBytes` 通过 [SmileTokenWriter] 直接写 Token 流，`parse(ByteArray)` 通过
 * [SmileTokenReader] 直接解码 Bean，均不构建完整 Node 树；[SmileReader] / [SmileWriter]
 * 的 Node 路径仍作为基类 fallback 保留。
 *
 * 与 Jackson 的 `SmileMapper` 类似：外部只使用这个 Mapper，不需要接触内部
 * Codec SPI。
 */
public open class SmileMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : BinaryDocumentMapper(
    "smile",
    { source -> SmileReader(source, config.maxNodeDepth).read() },
    { node, _ -> SmileWriter().write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    private val maxNodeDepth: Int = config.maxNodeDepth

    /**
     * 创建默认 Smile 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用通用字段配置创建 Smile 映射器。
     *
     * @param config 通用 Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    /**
     * 创建 Smile Token 读取器，使 `parse(ByteArray)` 走 Token 直读 Bean 路径。
     *
     * 读取器包装 `SmileFactory().createParser(source)`；[SmileReader] 的 Node
     * 解析路径仍作为基类 fallback 保留。
     *
     * @param source Smile 二进制内容
     * @return Smile Token 读取器
     */
    override fun createTokenReader(source: ByteArray): DocumentTokenReader = SmileTokenReader(source)

    override fun createStreamingTokenWriter(output: OutputStream, pretty: Boolean): DocumentTokenWriter? = if (pretty) null else SmileTokenWriter(output)

    /**
     * 将 Java 实体直接写成 Smile 字节。
     *
     * 通过 [writeTo] 驱动 [SmileTokenWriter]，不构建完整 Node 树；[SmileWriter]
     * 的 Node 写出路径仍保留在基类 fallback 中。
     *
     * @param value 任意实体、集合、Map 或 Node
     * @return Smile 二进制内容
     */
    override fun formatBytes(value: Any?): ByteArray {
        val writer = SmileTokenWriter()
        try {
            writeTo(value, writer)
            return writer.toByteArray()
        } finally {
            writer.close()
        }
    }

    /**
     * 解析 Smile 多消息流（连续多个根值），按出现顺序返回全部文档树。
     *
     * 空输入返回空列表；只有一个根值时返回长度为 1 的列表。每个根值独立解析，
     * 深度限制取自 Mapper 配置的 `maxNodeDepth`（0 表示不限制）。
     *
     * @param source Smile 多消息流二进制内容
     * @param maxItems 最大消息数量；0 表示不限制
     * @return 按出现顺序排列的文档树列表；空输入返回空列表
     * @throws com.mingliqiye.document.format.smile.exception.SmileParseException
     *   任一消息格式非法、数据不完整、消息数超过 maxItems，或包含 NaN/Infinity
     *   等无法表示的浮点值时抛出
     */
    public fun parseAllToNodes(source: ByteArray, maxItems: Int = 0): List<Node> {
        checkInputSize(source.size.toLong())
        return SmileSequenceReader(source, maxNodeDepth, maxItems).readAll()
    }

    /**
     * 解析 Smile 多消息流，按出现顺序把每个根值反序列化为指定类型。
     *
     * 空输入返回空列表；反序列化失败时整体抛出
     * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]。
     *
     * @param source Smile 多消息流二进制内容
     * @param targetType 每个根值的目标类型
     * @param maxItems 最大消息数量；0 表示不限制
     * @return 按出现顺序反序列化得到的实体列表
     */
    public fun <T> parseAll(
        source: ByteArray,
        targetType: Class<T>,
        maxItems: Int = 0,
    ): List<T> = parseAllToNodes(source, maxItems).map { fromNode(it, targetType) }

    /**
     * 解析 Smile 多消息流，按出现顺序把每个根值反序列化为指定泛型类型。
     *
     * @param source Smile 多消息流二进制内容
     * @param targetType 每个根值的目标泛型类型引用
     * @param maxItems 最大消息数量；0 表示不限制
     * @return 按出现顺序反序列化得到的实体列表
     */
    public fun <T> parseAll(
        source: ByteArray,
        targetType: DocumentTypeReference<T>,
        maxItems: Int = 0,
    ): List<T> = parseAllToNodes(source, maxItems).map { fromNode(it, targetType) }

    /**
     * 把多个 Java 实体按顺序写成 Smile 多消息流字节。
     *
     * 空 [Iterable] 返回空字节数组。通过 [writeTo] 驱动 [SmileTokenWriter]
     * 直接写 Token 流（不构建 Node 树），Smile 头部只写一次，各根值首尾相接。
     *
     * @param values 要写出的实体、Map、集合或文档树，按迭代顺序输出
     * @return Smile 多消息流二进制内容；空集合返回空字节数组
     * @throws com.mingliqiye.document.format.smile.exception.SmileException 写出失败时抛出
     */
    public fun formatAll(values: Iterable<Any?>): ByteArray {
        val iterator = values.iterator()
        if (!iterator.hasNext()) {
            return ByteArray(0)
        }
        val writer = SmileTokenWriter()
        return try {
            while (iterator.hasNext()) {
                writeTo(iterator.next(), writer)
            }
            writer.toByteArray()
        } finally {
            writer.close()
        }
    }

    public companion object {

        /**
         * 创建 Smile Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): SmileMapperBuilder = SmileMapperBuilder()
    }
}
