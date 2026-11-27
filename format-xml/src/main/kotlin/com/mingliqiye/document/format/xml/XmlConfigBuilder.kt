package com.mingliqiye.document.format.xml

import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonConverter
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
        converter: JsonConverter<*>,
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
        )
    }
}
