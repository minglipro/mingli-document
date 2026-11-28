package com.mingliqiye.document.format.ion

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import java.nio.charset.Charset

/**
 * [IonConfig] 构建器。
 *
 * 通用选项直接委托 [DocumentMapperConfig] 构建器，Ion 专属选项单独保存。
 * 读/写特性默认值对齐 Ion 底层库，可用 [enable] / [disable] /
 * [configure] 调整。
 */
public class IonConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var preserveTypes = false
    private val readFeatures = IonConfig.DEFAULT_READ_FEATURES.toMutableSet()
    private val writeFeatures = IonConfig.DEFAULT_WRITE_FEATURES.toMutableSet()

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
    ): IonConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): IonConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     */
    public fun failOnUnknownFields(fail: Boolean): IonConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     */
    public fun writeNulls(write: Boolean): IonConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * 设置字段命名策略。
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): IonConfigBuilder {
        base.namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写字符集。
     */
    public fun fileCharset(charset: Charset): IonConfigBuilder {
        base.fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否原子替换。
     */
    public fun atomicFileWrite(atomic: Boolean): IonConfigBuilder {
        base.atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     */
    public fun createParentDirectories(create: Boolean): IonConfigBuilder {
        base.createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     */
    public fun maxInputSize(max: Long): IonConfigBuilder {
        base.maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制。
     */
    public fun maxNodeDepth(max: Int): IonConfigBuilder {
        base.maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     */
    public fun maxNodeCount(max: Long): IonConfigBuilder {
        base.maxNodeCount(max)
        return this
    }

    /**
     * 设置 Node 树中单个字符串最大长度，0 表示不限制。
     *
     * @param max 最大长度
     * @return 当前构建器
     */
    public fun maxStringLength(max: Int): IonConfigBuilder {
        base.maxStringLength(max)
        return this
    }

    /**
     * 设置根对象 Bean 校验器；`null` 表示不校验。
     *
     * @param validator 校验器
     * @return 当前构建器
     */
    public fun beanValidator(validator: DocumentBeanValidator?): IonConfigBuilder {
        base.beanValidator(validator)
        return this
    }

    /**
     * 开启通用反序列化特性（[DeserializationFeature]）。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun enable(feature: DeserializationFeature): IonConfigBuilder {
        base.enable(feature)
        return this
    }

    /**
     * 关闭通用反序列化特性（[DeserializationFeature]）。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun disable(feature: DeserializationFeature): IonConfigBuilder {
        base.disable(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭通用反序列化特性（[DeserializationFeature]）。
     *
     * @param feature 反序列化特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: DeserializationFeature, enabled: Boolean): IonConfigBuilder {
        base.configure(feature, enabled)
        return this
    }

    /**
     * 便捷设置 [DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES]。
     *
     * @param fail 是否在 `null` 赋给基本类型时抛出异常
     * @return 当前构建器
     */
    public fun failOnNullForPrimitives(fail: Boolean): IonConfigBuilder {
        base.failOnNullForPrimitives(fail)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 `$ref`。
     */
    public fun resolveReferences(resolve: Boolean): IonConfigBuilder {
        base.resolveReferences(resolve)
        return this
    }

    /**
     * 设置 [com.mingliqiye.document.serialization.DocumentMapper.toNode]
     * 序列化时是否按对象身份写出本地 `$ref`。
     */
    public fun writeReferences(write: Boolean): IonConfigBuilder {
        base.writeReferences(write)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器；`null` 表示禁用远程引用。
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): IonConfigBuilder {
        base.remoteReferenceResolver(resolver)
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>): IonConfigBuilder {
        base.subtype(baseClass, typeName, subtypeClass)
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>, typeProperty: String): IonConfigBuilder {
        base.subtype(baseClass, typeName, subtypeClass, typeProperty)
        return this
    }

    /**
     * 覆盖指定多态基类的类型属性名。
     *
     * @param baseClass 基类/接口
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): IonConfigBuilder {
        base.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 设置是否保留 Ion 专属类型（timestamp / symbol / clob / blob）。
     *
     * @param preserve 是否保留类型，默认 `false`
     * @return 当前构建器
     */
    public fun preserveTypes(preserve: Boolean): IonConfigBuilder {
        preserveTypes = preserve
        return this
    }

    /**
     * 开启 Ion 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: IonReadFeature): IonConfigBuilder {
        readFeatures.add(feature)
        return this
    }

    /**
     * 关闭 Ion 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: IonReadFeature): IonConfigBuilder {
        readFeatures.remove(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭 Ion 读取特性。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: IonReadFeature, enabled: Boolean): IonConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启 Ion 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: IonWriteFeature): IonConfigBuilder {
        writeFeatures.add(feature)
        return this
    }

    /**
     * 关闭 Ion 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: IonWriteFeature): IonConfigBuilder {
        writeFeatures.remove(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭 Ion 写出特性。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: IonWriteFeature, enabled: Boolean): IonConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 构建配置。
     */
    public fun build(): IonConfig = IonConfig(base.build(), preserveTypes, readFeatures.toSet(), writeFeatures.toSet())
}
