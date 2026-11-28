package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import java.nio.charset.Charset

/**
 * [YamlConfig] 构建器。
 *
 * 除 YAML 专属选项外，这里同时转发 [DocumentMapperConfig] 的公共便捷方法
 * （命名策略、字符集、文件写入、输入/Node 限制、引用、Bean 校验与通用
 * [DeserializationFeature]），避免使用 YAML 配置时还要手工拼装
 * `DocumentMapperConfig`。
 */
public class YamlConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var pretty = false
    private var maxAliases = 0
    private var allowCustomTags = false
    private val readFeatures = YamlConfig.DEFAULT_READ_FEATURES.toMutableSet()
    private val writeFeatures = YamlConfig.DEFAULT_WRITE_FEATURES.toMutableSet()
    private var allowDuplicateKeys = false
    private var codePointLimit = 0
    private var indent = 2
    private var lineWidth = 0
    private var allowUnicode = true
    private var explicitEnd = false
    private var defaultFlowStyle: YamlFlowStyle? = null

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
    ): YamlConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): YamlConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     */
    public fun failOnUnknownFields(fail: Boolean): YamlConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     */
    public fun writeNulls(write: Boolean): YamlConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * 设置未标注 `@DocumentName` 的字段命名策略。
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): YamlConfigBuilder {
        base.namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写字符集，默认 UTF-8。
     */
    public fun fileCharset(charset: Charset): YamlConfigBuilder {
        base.fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否使用临时文件 + 原子替换。
     */
    public fun atomicFileWrite(atomic: Boolean): YamlConfigBuilder {
        base.atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     */
    public fun createParentDirectories(create: Boolean): YamlConfigBuilder {
        base.createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     */
    public fun maxInputSize(max: Long): YamlConfigBuilder {
        base.maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树中单个字符串最大长度，0 表示不限制。
     */
    public fun maxStringLength(max: Int): YamlConfigBuilder {
        base.maxStringLength(max)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制（同时作为 SnakeYAML 解析深度上限）。
     */
    public fun maxNodeDepth(max: Int): YamlConfigBuilder {
        base.maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     */
    public fun maxNodeCount(max: Long): YamlConfigBuilder {
        base.maxNodeCount(max)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器。
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): YamlConfigBuilder {
        base.remoteReferenceResolver(resolver)
        return this
    }

    /**
     * 设置 Bean 校验器（根对象序列化入口/反序列化出口各执行一次）。
     */
    public fun beanValidator(validator: DocumentBeanValidator?): YamlConfigBuilder {
        base.beanValidator(validator)
        return this
    }

    /**
     * 开启通用反序列化特性。
     */
    public fun enable(feature: DeserializationFeature): YamlConfigBuilder {
        base.enable(feature)
        return this
    }

    /**
     * 关闭通用反序列化特性。
     */
    public fun disable(feature: DeserializationFeature): YamlConfigBuilder {
        base.disable(feature)
        return this
    }

    /**
     * 设置通用反序列化特性开关。
     */
    public fun configure(feature: DeserializationFeature, enabled: Boolean): YamlConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 设置 `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES`。
     */
    public fun failOnNullForPrimitives(fail: Boolean): YamlConfigBuilder = configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, fail)

    /**
     * `format()` 是否默认输出块格式 YAML。
     */
    public fun pretty(pretty: Boolean): YamlConfigBuilder {
        this.pretty = pretty
        return this
    }

    /**
     * 设置单文档允许的非标量 alias 最大数量，0 表示使用 SnakeYAML 默认值。
     *
     * 该限制作用于原生 Token 路径与 Node 回退路径（[YamlReader]）。
     *
     * @param maxAliases 最大 alias 数量
     * @return 当前构建器
     */
    public fun maxAliases(maxAliases: Int): YamlConfigBuilder {
        require(maxAliases >= 0) { "maxAliases must be >= 0" }
        this.maxAliases = maxAliases
        return this
    }

    /**
     * 设置是否允许未知自定义 tag（如 `!foo`、`!env`）。
     *
     * 默认 `false`，未知 tag 抛出
     * [com.mingliqiye.document.format.yaml.exception.YamlParseException]；开启后
     * 标量 -> StringNode，序列 -> ArrayNode，映射 -> ObjectNode。该选项同时作用于
     * Node 路径（[YamlReader]）与原生 Token 路径（[YamlMapper.parse]）。
     *
     * @param allow 是否允许未知自定义 tag
     * @return 当前构建器
     */
    public fun allowCustomTags(allow: Boolean): YamlConfigBuilder {
        this.allowCustomTags = allow
        return this
    }

    /**
     * 开启 YAML 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: YamlReadFeature): YamlConfigBuilder {
        readFeatures.add(feature)
        return this
    }

    /**
     * 开启多个 YAML 读取特性。
     */
    public fun enable(first: YamlReadFeature, vararg others: YamlReadFeature): YamlConfigBuilder {
        enable(first)
        for (feature in others) {
            enable(feature)
        }
        return this
    }

    /**
     * 关闭 YAML 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: YamlReadFeature): YamlConfigBuilder {
        readFeatures.remove(feature)
        return this
    }

    /**
     * 关闭多个 YAML 读取特性。
     */
    public fun disable(first: YamlReadFeature, vararg others: YamlReadFeature): YamlConfigBuilder {
        disable(first)
        for (feature in others) {
            disable(feature)
        }
        return this
    }

    /**
     * 设置 YAML 读取特性开关。
     */
    public fun configure(feature: YamlReadFeature, enabled: Boolean): YamlConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启 YAML 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: YamlWriteFeature): YamlConfigBuilder {
        writeFeatures.add(feature)
        return this
    }

    /**
     * 开启多个 YAML 写出特性。
     */
    public fun enable(first: YamlWriteFeature, vararg others: YamlWriteFeature): YamlConfigBuilder {
        enable(first)
        for (feature in others) {
            enable(feature)
        }
        return this
    }

    /**
     * 关闭 YAML 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: YamlWriteFeature): YamlConfigBuilder {
        writeFeatures.remove(feature)
        return this
    }

    /**
     * 关闭多个 YAML 写出特性。
     */
    public fun disable(first: YamlWriteFeature, vararg others: YamlWriteFeature): YamlConfigBuilder {
        disable(first)
        for (feature in others) {
            disable(feature)
        }
        return this
    }

    /**
     * 设置 YAML 写出特性开关。
     */
    public fun configure(feature: YamlWriteFeature, enabled: Boolean): YamlConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 设置是否允许重复 mapping key，默认 `false`。
     */
    public fun allowDuplicateKeys(allow: Boolean): YamlConfigBuilder {
        this.allowDuplicateKeys = allow
        return this
    }

    /**
     * 设置单次输入最大 code point 数量，0 表示使用 SnakeYAML 默认值。
     */
    public fun codePointLimit(limit: Int): YamlConfigBuilder {
        require(limit >= 0) { "codePointLimit must be >= 0" }
        this.codePointLimit = limit
        return this
    }

    /**
     * 设置块式输出缩进空格数（2..9），默认 2。
     */
    public fun indent(indent: Int): YamlConfigBuilder {
        require(indent >= YamlConfig.MIN_INDENT && indent <= YamlConfig.MAX_INDENT) {
            "indent must be in ${YamlConfig.MIN_INDENT}..${YamlConfig.MAX_INDENT}, but was $indent"
        }
        this.indent = indent
        return this
    }

    /**
     * 设置输出行宽，0 表示使用 SnakeYAML 默认值（80）。
     */
    public fun lineWidth(width: Int): YamlConfigBuilder {
        require(width >= 0) { "lineWidth must be >= 0" }
        this.lineWidth = width
        return this
    }

    /**
     * 设置是否直接输出 Unicode 字符（`false` 时转义），默认 `true`。
     */
    public fun allowUnicode(allow: Boolean): YamlConfigBuilder {
        this.allowUnicode = allow
        return this
    }

    /**
     * 设置是否在文档末尾输出显式结束标记 `...`，默认 `false`。
     */
    public fun explicitEnd(explicit: Boolean): YamlConfigBuilder {
        this.explicitEnd = explicit
        return this
    }

    /**
     * 设置块式/流式布局；不设置时由 [pretty] 推导。
     */
    public fun defaultFlowStyle(style: YamlFlowStyle?): YamlConfigBuilder {
        this.defaultFlowStyle = style
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>): YamlConfigBuilder {
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>, typeProperty: String): YamlConfigBuilder {
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
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): YamlConfigBuilder {
        base.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 $ref。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): YamlConfigBuilder {
        base.resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否输出对象身份 $ref。
     *
     * @param write 是否输出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): YamlConfigBuilder {
        base.writeReferences(write)
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): YamlConfig = YamlConfig(
        base.build(),
        pretty,
        maxAliases,
        allowCustomTags,
        readFeatures.toSet(),
        writeFeatures.toSet(),
        allowDuplicateKeys,
        codePointLimit,
        indent,
        lineWidth,
        allowUnicode,
        explicitEnd,
        defaultFlowStyle,
    )
}
