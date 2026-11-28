package com.mingliqiye.document.format.ion

import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenWriter
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver
import java.io.OutputStream

/**
 * Ion 文档映射器。
 *
 * 继承通用 [DocumentMapper]，同时内置文本 Ion 与二进制 Ion 编解码能力：
 * - 文本：`parse(String, ...)` / `format` / `formatPretty`
 * - 二进制读：`parse(ByteArray, ...)`；默认 Bean 反序列化优先通过 [IonTokenReader]
 *   Token 直读，无需构建完整 Node 树，Node 路径保留为兜底
 * - 二进制写：`formatBytes`；默认通过 [IonBinaryTokenWriter] Token 直写，
 *   同时保留 [IonBinaryWriter] 的 Node 写出路径
 *
 * 通过 [IonConfig.preserveTypes] 开启类型保真后，Node 路径会把 timestamp /
 * symbol / clob / blob 读成包装对象、写出时反向还原（见 [IonTypeFidelity]）；
 * 同时按 [IonReadFeature.USE_NATIVE_TYPE_ID] / [IonWriteFeature.USE_NATIVE_TYPE_ID]
 * 保留 Ion annotation。类型保真、非默认读取特性需要构建完整 Node 树，因此会禁用
 * Token 直读/直写路径；此外 Bean 中对应字段需按包装对象结构映射（例如
 * timestamp/symbol 不再是普通字符串）。
 *
 * 读/写特性的默认值对齐 Ion 底层库（见 [IonConfig.DEFAULT_READ_FEATURES] /
 * [IonConfig.DEFAULT_WRITE_FEATURES]）。
 *
 * 两种形态共用同一套 Node 映射规则；[com.mingliqiye.document.core.BinaryNode]
 * 在文本中输出为 Ion blob、在二进制中输出为原生 blob。
 */
public open class IonMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "ion",
    { source ->
        IonReader(source, config.maxNodeDepth, config.ionPreserveTypes, config.ionReadFeatures).read()
    },
    { node, pretty -> IonWriter(pretty, config.ionPreserveTypes, config.ionWriteFeatures).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
    { source ->
        IonBinaryReader(source, config.maxNodeDepth, config.ionPreserveTypes, config.ionReadFeatures).read()
    },
    { node, _ -> IonBinaryWriter(config.ionPreserveTypes, config.ionWriteFeatures).write(node) },
) {

    private val preserveTypes: Boolean = config.ionPreserveTypes
    private val readFeatures: Set<IonReadFeature> = config.ionReadFeatures
    private val writeFeatures: Set<IonWriteFeature> = config.ionWriteFeatures
    private val mapperConfig: DocumentMapperConfig = config

    /**
     * 创建默认 Ion 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用 Ion 配置创建映射器。
     *
     * @param config Ion 配置
     */
    public constructor(config: IonConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 使用通用字段配置创建映射器。
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
     * 开启类型保真（[IonConfig.preserveTypes]）后改为 Node 写出路径，以便识别
     * [IonTypeFidelity] 包装对象；默认行为保持不变。写出特性
     * （例如 [IonWriteFeature.STRICT_DUPLICATE_DETECTION]、
     * [IonWriteFeature.USE_NATIVE_TYPE_ID] 的注解包装对象）由 Token 写出器处理。
     *
     * @param value Java 实体、集合、Map、Node 或标量
     * @return 二进制 Ion 内容
     */
    override fun formatBytes(value: Any?): ByteArray {
        if (preserveTypes) {
            return super.formatBytes(value)
        }
        val writer = IonBinaryTokenWriter(writeFeatures = writeFeatures)
        writeTo(value, writer)
        return writer.toByteArray()
    }

    /**
     * 创建 Ion Token 读取器：字节解析 Bean 时优先走 Token 直读，避免先构建 Node 树。
     *
     * 开启类型保真或使用非默认读取特性（例如
     * [IonReadFeature.STRICT_DUPLICATE_DETECTION]、关闭
     * [IonReadFeature.READ_NULL_AS_IONVALUE]）时返回 `null`，让二进制解析回退到
     * Node 路径以应用这些语义。
     *
     * @param source 二进制（或文本）Ion 内容
     * @return Ion Token 读取器；需要 Node 路径语义时为 `null`
     */
    protected override fun createTokenReader(source: ByteArray): DocumentTokenReader? = if (
        preserveTypes ||
        readFeatures != IonConfig.DEFAULT_READ_FEATURES
    ) {
        null
    } else {
        IonTokenReader(source)
    }

    override fun createStreamingTokenWriter(output: OutputStream, pretty: Boolean): DocumentTokenWriter = IonTextTokenWriter(output, pretty, mapperConfig.fileCharset, writeFeatures)

    public companion object {

        /**
         * 创建 Ion Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): IonMapperBuilder = IonMapperBuilder()
    }
}
