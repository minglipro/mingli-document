package com.mingliqiye.document.format.xml

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import java.nio.charset.Charset

/**
 * [XmlConfig] 构建器。
 *
 * 属性映射、Clark 名称模式与命名空间感知默认关闭；混合内容默认保留。
 * 读/写特性默认对齐 上游实现 XML 3.x，可用 [上游库2Defaults] 切到 上游实现 2.x 默认。
 */
public class XmlConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var rootName = XmlConfig.DEFAULT_ROOT_NAME
    private var attributePrefix: String? = null
    private var attributeModeConfigured = false
    private var namespaceAware = false
    private var namespaceMode = XmlNamespaceMode.PREFIX
    private var textKey = XmlConfig.DEFAULT_TEXT_KEY
    private var itemsKey = XmlConfig.DEFAULT_ITEMS_KEY
    private var contentKey = XmlConfig.DEFAULT_CONTENT_KEY
    private var mixedContentPolicy = XmlMixedContentPolicy.PRESERVE
    private var maxDepth = XmlConfig.DEFAULT_MAX_DEPTH
    private var typeAttributes = false
    private val readFeatures = XmlConfig.DEFAULT_READ_FEATURES.toMutableSet()
    private val writeFeatures = XmlConfig.DEFAULT_WRITE_FEATURES.toMutableSet()
    private var defaultUseWrapper = true
    private var nameProcessor: XmlNameProcessor = XmlNameProcessors.passthrough()

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
    ): XmlConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): XmlConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     */
    public fun failOnUnknownFields(fail: Boolean): XmlConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     */
    public fun writeNulls(write: Boolean): XmlConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * 设置未标注 `@DocumentName` 的字段使用的文档字段命名策略。
     *
     * @param strategy 命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): XmlConfigBuilder {
        base.namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写使用的字符集；XML 声明中的 `encoding` 也取自该值。
     *
     * @param charset 字符集
     * @return 当前构建器
     */
    public fun fileCharset(charset: Charset): XmlConfigBuilder {
        base.fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否使用临时文件 + 原子替换。
     *
     * @param atomic 是否原子写入
     * @return 当前构建器
     */
    public fun atomicFileWrite(atomic: Boolean): XmlConfigBuilder {
        base.atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     *
     * @param create 是否创建父目录
     * @return 当前构建器
     */
    public fun createParentDirectories(create: Boolean): XmlConfigBuilder {
        base.createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     *
     * @param max 最大字节数
     * @return 当前构建器
     */
    public fun maxInputSize(max: Long): XmlConfigBuilder {
        base.maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树中单个字符串最大长度，0 表示不限制。
     *
     * @param max 最大长度
     * @return 当前构建器
     */
    public fun maxStringLength(max: Int): XmlConfigBuilder {
        base.maxStringLength(max)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制；XML 解析深度取它与 [maxDepth] 的较小值。
     *
     * @param max 最大深度
     * @return 当前构建器
     */
    public fun maxNodeDepth(max: Int): XmlConfigBuilder {
        base.maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     *
     * @param max 最大节点数
     * @return 当前构建器
     */
    public fun maxNodeCount(max: Long): XmlConfigBuilder {
        base.maxNodeCount(max)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器。
     *
     * @param resolver 远程引用解析器；`null` 表示禁用远程引用
     * @return 当前构建器
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): XmlConfigBuilder {
        base.remoteReferenceResolver(resolver)
        return this
    }

    /**
     * 设置根对象 Bean 校验器。
     *
     * @param validator 校验器；`null` 表示不校验
     * @return 当前构建器
     */
    public fun beanValidator(validator: DocumentBeanValidator?): XmlConfigBuilder {
        base.beanValidator(validator)
        return this
    }

    /**
     * 开启反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun enable(feature: DeserializationFeature): XmlConfigBuilder {
        base.enable(feature)
        return this
    }

    /**
     * 关闭反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun disable(feature: DeserializationFeature): XmlConfigBuilder {
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
    public fun configure(feature: DeserializationFeature, enabled: Boolean): XmlConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 控制反序列化时 `null` 是否可用于 Java 基本类型字段。
     *
     * @param fail 为 `true` 时遇到 null 抛异常
     * @return 当前构建器
     */
    public fun failOnNullForPrimitives(fail: Boolean): XmlConfigBuilder = configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, fail)

    /**
     * 设置根元素名。
     */
    public fun rootName(rootName: String): XmlConfigBuilder {
        this.rootName = rootName
        return this
    }

    /**
     * 启用属性映射并设置属性字段前缀，例如 `@`。
     *
     * 前缀只作用于 Node 字段名，不会写入 XML；`@id` 字段写出为 `id="..."`。
     *
     * @param prefix 属性字段前缀；不能为空
     * @return 当前构建器
     * @throws IllegalArgumentException 前缀为空时抛出
     */
    public fun attributePrefix(prefix: String): XmlConfigBuilder {
        require(prefix.isNotEmpty()) { "attributePrefix must not be empty" }
        this.attributePrefix = prefix
        this.attributeModeConfigured = true
        return this
    }

    /**
     * 启用或关闭属性映射。
     *
     * 启用时若未显式设置 [attributePrefix]，使用 [XmlConfig.DEFAULT_ATTRIBUTE_PREFIX]；
     * 关闭时清除前缀并忽略 XML attribute，前缀字段按普通元素名处理。
     *
     * @param enabled 是否启用属性映射
     * @return 当前构建器
     */
    public fun attributeMode(enabled: Boolean): XmlConfigBuilder {
        attributeModeConfigured = true
        attributePrefix = if (enabled) {
            attributePrefix ?: XmlConfig.DEFAULT_ATTRIBUTE_PREFIX
        } else {
            null
        }
        return this
    }

    /**
     * 开启或关闭命名空间感知。
     *
     * 开启且未显式配置属性映射时，默认启用属性映射（前缀 [XmlConfig.DEFAULT_ATTRIBUTE_PREFIX]），
     * 以便把 `xmlns` 声明（前缀模式）或命名空间属性（Clark 模式）保留为属性字段。
     *
     * @param aware 是否启用命名空间感知
     * @return 当前构建器
     */
    public fun namespaceAware(aware: Boolean): XmlConfigBuilder {
        this.namespaceAware = aware
        return this
    }

    /**
     * 设置命名空间名称表示模式。
     *
     * [XmlNamespaceMode.CLARK] 会强制启用命名空间感知。
     *
     * @param mode 命名空间模式
     * @return 当前构建器
     */
    public fun namespaceMode(mode: XmlNamespaceMode): XmlConfigBuilder {
        this.namespaceMode = mode
        return this
    }

    /**
     * 设置混合内容直接文本的字段名，默认 `#text`。
     *
     * @param textKey 文本字段名；不能为空
     * @return 当前构建器
     * @throws IllegalArgumentException 字段名为空时抛出
     */
    public fun textKey(textKey: String): XmlConfigBuilder {
        require(textKey.isNotEmpty()) { "textKey must not be empty" }
        this.textKey = textKey
        return this
    }

    /**
     * 设置“文本元素”字段名，对齐 上游实现 XML 的 `XmlMapper.Builder.nameForTextElement`。
     *
     * 本库等价于 [textKey]：元素/对象文本内容在 Node 中使用的字段名。默认沿用 `#text`
     * （上游实现 默认是空伪属性名，本库 Node 模型需要具体键名，故默认值不同，见文档）。
     *
     * @param name 文本字段名；不能为空
     * @return 当前构建器
     * @throws IllegalArgumentException 字段名为空时抛出
     */
    public fun nameForTextElement(name: String): XmlConfigBuilder = textKey(name)

    /**
     * 设置带属性数组元素的数组字段名，默认 `#items`。
     *
     * @param itemsKey 数组字段名；不能为空
     * @return 当前构建器
     * @throws IllegalArgumentException 字段名为空时抛出
     */
    public fun itemsKey(itemsKey: String): XmlConfigBuilder {
        require(itemsKey.isNotEmpty()) { "itemsKey must not be empty" }
        this.itemsKey = itemsKey
        return this
    }

    /**
     * 设置有序混合内容的数组字段名，默认 `#content`。
     *
     * @param contentKey 有序内容字段名；不能为空
     * @return 当前构建器
     * @throws IllegalArgumentException 字段名为空时抛出
     */
    public fun contentKey(contentKey: String): XmlConfigBuilder {
        require(contentKey.isNotEmpty()) { "contentKey must not be empty" }
        this.contentKey = contentKey
        return this
    }

    /**
     * 设置混合内容处理策略，默认 [XmlMixedContentPolicy.PRESERVE]。
     *
     * 使用 [XmlMixedContentPolicy.PRESERVE_ORDERED] 时额外输出 `contentKey` 有序数组，
     * 写出时按该数组顺序交错输出文本与子元素。
     *
     * @param policy 混合内容策略
     * @return 当前构建器
     */
    public fun mixedContentPolicy(policy: XmlMixedContentPolicy): XmlConfigBuilder {
        this.mixedContentPolicy = policy
        return this
    }

    /**
     * 设置解析最大 XML 元素嵌套深度，默认 [XmlConfig.DEFAULT_MAX_DEPTH]（512）。
     *
     * 根元素深度为 `1`；超限时解析抛出
     * [com.mingliqiye.document.format.xml.exception.XmlParseException]。
     *
     * @param maxDepth 最大嵌套深度，必须为正数
     * @return 当前构建器
     * @throws IllegalArgumentException `maxDepth` 小于等于 0 时抛出
     */
    public fun maxDepth(maxDepth: Int): XmlConfigBuilder {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
        this.maxDepth = maxDepth
        return this
    }

    /**
     * 是否在写出 XML 时生成 `type="..."` 协议属性，默认 `false`。
     *
     * 默认不输出，得到更接近普通业务 XML 的结果；读取侧按元素结构/标量文本推断类型。
     * 看起来像数字/布尔的字符串会经标量转换得到对应字符串，但二进制等无法仅靠文本
     * 区分的类型无法严格保真；需要无损往返时开启。
     *
     * @param enabled 是否生成 type 属性
     * @return 当前构建器
     */
    public fun typeAttributes(enabled: Boolean): XmlConfigBuilder {
        this.typeAttributes = enabled
        return this
    }

    /**
     * 设置集合/数组字段写出时是否默认使用同名包装元素，默认 `true`（上游实现 默认）。
     *
     * @param useWrapper 是否使用包装元素，对齐 上游实现 `defaultUseWrapper`
     * @return 当前构建器
     */
    public fun defaultUseWrapper(useWrapper: Boolean): XmlConfigBuilder {
        this.defaultUseWrapper = useWrapper
        return this
    }

    /**
     * 设置 XML 名称处理器，对齐 上游实现 `XmlNameProcessor`。
     *
     * @param processor 名称处理器
     * @return 当前构建器
     */
    public fun nameProcessor(processor: XmlNameProcessor): XmlConfigBuilder {
        this.nameProcessor = processor
        return this
    }

    /**
     * 开启 XML 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: XmlReadFeature): XmlConfigBuilder {
        readFeatures.add(feature)
        return this
    }

    /**
     * 关闭 XML 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: XmlReadFeature): XmlConfigBuilder {
        readFeatures.remove(feature)
        return this
    }

    /**
     * 设置 XML 读取特性开关。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: XmlReadFeature, enabled: Boolean): XmlConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启 XML 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: XmlWriteFeature): XmlConfigBuilder {
        writeFeatures.add(feature)
        return this
    }

    /**
     * 关闭 XML 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: XmlWriteFeature): XmlConfigBuilder {
        writeFeatures.remove(feature)
        return this
    }

    /**
     * 设置 XML 写出特性开关。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: XmlWriteFeature, enabled: Boolean): XmlConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 切换为 上游实现 XML 2.x 默认值，对齐 上游实现 3.x 的 `configureFor上游实现2()`。
     *
     * 关闭 3.x 相比 2.x 默认开启的 5 个特性：
     * `WRITE_NULLS_AS_XSI_NIL`、`UNWRAP_ROOT_OBJECT_NODE`、
     * 写侧 `AUTO_DETECT_XSI_TYPE`、`WRITE_XML_SCHEMA_CONFORMING_FLOATS`、
     * 读侧 `AUTO_DETECT_XSI_TYPE`。其余配置不变。
     *
     * @return 当前构建器
     */
    public fun legacy2Defaults(): XmlConfigBuilder {
        writeFeatures.remove(XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL)
        writeFeatures.remove(XmlWriteFeature.UNWRAP_ROOT_OBJECT_NODE)
        writeFeatures.remove(XmlWriteFeature.AUTO_DETECT_XSI_TYPE)
        writeFeatures.remove(XmlWriteFeature.WRITE_XML_SCHEMA_CONFORMING_FLOATS)
        readFeatures.remove(XmlReadFeature.AUTO_DETECT_XSI_TYPE)
        return this
    }

    /**
     * 恢复为 上游实现 XML 3.x 默认值。
     *
     * @return 当前构建器
     */
    public fun modernDefaults(): XmlConfigBuilder {
        writeFeatures.addAll(XmlConfig.DEFAULT_WRITE_FEATURES)
        readFeatures.addAll(XmlConfig.DEFAULT_READ_FEATURES)
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>): XmlConfigBuilder {
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>, typeProperty: String): XmlConfigBuilder {
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
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): XmlConfigBuilder {
        base.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 $ref。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): XmlConfigBuilder {
        base.resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否输出对象身份 $ref。
     *
     * @param write 是否输出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): XmlConfigBuilder {
        base.writeReferences(write)
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): XmlConfig {
        val effectiveAware = namespaceAware || namespaceMode == XmlNamespaceMode.CLARK
        val effectivePrefix = when {
            attributeModeConfigured -> attributePrefix
            effectiveAware -> XmlConfig.DEFAULT_ATTRIBUTE_PREFIX
            else -> null
        }
        require(textKey != itemsKey && textKey != contentKey && itemsKey != contentKey) {
            "textKey, itemsKey and contentKey must be different"
        }
        require(
            effectivePrefix == null ||
                (
                    !textKey.startsWith(effectivePrefix) &&
                        !itemsKey.startsWith(effectivePrefix) &&
                        !contentKey.startsWith(effectivePrefix)
                    ),
        ) { "textKey, itemsKey and contentKey must not start with attributePrefix" }
        return XmlConfig(
            base.build(),
            rootName,
            effectivePrefix,
            effectiveAware,
            namespaceMode,
            textKey,
            itemsKey,
            contentKey,
            mixedContentPolicy,
            maxDepth,
            typeAttributes,
            readFeatures.toSet(),
            writeFeatures.toSet(),
            defaultUseWrapper,
            nameProcessor,
        )
    }
}
