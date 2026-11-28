package com.mingliqiye.document.format.protobuf

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentMapperConfigBuilder
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import java.nio.charset.Charset

/**
 * [ProtobufConfig] 构建器。
 *
 * 除 Protobuf 专属选项（未知字段策略、解析深度、extension 注册表、上游实现 对齐的读取/写出特性）
 * 外，还转发 [DocumentMapperConfigBuilder] 的全部公共便捷方法（命名策略、字符集、Node 限制、
 * `$ref`、多态子类型、`DeserializationFeature` 等），与其它格式模块的配置构建器保持一致；
 * 需要直接复用完整通用配置时使用 [base]。
 *
 * `base(DocumentMapperConfig)` 与其它通用配置便捷方法互斥，二者只能选其一。
 */
public class ProtobufConfigBuilder internal constructor() {

    private var baseConfig: DocumentMapperConfig? = null
    private val baseBuilder: DocumentMapperConfigBuilder = DocumentMapperConfig.builder()
    private var unknownFieldPolicy: ProtobufUnknownFieldPolicy = ProtobufUnknownFieldPolicy.FAIL
    private var maxDepth: Int = ProtobufConfig.DEFAULT_MAX_DEPTH
    private var extensions: ProtobufExtensionRegistry = ProtobufExtensionRegistry.EMPTY
    private val readFeatures: MutableSet<ProtobufReadFeature> = ProtobufConfig.DEFAULT_READ_FEATURES.toMutableSet()
    private val writeFeatures: MutableSet<ProtobufWriteFeature> = ProtobufConfig.DEFAULT_WRITE_FEATURES.toMutableSet()

    /**
     * 复用通用 Mapper 配置（字段转换器、命名策略、Node 限制等）。
     *
     * 与其它通用配置便捷方法（如 [field]、[writeNulls]）互斥，二者只能选其一。
     *
     * @param config 通用 Mapper 配置
     * @return 当前构建器
     */
    public fun base(config: DocumentMapperConfig): ProtobufConfigBuilder {
        baseConfig = config
        return this
    }

    /**
     * 设置未知字段策略。
     *
     * @param policy 未知字段策略，默认 [ProtobufUnknownFieldPolicy.FAIL]
     * @return 当前构建器
     */
    public fun unknownFieldPolicy(policy: ProtobufUnknownFieldPolicy): ProtobufConfigBuilder {
        unknownFieldPolicy = policy
        return this
    }

    /**
     * 设置解析最大嵌套深度，`0` 表示不限制。
     *
     * @param maxDepth 最大嵌套深度，不能为负
     * @return 当前构建器
     * @throws IllegalArgumentException `maxDepth` 小于 0 时抛出
     */
    public fun maxDepth(maxDepth: Int): ProtobufConfigBuilder {
        require(maxDepth >= 0) { "maxDepth must be >= 0, but was $maxDepth" }
        this.maxDepth = maxDepth
        return this
    }

    /**
     * 设置 extension 注册表。
     *
     * @param extensions extension 注册表；必须与 Mapper 的 Descriptor 来自同一 Descriptor 树
     * @return 当前构建器
     */
    public fun extensions(extensions: ProtobufExtensionRegistry): ProtobufConfigBuilder {
        this.extensions = extensions
        return this
    }

    /**
     * 整体设置读取特性集合。
     *
     * @param features 读取特性集合，默认 [ProtobufConfig.DEFAULT_READ_FEATURES]
     * @return 当前构建器
     */
    public fun readFeatures(features: Set<ProtobufReadFeature>): ProtobufConfigBuilder {
        readFeatures.clear()
        readFeatures.addAll(features)
        return this
    }

    /**
     * 整体设置写出特性集合。
     *
     * @param features 写出特性集合，默认 [ProtobufConfig.DEFAULT_WRITE_FEATURES]
     * @return 当前构建器
     */
    public fun writeFeatures(features: Set<ProtobufWriteFeature>): ProtobufConfigBuilder {
        writeFeatures.clear()
        writeFeatures.addAll(features)
        return this
    }

    /**
     * 开启读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: ProtobufReadFeature): ProtobufConfigBuilder {
        readFeatures.add(feature)
        return this
    }

    /**
     * 关闭读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: ProtobufReadFeature): ProtobufConfigBuilder {
        readFeatures.remove(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭读取特性。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: ProtobufReadFeature, enabled: Boolean): ProtobufConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: ProtobufWriteFeature): ProtobufConfigBuilder {
        writeFeatures.add(feature)
        return this
    }

    /**
     * 关闭写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: ProtobufWriteFeature): ProtobufConfigBuilder {
        writeFeatures.remove(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭写出特性。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: ProtobufWriteFeature, enabled: Boolean): ProtobufConfigBuilder = if (enabled) enable(feature) else disable(feature)

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
    ): ProtobufConfigBuilder {
        genericBuilder().field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     *
     * @param policy 缺失字段策略
     * @return 当前构建器
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): ProtobufConfigBuilder {
        genericBuilder().missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知 Bean 字段时是否抛出异常。
     *
     * @param fail 是否失败
     * @return 当前构建器
     */
    public fun failOnUnknownFields(fail: Boolean): ProtobufConfigBuilder {
        genericBuilder().failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     *
     * @param write 是否写出 null 字段
     * @return 当前构建器
     */
    public fun writeNulls(write: Boolean): ProtobufConfigBuilder {
        genericBuilder().writeNulls(write)
        return this
    }

    /**
     * 设置未标注 `@DocumentName` 的字段命名策略。
     *
     * @param strategy 字段命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): ProtobufConfigBuilder {
        genericBuilder().namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写字符集。
     *
     * @param charset 字符集
     * @return 当前构建器
     */
    public fun fileCharset(charset: Charset): ProtobufConfigBuilder {
        genericBuilder().fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否原子替换。
     *
     * @param atomic 是否原子写入
     * @return 当前构建器
     */
    public fun atomicFileWrite(atomic: Boolean): ProtobufConfigBuilder {
        genericBuilder().atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     *
     * @param create 是否创建父目录
     * @return 当前构建器
     */
    public fun createParentDirectories(create: Boolean): ProtobufConfigBuilder {
        genericBuilder().createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     *
     * @param max 最大字节数
     * @return 当前构建器
     */
    public fun maxInputSize(max: Long): ProtobufConfigBuilder {
        genericBuilder().maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树中单个字符串最大长度，0 表示不限制。
     *
     * @param max 最大长度
     * @return 当前构建器
     */
    public fun maxStringLength(max: Int): ProtobufConfigBuilder {
        genericBuilder().maxStringLength(max)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制。
     *
     * @param max 最大深度
     * @return 当前构建器
     */
    public fun maxNodeDepth(max: Int): ProtobufConfigBuilder {
        genericBuilder().maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     *
     * @param max 最大节点数
     * @return 当前构建器
     */
    public fun maxNodeCount(max: Long): ProtobufConfigBuilder {
        genericBuilder().maxNodeCount(max)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 `$ref`。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): ProtobufConfigBuilder {
        genericBuilder().resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否输出对象身份 `$ref`。
     *
     * @param write 是否输出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): ProtobufConfigBuilder {
        genericBuilder().writeReferences(write)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器。
     *
     * @param resolver 远程引用解析器；`null` 表示禁用远程引用
     * @return 当前构建器
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): ProtobufConfigBuilder {
        genericBuilder().remoteReferenceResolver(resolver)
        return this
    }

    /**
     * 设置根对象 Bean 校验器。
     *
     * @param validator 根对象校验器；`null` 表示不校验
     * @return 当前构建器
     */
    public fun beanValidator(validator: DocumentBeanValidator?): ProtobufConfigBuilder {
        genericBuilder().beanValidator(validator)
        return this
    }

    /**
     * 注册多态子类型。
     *
     * @param baseClass 多态基类/接口
     * @param typeName 反序列化可识别的类型名
     * @param subtypeClass 具体子类
     * @return 当前构建器
     */
    public fun subtype(
        baseClass: Class<*>,
        typeName: String,
        subtypeClass: Class<*>,
    ): ProtobufConfigBuilder {
        genericBuilder().subtype(baseClass, typeName, subtypeClass)
        return this
    }

    /**
     * 注册多态子类型并覆盖类型属性名。
     *
     * @param baseClass 多态基类/接口
     * @param typeName 反序列化可识别的类型名
     * @param subtypeClass 具体子类
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun subtype(
        baseClass: Class<*>,
        typeName: String,
        subtypeClass: Class<*>,
        typeProperty: String,
    ): ProtobufConfigBuilder {
        genericBuilder().subtype(baseClass, typeName, subtypeClass, typeProperty)
        return this
    }

    /**
     * 覆盖指定多态基类的类型属性名。
     *
     * @param baseClass 多态基类/接口
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): ProtobufConfigBuilder {
        genericBuilder().typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 开启指定反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun enable(feature: DeserializationFeature): ProtobufConfigBuilder {
        genericBuilder().enable(feature)
        return this
    }

    /**
     * 关闭指定反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun disable(feature: DeserializationFeature): ProtobufConfigBuilder {
        genericBuilder().disable(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭指定反序列化特性。
     *
     * @param feature 反序列化特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: DeserializationFeature, enabled: Boolean): ProtobufConfigBuilder {
        genericBuilder().configure(feature, enabled)
        return this
    }

    /**
     * 便捷设置 `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES`。
     *
     * @param fail 是否在 `null` 赋给基本类型时抛出异常
     * @return 当前构建器
     */
    public fun failOnNullForPrimitives(fail: Boolean): ProtobufConfigBuilder {
        genericBuilder().failOnNullForPrimitives(fail)
        return this
    }

    /**
     * 构建 Protobuf 配置。
     *
     * @return Protobuf 配置
     */
    public fun build(): ProtobufConfig = ProtobufConfig(
        baseConfig ?: baseBuilder.build(),
        unknownFieldPolicy,
        maxDepth,
        extensions,
        readFeatures.toSet(),
        writeFeatures.toSet(),
    )

    private fun genericBuilder(): DocumentMapperConfigBuilder {
        check(baseConfig == null) {
            "base(config) 不能与其它通用配置便捷方法同时使用"
        }
        return baseBuilder
    }
}
