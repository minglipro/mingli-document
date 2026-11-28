package com.mingliqiye.document.format.xml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.xml.exception.XmlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * XML 混合内容顺序保留测试。
 *
 * [XmlMixedContentPolicy.PRESERVE_ORDERED] 在 `#text` 兼容视图之外输出 `#content` 有序数组：
 * 文本项为 `StringNode`，子元素项为单字段 `ObjectNode`（`{元素名: 节点}`）。
 */
class XmlMixedOrderTest {

    @Test
    fun orderedContentKeepsTextElementTextOrder() {
        val mapper = orderedMapper()
        val node = mapper.parse("<p>Hello <b>world</b>!</p>", Node::class.java).asObject()

        val content = assertNotNull(node["#content"]).asArray()
        assertEquals(3, content.size)
        assertEquals("Hello ", content[0].asString())
        val elementEntry = content[1].asObject()
        assertEquals(1, elementEntry.size)
        assertEquals("world", elementEntry["b"]?.asString())
        assertEquals("!", content[2].asString())

        // 兼容视图：旧 #text 与分组子元素字段仍保留。
        assertEquals("Hello !", node["#text"]?.asString())
        assertEquals("world", node["b"]?.asString())
    }

    @Test
    fun orderedContentRoundTripsThroughWriter() {
        val mapper = orderedMapper()
        val node = mapper.parse("<p>Hello <b>world</b>!</p>", Node::class.java)

        val output = mapper.format(node)
        assertEquals("<root type=\"object\">Hello <b type=\"string\">world</b>!</root>", output)

        val reparsed = mapper.parse(output, Node::class.java).asObject()
        val content = assertNotNull(reparsed["#content"]).asArray()
        assertEquals(3, content.size)
        assertEquals("Hello ", content[0].asString())
        assertEquals("world", content[1].asObject()?.get("b")?.asString())
        assertEquals("!", content[2].asString())
    }

    @Test
    fun orderedContentWithAttributes() {
        val mapper = XmlMapper(
            XmlConfig.builder().typeAttributes(true)
                .attributePrefix("@")
                .mixedContentPolicy(XmlMixedContentPolicy.PRESERVE_ORDERED)
                .build(),
        )
        val node = mapper.parse("""<p id="1">Text <b>bold</b> tail</p>""", Node::class.java).asObject()

        assertEquals(1, node["@id"]?.asNumber()?.toInt())
        val content = assertNotNull(node["#content"]).asArray()
        assertEquals(3, content.size)
        assertEquals("Text ", content[0].asString())
        assertEquals("bold", content[1].asObject()?.get("b")?.asString())
        assertEquals(" tail", content[2].asString())

        val output = mapper.format(node)
        assertEquals(
            "<root type=\"object\" id=\"1\">Text <b type=\"string\">bold</b> tail</root>",
            output,
        )
        val reparsed = mapper.parse(output, Node::class.java).asObject()
        assertEquals(3, reparsed["#content"]?.asArray()?.size)
        assertEquals(" tail", reparsed["#content"]?.asArray()?.get(2)?.asString())
    }

    @Test
    fun nestedOrderedMixedContentRoundTrips() {
        val mapper = orderedMapper()
        val xml = "<div>before <p>inner <i>it</i> end</p> after</div>"
        val node = mapper.parse(xml, Node::class.java).asObject()

        val content = assertNotNull(node["#content"]).asArray()
        assertEquals(3, content.size)
        assertEquals("before ", content[0].asString())
        assertEquals(" after", content[2].asString())
        val paragraph = assertNotNull(content[1].asObject()?.get("p")).asObject()
        val nested = assertNotNull(paragraph["#content"]).asArray()
        assertEquals(3, nested.size)
        assertEquals("inner ", nested[0].asString())
        assertEquals("it", nested[1].asObject()?.get("i")?.asString())
        assertEquals(" end", nested[2].asString())

        val output = mapper.format(node)
        assertTrue(output.contains("before <p type=\"object\">inner <i type=\"string\">it</i> end</p> after"))
        val reparsed = mapper.parse(output, Node::class.java).asObject()
        val reparsedContent = assertNotNull(reparsed["#content"]).asArray()
        assertEquals(" after", reparsedContent[2].asString())
        val reparsedParagraph = assertNotNull(reparsedContent[1].asObject()?.get("p")).asObject()
        assertEquals(
            " end",
            assertNotNull(reparsedParagraph["#content"]).asArray()[2].asString(),
        )
    }

    @Test
    fun prettyFormattingKeepsMixedOrderStable() {
        val mapper = orderedMapper()
        val node = mapper.parse("<div>before <p><q>x</q></p> after</div>", Node::class.java)

        val pretty = mapper.formatPretty(node)
        assertTrue(pretty.contains("\n"))

        val reparsed = mapper.parse(pretty, Node::class.java).asObject()
        val content = assertNotNull(reparsed["#content"]).asArray()
        assertEquals(3, content.size)
        assertEquals("before ", content[0].asString())
        assertEquals("x", content[1].asObject()?.get("p")?.asObject()?.get("q")?.asString())
        assertEquals(" after", content[2].asString())

        assertEquals(pretty, mapper.formatPretty(reparsed))
    }

    @Test
    fun orderedTextIsEscapedOnWrite() {
        val mapper = orderedMapper()
        val xml = "<p>a &lt; b &amp; c <b>x</b> d &gt; e</p>"
        val node = mapper.parse(xml, Node::class.java)

        val output = mapper.format(node)
        assertTrue(output.contains("a &lt; b &amp; c <b type=\"string\">x</b> d &gt; e"))
        assertEquals(1, numberOccurrences(output, "<b type=\"string\">"))

        val reparsed = mapper.parse(output, Node::class.java).asObject()
        val content = assertNotNull(reparsed["#content"]).asArray()
        assertEquals("a < b & c ", content[0].asString())
        assertEquals(" d > e", content[2].asString())
    }

    @Test
    fun orderedContentKeyIsConfigurable() {
        val config = XmlConfig.builder().typeAttributes(true)
            .mixedContentPolicy(XmlMixedContentPolicy.PRESERVE_ORDERED)
            .contentKey("nodes")
            .build()
        val mapper = XmlMapper(config)
        val node = mapper.parse("<p>Hi <b>x</b></p>", Node::class.java).asObject()

        val content = assertNotNull(node["nodes"]).asArray()
        assertEquals(2, content.size)
        assertEquals("Hi ", content[0].asString())
        assertEquals("x", content[1].asObject()?.get("b")?.asString())
        assertNull(node["#content"])
        assertTrue(mapper.format(node).contains("Hi <b type=\"string\">x</b>"))
    }

    @Test
    fun orderedArrayMixedContentRoundTrips() {
        val mapper = XmlMapper(
            XmlConfig.builder().typeAttributes(true)
                .attributePrefix("@")
                .mixedContentPolicy(XmlMixedContentPolicy.PRESERVE_ORDERED).disable(XmlWriteFeature.UNWRAP_ROOT_OBJECT_NODE)
                .build(),
        )
        val xml = """<root><list id="1">pre<item>a</item>post</list></root>"""
        val node = mapper.parse(xml, Node::class.java).asObject()
        val list = assertNotNull(node["list"]).asObject()
        val content = assertNotNull(list["#content"]).asArray()

        assertEquals(3, content.size)
        assertEquals("pre", content[0].asString())
        assertEquals("a", content[1].asObject()?.get("item")?.asString())
        assertEquals("post", content[2].asString())

        val output = mapper.format(node)
        assertTrue(output.contains("<list type=\"array\" id=\"1\">pre<item type=\"string\">a</item>post</list>"))
        val reparsed = mapper.parse(output, Node::class.java).asObject()["list"]?.asObject()
        assertEquals("post", reparsed?.get("#content")?.asArray()?.get(2)?.asString())
    }

    @Test
    fun preservePolicyStillEmitsOnlyTextKey() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).build())
        val node = mapper.parse("<p>Hello <b>world</b>!</p>", Node::class.java).asObject()

        assertNull(node["#content"])
        assertEquals("Hello !", node["#text"]?.asString())
        assertEquals("world", node["b"]?.asString())
        assertEquals("<root type=\"object\">Hello !<b type=\"string\">world</b></root>", mapper.format(node))
    }

    @Test
    fun rejectPolicyStillThrows() {
        val mapper = XmlMapper(
            XmlConfig.builder().typeAttributes(true).mixedContentPolicy(XmlMixedContentPolicy.REJECT).build(),
        )

        assertFailsWith<XmlParseException> {
            mapper.parse("<p>Hello <b>world</b>!</p>", Node::class.java)
        }
    }

    @Test
    fun orderedPolicyKeepsPlainShapesUnchanged() {
        val mapper = orderedMapper()

        assertTrue(mapper.parse("<name>Bob</name>", Node::class.java).isString)
        val plainObject = mapper.parse("<r><a>1</a><b>2</b></r>", Node::class.java).asObject()
        assertEquals(setOf("a", "b"), plainObject.keys)

        val formatted = mapper.parse("<r>\n  <a>1</a>\n</r>", Node::class.java).asObject()
        assertEquals(setOf("a"), formatted.keys)
    }

    @Test
    fun orderedContentTakesPrecedenceOverCompatViews() {
        val node = ObjectNode.of(
            "b" to StringNode.of("ignored"),
            "#text" to StringNode.of("ignored"),
            "#content" to ArrayNode.from(
                listOf(
                    StringNode.of("X"),
                    ObjectNode.of("b" to StringNode.of("Y")),
                ),
            ),
        )

        assertEquals("<root type=\"object\">X<b type=\"string\">Y</b></root>", node.toXmlString(typeAttributes = true))
    }

    @Test
    fun orderedMixedContentWorksWithClarkNamespaces() {
        val config = XmlConfig.builder().typeAttributes(true)
            .namespaceMode(XmlNamespaceMode.CLARK)
            .mixedContentPolicy(XmlMixedContentPolicy.PRESERVE_ORDERED)
            .build()
        val mapper = XmlMapper(config)
        val node = mapper.parse("""<root xmlns:n="urn:x">pre<n:a>x</n:a>post</root>""", Node::class.java)

        val output = mapper.format(node)
        assertTrue(output.contains("xmlns:ns0=\"urn:x\""))
        assertTrue(output.contains("pre<ns0:a type=\"string\">x</ns0:a>post"))

        val reparsed = mapper.parse(output, Node::class.java).asObject()
        val content = assertNotNull(reparsed["#content"]).asArray()
        assertEquals(3, content.size)
        assertEquals("x", content[1].asObject()?.get("{urn:x}a")?.asString())
    }

    @Test
    fun invalidOrderedEntriesAreRejected() {
        val multipleFields = ObjectNode.of(
            "#content" to ArrayNode.from(
                listOf(ObjectNode.of("a" to StringNode.of("1"), "b" to StringNode.of("2"))),
            ),
        )
        assertFailsWith<IllegalArgumentException> { multipleFields.toXmlString() }

        val wrongArrayItemName = ObjectNode.of(
            "#items" to ArrayNode.from(emptyList()),
            "#content" to ArrayNode.from(listOf(ObjectNode.of("x" to StringNode.of("a")))),
        )
        assertFailsWith<IllegalArgumentException> { wrongArrayItemName.toXmlString() }
    }

    private fun orderedMapper(): XmlMapper = XmlMapper(
        XmlConfig.builder().typeAttributes(true).mixedContentPolicy(XmlMixedContentPolicy.PRESERVE_ORDERED).build(),
    )

    private fun numberOccurrences(text: String, target: String): Int {
        var count = 0
        var index = text.indexOf(target)
        while (index >= 0) {
            count++
            index = text.indexOf(target, index + target.length)
        }
        return count
    }
}
