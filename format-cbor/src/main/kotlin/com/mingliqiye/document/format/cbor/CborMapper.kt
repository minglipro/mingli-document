package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
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
 * [CborReader] 构建 Node 再映射。
 *
 * `formatBytes` 同样优先用 [CborTokenWriter] Token 直写，不构建完整 Node 树；
 * [CborWriter] 的 Node 写出能力仍然保留（例如 `Node.toCborBytes()`）。
 *
 * CBOR 的 byte string 映射为 [com.mingliqiye.document.core.BinaryNode]/BINARY Token，
 * 因此 Java 的 `byte[]` 属性可无损往返。
 */
public open class CborMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : BinaryDocumentMapper(
    "cbor",
    { source -> CborReader(source, config.maxNodeDepth).read() },
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

    /**
     * 将 Java 实体写成 CBOR 字节。
     *
     * 直接通过 [writeTo] 驱动 [CborTokenWriter]，不构建完整 Node 树；数字与二进制
     * 处理策略与 [CborWriter] 一致。注意 Token 直写的对象/数组为 CBOR 不定长形式，
     * 与 [CborWriter] 的定长写法不同但合法且可读回。
     *
     * @param value Java 实体、集合、Map 或 [com.mingliqiye.document.core.Node]
     * @return CBOR 二进制内容
     * @throws com.mingliqiye.document.format.cbor.exception.CborException 写出失败或
     *   数字 raw 非法时抛出
     */
    override fun formatBytes(value: Any?): ByteArray {
        val writer = CborTokenWriter()
        return try {
            writeTo(value, writer)
            writer.toByteArray()
        } finally {
            writer.close()
        }
    }

    /**
     * 将 Java 实体写成 canonical CBOR 字节。
     *
     * 与 [formatBytes] 不同，本方法先构建 Node 树，再按 canonical map key 顺序写出；
     * 适合需要稳定字节输出的签名、缓存键等场景。
     *
     * @param value Java 实体、集合、Map 或 Node
     * @return CBOR 二进制内容
     * @throws com.mingliqiye.document.format.cbor.exception.CborException 写出失败时抛出
     */
    public fun formatCanonicalBytes(value: Any?): ByteArray = CborWriter(canonical = true).write(toNode(value))

    public companion object {

        /**
         * 创建 CBOR Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): CborMapperBuilder = CborMapperBuilder()
    }
}
