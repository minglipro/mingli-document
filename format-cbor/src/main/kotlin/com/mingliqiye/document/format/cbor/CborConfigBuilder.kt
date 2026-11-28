package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ByteArrayPool
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentMapperConfigBuilder
import com.mingliqiye.document.serialization.MissingFieldPolicy

/**
 * [CborConfig] 构建器。
 *
 * 除 CBOR 专属选项（tag 保真、输出缓冲池、上游实现 CBOR 读取/写出特性）外，
 * 还提供与 JSON 等格式一致的通用配置便捷方法；需要其它通用选项时可用
 * [base] 直接传入完整的 [DocumentMapperConfig]。
 */
public class CborConfigBuilder internal constructor() {

    private var baseConfig: DocumentMapperConfig? = null
    private val baseBuilder: DocumentMapperConfigBuilder = DocumentMapperConfig.builder()
    private var preserveTags: Boolean = false
    private var bufferPool: ByteArrayPool? = null
    private var readFeatures: Set<CborReadFeature> = CborConfig.DEFAULT_READ_FEATURES
    private var writeFeatures: Set<CborWriteFeature> = CborConfig.DEFAULT_WRITE_FEATURES

    /**
     * 复用通用 Mapper 配置（字段转换器、缺字段策略、深度限制等）。
     *
     * 与其它通用配置便捷方法（如 [field]、[writeNulls]）互斥，二者只能选其一。
     *
     * @param config 通用 Mapper 配置
     * @return 当前构建器
     */
    public fun base(config: DocumentMapperConfig): CborConfigBuilder {
        baseConfig = config
        return this
    }

    /**
     * 是否保留 CBOR tag。
     *
     * @param preserve 是否保留 tag，默认 `false`
     * @return 当前构建器
     */
    public fun preserveTags(preserve: Boolean): CborConfigBuilder {
        this.preserveTags = preserve
        return this
    }

    /**
     * 设置可选的输出缓冲区池。
     *
     * @param pool 缓冲区池；`null` 表示不池化（默认）
     * @return 当前构建器
     */
    public fun bufferPool(pool: ByteArrayPool?): CborConfigBuilder {
        this.bufferPool = pool
        return this
    }

    /**
     * 整体设置 CBOR 读取特性集合。
     *
     * @param features 读取特性集合，默认 [CborConfig.DEFAULT_READ_FEATURES]
     * @return 当前构建器
     */
    public fun readFeatures(features: Set<CborReadFeature>): CborConfigBuilder {
        readFeatures = features
        return this
    }

    /**
     * 整体设置 CBOR 写出特性集合。
     *
     * @param features 写出特性集合，默认 [CborConfig.DEFAULT_WRITE_FEATURES]
     * @return 当前构建器
     */
    public fun writeFeatures(features: Set<CborWriteFeature>): CborConfigBuilder {
        writeFeatures = features
        return this
    }

    /**
     * 开启一个 CBOR 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: CborReadFeature): CborConfigBuilder {
        readFeatures = readFeatures + feature
        return this
    }

    /**
     * 关闭一个 CBOR 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: CborReadFeature): CborConfigBuilder {
        readFeatures = readFeatures - feature
        return this
    }

    /**
     * 按开关设置一个 CBOR 读取特性。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: CborReadFeature, enabled: Boolean): CborConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启一个 CBOR 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: CborWriteFeature): CborConfigBuilder {
        writeFeatures = writeFeatures + feature
        return this
    }

    /**
     * 关闭一个 CBOR 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: CborWriteFeature): CborConfigBuilder {
        writeFeatures = writeFeatures - feature
        return this
    }

    /**
     * 按开关设置一个 CBOR 写出特性。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: CborWriteFeature, enabled: Boolean): CborConfigBuilder = if (enabled) enable(feature) else disable(feature)

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
    ): CborConfigBuilder {
        genericBuilder().field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     *
     * @param policy 缺失字段策略
     * @return 当前构建器
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): CborConfigBuilder {
        genericBuilder().missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     *
     * @param fail 是否失败
     * @return 当前构建器
     */
    public fun failOnUnknownFields(fail: Boolean): CborConfigBuilder {
        genericBuilder().failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     *
     * @param write 是否写出 null 字段
     * @return 当前构建器
     */
    public fun writeNulls(write: Boolean): CborConfigBuilder {
        genericBuilder().writeNulls(write)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制。
     *
     * @param max 最大深度
     * @return 当前构建器
     * @throws IllegalArgumentException `max` 小于 0 时抛出
     */
    public fun maxNodeDepth(max: Int): CborConfigBuilder {
        genericBuilder().maxNodeDepth(max)
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
    public fun subtype(
        baseClass: Class<*>,
        typeName: String,
        subtypeClass: Class<*>,
    ): CborConfigBuilder {
        genericBuilder().subtype(baseClass, typeName, subtypeClass)
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
    ): CborConfigBuilder {
        genericBuilder().subtype(baseClass, typeName, subtypeClass, typeProperty)
        return this
    }

    /**
     * 覆盖指定多态基类的类型属性名。
     *
     * @param baseClass 基类/接口
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): CborConfigBuilder {
        genericBuilder().typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 `$ref`。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): CborConfigBuilder {
        genericBuilder().resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否输出对象身份 `$ref`。
     *
     * @param write 是否输出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): CborConfigBuilder {
        genericBuilder().writeReferences(write)
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): CborConfig = CborConfig(
        baseConfig ?: baseBuilder.build(),
        preserveTags,
        bufferPool,
        readFeatures,
        writeFeatures,
    )

    private fun genericBuilder(): DocumentMapperConfigBuilder {
        check(baseConfig == null) {
            "base(config) 不能与其它通用配置便捷方法同时使用"
        }
        return baseBuilder
    }
}
