package com.mingliqiye.document.format.xml

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.xml.exception.XmlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * XML 边界行为测试：标量/数组元素属性包装、Clark 命名空间、混合内容与旧行为兼容。
 */
class XmlEdgeTest {

    @Test
    fun scalarElementAttributesUseWrapperAndRoundTrip() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).attributePrefix("@").build())
        val xml = """<person id="7"><name lang="en">Bob</name><age unit="year">18</age></person>"""

        val node = mapper.parse(xml, Node::class.java).asObject()
        val name = node["name"]?.asObject()
        assertEquals("en", name?.get("@lang")?.asString())
        assertEquals("Bob", name?.get("#text")?.asString())
        val age = node["age"]?.asObject()
        assertEquals("year", age?.get("@unit")?.asString())
        assertEquals(18, age?.get("#text")?.asNumber()?.toInt())

        val output = mapper.format(node)
        assertTrue(output.contains("<name type=\"string\" lang=\"en\">Bob</name>"))
        assertTrue(output.contains("<age type=\"number\" unit=\"year\">18</age>"))
        assertFalse(output.contains("#text"))

        val reparsed = mapper.parse(output, Node::class.java).asObject()
        assertEquals("Bob", reparsed["name"]?.asObject()?.get("#text")?.asString())
        assertEquals("en", reparsed["name"]?.asObject()?.get("@lang")?.asString())
        assertEquals(18, reparsed["age"]?.asObject()?.get("#text")?.asNumber()?.toInt())

        val typed = "<root><name type=\"string\" lang=\"en\">Bob</name></root>"
        assertTrue(mapper.format(mapper.parse(typed, Node::class.java)).contains("<name type=\"string\" lang=\"en\">Bob</name>"))
    }

    @Test
    fun scalarAttributeWrapperMapsBeanFields() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).attributePrefix("@").build())
        val catalog = Catalog().apply {
            item = Item().apply {
                price = Price().apply {
                    currency = "CNY"
                    amount = 100
                }
            }
        }

        val xml = mapper.format(catalog)
        assertTrue(xml.contains("<price type=\"number\" currency=\"CNY\">100</price>"))

        val restored = mapper.parse(xml, Catalog::class.java)
        assertEquals("CNY", restored.item?.price?.currency)
        assertEquals(100, restored.item?.price?.amount)
    }

    @Test
    fun scalarWithoutAttributesKeepsPlainNode() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).attributePrefix("@").build())
        val node = mapper.parse("<root><name>Bob</name><count>3</count></root>", Node::class.java).asObject()

        assertTrue(node["name"]?.isString == true)
        assertEquals("Bob", node["name"]?.asString())
        assertEquals(3, node["count"]?.asNumber()?.toInt())
    }

    @Test
    fun customTextKeyIsUsedForScalarWrappers() {
        val config = XmlConfig.builder().typeAttributes(true).attributePrefix("@").textKey("value").build()
        val mapper = XmlMapper(config)
        val node = mapper.parse("<root><name lang=\"en\">Bob</name></root>", Node::class.java).asObject()

        assertEquals("Bob", node["name"]?.asObject()?.get("value")?.asString())
        val output = mapper.format(node)
        assertTrue(output.contains("<name type=\"string\" lang=\"en\">Bob</name>"))
        assertFalse(output.contains("<value"))
    }

    @Test
    fun nilElementWithAttributesKeepsAttributes() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).attributePrefix("@").build())
        val node = mapper.parse("<root><gone id=\"2\" nil=\"true\"/></root>", Node::class.java).asObject()
        val gone = node["gone"]?.asObject()

        assertEquals(2, gone?.get("@id")?.asNumber()?.toInt())
        assertTrue(gone?.get("#text") is NullNode)

        val output = mapper.format(node)
        assertTrue(output.contains("xmlns:xsi=\"${XmlConfig.XSI_NAMESPACE}\""))
        assertTrue(output.contains("<gone xsi:nil=\"true\" id=\"2\"/>"))
        val reparsed = mapper.parse(output, Node::class.java).asObject()["gone"]?.asObject()
        assertTrue(reparsed?.get("#text") is NullNode)
        assertEquals(2, reparsed?.get("@id")?.asNumber()?.toInt())
    }

    @Test
    fun arrayElementAttributesUseWrapperAndRoundTrip() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).attributePrefix("@").build())
        val xml = """<root><list id="1"><item>a</item><item>b</item></list></root>"""

        val node = mapper.parse(xml, Node::class.java).asObject()
        val list = node["list"]?.asObject()
        assertEquals(1, list?.get("@id")?.asNumber()?.toInt())
        assertEquals(2, list?.get("#items")?.asArray()?.size)
        assertEquals("a", list?.get("#items")?.asArray()?.get(0)?.asString())

        val output = mapper.format(node)
        assertTrue(output.contains("<list type=\"array\" id=\"1\">"))
        assertTrue(output.contains("<item type=\"string\">b</item>"))

        val reparsed = mapper.parse(output, Node::class.java).asObject()["list"]?.asObject()
        assertEquals(1, reparsed?.get("@id")?.asNumber()?.toInt())
        assertEquals("b", reparsed?.get("#items")?.asArray()?.get(1)?.asString())
    }

    @Test
    fun customItemsKeyIsUsedForArrayWrappers() {
        val config = XmlConfig.builder().typeAttributes(true).attributePrefix("@").itemsKey("values").build()
        val mapper = XmlMapper(config)
        val node = mapper.parse("<root><list id=\"1\"><item>a</item></list></root>", Node::class.java).asObject()
        val list = node["list"]?.asObject()

        assertEquals("a", list?.get("values")?.asArray()?.get(0)?.asString())
        val output = mapper.format(node)
        assertTrue(output.contains("<list type=\"array\" id=\"1\">"))
        assertFalse(output.contains("<values"))
    }

    @Test
    fun arrayElementMixedTextIsPreserved() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).attributePrefix("@").build())
        val xml = """<root><list id="1">prefix<item>a</item></list></root>"""

        val node = mapper.parse(xml, Node::class.java).asObject()
        val list = node["list"]?.asObject()
        assertEquals("prefix", list?.get("#text")?.asString())
        assertEquals("a", list?.get("#items")?.asArray()?.get(0)?.asString())

        val output = mapper.format(node)
        assertTrue(output.contains("<list type=\"array\" id=\"1\">prefix<item type=\"string\">a</item></list>"))
        val reparsed = mapper.parse(output, Node::class.java).asObject()["list"]?.asObject()
        assertEquals("prefix", reparsed?.get("#text")?.asString())
        assertEquals("a", reparsed?.get("#items")?.asArray()?.get(0)?.asString())
    }

    @Test
    fun clarkNamesPreserveNamespaceUriAndGenerateDeclarations() {
        val config = XmlConfig.builder().typeAttributes(true).namespaceMode(XmlNamespaceMode.CLARK).build()
        assertTrue(config.namespaceAware)
        assertTrue(config.attributeMode)
        val mapper = XmlMapper(config)
        val xml = """<ns:person xmlns:ns="urn:example" ns:id="7"><ns:name>小于</ns:name></ns:person>"""

        val node = mapper.parse(xml, Node::class.java).asObject()
        assertEquals(7, node["@{urn:example}id"]?.asNumber()?.toInt())
        assertEquals("小于", node["{urn:example}name"]?.asString())
        assertFalse(node.containsKey("@xmlns:ns"))

        val output = mapper.format(node)
        assertTrue(output.contains("xmlns:ns0=\"urn:example\""))
        assertTrue(output.contains("ns0:id=\"7\""))
        assertTrue(output.contains("<ns0:name type=\"string\">小于</ns0:name>"))

        val reparsed = mapper.parse(output, Node::class.java).asObject()
        assertEquals(7, reparsed["@{urn:example}id"]?.asNumber()?.toInt())
        assertEquals("小于", reparsed["{urn:example}name"]?.asString())
    }

    @Test
    fun clarkRootNameGetsPrefixAndDeclaration() {
        val config = XmlConfig.builder().typeAttributes(true)
            .namespaceMode(XmlNamespaceMode.CLARK)
            .rootName("{urn:example}catalog")
            .build()
        val mapper = XmlMapper(config)
        val node = ObjectNode.of(
            "{urn:example}item" to StringNode.of("a"),
            "plain" to StringNode.of("b"),
        )

        val xml = mapper.format(node)
        assertTrue(xml.startsWith("<ns0:catalog"))
        assertTrue(xml.contains("xmlns:ns0=\"urn:example\""))
        assertTrue(xml.contains("type=\"object\""))
        assertTrue(xml.contains("<ns0:item type=\"string\">a</ns0:item>"))
        assertTrue(xml.contains("<plain type=\"string\">b</plain>"))

        val reparsed = mapper.parse(xml, Node::class.java).asObject()
        assertEquals("a", reparsed["{urn:example}item"]?.asString())
        assertEquals("b", reparsed["plain"]?.asString())
    }

    @Test
    fun clarkModeAssignsPrefixesPerUri() {
        val config = XmlConfig.builder().typeAttributes(true).namespaceMode(XmlNamespaceMode.CLARK).build()
        val mapper = XmlMapper(config)
        val node = ObjectNode.of(
            "{urn:a}x" to StringNode.of("1"),
            "{urn:b}y" to StringNode.of("2"),
            "{urn:a}z" to StringNode.of("3"),
        )

        val xml = mapper.format(node)
        assertTrue(xml.contains("xmlns:ns0=\"urn:a\""))
        assertTrue(xml.contains("xmlns:ns1=\"urn:b\""))
        assertTrue(xml.contains("<ns0:x type=\"string\">1</ns0:x>"))
        assertTrue(xml.contains("<ns1:y type=\"string\">2</ns1:y>"))
        assertTrue(xml.contains("<ns0:z type=\"string\">3</ns0:z>"))
    }

    @Test
    fun clarkScalarAttributeWrapperRoundTrips() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).namespaceMode(XmlNamespaceMode.CLARK).build())
        val xml = """<root><price xmlns:p="urn:p" p:currency="CNY">100</price></root>"""

        val node = mapper.parse(xml, Node::class.java).asObject()
        assertEquals("CNY", node["price"]?.asObject()?.get("@{urn:p}currency")?.asString())
        assertEquals(100, node["price"]?.asObject()?.get("#text")?.asNumber()?.toInt())

        val output = mapper.format(node)
        assertTrue(output.contains("<price type=\"number\" ns0:currency=\"CNY\">100</price>"))
        val reparsed = mapper.parse(output, Node::class.java).asObject()["price"]?.asObject()
        assertEquals("CNY", reparsed?.get("@{urn:p}currency")?.asString())
        assertEquals(100, reparsed?.get("#text")?.asNumber()?.toInt())
    }

    @Test
    fun clarkScalarRootNameGetsDeclaration() {
        val config = XmlConfig.builder().typeAttributes(true)
            .namespaceMode(XmlNamespaceMode.CLARK)
            .rootName("{urn:example}value")
            .build()
        val mapper = XmlMapper(config)

        val xml = mapper.format(StringNode.of("a"))
        assertTrue(xml.startsWith("<ns0:value xmlns:ns0=\"urn:example\" type=\"string\">a</ns0:value>"))
        assertEquals("a", mapper.parse(xml, Node::class.java).asString())
    }

    @Test
    fun prefixModeStillKeepsQualifiedNames() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).attributePrefix("@").namespaceAware(true).build())
        val xml = """<ns:person xmlns:ns="urn:example" ns:id="7"><ns:name>小于</ns:name></ns:person>"""

        val node = mapper.parse(xml, Node::class.java).asObject()
        assertEquals("urn:example", node["@xmlns:ns"]?.asString())
        assertEquals(7, node["@ns:id"]?.asNumber()?.toInt())
        assertEquals("小于", node["ns:name"]?.asString())
        assertFalse(node.containsKey("{urn:example}name"))
    }

    @Test
    fun mixedContentPreserveKeepsTextAndChildren() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).build())
        val node = mapper.parse("<p>Hello <b>world</b>!</p>", Node::class.java).asObject()

        assertEquals("Hello !", node["#text"]?.asString())
        assertEquals("world", node["b"]?.asString())

        val output = mapper.format(node)
        assertEquals("<root type=\"object\">Hello !<b type=\"string\">world</b></root>", output)
        val reparsed = mapper.parse(output, Node::class.java).asObject()
        assertEquals("Hello !", reparsed["#text"]?.asString())
        assertEquals("world", reparsed["b"]?.asString())
    }

    @Test
    fun mixedContentWithAttributesIsPreserved() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).attributePrefix("@").build())
        val node = mapper.parse("""<p id="1">Hello <b>world</b>!</p>""", Node::class.java).asObject()

        assertEquals(1, node["@id"]?.asNumber()?.toInt())
        assertEquals("Hello !", node["#text"]?.asString())
        assertEquals("world", node["b"]?.asString())

        val output = mapper.format(node)
        assertEquals("<root type=\"object\" id=\"1\">Hello !<b type=\"string\">world</b></root>", output)
        val reparsed = mapper.parse(output, Node::class.java).asObject()
        assertEquals(1, reparsed["@id"]?.asNumber()?.toInt())
        assertEquals("Hello !", reparsed["#text"]?.asString())
    }

    @Test
    fun mixedContentPreserveIsStableWithPrettyFormatting() {
        val mapper = XmlMapper()
        val node = mapper.parse("<p>Hello <b>world</b>!</p>", Node::class.java)

        val pretty = mapper.formatPretty(node)
        val reparsed = mapper.parse(pretty, Node::class.java).asObject()
        assertEquals("Hello !", reparsed["#text"]?.asString())
        assertEquals("world", reparsed["b"]?.asString())
    }

    @Test
    fun mixedContentRejectThrowsParseException() {
        val config = XmlConfig.builder().typeAttributes(true).mixedContentPolicy(XmlMixedContentPolicy.REJECT).build()
        val mapper = XmlMapper(config)

        assertFailsWith<XmlParseException> {
            mapper.parse("<p>Hello <b>world</b>!</p>", Node::class.java)
        }
        assertFailsWith<XmlParseException> {
            mapper.parse("<root type=\"object\">abc<b/></root>", Node::class.java)
        }
        assertFailsWith<XmlParseException> {
            mapper.parse("<root><list type=\"array\">abc<item>1</item></list></root>", Node::class.java)
        }
    }

    @Test
    fun mixedContentRejectAllowsFormattingWhitespace() {
        val config = XmlConfig.builder().typeAttributes(true).mixedContentPolicy(XmlMixedContentPolicy.REJECT).build()
        val mapper = XmlMapper(config)
        val pretty = ObjectNode.of("b" to StringNode.of("world")).toXmlString(pretty = true)

        val node = mapper.parse(pretty, Node::class.java).asObject()
        assertEquals("world", node["b"]?.asString())
        assertFalse(node.containsKey("#text"))
    }

    @Test
    fun legacyNodeShapesAreUnchanged() {
        val node = ObjectNode.of(
            "name" to StringNode.of("Bob"),
            "tags" to ArrayNode.from(listOf(StringNode.of("a"))),
        )
        assertEquals(
            "<root type=\"object\"><name type=\"string\">Bob</name>" +
                "<tags type=\"array\"><item type=\"string\">a</item></tags></root>",
            node.toXmlString(typeAttributes = true),
        )

        val parsed = XmlReader("<root type=\"object\">\n  <name type=\"string\">Bob</name>\n</root>").read().asObject()
        assertEquals(setOf("name"), parsed.keys)

        assertTrue(XmlReader("<name lang=\"en\">Bob</name>").read().isString)
    }

    @Test
    fun clarkModeConfigurationAndValidation() {
        val clark = XmlConfig.builder().typeAttributes(true).namespaceMode(XmlNamespaceMode.CLARK).namespaceAware(false).build()
        assertTrue(clark.namespaceAware)
        assertTrue(clark.clarkNames)

        assertFailsWith<IllegalArgumentException> { XmlConfig.builder().typeAttributes(true).textKey("") }
        assertFailsWith<IllegalArgumentException> { XmlConfig.builder().typeAttributes(true).itemsKey("") }
        assertFailsWith<IllegalArgumentException> { XmlConfig.builder().typeAttributes(true).textKey("#x").itemsKey("#x").build() }
        assertFailsWith<IllegalArgumentException> { XmlConfig.builder().typeAttributes(true).attributePrefix("@").textKey("@text").build() }
        assertFailsWith<IllegalArgumentException> {
            XmlReader("<x/>", namespaceMode = XmlNamespaceMode.CLARK)
        }
    }

    private class Catalog {
        var item: Item? = null
    }

    private class Item {
        var price: Price? = null
    }

    private class Price {
        @field:DocumentName("@currency")
        var currency: String? = null

        @field:DocumentName("#text")
        var amount: Int = 0
    }
}
