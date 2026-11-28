package com.mingliqiye.document.format.avro

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentMapperConfigBuilder
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import org.apache.avro.Schema
import java.nio.charset.Charset

/**
 * [AvroConfig] 构建器。
 *
 * 除 Avro 专属选项（schema / readerSchema / codec / 上游实现 Avro 读写出特性）外，
 * 转发全部通用 [DocumentMapperConfig] 选项，因此始终使用本构建器也不会丢失
 * `namingStrategy`、`fileCharset`、`maxInputSize` 等配置。
 */
public class AvroConfigBuilder internal constructor() {

    private val baseBuilder: DocumentMapperConfigBuilder = DocumentMapperConfig.builder()
    private var schema: Schema? = null
    private var schemaView: AvroSchema? = null
    private var readerSchema: Schema? = null
    private var readerSchemaView: AvroSchema? = null
    private var codec: AvroCodec = AvroCodec.NULL
    private var readFeatures: Set<AvroReadFeature> = AvroConfig.DEFAULT_READ_FEATURES
    private var writeFeatures: Set<AvroWriteFeature> = AvroConfig.DEFAULT_WRITE_FEATURES

    /**
     * 设置 writer Schema。
     *
     * @param schema Apache Avro record Schema
     * @return 当前构建器
     */
    public fun schema(schema: Schema): AvroConfigBuilder {
        this.schema = schema
        this.schemaView = null
        return this
    }

    /**
     * 设置 writer Schema（以 [AvroSchema] 视图表示）。
     *
     * 在 [build] 时按 [AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA] 转换为
     * Apache Avro Schema。
     *
     * @param schema Avro Schema 视图
     * @return 当前构建器
     */
    public fun schema(schema: AvroSchema): AvroConfigBuilder {
        this.schemaView = schema
        this.schema = null
        return this
    }

    /**
     * 设置 reader Schema（Schema 演进）。
     *
     * @param readerSchema Apache Avro record Schema
     * @return 当前构建器
     */
    public fun readerSchema(readerSchema: Schema): AvroConfigBuilder {
        this.readerSchema = readerSchema
        this.readerSchemaView = null
        return this
    }

    /**
     * 设置 reader Schema（以 [AvroSchema] 视图表示）。
     *
     * @param readerSchema Avro Schema 视图
     * @return 当前构建器
     */
    public fun readerSchema(readerSchema: AvroSchema): AvroConfigBuilder {
        this.readerSchemaView = readerSchema
        this.readerSchema = null
        return this
    }

    /**
     * 设置 OCF 容器默认 codec。
     *
     * @param codec 容器 codec，默认 [AvroCodec.NULL]
     * @return 当前构建器
     */
    public fun codec(codec: AvroCodec): AvroConfigBuilder {
        this.codec = codec
        return this
    }

    /**
     * 整体设置 Avro 读取特性集合。
     *
     * @param features 读取特性集合，默认 [AvroConfig.DEFAULT_READ_FEATURES]
     * @return 当前构建器
     */
    public fun readFeatures(features: Set<AvroReadFeature>): AvroConfigBuilder {
        readFeatures = features
        return this
    }

    /**
     * 整体设置 Avro 写出特性集合。
     *
     * @param features 写出特性集合，默认 [AvroConfig.DEFAULT_WRITE_FEATURES]
     * @return 当前构建器
     */
    public fun writeFeatures(features: Set<AvroWriteFeature>): AvroConfigBuilder {
        writeFeatures = features
        return this
    }

    /**
     * 开启一个 Avro 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: AvroReadFeature): AvroConfigBuilder {
        readFeatures = readFeatures + feature
        return this
    }

    /**
     * 关闭一个 Avro 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: AvroReadFeature): AvroConfigBuilder {
        readFeatures = readFeatures - feature
        return this
    }

    /**
     * 按开关设置一个 Avro 读取特性。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: AvroReadFeature, enabled: Boolean): AvroConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启一个 Avro 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: AvroWriteFeature): AvroConfigBuilder {
        writeFeatures = writeFeatures + feature
        return this
    }

    /**
     * 关闭一个 Avro 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: AvroWriteFeature): AvroConfigBuilder {
        writeFeatures = writeFeatures - feature
        return this
    }

    /**
     * 按开关设置一个 Avro 写出特性。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: AvroWriteFeature, enabled: Boolean): AvroConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 为指定字段绑定转换器。
     *
     * @param declaringClass 声明类
     * @param name 属性名
     * @param converter 字段转换器
     * @return 当前构建器
     */
    public fun field(
        declaringClass: Class<*>,
        name: String,
        converter: DocumentBaseConverter<*>,
    ): AvroConfigBuilder {
        baseBuilder.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     *
     * @param policy 缺失字段策略
     * @return 当前构建器
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): AvroConfigBuilder {
        baseBuilder.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     *
     * @param fail 是否失败
     * @return 当前构建器
     */
    public fun failOnUnknownFields(fail: Boolean): AvroConfigBuilder {
        baseBuilder.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     *
     * @param write 是否写出 null 字段
     * @return 当前构建器
     */
    public fun writeNulls(write: Boolean): AvroConfigBuilder {
        baseBuilder.writeNulls(write)
        return this
    }

    /**
     * 设置未标注 `@DocumentName` 的字段使用的命名策略。
     *
     * @param strategy 字段命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): AvroConfigBuilder {
        baseBuilder.namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写字符集（二进制格式的文本入口不使用，保留通用配置语义）。
     *
     * @param charset 字符集
     * @return 当前构建器
     */
    public fun fileCharset(charset: Charset): AvroConfigBuilder {
        baseBuilder.fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否使用临时文件 + 原子替换。
     *
     * @param atomic 是否原子写入
     * @return 当前构建器
     */
    public fun atomicFileWrite(atomic: Boolean): AvroConfigBuilder {
        baseBuilder.atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     *
     * @param create 是否创建父目录
     * @return 当前构建器
     */
    public fun createParentDirectories(create: Boolean): AvroConfigBuilder {
        baseBuilder.createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     *
     * @param max 最大字节数
     * @return 当前构建器
     * @throws IllegalArgumentException 参数为负数时抛出
     */
    public fun maxInputSize(max: Long): AvroConfigBuilder {
        baseBuilder.maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树中单个字符串最大长度，0 表示不限制。
     *
     * @param max 最大长度
     * @return 当前构建器
     * @throws IllegalArgumentException 参数为负数时抛出
     */
    public fun maxStringLength(max: Int): AvroConfigBuilder {
        baseBuilder.maxStringLength(max)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制。
     *
     * @param max 最大深度
     * @return 当前构建器
     * @throws IllegalArgumentException 参数为负数时抛出
     */
    public fun maxNodeDepth(max: Int): AvroConfigBuilder {
        baseBuilder.maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     *
     * @param max 最大节点数
     * @return 当前构建器
     * @throws IllegalArgumentException 参数为负数时抛出
     */
    public fun maxNodeCount(max: Long): AvroConfigBuilder {
        baseBuilder.maxNodeCount(max)
        return this
    }

    /**
     * 注册多态子类型。
     *
     * @param baseClass 基类/接口
     * @param typeName 类型名
     * @param subtypeClass 子类型
     * @return 当前构建器
     */
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>): AvroConfigBuilder {
        baseBuilder.subtype(baseClass, typeName, subtypeClass)
        return this
    }

    /**
     * 注册多态子类型并覆盖类型属性名。
     *
     * @param baseClass 基类/接口
     * @param typeName 类型名
     * @param subtypeClass 子类型
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun subtype(
        baseClass: Class<*>,
        typeName: String,
        subtypeClass: Class<*>,
        typeProperty: String,
    ): AvroConfigBuilder {
        baseBuilder.subtype(baseClass, typeName, subtypeClass, typeProperty)
        return this
    }

    /**
     * 覆盖指定多态基类的类型属性名。
     *
     * @param baseClass 基类/接口
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): AvroConfigBuilder {
        baseBuilder.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 `$ref`。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): AvroConfigBuilder {
        baseBuilder.resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否输出对象身份 `$ref`。
     *
     * @param write 是否输出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): AvroConfigBuilder {
        baseBuilder.writeReferences(write)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器。
     *
     * @param resolver 解析器；`null` 表示禁用远程引用
     * @return 当前构建器
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): AvroConfigBuilder {
        baseBuilder.remoteReferenceResolver(resolver)
        return this
    }

    /**
     * 设置根对象 Bean 校验器。
     *
     * @param validator 校验器；`null` 表示不校验
     * @return 当前构建器
     */
    public fun beanValidator(validator: DocumentBeanValidator?): AvroConfigBuilder {
        baseBuilder.beanValidator(validator)
        return this
    }

    /**
     * 开启通用反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun enable(feature: DeserializationFeature): AvroConfigBuilder {
        baseBuilder.enable(feature)
        return this
    }

    /**
     * 关闭通用反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun disable(feature: DeserializationFeature): AvroConfigBuilder {
        baseBuilder.disable(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭通用反序列化特性。
     *
     * @param feature 反序列化特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: DeserializationFeature, enabled: Boolean): AvroConfigBuilder {
        baseBuilder.configure(feature, enabled)
        return this
    }

    /**
     * 便捷设置 `FAIL_ON_NULL_FOR_PRIMITIVES`。
     *
     * @param fail 是否在 `null` 赋给基本类型时抛出异常
     * @return 当前构建器
     */
    public fun failOnNullForPrimitives(fail: Boolean): AvroConfigBuilder {
        baseBuilder.failOnNullForPrimitives(fail)
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): AvroConfig {
        val addNullAsDefaultValue = AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA in writeFeatures
        return AvroConfig(
            baseBuilder.build(),
            schema ?: schemaView?.toAvroSchema(addNullAsDefaultValue),
            readerSchema ?: readerSchemaView?.toAvroSchema(addNullAsDefaultValue),
            codec,
            readFeatures,
            writeFeatures,
        )
    }
}
