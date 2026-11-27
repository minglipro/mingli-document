package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.yaml.exception.YamlParseException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YamlCodecTest {

    @Test
    fun codecSpi() {
        val node = YamlCodec.parse("a: 1\n")
        assertEquals("yaml", YamlCodec.format)
        assertTrue(YamlCodec.write(node, false).contains("a: 1"))
    }

    @Test
    fun parseObject() {
        val yaml = """
            name: 小于
            age: 18
            pi: 3.14
            ok: true
            none: null
            list:
              - 1
              - 2
            child:
              x: y
        """.trimIndent()

        val node = YamlReader(yaml).read()
        assertTrue(node.isObject)
        assertEquals(NodeType.OBJECT, node.type)
        val obj = node.asObject()
        assertEquals("小于", obj["name"]?.asString())
        assertEquals(18, obj["age"]?.asNumber()?.toInt())
        assertEquals(0, BigDecimal("3.14").compareTo(obj["pi"]!!.asNumber().value))
        assertEquals(true, obj["ok"]?.asBoolean())
        assertTrue(obj["none"]!!.isNull)
        assertEquals(2, obj["list"]!!.asArray().size)
        assertEquals(1, obj["list"]!!.asArray()[0].asNumber().toInt())
        assertEquals("y", obj["child"]!!.asObject()["x"]?.asString())
    }

    @Test
    fun parseFlowStyleAndComments() {
        val yaml = """
            # header
            {a: 1, b: [true, null], url: "http://example.com/#hash"} # footer
        """.trimIndent()

        val obj = YamlReader(yaml).read().asObject()
        assertEquals(1, obj["a"]?.asNumber()?.toInt())
        assertEquals(2, obj["b"]?.asArray()?.size)
        assertTrue(obj["b"]!!.asArray()[1].isNull)
        assertEquals("http://example.com/#hash", obj["url"]?.asString())
    }

    @Test
    fun parseNumbers() {
        val array = YamlReader("[0, -1, 1.5, 2e3, -4.2E-2, 999999999999999999999999]")
            .read()
            .asArray()

        assertEquals(6, array.size)
        assertEquals("0", array[0].asNumber().raw)
        assertEquals(-1, array[1].asNumber().toInt())
        assertEquals(0, BigDecimal("1.5").compareTo(array[2].asNumber().value))
        assertEquals(0, BigDecimal("2000").compareTo(array[3].asNumber().value))
        assertEquals(0, BigDecimal("-0.042").compareTo(array[4].asNumber().value))
        assertEquals("999999999999999999999999", array[5].asNumber().raw)
    }

    @Test
    fun parseBlockScalarAndUnicode() {
        val yaml = """
            text: |
              line1
              line2
            unicode: 小于
        """.trimIndent()

        val obj = YamlReader(yaml).read().asObject()
        assertEquals("line1\nline2\n", obj["text"]?.asString())
        assertEquals("小于", obj["unicode"]?.asString())
    }

    @Test
    fun nonStringKeysAreConverted() {
        val obj = YamlReader("1: one\n2: two\n").read().asObject()
        assertEquals("one", obj["1"]?.asString())
        assertEquals("two", obj["2"]?.asString())
    }

    @Test
    fun emptyDocumentIsNullNode() {
        assertTrue(YamlReader("").read().isNull)
        assertTrue(YamlReader("# only comment\n").read().isNull)
    }

    @Test
    fun invalidYamlThrowsDocumentException() {
        assertFailsWith<YamlParseException> {
            YamlReader("a: [1\n").read()
        }
    }

    @Test
    fun elementExtensions() {
        val node = "a: 1\n".toYamlNode()
        assertTrue(node.isObject)
        assertTrue(node.toYamlString().contains("a: 1"))
    }

    @Test
    fun writerRoundTrip() {
        val node = ObjectNode.of(
            "a" to NumberNode.of(1),
            "b" to ArrayNode.of(StringNode.of("x"), NullNode.INSTANCE, BooleanNode.of(true)),
            "c" to ObjectNode.of("nested" to NumberNode.of(BigDecimal("1.5"))),
        )

        val compact = node.toYamlString()
        val pretty = node.toYamlString(pretty = true)
        assertTrue(compact.contains("a: 1"))
        assertTrue(pretty.contains("a: 1"))

        val restored = YamlCodec.parse(compact)
        assertEquals(node, restored)
        assertEquals(node, YamlCodec.parse(pretty))
    }

    @Test
    fun formatterIsReadable() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "values" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
        )
        val pretty = YamlCodec.write(node, pretty = true)
        assertTrue(pretty.contains("name: 小于"))
        assertFalse(pretty.contains("\"小于\""))
    }
}
