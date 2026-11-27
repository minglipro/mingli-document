package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.exception.DocumentTypeException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonCodecTest {

    @Test
    fun codecSpi() {
        val node = JsonCodec.parse("""{"a":1}""")
        assertEquals("json", JsonCodec.format)
        assertEquals("""{"a":1}""", JsonCodec.write(node, false))
    }

    @Test
    fun parseObject() {
        val node = JsonReader(
            """{"name":"小于","age":18,"pi":3.14,"ok":true,"none":null,"list":[1,2],"child":{"x":"y"}}""",
        ).read()

        assertTrue(node.isObject)
        assertEquals(NodeType.OBJECT, node.type)
        val obj = node.asObject()
        assertEquals(7, obj.size)
        assertEquals("小于", obj["name"]?.asString())
        assertEquals(18, obj["age"]?.asNumber()?.toInt())
        assertEquals(3.14, obj["pi"]?.asNumber()?.toDouble())
        assertEquals(true, obj["ok"]?.asBoolean())
        assertTrue(obj["none"]!!.isNull)
        assertEquals(2, obj["list"]!!.asArray().size)
        assertEquals(1, obj["list"]!!.asArray()[0].asNumber().toInt())
        assertEquals("y", obj["child"]!!.asObject()["x"]?.asString())
    }

    @Test
    fun parseStringEscapes() {
        val json = "\"line\\n tab\\t quote\\\" slash\\\\ unicode\\u4F60\\u597D\""
        assertEquals("line\n tab\t quote\" slash\\ unicode你好", JsonReader(json).read().asString())
    }

    @Test
    fun parseNumbers() {
        val array = JsonReader("[0,-1,1.5,2e3,-4.2E-2,999999999999999999999999]").read().asArray()

        assertEquals(6, array.size)
        assertEquals("0", array[0].asNumber().raw)
        assertEquals(0, array[0].asNumber().toInt())
        assertEquals(-1, array[1].asNumber().toInt())
        assertEquals(0, BigDecimal("1.5").compareTo(array[2].asNumber().value))
        assertEquals(0, BigDecimal("2000").compareTo(array[3].asNumber().value))
        assertEquals(0, BigDecimal("-0.042").compareTo(array[4].asNumber().value))
        assertEquals("999999999999999999999999", array[5].asNumber().raw)
    }

    @Test
    fun parseCommentsAndExtraWhitespace() {
        val json = """
            // header
            {
                "a" /* key */ : 1, // line comment
                "b": [ true, /* inline */ null ],
                "url": "http://example.com/*not-comment*/"
            }
            // footer
        """.trimIndent()

        val obj = JsonReader(json).read().asObject()
        assertEquals(1, obj["a"]?.asNumber()?.toInt())
        assertEquals(2, obj["b"]?.asArray()?.size)
        assertEquals("http://example.com/*not-comment*/", obj["url"]?.asString())
    }

    @Test
    fun parseUnicodeWhitespace() {
        val json = "\uFEFF\u00A0{\u000B\"a\"\u000C:\u2028\u00A01\u3000}\u2029"
        val obj = JsonReader(json).read().asObject()
        assertEquals(1, obj["a"]?.asNumber()?.toInt())
    }

    @Test
    fun strictModeRejectsComments() {
        assertFailsWith<JsonParseException> {
            JsonReader("""{"a":1} // strict""", allowComments = false).read()
        }
    }

    @Test
    fun unterminatedBlockComment() {
        assertFailsWith<JsonParseException> {
            JsonReader("{/* unterminated").read()
        }
    }

    @Test
    fun parseNullBooleanAndWhitespace() {
        assertTrue(JsonReader("  null \n\t").read().isNull)
        assertTrue(JsonReader("true").read().asBoolean())
        assertFalse(JsonReader("false").read().asBoolean())
    }

    @Test
    fun builderAndRoundTrip() {
        val node = ObjectNode.of(
            "a" to NumberNode.of(1),
            "b" to ArrayNode.of(StringNode("x"), NullNode),
            "c" to ObjectNode.of("nested" to BooleanNode(true)),
        )

        assertEquals("""{"a":1,"b":["x",null],"c":{"nested":true}}""", node.toJsonString())
        assertEquals(node, JsonReader(node.toJsonString()).read())
    }

    @Test
    fun prettyPrint() {
        val node = ObjectNode.of("a" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)))
        assertEquals(
            """
            {
              "a": [
                1,
                2
              ]
            }
            """.trimIndent(),
            node.toJsonString(pretty = true),
        )
    }

    @Test
    fun numberEqualityIsNumeric() {
        assertEquals(NumberNode.of(1), NumberNode.of(1.0))
        assertEquals(NumberNode.of(1).hashCode(), NumberNode.of(1.0).hashCode())
    }

    @Test
    fun typeMismatch() {
        val array = JsonReader("[]").read()
        assertTrue(array.isArray)
        assertNull(array.asObjectOrNull())
        assertFailsWith<DocumentTypeException> { array.asObject() }
        assertFailsWith<DocumentTypeException> { array.asString() }
    }

    @Test
    fun invalidJson() {
        val invalid = listOf(
            "",
            " ",
            "[",
            "{",
            "}",
            "{\"a\":}",
            "{\"a\":[1}",
            "{\"a\":}",
            "{\"a\":1,}",
            "[1,]",
            "[01]",
            "+1",
            "\"unterminated",
            "tru",
            "{} extra",
        )
        for (json in invalid) {
            assertFailsWith<JsonParseException>("should fail: <$json>") {
                JsonReader(json).read()
            }
        }
    }

    @Test
    fun extensionParseAndWrite() {
        val node = """{"x":[1,true,null]}""".toJsonNode()
        assertEquals("""{"x":[1,true,null]}""", node.toJsonString())
    }
}
