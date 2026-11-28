package com.mingliqiye.document.format.xml

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.xml.exception.XmlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * XML attribute 映射测试。
 *
 * 属性在 Node 中用 `attributePrefix + 名称` 的普通字段表示，例如 `@id`；
 * 默认不启用，保持原有 `type` / `nil` 协议行为。
 */
class XmlAttributeTest {

    @Test
    fun mapperMapsAttributesToBeanFields() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).attributePrefix("@").build())
        val person = Person().apply {
            id = 7
            name = "小于"
            address = Address().apply {
                zip = 310000
                city = "杭州"
            }
        }

        val xml = mapper.format(person)
        assertTrue(xml.contains(" id=\"7\""))
        assertTrue(xml.contains(" zip=\"310000\""))
        assertFalse(xml.contains("<id"))

        val restored = mapper.parse(xml, Person::class.java)
        assertEquals(7, restored.id)
        assertEquals("小于", restored.name)
        assertEquals(310000, restored.address?.zip)
        assertEquals("杭州", restored.address?.city)
    }

    @Test
    fun attributePrefixIsConfigurable() {
        val config = XmlConfig.builder().typeAttributes(true).attributePrefix("$").build()
        assertEquals("$", config.attributePrefix)
        assertTrue(config.attributeMode)
        assertEquals("root", config.rootName)

        val node = ObjectNode.of("\$id" to NumberNode.of(3))
        val mapper = XmlMapper(config)
        val xml = mapper.format(node)
        assertTrue(xml.contains(" id=\"3\""))

        val restored = mapper.parse(xml, Node::class.java).asObject()
        assertEquals(3, restored["\$id"]?.asNumber()?.toInt())
    }

    @Test
    fun attributeModeSwitchUsesDefaultPrefix() {
        val defaultMode = XmlConfig.builder().typeAttributes(true).attributeMode(true).build()
        assertTrue(defaultMode.attributeMode)
        assertEquals(XmlConfig.DEFAULT_ATTRIBUTE_PREFIX, defaultMode.attributePrefix)

        val disabled = XmlConfig.builder().typeAttributes(true).attributePrefix("@").attributeMode(false).build()
        assertFalse(disabled.attributeMode)
        assertNull(disabled.attributePrefix)
    }

    @Test
    fun attributeAndNamespaceDefaultToDisabled() {
        val config = XmlConfig.builder().typeAttributes(true).build()

        assertFalse(config.attributeMode)
        assertNull(config.attributePrefix)
        assertFalse(config.namespaceAware)
    }

    @Test
    fun defaultConfigOmitsLegacyTypeProtocol() {
        val mapper = XmlMapper()
        val xml = mapper.format(Person().apply { id = 5 })

        assertFalse(xml.contains(" id=\"5\""))
        assertFalse(xml.contains("type=\"number\""))
        assertTrue(xml.contains("_x0040_id>5</_x0040_id>"))
        assertEquals(5, mapper.parse(xml, Person::class.java).id)
    }

    @Test
    fun legacyTypeProtocolCanBeEnabled() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).build())
        val xml = mapper.format(Person().apply { id = 5 })

        assertTrue(xml.contains("_x0040_id type=\"number\""))
        assertEquals(5, mapper.parse(xml, Person::class.java).id)
    }

    @Test
    fun plainXmlAttributesAreIgnoredWithoutAttributeMode() {
        val node = XmlReader("<person id=\"7\"><name>Bob</name></person>").read()
        assertEquals(setOf("name"), node.asObject().keys)
    }

    @Test
    fun attributeModeReadsPlainXmlAttributes() {
        val node = XmlReader("<person id=\"7\" active=\"true\"><name>Bob</name></person>", attributePrefix = "@").read()
        assertEquals(setOf("@id", "@active", "name"), node.asObject().keys)
        assertEquals(7, node.asObject()["@id"]?.asNumber()?.toInt())
        assertEquals(true, node.asObject()["@active"]?.asBoolean())
    }

    @Test
    fun attributeValuesAreEscapedAndRoundTrip() {
        val node = ObjectNode.of(
            "@label" to StringNode.of("a\"b&<c>\nd"),
            "@count" to NumberNode.of(3),
            "@enabled" to BooleanNode.of(true),
            "text" to StringNode.of("x"),
        )
        val xml = node.toXmlString(attributePrefix = "@")

        assertTrue(xml.contains("label=\"a&quot;b&amp;&lt;c&gt;&#10;d\""))
        assertTrue(xml.contains("count=\"3\""))
        assertTrue(xml.contains("enabled=\"true\""))

        val restored = XmlReader(xml, attributePrefix = "@").read().asObject()
        assertEquals("a\"b&<c>\nd", restored["@label"]?.asString())
        assertEquals(3, restored["@count"]?.asNumber()?.toInt())
        assertEquals(true, restored["@enabled"]?.asBoolean())
        assertEquals("x", restored["text"]?.asString())
    }

    @Test
    fun attributeNamesAreEscapedAndRoundTrip() {
        val node = ObjectNode.of(
            "@user name" to StringNode.of("v"),
            "text" to StringNode.of("x"),
        )
        val xml = node.toXmlString(attributePrefix = "@")

        assertTrue(xml.contains("user_x0020_name=\"v\""))
        val restored = XmlReader(xml, attributePrefix = "@").read().asObject()
        assertEquals("v", restored["@user name"]?.asString())
    }

    @Test
    fun typeAndNilAreReservedForProtocol() {
        val node = ObjectNode.of(
            "@type" to StringNode.of("custom"),
            "@nil" to StringNode.of("ignored"),
            "@id" to NumberNode.of(1),
            "name" to StringNode.of("n"),
        )

        val xml = node.toXmlString(attributePrefix = "@")
        assertFalse(xml.contains("custom"))
        assertFalse(xml.contains("ignored"))
        assertTrue(xml.contains("id=\"1\""))

        val restored = XmlReader(xml, attributePrefix = "@").read().asObject()
        assertEquals(setOf("@id", "name"), restored.keys)
        assertFalse(restored.containsKey("@type"))
        assertFalse(restored.containsKey("@nil"))
    }

    @Test
    fun protocolAttributesAreNotMappedToFields() {
        val xml = "<root type=\"object\" id=\"7\"><name type=\"string\">n</name></root>"

        val restored = XmlReader(xml, attributePrefix = "@").read().asObject()
        assertEquals(7, restored["@id"]?.asNumber()?.toInt())
        assertEquals("n", restored["name"]?.asString())
        assertFalse(restored.containsKey("@type"))
        assertFalse(restored.containsKey("@nil"))
    }

    @Test
    fun nullAttributeFieldIsSkipped() {
        val node = ObjectNode.of(
            "@id" to NullNode.INSTANCE,
            "name" to StringNode.of("n"),
        )
        val xml = node.toXmlString(attributePrefix = "@")

        assertFalse(xml.contains(" id="))
        assertFalse(XmlReader(xml, attributePrefix = "@").read().asObject().containsKey("@id"))
    }

    @Test
    fun nonScalarAttributeFieldIsRejected() {
        val node = ObjectNode.of("@nested" to ObjectNode.EMPTY)

        assertFailsWith<IllegalArgumentException> {
            node.toXmlString(attributePrefix = "@")
        }
    }

    @Test
    fun emptyAttributeNameIsRejected() {
        val node = ObjectNode.of("@" to StringNode.of("v"))

        assertFailsWith<IllegalArgumentException> {
            node.toXmlString(attributePrefix = "@")
        }
    }

    @Test
    fun emptyAttributePrefixIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            XmlConfig.builder().typeAttributes(true).attributePrefix("")
        }
    }

    @Test
    fun doctypeAndExternalEntitiesAreRejected() {
        val payload = """
            <?xml version="1.0"?>
            <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <root id="1"><name>&xxe;</name></root>
        """.trimIndent()

        assertFailsWith<XmlParseException> { XmlReader(payload, attributePrefix = "@").read() }
        assertFailsWith<XmlParseException> {
            XmlReader(payload, attributePrefix = "@", namespaceAware = true).read()
        }
    }

    private class Person {
        @field:DocumentName("@id")
        var id: Int = 0

        var name: String? = null
        var address: Address? = null
    }

    private class Address {
        @field:DocumentName("@zip")
        var zip: Int = 0

        var city: String? = null
    }
}
