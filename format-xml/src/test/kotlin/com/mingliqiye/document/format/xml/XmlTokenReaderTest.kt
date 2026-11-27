package com.mingliqiye.document.format.xml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.xml.exception.XmlParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import com.mingliqiye.document.serialization.NodeTokenReader
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [XmlTokenReader] 与 Mapper 原生 Token 路径测试。
 */
class XmlTokenReaderTest {

    @Test
    fun tokenSequenceMatchesNodeTokenReader() {
        for (source in SAMPLES) {
            val expected = readTokens(NodeTokenReader(XmlReader(source).read()))
            val actual = readTokens(XmlTokenReader(source))
            assertEquals(expected, actual, "Token 序列与 Node 路径不一致：$source")
        }
    }

    @Test
    fun rootElementContentIsNotWrapped() {
        val tokens = readTokens(XmlTokenReader("<user><name>x</name></user>"))

        assertEquals(DocumentTokenType.START_OBJECT, tokens[0].type)
        assertEquals(DocumentTokenType.FIELD_NAME, tokens[1].type)
        assertEquals("name", tokens[1].text)
        assertEquals(DocumentTokenType.END_OBJECT, tokens.last().type)
        assertTrue(tokens.none { it.type == DocumentTokenType.FIELD_NAME && it.text == "user" })
    }

    @Test
    fun typeAndNilProtocolTokens() {
        val source = """
            <root type="object">
              <s type="string">text</s>
              <n type="number"> 42 </n>
              <b type="boolean">true</b>
              <bin type="binary">AQID</bin>
              <missing nil="true"/>
            </root>
        """.trimIndent()

        val tokens = readTokens(XmlTokenReader(source))

        assertTrue(tokens.any { it.type == DocumentTokenType.STRING && it.text == "text" })
        assertTrue(tokens.any { it.type == DocumentTokenType.NUMBER && it.text == "42" })
        assertTrue(tokens.any { it.type == DocumentTokenType.BOOLEAN && it.booleanValue == true })
        assertTrue(
            tokens.any {
                it.type == DocumentTokenType.BINARY && it.binaryValue == listOf(1.toByte(), 2.toByte(), 3.toByte())
            },
        )
        assertTrue(tokens.any { it.type == DocumentTokenType.NULL })
    }

    @Test
    fun emptyAndRootElements() {
        assertEquals(
            listOf(TokenSnapshot(DocumentTokenType.STRING, text = "")),
            readTokens(XmlTokenReader("<empty/>")),
        )
        assertEquals(
            listOf(TokenSnapshot(DocumentTokenType.START_OBJECT), TokenSnapshot(DocumentTokenType.END_OBJECT)),
            readTokens(XmlTokenReader("<root type=\"object\"/>")),
        )
        assertEquals(
            listOf(TokenSnapshot(DocumentTokenType.START_ARRAY), TokenSnapshot(DocumentTokenType.END_ARRAY)),
            readTokens(XmlTokenReader("<root type=\"array\"/>")),
        )
        assertEquals(
            listOf(TokenSnapshot(DocumentTokenType.NULL)),
            readTokens(XmlTokenReader("<root nil=\"true\"/>")),
        )
    }

    @Test
    fun rootArrayTokens() {
        val tokens = readTokens(XmlTokenReader("<list><item>1</item><item>2</item></list>"))

        assertEquals(
            listOf(
                TokenSnapshot(DocumentTokenType.START_ARRAY),
                TokenSnapshot(DocumentTokenType.NUMBER, text = "1"),
                TokenSnapshot(DocumentTokenType.NUMBER, text = "2"),
                TokenSnapshot(DocumentTokenType.END_ARRAY),
            ),
            tokens,
        )
    }

    @Test
    fun mixedContentTextIsKeptInTextKey() {
        val tokens = readTokens(XmlTokenReader("<p>Hello <b>world</b>!</p>"))

        assertEquals(
            listOf(
                TokenSnapshot(DocumentTokenType.START_OBJECT),
                TokenSnapshot(DocumentTokenType.FIELD_NAME, text = "b"),
                TokenSnapshot(DocumentTokenType.STRING, text = "world"),
                TokenSnapshot(DocumentTokenType.FIELD_NAME, text = "#text"),
                TokenSnapshot(DocumentTokenType.STRING, text = "Hello !"),
                TokenSnapshot(DocumentTokenType.END_OBJECT),
            ),
            tokens,
        )
    }

    @Test
    fun mixedArrayTextWrapsItemsAndText() {
        val tokens = readTokens(XmlTokenReader("<r>x<item>1</item>y</r>"))

        assertEquals(
            listOf(
                TokenSnapshot(DocumentTokenType.START_OBJECT),
                TokenSnapshot(DocumentTokenType.FIELD_NAME, text = "#items"),
                TokenSnapshot(DocumentTokenType.START_ARRAY),
                TokenSnapshot(DocumentTokenType.NUMBER, text = "1"),
                TokenSnapshot(DocumentTokenType.END_ARRAY),
                TokenSnapshot(DocumentTokenType.FIELD_NAME, text = "#text"),
                TokenSnapshot(DocumentTokenType.STRING, text = "xy"),
                TokenSnapshot(DocumentTokenType.END_OBJECT),
            ),
            tokens,
        )
    }

    @Test
    fun formattedWhitespaceIsIgnored() {
        val tokens = readTokens(XmlTokenReader("<r>\n  <a>1</a>\n</r>"))

        assertEquals(
            listOf(
                TokenSnapshot(DocumentTokenType.START_OBJECT),
                TokenSnapshot(DocumentTokenType.FIELD_NAME, text = "a"),
                TokenSnapshot(DocumentTokenType.NUMBER, text = "1"),
                TokenSnapshot(DocumentTokenType.END_OBJECT),
            ),
            tokens,
        )
    }

    @Test
    fun duplicateChildrenAreGroupedIntoArray() {
        val tokens = readTokens(XmlTokenReader("<r><x>1</x><x>2</x></r>"))

        assertEquals(
            listOf(
                TokenSnapshot(DocumentTokenType.START_OBJECT),
                TokenSnapshot(DocumentTokenType.FIELD_NAME, text = "x"),
                TokenSnapshot(DocumentTokenType.START_ARRAY),
                TokenSnapshot(DocumentTokenType.NUMBER, text = "1"),
                TokenSnapshot(DocumentTokenType.NUMBER, text = "2"),
                TokenSnapshot(DocumentTokenType.END_ARRAY),
                TokenSnapshot(DocumentTokenType.END_OBJECT),
            ),
            tokens,
        )
    }

    @Test
    fun readerContractAndEndState() {
        val reader = XmlTokenReader("<a>1</a>")

        assertNull(reader.currentToken)
        assertEquals(DocumentTokenType.NUMBER, reader.nextToken())
        assertEquals("1", reader.textValue)
        assertNull(reader.booleanValue)
        assertNull(reader.binaryValue)
        assertNull(reader.nextToken())
        assertNull(reader.currentToken)
        assertNull(reader.textValue)
    }

    @Test
    fun invalidXmlAndDoctypeAreRejected() {
        val malformed = "<root><a></root>"
        val nativeError = assertFailsWith<XmlParseException> { XmlTokenReader(malformed) }
        val nodeError = assertFailsWith<XmlParseException> { XmlReader(malformed).read() }
        assertEquals(nodeError.message, nativeError.message)

        val xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <root><name>&xxe;</name></root>
        """.trimIndent()
        assertFailsWith<XmlParseException> { XmlTokenReader(xxe) }
    }

    @Test
    fun invalidProtocolValuesThrowSameExceptionAsNodeReader() {
        for (source in listOf("<a type=\"number\">x</a>", "<a type=\"boolean\">maybe</a>")) {
            val nativeError = assertFailsWith<XmlParseException> { XmlTokenReader(source) }
            val nodeError = assertFailsWith<XmlParseException> { XmlReader(source).read() }
            assertEquals(nodeError.message, nativeError.message)
        }
    }

    @Test
    fun mapperParsesBeanThroughNativeReader() {
        val mapper = ProbeXmlMapper()
        val xml = """
            <person>
              <name>小于</name>
              <age>18</age>
              <tags><item>a</item><item>b</item></tags>
              <address><city>杭州</city></address>
            </person>
        """.trimIndent()

        val person = mapper.parse(xml, Person::class.java)

        assertTrue(mapper.readerUsed is XmlTokenReader)
        assertEquals("小于", person.name)
        assertEquals(18, person.age)
        assertEquals(listOf("a", "b"), person.tags)
        assertEquals("杭州", person.address?.city)

        val expected = mapper.fromNode(XmlReader(xml).read(), Person::class.java)
        assertEquals(expected.name, person.name)
        assertEquals(expected.age, person.age)
        assertEquals(expected.tags, person.tags)
        assertEquals(expected.address?.city, person.address?.city)
    }

    @Test
    fun mapperParsesMapThroughNativeReader() {
        val mapper = ProbeXmlMapper()
        val xml = """
            <root>
              <name>x</name>
              <count>3</count>
              <nested><flag>true</flag></nested>
              <list><item>1</item><item>2</item></list>
            </root>
        """.trimIndent()

        val map = mapper.parse(xml, Map::class.java)

        assertTrue(mapper.readerUsed is XmlTokenReader)
        assertEquals("x", (map["name"] as StringNode).value)
        assertEquals(3, (map["count"] as NumberNode).toInt())
        assertEquals(true, ((map["nested"] as ObjectNode)["flag"] as BooleanNode).value)
        assertEquals(2, (map["list"] as ArrayNode).size)
    }

    @Test
    fun defaultConfigsUseNativeReaderProbe() {
        val defaultProbe = ProbeXmlMapper()
        defaultProbe.parse("<root><name>x</name></root>", Node::class.java)
        assertTrue(defaultProbe.readerUsed is XmlTokenReader)

        val genericProbe = ProbeXmlMapper(DocumentMapperConfig.empty())
        genericProbe.parse("<root><name>x</name></root>", Node::class.java)
        assertTrue(genericProbe.readerUsed is XmlTokenReader)

        // rootName 只影响写出，读取仍走原生路径。
        val rootNameProbe = ProbeXmlMapper(XmlConfig.builder().rootName("person").build())
        rootNameProbe.parse("<root><name>x</name></root>", Node::class.java)
        assertTrue(rootNameProbe.readerUsed is XmlTokenReader)
    }

    @Test
    fun nonDefaultConfigsFallBackToNodeTokenReaderProbe() {
        val configs = listOf(
            XmlConfig.builder().attributePrefix("@").build(),
            XmlConfig.builder().namespaceAware(true).build(),
            XmlConfig.builder().namespaceMode(XmlNamespaceMode.CLARK).build(),
            XmlConfig.builder().textKey("value").build(),
            XmlConfig.builder().itemsKey("values").build(),
            XmlConfig.builder().contentKey("nodes").build(),
            XmlConfig.builder().mixedContentPolicy(XmlMixedContentPolicy.PRESERVE_ORDERED).build(),
            XmlConfig.builder().mixedContentPolicy(XmlMixedContentPolicy.REJECT).build(),
        )
        for (config in configs) {
            val probe = ProbeXmlMapper(config)
            probe.parse("<root><name>x</name></root>", Node::class.java)
            assertTrue(probe.readerUsed is NodeTokenReader, "非默认配置应回退 NodeTokenReader：$config")
        }
    }

    @Test
    fun nodeLimitsAndReferenceResolutionFallBackToNodeReader() {
        val limitsProbe = ProbeXmlMapper(DocumentMapperConfig.builder().maxStringLength(3).build())
        limitsProbe.parse("<r><a>1</a></r>", Map::class.java)
        assertTrue(limitsProbe.readerUsed is NodeTokenReader)

        val stringError = assertFailsWith<DocumentSerializationException> {
            limitsProbe.parse("<r><name>abcd</name></r>", Map::class.java)
        }
        assertTrue(stringError.message.orEmpty().contains("maxStringLength 3"))

        val depthProbe = ProbeXmlMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build())
        val depthError = assertFailsWith<XmlParseException> {
            depthProbe.parse("<a><b><c>1</c></b></a>", Map::class.java)
        }
        assertTrue(depthError.message.orEmpty().contains("maxDepth 2"))

        val countProbe = ProbeXmlMapper(DocumentMapperConfig.builder().maxNodeCount(10).build())
        countProbe.parse("<r><a>1</a></r>", Map::class.java)
        assertTrue(countProbe.readerUsed is NodeTokenReader)

        val referenceProbe = ProbeXmlMapper(DocumentMapperConfig.builder().resolveReferences(true).build())
        referenceProbe.parse("<r><a>1</a></r>", Map::class.java)
        assertTrue(referenceProbe.readerUsed is NodeTokenReader)
    }

    private fun readTokens(reader: DocumentTokenReader): List<TokenSnapshot> {
        val tokens = ArrayList<TokenSnapshot>()
        while (true) {
            val type = reader.nextToken() ?: break
            tokens.add(
                TokenSnapshot(
                    type,
                    text = reader.textValue,
                    booleanValue = reader.booleanValue,
                    binaryValue = reader.binaryValue?.toList(),
                ),
            )
        }
        return tokens
    }

    private class ProbeXmlMapper : XmlMapper {
        var readerUsed: DocumentTokenReader? = null

        constructor() : super()

        constructor(config: XmlConfig) : super(config)

        constructor(config: DocumentMapperConfig) : super(config)

        override fun createTextTokenReader(source: String): DocumentTokenReader = super.createTextTokenReader(source).also { readerUsed = it }
    }

    private data class TokenSnapshot(
        val type: DocumentTokenType,
        val text: String? = null,
        val booleanValue: Boolean? = null,
        val binaryValue: List<Byte>? = null,
    )

    private class Person {
        var name: String? = null
        var age: Int = 0
        var tags: List<String> = emptyList()
        var address: Address? = null
    }

    private class Address {
        var city: String? = null
    }

    private companion object {

        private val SAMPLES = listOf(
            "<user><name>小于</name><age>18</age></user>",
            "<user><name>小于</name><address><city>杭州</city></address></user>",
            "<user><tags><item>a</item><item>b</item></tags><scores><item>1</item><item>2</item></scores></user>",
            "<list><item>1</item><item>2</item></list>",
            "<list><item><name>a</name></item><item><name>b</name></item></list>",
            "<solo type=\"string\">x</solo>",
            "<solo type=\"number\">-1.5e2</solo>",
            "<solo type=\"boolean\">false</solo>",
            "<root type=\"object\"><name type=\"string\">x</name><age type=\"number\">3</age></root>",
            "<root type=\"array\"><item>1</item><item>2</item></root>",
            "<root type=\"binary\">AQID</root>",
            "<root><empty/><blank></blank><nilValue nil=\"true\"/></root>",
            "<root nil=\"true\"/>",
            "<a><b><c><d>deep</d></c></b></a>",
            "<p>Hello <b>world</b>!</p>",
            "<r>x<item>1</item>y</r>",
            "<root>\n  <a>1</a>\n  <b>2</b>\n</root>",
            "<r><x>1</x><x>2</x></r>",
            "<r><user_x0020_name>v</user_x0020_name></r>",
            "<root><unknown type=\"mystery\"><q>1</q></unknown></root>",
            "<root><attr id=\"7\"/><name>x</name></root>",
            "<root type=\"object\"/>",
            "<root type=\"array\"/>",
            "<empty/>",
            "<root><v type=\"string\">5</v><w type=\"number\"> 6 </w></root>",
            "<root>  </root>",
            "<root type=\"object\">abc</root>",
            "<root><a>x</a> tail</root>",
            "<ns:root xmlns:ns=\"urn:x\"><ns:name>x</ns:name></ns:root>",
            "<root><![CDATA[<x>&]]></root>",
            "<root type=\"array\">x<item>1</item></root>",
            "<root type=\"object\">a<b>1</b></root>",
            "<root id=\"7\"/>",
            "<root> 5 </root>",
            "<p>\n Hello <b>x</b>!\n</p>",
            "<list>x<item>1</item></list>",
        )
    }
}
