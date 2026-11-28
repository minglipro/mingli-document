package com.mingliqiye.document.format.properties

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.properties.exception.PropertiesParseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 对齐 上游实现 `Properties 底层库` / `JavaPropsSchema` 的读写行为测试。
 *
 * 每个配置项都有实际行为断言（不是空开关）。
 */
class PropertiesJacksonSemanticsTest {

    @Test
    fun defaultReadSupportsMarkersAndSimpleIndexes() {
        val node = PropertiesReader(
            "p.1.x=1\n" +
                "p.1.y=2\n" +
                "p[2].x=3\n" +
                "p[2].y=4\n",
        ).read()

        val p = node.asObject()["p"]!!.asArray()
        assertEquals(2, p.size)
        assertEquals(1, p[0].asObject()["x"]!!.asNumber().toInt())
        assertEquals(2, p[0].asObject()["y"]!!.asNumber().toInt())
        assertEquals(3, p[1].asObject()["x"]!!.asNumber().toInt())
        assertEquals(4, p[1].asObject()["y"]!!.asNumber().toInt())
    }

    @Test
    fun denseArraysSortByIndexOnly() {
        // 上游实现：下标只用于排序，缺口不补 null，数组稠密。
        val node = PropertiesReader("p[10]=a\np[5]=b\np[7]=c").read()

        val p = node.asObject()["p"]!!.asArray()
        assertEquals(3, p.size)
        assertEquals("b", p[0].asString())
        assertEquals("c", p[1].asString())
        assertEquals("a", p[2].asString())
    }

    @Test
    fun valueAndChildrenBecomeArrayWithValueFirst() {
        // 上游实现 语义：节点同时有值和下标子节点时，值成为数组首元素。
        val node = PropertiesReader("str=first\nstr.11=third\nstr.2=second").read()

        val str = node.asObject()["str"]!!.asArray()
        assertEquals(3, str.size)
        assertEquals("first", str[0].asString())
        assertEquals("second", str[1].asString())
        assertEquals("third", str[2].asString())
    }

    @Test
    fun indexAndNameChildrenCoerceToNames() {
        // 上游实现 语义：已有命名字节点时，下标子节点降级为名字。
        val node = PropertiesReader("a[0]=x\na.b=y").read()

        val a = node.asObject()["a"]!!.asObject()
        assertEquals("x", a["0"]!!.asString())
        assertEquals("y", a["b"]!!.asString())
    }

    @Test
    fun parseSimpleIndexesCanBeDisabled() {
        val config = PropertiesConfig.builder().parseSimpleIndexes(false).build()

        val node = PropertiesReader("p.1.x=1", config).read()

        val p = node.asObject()["p"]!!.asObject()
        assertEquals(1, p["1"]!!.asObject()!!["x"]!!.asNumber().toInt())
    }

    @Test
    fun indexMarkersCanBeDisabled() {
        val config = PropertiesConfig.builder()
            .disable(PropertiesReadFeature.ALLOW_INDEX_MARKERS)
            .build()

        val node = PropertiesReader("tags[0]=a", config).read()

        assertEquals("a", node.asObject()["tags[0]"]!!.asString())
    }

    @Test
    fun customIndexMarkersAreRead() {
        val config = PropertiesConfig.builder().indexMarker("<<", ">>").build()

        val node = PropertiesReader("p<<1>>.x=1\np<<2>>.x=2", config).read()

        val p = node.asObject()["p"]!!.asArray()
        assertEquals(2, p.size)
        assertEquals(1, p[0].asObject()["x"]!!.asNumber().toInt())
        assertEquals(2, p[1].asObject()["x"]!!.asNumber().toInt())
    }

    @Test
    fun customPathSeparatorRoundTrip() {
        val config = PropertiesConfig.builder().pathSeparator("->").build()
        val node = ObjectNode.of("a" to ObjectNode.of("b" to NumberNode.of(1)))

        val text = node.toPropertiesString(config)

        assertEquals("a->b=1\n", text)
        val restored = PropertiesReader("a->b=1", config).read()
        assertEquals(1, restored.asObject()["a"]!!.asObject()!!["b"]!!.asNumber().toInt())
    }

    @Test
    fun pathSeparatorEscapeCharEscapesSeparator() {
        val config = PropertiesConfig.builder().pathSeparatorEscapeChar('#').build()

        val node = PropertiesReader("a#.b=1", config).read()

        assertEquals(1, node.asObject()["a.b"]!!.asNumber().toInt())
    }

    @Test
    fun withoutPathSeparatorKeepsFlatKeys() {
        val config = PropertiesConfig.builder().withoutPathSeparator().build()

        val node = PropertiesReader("a.b=1", config).read()

        assertEquals(1, node.asObject()["a.b"]!!.asNumber().toInt())
    }

    @Test
    fun arrayWritingUsesSimpleIndexesByDefault() {
        val node = ObjectNode.of(
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
        )

        assertEquals("tags.1=a\ntags.2=b\n", node.toPropertiesString())
        assertEquals(
            "tags.0=a\ntags.1=b\n",
            node.toPropertiesString(PropertiesConfig.builder().firstArrayOffset(0).build()),
        )
        assertEquals(
            "tags[1]=a\ntags[2]=b\n",
            node.toPropertiesString(PropertiesConfig.builder().writeIndexUsingMarkers(true).build()),
        )
        assertEquals(
            "tags[3]=a\ntags[4]=b\n",
            node.toPropertiesString(
                PropertiesConfig.builder().writeIndexUsingMarkers(true).firstArrayOffset(3).build(),
            ),
        )
        assertEquals(
            "tags<<1>>=a\ntags<<2>>=b\n",
            node.toPropertiesString(
                PropertiesConfig.builder()
                    .indexMarker("<<", ">>")
                    .writeIndexUsingMarkers(true)
                    .build(),
            ),
        )
        // 关闭下标标记后，即使开启标记写法也回退简单下标。
        assertEquals(
            "tags.1=a\ntags.2=b\n",
            node.toPropertiesString(
                PropertiesConfig.builder().writeIndexUsingMarkers(true).withoutIndexMarker().build(),
            ),
        )
    }

    @Test
    fun outputFormattingOptions() {
        val node = ObjectNode.of(
            "a" to NumberNode.of(1),
            "b" to ObjectNode.of("c" to StringNode.of("x")),
        )
        val config = PropertiesConfig.builder()
            .header("# head\n")
            .lineIndentation("  ")
            .keyValueSeparator(": ")
            .lineEnding("\r\n")
            .build()

        val text = node.toPropertiesString(config)

        assertEquals("# head\n  a: 1\r\n  b.c: x\r\n", text)
    }

    @Test
    fun prefixIsAppliedOnReadAndWrite() {
        val config = PropertiesConfig.builder().prefix("org.o1").build()
        val input = "org.o1.firstName=Bob\n" +
            "org.o2.firstName=Alice\n" +
            "junk=1\n"

        val node = PropertiesReader(input, config).read()

        assertEquals("Bob", node.asObject()["firstName"]!!.asString())
        assertEquals(
            "org.o1.firstName=Bob\n",
            ObjectNode.of("firstName" to StringNode.of("Bob")).toPropertiesString(config),
        )
    }

    @Test
    fun percentEscapingExtensionCanBeDisabled() {
        val config = PropertiesConfig.builder()
            .disable(PropertiesReadFeature.DECODE_PERCENT_ESCAPED_SEGMENTS)
            .disable(PropertiesWriteFeature.ESCAPE_PERCENT_ENCODED_SEGMENTS)
            .build()

        val node = PropertiesReader("a%2Eb=1", config).read()
        assertEquals(1, node.asObject()["a%2Eb"]!!.asNumber().toInt())

        // 关闭扩展后按 上游实现 语义直接拼接路径段（含分隔符的字段名会歧义）。
        assertEquals(
            "a.b=1\n",
            ObjectNode.of("a.b" to NumberNode.of(1)).toPropertiesString(config),
        )
    }

    @Test
    fun nonAsciiEscapingCanBeEnabled() {
        val config = PropertiesConfig.builder().enable(PropertiesWriteFeature.ESCAPE_NON_ASCII).build()

        val text = ObjectNode.of("name" to StringNode.of("小于")).toPropertiesString(config)

        assertEquals("name=\\u5C0F\\u4E8E\n", text)
        // 读回时 JDK Properties 语法会还原 \uXXXX。
        assertEquals("小于", PropertiesReader(text).read().asObject()["name"]!!.asString())
    }

    @Test
    fun keyAndValueEscapingMatchesPropertiesRules() {
        val text = ObjectNode.of(
            "ns:key" to StringNode.of("tab:\tTAB!"),
            "combo key" to StringNode.of("NUL=\u0000..."),
        ).toPropertiesString()

        assertEquals("ns\\:key=tab:\\tTAB!\ncombo\\ key=NUL=\\u0000...\n", text)
    }

    @Test
    fun nullAndBinaryKeepLegacyNotation() {
        val node = ObjectNode.of(
            "empty" to com.mingliqiye.document.core.NullNode.INSTANCE,
            "data" to BinaryNode.of(byteArrayOf(1, 2, 3)),
        )

        val text = node.toPropertiesString()

        assertEquals("empty=\ndata=AQID\n", text)
        val restored = PropertiesReader(text).read()
        assertContentEquals(byteArrayOf(1, 2, 3), java.util.Base64.getDecoder().decode(restored.asObject()["data"]!!.asString()))
    }

    @Test
    fun commentsAreIgnoredOnRead() {
        val node = PropertiesReader("# comment\n! another\nname=小于\n").read()

        assertEquals("小于", node.asObject()["name"]!!.asString())
    }

    @Test
    fun maxDepthFromConfigIsEnforced() {
        val config = PropertiesConfig.builder().maxDepth(2).build()

        assertFailsWith<PropertiesParseException> {
            PropertiesReader("a.b=1", config).read()
        }
        assertEquals(1, PropertiesReader("a=1", config).read().asObject()["a"]!!.asNumber().toInt())
    }

    @Test
    fun mapperBeanRoundTripWithCustomFormatConfig() {
        val config = PropertiesConfig.builder()
            .pathSeparator("/")
            .prefix("app")
            .writeIndexUsingMarkers(true)
            .build()
        val mapper = PropertiesMapper(config)
        val user = BeanUser().apply {
            name = "小于"
            age = 18
            tags = listOf("a", "b")
        }

        val text = mapper.format(user)

        assertTrue(text.contains("app/name=小于"), text)
        assertTrue(text.contains("app/tags[1]=a"), text)
        val restored = mapper.parse(text, BeanUser::class.java)
        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertEquals(listOf("a", "b"), restored.tags)
    }

    private class BeanUser {
        var name: String? = null
        var age: Int = 0
        var tags: List<String> = emptyList()
    }
}
