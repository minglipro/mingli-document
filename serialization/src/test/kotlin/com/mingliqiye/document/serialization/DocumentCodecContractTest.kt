package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.core.codec.DocumentCodec
import com.mingliqiye.document.core.codec.DocumentParser
import com.mingliqiye.document.core.codec.DocumentWriter
import com.mingliqiye.document.core.codec.write
import com.mingliqiye.document.exception.DocumentException
import com.mingliqiye.document.format.json.JsonCodec
import com.mingliqiye.document.format.json.JsonParseException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [DocumentCodec]、[DocumentParser]、[DocumentWriter] 契约测试。
 *
 * 用 format-json 模块的 [JsonCodec] 验证“解析得到 [Node] 树 / 写回文本 / 非法输入抛异常”的公共契约。
 */
class DocumentCodecContractTest {

    @Test
    fun jsonCodecImplementsParserAndWriterSpi() {
        val parser: DocumentParser = JsonCodec
        val writer: DocumentWriter = JsonCodec
        val codec: DocumentCodec = JsonCodec

        assertSame(JsonCodec, parser)
        assertSame(JsonCodec, writer)
        assertSame(JsonCodec, codec)
        assertEquals("json", codec.format)
    }

    @Test
    fun parseMapsTextToNodeTree() {
        val node = JsonCodec.parse(
            """{"text":"s","int":42,"decimal":1.5,"flag":true,"nothing":null,"list":[1,"x"],"child":{"key":"v"}}""",
        )

        assertEquals(NodeType.OBJECT, node.type)
        val root = node.asObject()
        assertEquals(7, root.size)
        assertEquals("s", root["text"]?.asString())
        assertEquals(42, root["int"]?.asNumber()?.toInt())
        assertEquals(0, BigDecimal("1.5").compareTo(root["decimal"]!!.asNumber().value))
        assertTrue(root["flag"]!!.asBoolean())
        assertSame(NullNode.INSTANCE, root["nothing"])
        assertEquals(2, root["list"]!!.asArray().size)
        assertEquals(1, root["list"]!!.asArray()[0].asNumber().toInt())
        assertEquals("x", root["list"]!!.asArray()[1].asString())
        assertEquals("v", root["child"]!!.asObject()["key"]?.asString())
    }

    @Test
    fun writeCompactsEscapesAndPreservesNumberText() {
        val node = ObjectNode.of(
            "text" to StringNode.of("a\"b\\c\n"),
            "number" to NumberNode.ofRaw("1.50"),
            "array" to ArrayNode.of(BooleanNode.of(true), NullNode.INSTANCE),
            "empty" to ObjectNode.EMPTY,
        )

        assertEquals(
            """{"text":"a\"b\\c\n","number":1.50,"array":[true,null],"empty":{}}""",
            JsonCodec.write(node, false),
        )
    }

    @Test
    fun writePrettyFormatsAndStaysParseable() {
        val node = ObjectNode.of(
            "list" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
            "child" to ObjectNode.of("flag" to BooleanNode.of(false)),
        )

        val pretty = JsonCodec.write(node, true)
        assertTrue(pretty.startsWith("{\n  "))
        assertTrue(pretty.contains("\n  \"list\": ["))
        assertEquals(node, JsonCodec.parse(pretty))
        assertEquals("\"x\"", JsonCodec.write(StringNode.of("x"), true))
    }

    @Test
    fun defaultWriteOverloadUsesCompactMode() {
        val writer: DocumentWriter = JsonCodec
        val node = ObjectNode.of("a" to NumberNode.of(1))

        assertEquals("""{"a":1}""", writer.write(node))
        assertEquals(JsonCodec.write(node, false), writer.write(node))
    }

    @Test
    fun writeThenParseRoundTripsTree() {
        val node = ObjectNode.of(
            "unicode" to StringNode.of("小于\u0000\u001F"),
            "negative" to NumberNode.ofRaw("-0.50"),
            "huge" to NumberNode.ofRaw("123456789012345678901234567890"),
            "nested" to ArrayNode.of(ObjectNode.of("key" to StringNode.of("v")), NullNode.INSTANCE),
        )

        assertEquals(node, JsonCodec.parse(JsonCodec.write(node, false)))
        assertEquals(node, JsonCodec.parse(JsonCodec.write(node, true)))
    }

    @Test
    fun parseRejectsMalformedInputWithJsonParseException() {
        val malformed = listOf(
            """{"a" 1}""",
            """{"a":1 "b":2}""",
            "[true false]",
            "[1,2",
            """{"a":[,]}""",
            "1.",
            ".5",
            "nul",
            "\"\\u12\"",
            """{"a":1}}""",
        )

        for (source in malformed) {
            val error = assertFailsWith<DocumentException>("should reject <$source>") {
                JsonCodec.parse(source)
            }
            assertTrue(error is JsonParseException)
        }
    }

    @Test
    fun parserAndWriterAreFunctionInterfaces() {
        val parser = DocumentParser { source -> StringNode.of(source) }
        assertEquals("raw", parser.parse("raw").asString())

        val writer = DocumentWriter { _, pretty -> if (pretty) "pretty" else "compact" }
        val node: Node = StringNode.of("x")
        assertEquals("compact", writer.write(node))
        assertEquals("pretty", writer.write(node, true))
    }
}
