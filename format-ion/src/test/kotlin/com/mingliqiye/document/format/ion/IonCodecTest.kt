package com.mingliqiye.document.format.ion

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.ion.exception.IonParseException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Ion 读写测试。
 */
class IonCodecTest {

    @Test
    fun documentTreeRoundTrip() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于 & <ion>"),
            "age" to NumberNode.of(18),
            "pi" to NumberNode.of(BigDecimal("3.14")),
            "active" to BooleanNode.of(true),
            "missing" to NullNode.INSTANCE,
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "address" to ObjectNode.of("city" to StringNode.of("杭州")),
            "matrix" to ArrayNode.of(
                ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
                ObjectNode.of("nested" to NullNode.INSTANCE),
            ),
        )

        val ion = node.toIonString()
        assertTrue(ion.startsWith("{"))
        val restored = IonReader(ion).read()
        assertEquals(node, restored)
    }

    @Test
    fun prettyOutputIsMultiline() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "values" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
        )

        val pretty = node.toIonString(pretty = true)
        assertTrue(pretty.contains("\n"))
        assertEquals(node, IonReader(pretty).read())
    }

    @Test
    fun parseStructListAndNestedValues() {
        val ion = """
            {
              name: "小于",
              tags: ["a", "b"],
              child: { value: null },
              list: [1, [2, 3], { x: true }]
            }
        """.trimIndent()

        val obj = IonReader(ion).read().asObject()
        assertEquals("小于", obj["name"]?.asString())
        assertEquals(2, obj["tags"]?.asArray()?.size)
        assertTrue(obj["child"]?.asObject()?.get("value")!!.isNull)
        assertEquals(3, obj["list"]?.asArray()?.size)
        assertEquals(true, obj["list"]?.asArray()?.get(2)?.asObject()?.get("x")?.asBoolean())
    }

    @Test
    fun parseNumbers() {
        val array = IonReader("[0, -1, 1.5, 2e3, 1d3, 999999999999999999999999]").read().asArray()

        assertEquals(6, array.size)
        assertEquals("0", array[0].asNumber().raw)
        assertEquals(-1, array[1].asNumber().toInt())
        assertEquals(0, BigDecimal("1.5").compareTo(array[2].asNumber().value))
        assertEquals(0, BigDecimal("2000").compareTo(array[3].asNumber().value))
        assertEquals(0, BigDecimal("1000").compareTo(array[4].asNumber().value))
        assertEquals("999999999999999999999999", array[5].asNumber().raw)
    }

    @Test
    fun parseStringsSymbolsTimestampsAndLobs() {
        val ion = """
            {
              text: "héllo",
              symbol: 'sym',
              at: 2024-01-02T03:04:05Z,
              data: {{aGVsbG8=}},
              clob: {{"hi"}}
            }
        """.trimIndent()

        val obj = IonReader(ion).read().asObject()
        assertEquals("héllo", obj["text"]?.asString())
        assertEquals("sym", obj["symbol"]?.asString())
        assertEquals("2024-01-02T03:04:05Z", obj["at"]?.asString())
        assertEquals(
            "hello",
            obj["data"]?.asBinary()?.toString(Charsets.UTF_8),
        )
        assertEquals(
            "hi",
            obj["clob"]?.asBinary()?.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun typedNullValuesMapToNullNode() {
        val obj = IonReader("{a: null, b: null.int, c: null.struct, d: null.list}").read().asObject()

        assertTrue(obj["a"]!!.isNull)
        assertTrue(obj["b"]!!.isNull)
        assertTrue(obj["c"]!!.isNull)
        assertTrue(obj["d"]!!.isNull)
    }

    @Test
    fun sexpMapsToArrayNode() {
        val array = IonReader("(1 2 (3 4))").read().asArray()

        assertEquals(3, array.size)
        assertEquals(2, array[2].asArray().size)
    }

    @Test
    fun multipleTopLevelValuesThrow() {
        assertFailsWith<IonParseException> {
            IonReader("1 2").read()
        }
    }

    @Test
    fun emptyDocumentThrows() {
        assertFailsWith<IonParseException> {
            IonReader("").read()
        }
        assertFailsWith<IonParseException> {
            IonReader("  // only comment\n").read()
        }
    }

    @Test
    fun invalidIonThrowsParseException() {
        assertFailsWith<IonParseException> {
            IonReader("{a: ").read()
        }
    }

    @Test
    fun numberWriterUsesIntAndDecimalTypes() {
        assertEquals("18", NumberNode.of(18).toIonString())
        assertEquals("3.14", NumberNode.of(BigDecimal("3.14")).toIonString())
        assertEquals(0, BigDecimal("3.14").compareTo(IonReader("3.14").read().asNumber().value))
    }

    @Test
    fun elementExtensions() {
        val node = "{a:1}".toIonNode()

        assertTrue(node.isObject)
        assertTrue(node.toIonString().contains("a:"))
        assertEquals(1, node.asObject()["a"]?.asNumber()?.toInt())
    }
}
