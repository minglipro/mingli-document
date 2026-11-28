package com.mingliqiye.document.format.toml

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import java.nio.charset.Charset

/**
 * [TomlConfig] 构建器。
 *
 * 提供三类能力：
 * 1. 转发全部通用 [DocumentMapperConfig] 便捷方法（字段转换器、命名策略、文件/流选项、
 *    Node 限制、`$ref`、Bean 校验、多态子类型、[DeserializationFeature] 等）；
 * 2. TOML 读取/写出特性 [TomlReadFeature] / [TomlWriteFeature] 的
 *    `enable` / `disable` / `configure`；
 * 3. 解析深度 `maxDepth` 与注释保留配置（[TomlCommentConfig]），
 *    与既有 [TomlCommentConfigBuilder] 合并。
 */
public class TomlConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var maxDepth: Int = TomlConfig.DEFAULT_MAX_DEPTH
    private val readFeatures = LinkedHashSet(TomlConfig.DEFAULT_READ_FEATURES)
    private val writeFeatures = LinkedHashSet(TomlConfig.DEFAULT_WRITE_FEATURES)
    private var orphanCommentPolicy: TomlOrphanCommentPolicy =
        TomlCommentConfig().orphanCommentPolicy
    private var preserveBlankLines: Boolean = TomlCommentConfig().preserveBlankLines
    private var trailingNewline: Boolean = TomlCommentConfig().trailingNewline

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
    ): TomlConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     *
     * @param policy 缺失字段策略
     * @return 当前构建器
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): TomlConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     *
     * @param fail 是否失败
     * @return 当前构建器
     */
    public fun failOnUnknownFields(fail: Boolean): TomlConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     *
     * @param write 是否写出 null 字段
     * @return 当前构建器
     */
    public fun writeNulls(write: Boolean): TomlConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * 设置未标注 `@DocumentName` 的字段命名策略。
     *
     * @param strategy 字段命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): TomlConfigBuilder {
        base.namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写字符集。
     *
     * @param charset 字符集
     * @return 当前构建器
     */
    public fun fileCharset(charset: Charset): TomlConfigBuilder {
        base.fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否原子替换。
     *
     * @param atomic 是否原子写入
     * @return 当前构建器
     */
    public fun atomicFileWrite(atomic: Boolean): TomlConfigBuilder {
        base.atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     *
     * @param create 是否创建父目录
     * @return 当前构建器
     */
    public fun createParentDirectories(create: Boolean): TomlConfigBuilder {
        base.createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     *
     * @param max 最大字节数
     * @return 当前构建器
     */
    public fun maxInputSize(max: Long): TomlConfigBuilder {
        base.maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树中单个字符串最大长度，0 表示不限制。
     *
     * @param max 最大长度
     * @return 当前构建器
     */
    public fun maxStringLength(max: Int): TomlConfigBuilder {
        base.maxStringLength(max)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制。
     *
     * @param max 最大深度
     * @return 当前构建器
     */
    public fun maxNodeDepth(max: Int): TomlConfigBuilder {
        base.maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     *
     * @param max 最大节点数
     * @return 当前构建器
     */
    public fun maxNodeCount(max: Long): TomlConfigBuilder {
        base.maxNodeCount(max)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 `$ref`。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): TomlConfigBuilder {
        base.resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否按对象身份写出本地 `$ref`。
     *
     * @param write 是否写出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): TomlConfigBuilder {
        base.writeReferences(write)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器。
     *
     * @param resolver 远程引用解析器；`null` 表示禁用
     * @return 当前构建器
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): TomlConfigBuilder {
        base.remoteReferenceResolver(resolver)
        return this
    }

    /**
     * 设置根对象 Bean 校验器。
     *
     * @param validator 校验器；`null` 表示不校验
     * @return 当前构建器
     */
    public fun beanValidator(validator: DocumentBeanValidator?): TomlConfigBuilder {
        base.beanValidator(validator)
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
    ): TomlConfigBuilder {
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
    public fun subtype(
        baseClass: Class<*>,
        typeName: String,
        subtypeClass: Class<*>,
        typeProperty: String,
    ): TomlConfigBuilder {
        base.subtype(baseClass, typeName, subtypeClass, typeProperty)
        return this
    }

    /**
     * 覆盖指定多态基类在文档中的类型属性名。
     *
     * @param baseClass 基类/接口
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun typeProperty(
        baseClass: Class<*>,
        typeProperty: String,
    ): TomlConfigBuilder {
        base.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 开启指定反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun enable(feature: DeserializationFeature): TomlConfigBuilder {
        base.enable(feature)
        return this
    }

    /**
     * 关闭指定反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun disable(feature: DeserializationFeature): TomlConfigBuilder {
        base.disable(feature)
        return this
    }

    /**
     * 设置反序列化特性开关。
     *
     * @param feature 反序列化特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: DeserializationFeature, enabled: Boolean): TomlConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 便捷设置 [DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES]。
     *
     * @param fail 是否在 `null` 赋给基本类型时抛出异常
     * @return 当前构建器
     */
    public fun failOnNullForPrimitives(fail: Boolean): TomlConfigBuilder = configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, fail)

    /**
     * 设置解析最大嵌套深度，默认 [TomlConfig.DEFAULT_MAX_DEPTH]（512）。
     *
     * 根表深度为 `1`，`[a.b]` 中 `b` 的深度为 `3`；超限时解析抛
     * [com.mingliqiye.document.format.toml.exception.TomlParseException]。
     *
     * @param maxDepth 最大嵌套深度，必须为正数
     * @return 当前构建器
     * @throws IllegalArgumentException `maxDepth` 小于等于 0 时抛出
     */
    public fun maxDepth(maxDepth: Int): TomlConfigBuilder {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
        this.maxDepth = maxDepth
        return this
    }

    /**
     * 开启 TOML 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: TomlReadFeature): TomlConfigBuilder {
        readFeatures.add(feature)
        return this
    }

    /**
     * 关闭 TOML 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: TomlReadFeature): TomlConfigBuilder {
        readFeatures.remove(feature)
        return this
    }

    /**
     * 设置 TOML 读取特性开关。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: TomlReadFeature, enabled: Boolean): TomlConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启 TOML 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: TomlWriteFeature): TomlConfigBuilder {
        writeFeatures.add(feature)
        return this
    }

    /**
     * 关闭 TOML 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: TomlWriteFeature): TomlConfigBuilder {
        writeFeatures.remove(feature)
        return this
    }

    /**
     * 设置 TOML 写出特性开关。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: TomlWriteFeature, enabled: Boolean): TomlConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 设置游离注释与“关联键/表已不存在”的注释的保留策略。
     *
     * @param policy 保留策略
     * @return 当前构建器
     */
    public fun orphanCommentPolicy(policy: TomlOrphanCommentPolicy): TomlConfigBuilder {
        this.orphanCommentPolicy = policy
        return this
    }

    /**
     * 设置保留注释写出时是否输出空行。
     *
     * @param preserve 是否保留空行
     * @return 当前构建器
     */
    public fun preserveBlankLines(preserve: Boolean): TomlConfigBuilder {
        this.preserveBlankLines = preserve
        return this
    }

    /**
     * 设置保留注释写出时非空输出是否以换行符结尾。
     *
     * @param write 是否输出结尾换行
     * @return 当前构建器
     */
    public fun trailingNewline(write: Boolean): TomlConfigBuilder {
        this.trailingNewline = write
        return this
    }

    /**
     * 整体合并一份注释写出配置（与逐项设置等价，后设置者优先）。
     *
     * @param config 注释写出配置
     * @return 当前构建器
     */
    public fun commentConfig(config: TomlCommentConfig): TomlConfigBuilder {
        this.orphanCommentPolicy = config.orphanCommentPolicy
        this.preserveBlankLines = config.preserveBlankLines
        this.trailingNewline = config.trailingNewline
        return this
    }

    /**
     * 构建不可变 TOML 配置。
     *
     * @return TOML 配置
     * @throws IllegalArgumentException 配置项非法（如 `maxDepth <= 0`）时抛出
     */
    public fun build(): TomlConfig = TomlConfig(
        base.build(),
        maxDepth,
        readFeatures.toSet(),
        writeFeatures.toSet(),
        TomlCommentConfig(orphanCommentPolicy, preserveBlankLines, trailingNewline),
    )
}
