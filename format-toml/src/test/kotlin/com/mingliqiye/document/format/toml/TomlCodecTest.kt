package com.mingliqiye.document.format.toml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.toml.exception.TomlException
import com.mingliqiye.document.format.toml.exception.TomlParseException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * TOML 读写测试。
 *
 * 注意：tomlj 1.0.0 支持 TOML 0.5，数组元素必须同类型，且解析器不接受内联 table
 * 中的空数组（`{ list = [] }`）；因此空数组只做顶层字段的 round-trip 测试。
 */
class TomlCodecTest {

    @Test
    fun documentTreeRoundTrip() {
        val node = ObjectNode.from(
            linkedMapOf(
                "name" to StringNode.of("小于"),
                "age" to NumberNode.of(18),
                "score" to NumberNode.of(98.5),
                "active" to BooleanNode.of(true),
                "tags" to ArrayNode.from(listOf(StringNode.of("a"), StringNode.of("b"))),
                "address" to ObjectNode.from(
                    mapOf(
                        "city" to StringNode.of("杭州"),
                        "zip" to StringNode.of("310000"),
                    ),
                ),
            ),
        )

        val toml = node.toTomlString()
        assertTrue(toml.contains("name = \"小于\""))
        assertTrue(toml.contains("score = 98.5"))
        assertTrue(toml.contains("address = { city = \"杭州\", zip = \"310000\" }"))

        assertEquals(node, TomlReader(toml).read())
    }

    @Test
    fun exactCanonicalOutput() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "age" to NumberNode.of(18),
            "user" to ObjectNode.of("name" to StringNode.of("a")),
            "tags" to ArrayNode.of(StringNode.of("x"), StringNode.of("y")),
            "emptyTable" to ObjectNode.EMPTY,
            "emptyArray" to ArrayNode.EMPTY,
        )

        assertEquals(
            """
            name = "小于"
            age = 18
            user = { name = "a" }
            tags = [ "x", "y" ]
            emptyTable = {}
            emptyArray = []
            """.trimIndent() + "\n",
            node.toTomlString(),
        )
    }

    @Test
    fun parsesTableHeadersAndDottedKeys() {
        val toml = """
            [user]
            name = "小于"
            [user.address]
            city = "杭州"
            [[products]]
            sku = "a"
            [[products]]
            sku = "b"
        """.trimIndent()

        val root = TomlReader(toml).read().asObject()
        assertEquals("小于", root["user"]?.asObject()?.get("name")?.asString())
        assertEquals("杭州", root["user"]?.asObject()?.get("address")?.asObject()?.get("city")?.asString())
        assertEquals(2, root["products"]?.asArray()?.size)
        assertEquals("b", root["products"]?.asArray()?.get(1)?.asObject()?.get("sku")?.asString())

        val dotted = TomlReader("a.b = 1").read().asObject()
        assertEquals(1, dotted["a"]?.asObject()?.get("b")?.asNumber()?.toInt())
    }

    @Test
    fun nestedTablesRoundTrip() {
        val node = ObjectNode.of(
            "user" to ObjectNode.of(
                "name" to StringNode.of("小于"),
                "address" to ObjectNode.of(
                    "city" to StringNode.of("杭州"),
                    "geo" to ObjectNode.of("lat" to NumberNode.of(30.2)),
                ),
            ),
        )

        val toml = node.toTomlString()
        assertTrue(toml.contains("user = { name = \"小于\", address = { city = \"杭州\", geo = { lat = 30.2 } } }"))
        assertEquals(node, TomlReader(toml).read())
    }

    @Test
    fun arrayRoundTrip() {
        val node = ObjectNode.of(
            "strings" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "numbers" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
            "booleans" to ArrayNode.of(BooleanNode.of(true), BooleanNode.of(false)),
            "tables" to ArrayNode.of(
                ObjectNode.of("x" to NumberNode.of(1)),
                ObjectNode.of("x" to NumberNode.of(2)),
            ),
            "nestedArrays" to ArrayNode.of(
                ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
                ArrayNode.of(NumberNode.of(3)),
            ),
            "emptyTables" to ArrayNode.of(ObjectNode.EMPTY, ObjectNode.EMPTY),
        )

        assertEquals(node, TomlReader(node.toTomlString()).read())
    }

    @Test
    fun numberRoundTrip() {
        val node = ObjectNode.of(
            "int" to NumberNode.of(18),
            "negative" to NumberNode.of(-7),
            "longMax" to NumberNode.of(Long.MAX_VALUE),
            "longMin" to NumberNode.of(Long.MIN_VALUE),
            "decimal" to NumberNode.of(3.14),
            "fraction" to NumberNode.ofRaw("-0.5"),
            "underscore" to NumberNode.ofRaw("1_000"),
            "hex" to NumberNode.ofRaw("0x1A"),
            "exponent" to NumberNode.ofRaw("1e5"),
            "huge" to NumberNode.of(1.0E300),
        )

        val toml = node.toTomlString()
        assertTrue(toml.contains("int = 18"))
        assertTrue(toml.contains("decimal = 3.14"))
        assertTrue(toml.contains("underscore = 1_000"))
        assertTrue(toml.contains("hex = 0x1A"))
        assertTrue(toml.contains("exponent = 1e5"))
        assertTrue(toml.contains("huge = 1E+300"))

        val restored = TomlReader(toml).read().asObject()
        assertEquals(18, restored["int"]?.asNumber()?.toInt())
        assertEquals(-7, restored["negative"]?.asNumber()?.toInt())
        assertEquals(Long.MAX_VALUE, restored["longMax"]?.asNumber()?.toLong())
        assertEquals(Long.MIN_VALUE, restored["longMin"]?.asNumber()?.toLong())
        assertEquals(0, BigDecimal("3.14").compareTo(restored["decimal"]?.asNumber()?.value))
        assertEquals(0, BigDecimal("-0.5").compareTo(restored["fraction"]?.asNumber()?.value))
        assertEquals(1000L, restored["underscore"]?.asNumber()?.toLong())
        assertEquals(26L, restored["hex"]?.asNumber()?.toLong())
        assertEquals(0, BigDecimal("100000").compareTo(restored["exponent"]?.asNumber()?.value))
        assertEquals(1.0E300, restored["huge"]?.asNumber()?.toDouble())
    }

    @Test
    fun booleanRoundTrip() {
        val node = ObjectNode.of(
            "yes" to BooleanNode.of(true),
            "no" to BooleanNode.of(false),
        )

        assertEquals("yes = true\nno = false\n", node.toTomlString())
        assertEquals(node, TomlReader(node.toTomlString()).read())
    }

    @Test
    fun stringEscapingRoundTrip() {
        val node = ObjectNode.of(
            "text" to StringNode.of("line\n tab\t quote\" slash\\ end"),
            "control" to StringNode.of("\u0001\u007F"),
            "empty" to StringNode.of(""),
            "unicode" to StringNode.of("中文😀#=[]{}"),
        )

        val toml = node.toTomlString()
        assertTrue(toml.contains("\\n"))
        assertTrue(toml.contains("\\t"))
        assertTrue(toml.contains("\\\""))
        assertTrue(toml.contains("\\\\"))
        assertTrue(toml.contains("\\u0001"))
        assertTrue(toml.contains("\\u007F"))
        assertTrue(toml.contains("empty = \"\""))
        assertEquals(node, TomlReader(toml).read())
    }

    @Test
    fun keyEscapingRoundTrip() {
        val node = ObjectNode.of(
            "bare_key-1" to NumberNode.of(1),
            "with space" to NumberNode.of(2),
            "a.b" to NumberNode.of(3),
            "中文" to NumberNode.of(4),
            "" to NumberNode.of(5),
            "quote\"key" to NumberNode.of(6),
        )

        val toml = node.toTomlString()
        assertTrue(toml.contains("bare_key-1 = 1"))
        assertTrue(toml.contains("\"with space\" = 2"))
        assertTrue(toml.contains("\"a.b\" = 3"))
        assertEquals(node, TomlReader(toml).read())
    }

    @Test
    fun emptyContainersRoundTrip() {
        val node = ObjectNode.of(
            "emptyTable" to ObjectNode.EMPTY,
            "emptyArray" to ArrayNode.EMPTY,
            "nested" to ObjectNode.of("inner" to ObjectNode.EMPTY),
        )

        val toml = node.toTomlString()
        assertTrue(toml.contains("emptyTable = {}"))
        assertTrue(toml.contains("emptyArray = []"))
        assertTrue(toml.contains("nested = { inner = {} }"))
        assertEquals(node, TomlReader(toml).read())

        assertEquals("", ObjectNode.EMPTY.toTomlString())
        assertEquals(0, TomlReader("").read().asObject().size)
    }

    @Test
    fun nullObjectFieldsAreWrittenAsEmptyString() {
        val node = ObjectNode.of(
            "name" to StringNode.of("x"),
            "missing" to NullNode.INSTANCE,
            "nested" to ObjectNode.of("nil" to NullNode.INSTANCE, "keep" to NumberNode.of(1)),
        )

        // 对齐 上游实现 TOML 默认行为：TOML 没有 null，FAIL_ON_NULL_WRITE 关闭时写成空字符串。
        assertEquals("name = \"x\"\nmissing = \"\"\nnested = { nil = \"\", keep = 1 }\n", node.toTomlString())
        val restored = TomlReader(node.toTomlString()).read().asObject()
        assertEquals("", restored["missing"]?.asString())
        assertEquals("", restored["nested"]?.asObject()?.get("nil")?.asString())
    }

    @Test
    fun nullArrayElementIsWrittenAsEmptyString() {
        val toml = ObjectNode.of(
            "list" to ArrayNode.of(StringNode.of("a"), NullNode.INSTANCE),
        ).toTomlString()

        assertEquals("list = [ \"a\", \"\" ]\n", toml)
    }

    @Test
    fun rootMustBeObject() {
        assertFailsWith<TomlException> { StringNode.of("value").toTomlString() }
        assertFailsWith<TomlException> { ArrayNode.of(NumberNode.of(1)).toTomlString() }
        assertFailsWith<TomlException> { NullNode.INSTANCE.toTomlString() }
    }

    @Test
    fun invalidNumberThrows() {
        assertFailsWith<TomlException> {
            ObjectNode.of("bad" to NumberNode.ofRaw("not-a-number")).toTomlString()
        }
    }

    @Test
    fun invalidTomlThrowsParseException() {
        val invalid = listOf(
            "42",
            "a =",
            "= 1",
            "a = 1\na = 2",
            "[table",
            "a = [1,",
            "a = { b = 1",
            "a = 1 b = 2",
            "a = \"unterminated",
        )
        for (source in invalid) {
            assertFailsWith<TomlParseException>("should fail: <$source>") {
                TomlReader(source).read()
            }
        }
    }

    @Test
    fun parseErrorContainsLineAndColumn() {
        val error = assertFailsWith<TomlParseException> {
            TomlReader("a = 1\nb = ").read()
        }
        val message = error.message.orEmpty()
        assertTrue(message.contains("line 2"), "message was: $message")
        assertTrue(message.contains("column"), "message was: $message")
    }

    @Test
    fun temporalValuesUseSourceTextByDefault() {
        val toml = """
            offset = 1979-05-27T07:32:00Z
            local = 1979-05-27T07:32:00
            spaced = 1979-05-27 07:32:00.123
            date = 1979-05-27
            time = 07:32:00
        """.trimIndent()

        val root = TomlReader(toml).read().asObject()
        // 对齐 上游实现 TOML 默认行为：PARSE_JAVA_TIME 关闭时返回源码原文，
        // 仅把日期时间分隔符空格归一化为 T。
        assertEquals("1979-05-27T07:32:00Z", root["offset"]?.asString())
        assertEquals("1979-05-27T07:32:00", root["local"]?.asString())
        assertEquals("1979-05-27T07:32:00.123", root["spaced"]?.asString())
        assertEquals("1979-05-27", root["date"]?.asString())
        assertEquals("07:32:00", root["time"]?.asString())
    }

    @Test
    fun nanAndInfinityAreRejected() {
        assertFailsWith<TomlParseException> { TomlReader("a = nan").read() }
        assertFailsWith<TomlParseException> { TomlReader("a = inf").read() }
        assertFailsWith<TomlParseException> { TomlReader("a = -inf").read() }
    }

    @Test
    fun companionAndExtensionParse() {
        val node = TomlReader.parse("a = 1")
        assertEquals(1, node.asObject()["a"]?.asNumber()?.toInt())

        val viaExtension = "b = true".toTomlNode()
        assertEquals(true, viaExtension.asObject()["b"]?.asBoolean())
    }
}
