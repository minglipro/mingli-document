package com.mingliqiye.document.format.ion

import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * Ion 文档映射器。
 *
 * 继承通用 [DocumentMapper]，同时内置文本 Ion 与二进制 Ion 编解码能力：
 * - 文本：`parse(String, ...)` / `format` / `formatPretty`
 * - 二进制读：`parse(ByteArray, ...)`；Bean 反序列化优先通过 [IonTokenReader]
 *   Token 直读，无需构建完整 Node 树，Node 路径保留为兜底
 * - 二进制写：`formatBytes`；通过 [IonBinaryTokenWriter] Token 直写，
 *   同时保留 [IonBinaryWriter] 的 Node 写出路径
 *
 * 两种形态共用同一套 Node 映射规则；[com.mingliqiye.document.core.BinaryNode]
 * 在文本中输出为 Ion blob、在二进制中输出为原生 blob。
 */
public open class IonMapper internal constructor(
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "ion",
    { source -> IonReader(source).read() },
    { node, pretty -> IonWriter(pretty).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
    { source -> IonBinaryReader(source).read() },
    { node, _ -> IonBinaryWriter().write(node) },
) {

    /**
     * 创建默认 Ion 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用通用字段配置创建 Ion 映射器。
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
     * 使用 Token 直写路径把实体写成二进制 Ion 字节，不构建完整 Node 树。
     *
     * @param value Java 实体、集合、Map、Node 或标量
     * @return 二进制 Ion 内容
     */
    override fun formatBytes(value: Any?): ByteArray {
        val writer = IonBinaryTokenWriter()
        writeTo(value, writer)
        return writer.toByteArray()
    }

    /**
     * 创建 Ion Token 读取器：字节解析 Bean 时优先走 Token 直读，避免先构建 Node 树。
     *
     * @param source 二进制（或文本）Ion 内容
     * @return Ion Token 读取器
     */
    protected override fun createTokenReader(source: ByteArray): DocumentTokenReader = IonTokenReader(source)

    public companion object {

        /**
         * 创建 Ion Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): IonMapperBuilder = IonMapperBuilder()
    }
}
