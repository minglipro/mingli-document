package com.mingliqiye.document.format.smile

import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
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

    public companion object {

        /**
         * 创建 Smile Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): SmileMapperBuilder = SmileMapperBuilder()
    }
}
