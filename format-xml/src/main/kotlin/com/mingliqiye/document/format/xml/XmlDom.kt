package com.mingliqiye.document.format.xml

import com.mingliqiye.document.format.xml.exception.XmlParseException
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XML 安全 DOM 解析。
 *
 * [XmlReader] 与 [XmlTokenReader] 共用同一套解析器配置，保证两条读取路径的
 * XXE 防护完全一致：禁用 DOCTYPE、禁用外部通用/参数实体、关闭 XInclude 与
 * 实体引用展开。
 */
internal object XmlDom {

    /**
     * 将 XML 文本解析为 DOM 根元素。
     *
     * @param source XML 文本
     * @param namespaceAware 是否启用命名空间感知
     * @return DOM 根元素
     * @throws XmlParseException 解析器配置失败、XML 格式非法或文档没有根元素时抛出
     */
    fun parse(source: String, namespaceAware: Boolean = false): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = namespaceAware
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        } catch (e: Exception) {
            throw XmlParseException("Cannot configure XML parser securely", e)
        }

        val document = try {
            val builder = factory.newDocumentBuilder()
            builder.setErrorHandler(
                object : org.xml.sax.ErrorHandler {
                    override fun warning(exception: org.xml.sax.SAXParseException) = Unit

                    override fun error(exception: org.xml.sax.SAXParseException): Unit = throw exception

                    override fun fatalError(exception: org.xml.sax.SAXParseException): Unit = throw exception
                },
            )
            builder.parse(InputSource(StringReader(source)))
        } catch (e: Exception) {
            throw XmlParseException("Invalid XML: ${e.message}", e)
        }
        return document.documentElement ?: throw XmlParseException("XML document has no root element")
    }
}
