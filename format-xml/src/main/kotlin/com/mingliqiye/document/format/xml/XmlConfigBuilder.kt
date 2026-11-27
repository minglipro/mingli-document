package com.mingliqiye.document.format.xml

import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonConverter
import com.mingliqiye.document.serialization.MissingFieldPolicy

/**
 * [XmlConfig] 构建器。
 *
 * 属性映射与命名空间感知默认关闭；显式开启后由 [XmlConfig] 保存最终取值。
 */
public class XmlConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var rootName = "root"
    private var attributePrefix: String? = null
    private var attributeModeConfigured = false
    private var namespaceAware = false

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
     * 以便把 `xmlns` 声明保留为属性字段；显式 [attributeMode] 关闭时 `xmlns` 会丢失。
     *
     * @param aware 是否启用命名空间感知
     * @return 当前构建器
     */
    public fun namespaceAware(aware: Boolean): XmlConfigBuilder {
        this.namespaceAware = aware
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): XmlConfig {
        val effectivePrefix = when {
            attributeModeConfigured -> attributePrefix
            namespaceAware -> XmlConfig.DEFAULT_ATTRIBUTE_PREFIX
            else -> null
        }
        return XmlConfig(base.build(), rootName, effectivePrefix, namespaceAware)
    }
}
