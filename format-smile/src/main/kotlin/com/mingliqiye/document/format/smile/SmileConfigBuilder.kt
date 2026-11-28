package com.mingliqiye.document.format.smile

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import java.nio.charset.Charset

/**
 * [SmileConfig] 构建器。
 *
 * 通用选项（字段转换器、缺字段策略、命名策略、深度/大小限制、多态子类型、`$ref` 等）
 * 直接委托 [DocumentMapperConfig] 构建器；Smile 专属读写特性通过
 * [enable] / [disable] / [configure] 配置，名称与 上游实现 保持一致，默认值对齐
 * Smile 底层库。
 */
public class SmileConfigBuilder internal constructor() {

    private val base = DocumentMapperConfig.builder()
    private val readFeatures = SmileConfig.DEFAULT_READ_FEATURES.toMutableSet()
    private val writeFeatures = SmileConfig.DEFAULT_WRITE_FEATURES.toMutableSet()

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
    ): SmileConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     *
     * @param policy 缺失字段策略
     * @return 当前构建器
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): SmileConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     *
     * @param fail 是否抛出异常
     * @return 当前构建器
     */
    public fun failOnUnknownFields(fail: Boolean): SmileConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     *
     * @param write 是否写出 null 字段
     * @return 当前构建器
     */
    public fun writeNulls(write: Boolean): SmileConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * 设置未标注 `@DocumentName` 时使用的字段命名策略。
     *
     * @param strategy 字段命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): SmileConfigBuilder {
        base.namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写字符集。
     *
     * Smile 为二进制格式，该选项不参与字节编解码，仅保持与通用配置一致。
     *
     * @param charset 字符集
     * @return 当前构建器
     */
    public fun fileCharset(charset: Charset): SmileConfigBuilder {
        base.fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否使用临时文件 + 原子替换。
     *
     * @param atomic 是否原子写入
     * @return 当前构建器
     */
    public fun atomicFileWrite(atomic: Boolean): SmileConfigBuilder {
        base.atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     *
     * @param create 是否创建父目录
     * @return 当前构建器
     */
    public fun createParentDirectories(create: Boolean): SmileConfigBuilder {
        base.createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     *
     * @param max 最大字节数
     * @return 当前构建器
     */
    public fun maxInputSize(max: Long): SmileConfigBuilder {
        base.maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树中单个字符串最大长度，0 表示不限制。
     *
     * @param max 最大长度
     * @return 当前构建器
     */
    public fun maxStringLength(max: Int): SmileConfigBuilder {
        base.maxStringLength(max)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制。
     *
     * @param max 最大深度
     * @return 当前构建器
     */
    public fun maxNodeDepth(max: Int): SmileConfigBuilder {
        base.maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     *
     * @param max 最大节点数
     * @return 当前构建器
     */
    public fun maxNodeCount(max: Long): SmileConfigBuilder {
        base.maxNodeCount(max)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 `$ref`。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): SmileConfigBuilder {
        base.resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否输出对象身份 `$ref`。
     *
     * @param write 是否输出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): SmileConfigBuilder {
        base.writeReferences(write)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器。
     *
     * @param resolver 远程引用解析器；`null` 表示禁用远程引用
     * @return 当前构建器
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): SmileConfigBuilder {
        base.remoteReferenceResolver(resolver)
        return this
    }

    /**
     * 设置根对象 Bean 校验器。
     *
     * @param validator 校验器；`null` 表示不校验
     * @return 当前构建器
     */
    public fun beanValidator(validator: DocumentBeanValidator?): SmileConfigBuilder {
        base.beanValidator(validator)
        return this
    }

    /**
     * 开启指定反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun enable(feature: DeserializationFeature): SmileConfigBuilder {
        base.enable(feature)
        return this
    }

    /**
     * 关闭指定反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun disable(feature: DeserializationFeature): SmileConfigBuilder {
        base.disable(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭指定反序列化特性。
     *
     * @param feature 反序列化特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: DeserializationFeature, enabled: Boolean): SmileConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 便捷设置 [DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES]。
     *
     * @param fail 是否在 `null` 赋给基本类型时抛出异常
     * @return 当前构建器
     */
    public fun failOnNullForPrimitives(fail: Boolean): SmileConfigBuilder = configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, fail)

    /**
     * 注册多态子类型。
     *
     * @param baseClass 多态基类/接口
     * @param typeName 类型名
     * @param subtypeClass 具体子类
     * @return 当前构建器
     */
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>): SmileConfigBuilder {
        base.subtype(baseClass, typeName, subtypeClass)
        return this
    }

    /**
     * 注册多态子类型并覆盖类型属性名。
     *
     * @param baseClass 多态基类/接口
     * @param typeName 类型名
     * @param subtypeClass 具体子类
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun subtype(
        baseClass: Class<*>,
        typeName: String,
        subtypeClass: Class<*>,
        typeProperty: String,
    ): SmileConfigBuilder {
        base.subtype(baseClass, typeName, subtypeClass, typeProperty)
        return this
    }

    /**
     * 覆盖指定多态基类的类型属性名。
     *
     * @param baseClass 多态基类/接口
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): SmileConfigBuilder {
        base.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 开启 Smile 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: SmileReadFeature): SmileConfigBuilder {
        readFeatures.add(feature)
        return this
    }

    /**
     * 关闭 Smile 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: SmileReadFeature): SmileConfigBuilder {
        readFeatures.remove(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭 Smile 读取特性。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: SmileReadFeature, enabled: Boolean): SmileConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启 Smile 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: SmileWriteFeature): SmileConfigBuilder {
        writeFeatures.add(feature)
        return this
    }

    /**
     * 关闭 Smile 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: SmileWriteFeature): SmileConfigBuilder {
        writeFeatures.remove(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭 Smile 写出特性。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: SmileWriteFeature, enabled: Boolean): SmileConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 构建配置。
     *
     * 与 上游实现 生成器一致，拒绝会导致数据不一致的写出组合：
     * 关闭 `WRITE_HEADER` 时 `CHECK_SHARED_STRING_VALUES` 必须关闭、
     * `ENCODE_BINARY_AS_7BIT` 必须开启（无头部时解析方无法得知这些设置）。
     *
     * @return Smile 配置
     * @throws IllegalArgumentException 写出特性组合与 上游实现 不一致时抛出
     */
    public fun build(): SmileConfig {
        require(SmileWriteFeature.WRITE_HEADER in writeFeatures || SmileWriteFeature.CHECK_SHARED_STRING_VALUES !in writeFeatures) {
            "Inconsistent settings: WRITE_HEADER disabled, but CHECK_SHARED_STRING_VALUES enabled; " +
                "enable WRITE_HEADER or disable CHECK_SHARED_STRING_VALUES"
        }
        require(SmileWriteFeature.WRITE_HEADER in writeFeatures || SmileWriteFeature.ENCODE_BINARY_AS_7BIT in writeFeatures) {
            "Inconsistent settings: WRITE_HEADER disabled, but ENCODE_BINARY_AS_7BIT disabled; " +
                "enable WRITE_HEADER or enable ENCODE_BINARY_AS_7BIT"
        }
        return SmileConfig(base.build(), readFeatures.toSet(), writeFeatures.toSet())
    }
}
