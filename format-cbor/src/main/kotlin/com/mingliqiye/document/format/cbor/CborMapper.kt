package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * CBOR 文档映射器。
 *
 * 继承 [BinaryDocumentMapper]，通过 `parse(ByteArray, Class)` / `formatBytes` 以
 * `byte[]` 读写 [com.mingliqiye.document.core.Node]，
 * 文本 API（`parse(String)` / `format`）会抛出不支持异常。
 *
 * `parse(ByteArray, Class)` 会优先用 [createTokenReader] 返回的 [CborTokenReader]
 * 流式直读 Bean，不构建完整 Node 树；Token 路径不可用时回退到
 * [CborReader] 构建 Node 再映射。写出仍由 [CborWriter] 完成。
 *
 * CBOR 的 byte string 映射为 [com.mingliqiye.document.core.BinaryNode]/BINARY Token，
 * 因此 Java 的 `byte[]` 属性可无损往返。
 */
public open class CborMapper internal constructor(
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : BinaryDocumentMapper(
    "cbor",
    { source -> CborReader(source).read() },
    { node, _ -> CborWriter().write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    /**
     * 创建默认 CBOR 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用通用字段配置创建 CBOR 映射器。
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
     * 创建 CBOR Token 直读器。
     *
     * 用于从字节流直接解码 Bean；解析失败抛
     * [com.mingliqiye.document.format.cbor.exception.CborParseException]。
     *
     * @param source CBOR 二进制内容
     * @return Token 读取器
     */
    override fun createTokenReader(source: ByteArray): DocumentTokenReader = CborTokenReader(source)

    public companion object {

        /**
         * 创建 CBOR Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): CborMapperBuilder = CborMapperBuilder()
    }
}
