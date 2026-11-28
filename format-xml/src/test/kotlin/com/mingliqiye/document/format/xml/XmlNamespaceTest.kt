package com.mingliqiye.document.format.xml

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.annotation.DocumentRootName
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * XML 命名空间测试。
 *
 * 命名空间表示方式：元素名/属性名保留 `prefix:local` 前缀限定形式，
 * `xmlns` 声明保留为属性字段（如 `@xmlns:ns`），不引入 Clark 形式 `{uri}local`。
 */
class XmlNamespaceTest {

    @Test
    fun readsPrefixedDocumentWithDeclarations() {
        val xml = """
            <ns:person xmlns:ns="urn:example" ns:id="7">
              <ns:name>小于</ns:name>
            </ns:person>
        """.trimIndent()
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).attributePrefix("@").namespaceAware(true).build())

        val node = mapper.parse(xml, Node::class.java).asObject()
        assertEquals("urn:example", node["@xmlns:ns"]?.asString())
        assertEquals(7, node["@ns:id"]?.asNumber()?.toInt())
        assertEquals("小于", node["ns:name"]?.asString())
    }

    @Test
    fun namespacedBeanRoundTrip() {
        val config = XmlConfig.builder().typeAttributes(true).attributePrefix("@").namespaceAware(true).build()
        val mapper = XmlMapper(config)
        val person = NamespacedPerson().apply {
            xmlnsNs = "urn:example"
            id = 7
            name = "小于"
        }

        val xml = mapper.format(person)
        assertTrue(xml.startsWith("<ns:person type=\"object\""))
        assertTrue(xml.contains(" xmlns:ns=\"urn:example\""))
        assertTrue(xml.contains(" ns:id=\"7\""))
        assertTrue(xml.contains("<ns:name type=\"string\">小于</ns:name>"))

        val restored = mapper.parse(xml, NamespacedPerson::class.java)
        assertEquals(7, restored.id)
        assertEquals("小于", restored.name)
        assertEquals("urn:example", restored.xmlnsNs)
    }

    @Test
    fun namespaceAwareEnablesDefaultAttributePrefix() {
        val config = XmlConfig.builder().typeAttributes(true).namespaceAware(true).build()
        assertTrue(config.namespaceAware)
        assertTrue(config.attributeMode)
        assertEquals(XmlConfig.DEFAULT_ATTRIBUTE_PREFIX, config.attributePrefix)

        val configWithPrefix = XmlConfig.builder().typeAttributes(true).attributePrefix("$").namespaceAware(true).build()
        assertEquals("$", configWithPrefix.attributePrefix)
        assertTrue(configWithPrefix.namespaceAware)
    }

    @Test
    fun namespaceAwareKeepsQualifiedNameWhileLegacyEscapesColon() {
        val node = ObjectNode.of("ns:name" to StringNode.of("x"))

        val legacy = node.toXmlString(typeAttributes = true)
        assertTrue(legacy.contains("ns_x003A_name"))

        val namespaced = node.toXmlString(namespaceAware = true, typeAttributes = true)
        assertTrue(namespaced.contains("<ns:name type=\"string\">x</ns:name>"))
    }

    @Test
    fun qualifiedNamesWithInvalidCharactersRoundTrip() {
        val node = ObjectNode.of(
            "@xmlns:ns" to StringNode.of("urn:example"),
            "ns:user name" to StringNode.of("v"),
            "@ns:user id" to StringNode.of("u-1"),
        )
        val xml = node.toXmlString(rootName = "ns:root", attributePrefix = "@", namespaceAware = true, typeAttributes = true)

        assertTrue(xml.contains("<ns:root type=\"object\""))
        assertTrue(xml.contains("ns:user_x0020_id=\"u-1\""))
        assertTrue(xml.contains("<ns:user_x0020_name type=\"string\">v</ns:user_x0020_name>"))

        val restored = XmlReader(xml, attributePrefix = "@", namespaceAware = true).read().asObject()
        assertEquals("u-1", restored["@ns:user id"]?.asString())
        assertEquals("v", restored["ns:user name"]?.asString())
    }

    @Test
    fun invalidQualifiedNameIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ObjectNode.of("ns:name:extra" to StringNode.of("x")).toXmlString(namespaceAware = true)
        }
        assertFailsWith<IllegalArgumentException> {
            ObjectNode.of(":name" to StringNode.of("x")).toXmlString(namespaceAware = true)
        }
    }

    @Test
    fun defaultNamespaceIsPreservedAsAttributeField() {
        val xml = """<person xmlns="urn:default"><name>小于</name></person>"""
        val config = XmlConfig.builder().typeAttributes(true).namespaceAware(true).build()
        val mapper = XmlMapper(config)

        val node = mapper.parse(xml, Node::class.java).asObject()
        assertEquals("urn:default", node["@xmlns"]?.asString())
        assertEquals("小于", node["name"]?.asString())

        val output = node.toXmlString(attributePrefix = "@", namespaceAware = true, rootName = "person")
        assertTrue(output.contains("xmlns=\"urn:default\""))
        val reparsed = XmlReader(output, attributePrefix = "@", namespaceAware = true).read().asObject()
        assertEquals("urn:default", reparsed["@xmlns"]?.asString())
        assertEquals("小于", reparsed["name"]?.asString())
    }

    @Test
    fun attributeOnlyNamespaceElementRoundTrips() {
        val xml = """<ns:person xmlns:ns="urn:example" ns:id="7"/>"""
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).namespaceAware(true).build())

        val node = mapper.parse(xml, Node::class.java)
        val output = mapper.format(node)
        assertTrue(output.contains("xmlns:ns=\"urn:example\""))
        assertTrue(output.contains("ns:id=\"7\""))

        val reparsed = mapper.parse(output, Node::class.java).asObject()
        assertEquals("urn:example", reparsed["@xmlns:ns"]?.asString())
        assertEquals(7, reparsed["@ns:id"]?.asNumber()?.toInt())
    }

    @Test
    fun typeProtocolStillWorksWhenNamespaceAware() {
        val xml = """
            <root type="object" xmlns:ns="urn:example">
              <ns:name type="string">x</ns:name>
              <count type="number">3</count>
            </root>
        """.trimIndent()
        val node = XmlReader(xml, attributePrefix = "@", namespaceAware = true).read().asObject()

        assertEquals("x", node["ns:name"]?.asString())
        assertEquals(3, node["count"]?.asNumber()?.toInt())
        assertEquals("urn:example", node["@xmlns:ns"]?.asString())
    }

    @DocumentRootName("ns:person")
    private class NamespacedPerson {
        @field:DocumentName("@xmlns:ns")
        var xmlnsNs: String? = null

        @field:DocumentName("@ns:id")
        var id: Int = 0

        @field:DocumentName("ns:name")
        var name: String? = null
    }
}
