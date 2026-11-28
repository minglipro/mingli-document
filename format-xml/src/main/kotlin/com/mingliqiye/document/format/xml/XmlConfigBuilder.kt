package com.mingliqiye.document.format.xml

import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy

/**
 * [XmlConfig] 构建器。
 *
 * 属性映射、Clark 名称模式与命名空间感知默认关闭；混合内容默认保留。
 */
public class XmlConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var rootName = "root"
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
        )
    }
}
