package com.mingliqiye.document.format.smile

import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

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
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : BinaryDocumentMapper(
    "smile",
    { source -> SmileReader(source).read() },
    { node, _ -> SmileWriter().write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

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

    public companion object {

        /**
         * 创建 Smile Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): SmileMapperBuilder = SmileMapperBuilder()
    }
}
