package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.yaml.exception.YamlParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import com.mingliqiye.document.serialization.NodeTokenReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * YAML 自定义 tag（[YamlConfig.allowCustomTags]）测试。
 *
 * 覆盖：
 * - 开启后未知 tag 的标量/序列/映射还原、嵌套、anchor/alias/merge、全局自定义 tag；
 * - 默认（关闭）时单文档/多文档/Token 路径/Node 路径一致拒绝；
 * - Token 路径与 Node 路径的 tag 行为一致性；
 * - 多文档 reader 与多文档 mapper API；
 * - 深度/alias/重复 key 限制在自定义 tag 下仍然生效；
 * - 普通 YAML 行为回归（开启自定义 tag 不影响标准 tag）。
 */
class YamlCustomTagTest {

    // ---------------------------------------------------------------- 开启后读取

    @Test
    fun customScalarTagBecomesStringNode() {
        val value = readObjectValue("value: !env HOME\n")

        assertEquals(NodeType.STRING, value.type)
        assertEquals("HOME", value.asString())
    }

    @Test
    fun customScalarTagKeepsRawScalarText() {
        val obj = YamlReader("num: !foo 123\nflag: !foo true\nnil: !foo null\n", allowCustomTags = true).read().asObject()

        assertEquals("123", obj["num"]?.asString())
        assertEquals("true", obj["flag"]?.asString())
        assertEquals("null", obj["nil"]?.asString())
        assertEquals(NodeType.STRING, obj["num"]?.type)
    }

    @Test
    fun customSequenceTagBecomesArrayNode() {
        val value = readObjectValue("list: !tags [a, 1, true, null]\n")

        assertTrue(value is ArrayNode)
        val array = value.asArray()
        assertEquals(4, array.size)
        assertEquals("a", array[0].asString())
        assertEquals(1, array[1].asNumber().toInt())
        assertTrue(array[2].asBoolean())
        assertTrue(array[3].isNull)
    }

    @Test
    fun customMappingTagBecomesObjectNode() {
        val value = readObjectValue("config: !app {name: demo, port: 8080}\n")

        assertTrue(value is ObjectNode)
        assertEquals("demo", value.asObject()["name"]?.asString())
        assertEquals(8080, value.asObject()["port"]?.asNumber()?.toInt())
    }

    @Test
    fun customBlockMappingTagBecomesObjectNode() {
        val value = readObjectValue("config: !app\n  name: demo\n  nested:\n    ok: true\n")

        assertEquals("demo", value.asObject()["name"]?.asString())
        assertTrue(value.asObject()["nested"]!!.asObject()["ok"]!!.asBoolean())
    }

    @Test
    fun nestedCustomTags() {
        val doc = """
            app: !app
              name: demo
              args: !list
                - !env HOME
                - plain
              extra: {ref: !ref value, count: !count 3}
        """.trimIndent()

        val app = YamlReader(doc, allowCustomTags = true).read().asObject()["app"]!!.asObject()

        assertEquals("demo", app["name"]?.asString())
        val args = app["args"]!!.asArray()
        assertEquals("HOME", args[0].asString())
        assertEquals("plain", args[1].asString())
        val extra = app["extra"]!!.asObject()
        assertEquals("value", extra["ref"]?.asString())
        assertEquals("3", extra["count"]?.asString())
    }

    @Test
    fun customTagOnRootNodes() {
        val mapping = YamlReader("!app\nname: demo\n", allowCustomTags = true).read()
        assertEquals("demo", mapping.asObject()["name"]?.asString())

        val scalar = YamlReader("!env HOME\n", allowCustomTags = true).read()
        assertEquals(NodeType.STRING, scalar.type)
        assertEquals("HOME", scalar.asString())

        val sequence = YamlReader("!list [1, 2]\n", allowCustomTags = true).read()
        assertEquals(2, sequence.asArray().size)
    }

    @Test
    fun customTagsKeepAnchorsAndAliases() {
        val doc = """
            value: &env !env HOME
            copy: *env
            list: &list !tags [1, 2]
            listCopy: *list
        """.trimIndent()

        val obj = YamlReader(doc, allowCustomTags = true).read().asObject()

        assertEquals("HOME", obj["value"]?.asString())
        assertEquals(obj["value"], obj["copy"])
        assertEquals(obj["list"], obj["listCopy"])
        assertEquals(2, obj["listCopy"]!!.asArray().size)
    }

    @Test
    fun customTaggedMappingKeepsMergeKeys() {
        val doc = """
            base: &base {a: 1, b: 2}
            derived: !app
              <<: *base
              b: 3
        """.trimIndent()

        val derived = YamlReader(doc, allowCustomTags = true).read().asObject()["derived"]!!.asObject()

        assertEquals(1, derived["a"]?.asNumber()?.toInt())
        assertEquals(3, derived["b"]?.asNumber()?.toInt())
    }

    @Test
    fun customTagsAreTransparentInNodeModel() {
        val plain = YamlReader("a: x\nb: [1, true]\n").read()
        val custom = YamlReader("a: !env x\nb: !list [1, true]\n", allowCustomTags = true).read()

        assertEquals(plain, custom)
    }

    @Test
    fun writerDoesNotEmitCustomTags() {
        val node = YamlReader("a: !env HOME\n", allowCustomTags = true).read()
        val text = node.toYamlString()

        assertTrue(!text.contains("!"))
        assertEquals(node, YamlReader(text).read())
    }

    @Test
    fun customTaggedMappingKeysSupportedWhenEnabled() {
        val obj = YamlReader("!env NAME: value\n", allowCustomTags = true).read().asObject()

        assertEquals("value", obj["NAME"]?.asString())
    }

    @Test
    fun unknownGlobalTagsSupportedWhenEnabled() {
        val tagged = YamlReader("a: !<tag:example.com,2024:env> HOME\n", allowCustomTags = true).read().asObject()

        assertEquals("HOME", tagged["a"]?.asString())

        val primary = YamlReader("a: !!foo bar\n", allowCustomTags = true).read().asObject()

        assertEquals("bar", primary["a"]?.asString())
    }

    @Test
    fun recursiveCustomTagIsRejected() {
        assertFailsWith<YamlParseException> {
            YamlReader("a: &a !app\n  self: *a\n", allowCustomTags = true).read()
        }
    }

    // ---------------------------------------------------------------- 关闭时拒绝

    @Test
    fun unknownTagsRejectedByDefault() {
        assertFailsWith<YamlParseException> { YamlReader("a: !env HOME\n").read() }
        assertFailsWith<YamlParseException> { YamlReader("a: !list [1]\n").read() }
        assertFailsWith<YamlParseException> { YamlReader("a: !map {b: 1}\n").read() }
        assertFailsWith<YamlParseException> { YamlReader("a: !<tag:example.com,2024:env> HOME\n").read() }
        assertFailsWith<YamlParseException> { YamlReader("a: !!foo bar\n").read() }
        assertFailsWith<YamlParseException> { YamlReader("a: !env HOME\n", allowCustomTags = false).read() }
    }

    @Test
    fun unknownTagsRejectedByMultiDocumentReaderByDefault() {
        assertFailsWith<YamlParseException> {
            YamlMultiDocumentReader("a: 1\n---\nb: !env HOME\n").readAll()
        }
    }

    @Test
    fun unknownTagsRejectedByTokenReaderByDefault() {
        assertFailsWith<YamlParseException> { YamlTokenReader("a: !env HOME\n") }
        assertFailsWith<YamlParseException> { YamlTokenReader("a: !list [1]\n") }
        assertFailsWith<YamlParseException> { YamlTokenReader("a: !map {b: 1}\n") }
        assertFailsWith<YamlParseException> { YamlTokenReader("!env HOME\n") }
    }

    @Test
    fun unknownTagsRejectedByMapperByDefault() {
        assertFailsWith<YamlParseException> {
            YamlMapper().parse("a: !env HOME\n", Map::class.java)
        }
        assertFailsWith<YamlParseException> {
            YamlMapper().parseAllToNodes("a: 1\n---\nb: !env HOME\n")
        }
    }

    @Test
    fun unknownTagsRejectedByNodeFallbackPathByDefault() {
        val fallback = ProbeMapper(DocumentMapperConfig.builder().maxNodeDepth(10).build())

        assertFailsWith<YamlParseException> {
            fallback.parse("a: !env HOME\n", Map::class.java)
        }
        // 限制配置触发 Node 回退路径：createTextTokenReader 在构建 Node 树时抛出，
        // 因此没有成功返回 NodeTokenReader。
        assertEquals(1, fallback.calls)
        assertNull(fallback.lastReader)
    }

    @Test
    fun allowCustomTagsDefaultsToFalse() {
        assertFalse(YamlConfig.builder().build().allowCustomTags)
        assertTrue(YamlConfig.builder().allowCustomTags(true).build().allowCustomTags)
    }

    // ---------------------------------------------------------------- Mapper

    @Test
    fun mapperTokenPathSupportsCustomTags() {
        val mapper = customTagMapper()

        val map = mapper.parse("a: !env HOME\nb: !list [1, 2]\nc: !map {x: y}\n", Map::class.java)

        assertEquals("HOME", (map["a"] as StringNode).value)
        assertEquals(2, (map["b"] as ArrayNode).size)
        assertEquals("y", (map["c"] as ObjectNode)["x"]?.asString())
    }

    @Test
    fun mapperNodeFallbackPathMatchesTokenPath() {
        val text = "a: !env HOME\nb: !list [1, 2]\nc: !map {x: y}\n"

        val token = ProbeMapper(YamlConfig.builder().allowCustomTags(true).build())
        val tokenMap = token.parse(text, Map::class.java)

        val fallback = ProbeMapper(
            YamlConfig(
                DocumentMapperConfig.builder().maxNodeDepth(10).build(),
                pretty = false,
                maxAliases = 0,
                allowCustomTags = true,
            ),
        )
        val fallbackMap = fallback.parse(text, Map::class.java)

        assertIs<YamlTokenReader>(token.lastReader)
        assertIs<NodeTokenReader>(fallback.lastReader)
        assertEquals(tokenMap, fallbackMap)
    }

    @Test
    fun mapperParsesBeanWithCustomTags() {
        val mapper = customTagMapper()

        val bean = mapper.parse("name: !env APP_NAME\nport: !count 8080\n", CustomTagBean::class.java)

        assertEquals("APP_NAME", bean.name)
        assertEquals("8080", bean.port)
    }

    @Test
    fun mapperMultiDocumentSupportsCustomTags() {
        val mapper = customTagMapper()

        val nodes = mapper.parseAllToNodes("a: !env HOME\n---\nb: !list [1, 2]\n")

        assertEquals(2, nodes.size)
        assertEquals("HOME", nodes[0].asObject()["a"]?.asString())
        assertEquals(2, nodes[1].asObject()["b"]!!.asArray().size)

        val beans = mapper.parseAll("name: !env A\n---\nname: !env B\n", CustomTagBean::class.java)
        assertEquals(listOf("A", "B"), beans.map { it.name })
    }

    @Test
    fun multiDocumentReaderSupportsCustomTags() {
        val nodes = YamlMultiDocumentReader("a: !env HOME\n---\nb: !list [1]\n", allowCustomTags = true).readAll()

        assertEquals(2, nodes.size)
        assertEquals("HOME", nodes[0].asObject()["a"]?.asString())
        assertEquals(1, nodes[1].asObject()["b"]!!.asArray().size)
    }

    @Test
    fun mapperConfigLimitsStillApplyWithCustomTags() {
        val mapper = YamlMapper(YamlConfig.builder().allowCustomTags(true).maxAliases(1).build())

        assertFailsWith<YamlParseException> {
            mapper.parse("base: &base !app {a: 1}\none: *base\ntwo: *base\n", Map::class.java)
        }
        assertFailsWith<YamlParseException> {
            mapper.parseAllToNodes("base: &base !app {a: 1}\none: *base\ntwo: *base\n")
        }
    }

    @Test
    fun crossDocumentAnchorsAreRejected() {
        // YAML 规范不允许跨文档 anchor/alias；即使开启自定义 tag 也不支持。
        assertFailsWith<YamlParseException> {
            YamlMultiDocumentReader("a: &x 1\n---\nb: *x\n", allowCustomTags = true).readAll()
        }
    }

    // ---------------------------------------------------------------- Token / Node 一致性

    @Test
    fun tokenTypesMatchNodeTokenReaderForCustomTags() {
        val documents = listOf(
            "a: !env HOME\n",
            "a: !list [1, two, true, null]\n",
            "a: !map {b: 1, c: [x]}\n",
            "a: &x !env HOME\nb: *x\n",
            "base: &base {a: 1}\nderived: !app\n  <<: *base\n  b: 2\n",
            "- !env A\n- !list [1]\n",
            "!app\nname: x\n",
            "a: !<tag:example.com,2024:env> v\n",
            "!env NAME: value\n",
        )

        for (document in documents) {
            assertEquals(
                readTypes(NodeTokenReader(YamlReader(document, allowCustomTags = true).read())),
                readTypes(YamlTokenReader(document, allowCustomTags = true)),
                "自定义 tag 的 Token 类型序列不一致: $document",
            )
        }
    }

    @Test
    fun tokenValuesMatchNodeTokenReaderForCustomTags() {
        val document = """
            scalar: !env HOME
            list: !list [1, two, true, null]
            map: !map {b: 1, c: [x]}
        """.trimIndent()

        assertEquals(
            snapshot(NodeTokenReader(YamlReader(document, allowCustomTags = true).read())),
            snapshot(YamlTokenReader(document, allowCustomTags = true)),
        )
    }

    @Test
    fun tokenReaderAndNodeReaderRejectTheSameTagsByDefault() {
        val documents = listOf(
            "a: !env HOME\n",
            "a: !list [1]\n",
            "a: !map {b: 1}\n",
            "a: !!foo bar\n",
            "!env NAME: value\n",
            "a: [1, !env HOME]\n",
        )

        for (document in documents) {
            assertFailsWith<YamlParseException>("Node 路径未拒绝: $document") {
                YamlReader(document).read()
            }
            assertFailsWith<YamlParseException>("Token 路径未拒绝: $document") {
                YamlTokenReader(document)
            }
        }
    }

    // ---------------------------------------------------------------- 限制保持

    @Test
    fun customTagsPreserveAliasLimit() {
        val text = "base: &base !app {a: 1}\none: *base\ntwo: *base\n"

        assertFailsWith<YamlParseException> {
            YamlReader(text, maxAliases = 1, allowCustomTags = true).read()
        }
        YamlReader(text, maxAliases = 2, allowCustomTags = true).read()

        assertFailsWith<YamlParseException> {
            YamlTokenReader(text, maxAliases = 1, allowCustomTags = true)
        }
        YamlTokenReader(text, maxAliases = 2, allowCustomTags = true)
    }

    @Test
    fun customTagsPreserveDepthLimit() {
        val text = "a: !app\n  b: !app\n    c: 1\n"

        assertFailsWith<YamlParseException> {
            YamlReader(text, maxDepth = 2, allowCustomTags = true).read()
        }
        YamlReader(text, maxDepth = 3, allowCustomTags = true).read()

        assertFailsWith<YamlParseException> {
            YamlTokenReader(text, maxDepth = 2, allowCustomTags = true)
        }
        YamlTokenReader(text, maxDepth = 3, allowCustomTags = true)
    }

    @Test
    fun customTagsPreserveDuplicateKeyValidation() {
        assertFailsWith<YamlParseException> {
            YamlReader("a: !app\n  x: 1\n  x: 2\n", allowCustomTags = true).read()
        }
        assertFailsWith<YamlParseException> {
            YamlTokenReader("a: !app\n  x: 1\n  x: 2\n", allowCustomTags = true)
        }
    }

    // ---------------------------------------------------------------- 标准 YAML 回归

    @Test
    fun standardYamlUnaffectedByAllowCustomTags() {
        val documents = listOf(
            "a: 1\nb: [true, null, 'x']\n",
            "base: &base {a: 1, b: 2}\nderived:\n  <<: *base\n  b: 3\n",
            "date: 2020-01-02\ndata: !!binary AQID\n",
            "hex: 0x1F\nexp: 1e3\nok: yes\nnone: ~\n",
            "text: |\n  line1\n  line2\n",
            "flow: {a: [1, true, null, 'x'], b: {}}\n",
        )

        for (document in documents) {
            assertEquals(
                YamlReader(document).read(),
                YamlReader(document, allowCustomTags = true).read(),
                "开启自定义 tag 后标准 YAML 结果发生变化: $document",
            )
            assertEquals(
                snapshot(NodeTokenReader(YamlReader(document).read())),
                snapshot(YamlTokenReader(document, allowCustomTags = true)),
                "开启自定义 tag 后 Token 结果发生变化: $document",
            )
        }
    }

    @Test
    fun standardTagsStillResolveWhenCustomTagsEnabled() {
        val obj = YamlReader("num: 18\nflag: true\nstr: '18'\n", allowCustomTags = true).read().asObject()

        assertEquals(NodeType.NUMBER, obj["num"]?.type)
        assertEquals(NodeType.BOOLEAN, obj["flag"]?.type)
        assertEquals(NodeType.STRING, obj["str"]?.type)
    }

    // ---------------------------------------------------------------- 辅助

    private fun readObjectValue(document: String): Node = YamlReader(document, allowCustomTags = true).read().asObject().asMap().values.first()

    private fun customTagMapper(): YamlMapper = YamlMapper(YamlConfig.builder().allowCustomTags(true).build())

    private fun readTypes(reader: DocumentTokenReader): List<DocumentTokenType> {
        val types = ArrayList<DocumentTokenType>()
        while (true) {
            types.add(reader.nextToken() ?: break)
        }
        return types
    }

    private fun snapshot(reader: DocumentTokenReader): List<TokenView> {
        val tokens = ArrayList<TokenView>()
        while (true) {
            val type = reader.nextToken() ?: break
            tokens.add(
                TokenView(
                    type = type,
                    text = reader.textValue,
                    boolean = reader.booleanValue,
                    binary = reader.binaryValue?.toList(),
                ),
            )
        }
        return tokens
    }

    private data class TokenView(
        val type: DocumentTokenType,
        val text: String?,
        val boolean: Boolean?,
        val binary: List<Byte>?,
    )

    private class CustomTagBean(
        var name: String = "",
        var port: String = "",
    )

    private class ProbeMapper(
        config: DocumentMapperConfig,
    ) : YamlMapper(config) {

        var calls: Int = 0
            private set

        var lastReader: DocumentTokenReader? = null
            private set

        override fun createTextTokenReader(source: String): DocumentTokenReader {
            calls++
            val reader = super.createTextTokenReader(source)
            lastReader = reader
            return reader
        }
    }
}
