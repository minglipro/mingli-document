package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.yaml.exception.YamlParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import com.mingliqiye.document.serialization.NodeTokenReader
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [YamlTokenReader] 与 Mapper 原生 Token 路径测试。
 *
 * 覆盖原生 Token 序列、Scalar tag 映射、多文档/重复 key/递归拒绝、与
 * [NodeTokenReader] 的一致性、Mapper Token 直读路径与限制回退。
 */
class YamlTokenReaderTest {

    @Test
    fun emitsTokenSequenceForSimpleMapping() {
        val tokens = snapshot(
            YamlTokenReader(
                """
                name: 小于
                age: 18
                ok: true
                none: null
                """.trimIndent(),
            ),
        )

        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.STRING,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NUMBER,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.BOOLEAN,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NULL,
                DocumentTokenType.END_OBJECT,
            ),
            tokens.map { it.type },
        )
        assertEquals("小于", tokens[2].text)
        assertEquals("18", tokens[4].text)
        assertEquals(true, tokens[6].boolean)
    }

    @Test
    fun emitsNestedTokens() {
        val tokens = snapshot(
            YamlTokenReader(
                """
                child:
                  list:
                    - a: 1
                      b: [true, null]
                """.trimIndent(),
            ),
        )

        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NUMBER,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.BOOLEAN,
                DocumentTokenType.NULL,
                DocumentTokenType.END_ARRAY,
                DocumentTokenType.END_OBJECT,
                DocumentTokenType.END_ARRAY,
                DocumentTokenType.END_OBJECT,
                DocumentTokenType.END_OBJECT,
            ),
            tokens.map { it.type },
        )
    }

    @Test
    fun scalarAndSequenceRoots() {
        assertEquals(listOf(DocumentTokenType.STRING), readTypes(YamlTokenReader("just text\n")))
        assertEquals(
            listOf(
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.NUMBER,
                DocumentTokenType.STRING,
                DocumentTokenType.END_ARRAY,
            ),
            readTypes(YamlTokenReader("- 1\n- two\n")),
        )
    }

    @Test
    fun emptyDocumentEmitsNull() {
        assertEquals(listOf(DocumentTokenType.NULL), readTypes(YamlTokenReader("")))
        assertEquals(listOf(DocumentTokenType.NULL), readTypes(YamlTokenReader("# only comment\n")))
        assertEquals(listOf(DocumentTokenType.NULL), readTypes(YamlTokenReader("---\n")))
        assertEquals(listOf(DocumentTokenType.NULL), readTypes(YamlTokenReader("null\n")))
    }

    @Test
    fun binaryTokenKeepsDecodedBytes() {
        val tokens = snapshot(YamlTokenReader("data: !!binary AQID\n"))

        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.BINARY,
                DocumentTokenType.END_OBJECT,
            ),
            tokens.map { it.type },
        )
        assertEquals("data", tokens[1].text)
        assertContentEquals(byteArrayOf(1, 2, 3), tokens[2].binary?.toByteArray())
    }

    @Test
    fun timestampBecomesIsoString() {
        val tokens = snapshot(YamlTokenReader("date: 2020-01-02\nfull: 2020-01-02T03:04:05Z\n"))

        assertEquals(DocumentTokenType.STRING, tokens[2].type)
        assertEquals("2020-01-02T00:00:00Z", tokens[2].text)
        assertEquals("2020-01-02T03:04:05Z", tokens[4].text)
    }

    @Test
    fun numbersAreNormalizedLikeNodePath() {
        val tokens = snapshot(
            YamlTokenReader("hex: 0x1F\noct: 010\nunder: 1_000\nexp: 1e3\nplain: 18\nfloat: 3.5\n"),
        )
        val numbers = tokens.filter { it.type == DocumentTokenType.NUMBER }.map { it.text }

        assertEquals(listOf("31", "8", "1000", "1000.0", "18", "3.5"), numbers)
    }

    @Test
    fun rejectsNaNAndInfinity() {
        assertFailsWith<YamlParseException> { YamlTokenReader("value: .inf\n") }
        assertFailsWith<YamlParseException> { YamlTokenReader("value: +.inf\n") }
        assertFailsWith<YamlParseException> { YamlTokenReader("value: .NaN\n") }
    }

    @Test
    fun rejectsMultipleDocuments() {
        assertFailsWith<YamlParseException> {
            YamlTokenReader("a: 1\n---\nb: 2\n")
        }
    }

    @Test
    fun rejectsDuplicateKeysLikeNodePath() {
        assertFailsWith<YamlParseException> {
            YamlTokenReader("a: 1\na: 2\n")
        }
    }

    @Test
    fun rejectsRecursiveNodes() {
        assertFailsWith<YamlParseException> {
            YamlTokenReader("a: &a\n  self: *a\n")
        }
    }

    @Test
    fun rejectsInvalidBinaryAndUnknownMappingKeyType() {
        assertFailsWith<YamlParseException> {
            YamlTokenReader("data: !!binary '***'\n")
        }
        assertFailsWith<YamlParseException> {
            YamlTokenReader("? [1, 2]\n: value\n")
        }
    }

    @Test
    fun customScalarTagBecomesString() {
        val tokens = snapshot(YamlTokenReader("a: !custom 123\n"))

        assertEquals(DocumentTokenType.STRING, tokens[2].type)
        assertEquals("123", tokens[2].text)
    }

    @Test
    fun tokenTypesMatchNodeTokenReader() {
        val documents = listOf(
            "a: 1\nb: [true, null, 'x']\n",
            "outer:\n  inner:\n    value: 3.5\n",
            "anchors: &base {a: 1, b: 2}\nuse: *base\n",
            "base: &base {a: 1, b: 2}\nderived:\n  <<: *base\n  b: 3\n",
            "text: |\n  line1\n  line2\n",
            "flow: {a: [1, true, null, 'x'], b: {}}\n",
            "quoted: \"null\"\nnumstr: '123'\nempty-seq: []\nempty-map: {}\n",
            "unicode: 小于\n",
            "date: 2020-01-02\nfull: 2020-01-02T03:04:05Z\n",
            "data: !!binary AQID\n",
            "folded: >\n  a b\n  c\nkeep: |+\n  x\n\n",
            "quoted: \"a\\nb\\t\\u00e9\"\nemoji: 😀\n",
            "big: 999999999999999999999999\nneg: -42\nzero: 0\nexplicit-float: !!float 12\n",
            "flags: [true, False, on, off, yes, no]\n",
            "alias-seq:\n  - &x [1, 2]\n  - *x\n",
            "single\n",
            "- 1\n- two\n",
        )

        for (document in documents) {
            assertEquals(
                readTypes(NodeTokenReader(YamlReader(document).read())),
                readTypes(YamlTokenReader(document)),
                "Token 类型序列不一致: $document",
            )
        }
    }

    @Test
    fun tokenValuesMatchNodeTokenReader() {
        val document = """
            name: 小于
            age: 18
            hex: 0x1F
            exp: 1e3
            ok: yes
            none: ~
            date: 2020-01-02
            data: !!binary AQID
            list:
              - 1
              - two
            nested:
              x: 3.5
            text: |
              line1
              line2
        """.trimIndent()

        assertEquals(
            snapshot(NodeTokenReader(YamlReader(document).read())),
            snapshot(YamlTokenReader(document)),
        )
    }

    @Test
    fun mapperParseGoesThroughNativeTokenReader() {
        val mapper = ProbeMapper()

        val map = mapper.parse("name: 小于\nage: 18\nhex: 0x1F\nok: true\n", Map::class.java)

        assertEquals("小于", (map["name"] as StringNode).value)
        assertEquals(18, (map["age"] as NumberNode).toInt())
        assertEquals(31, (map["hex"] as NumberNode).toInt())
        assertTrue((map["ok"] as BooleanNode).value)
        assertEquals(1, mapper.tokenReaderCalls)
        assertIs<YamlTokenReader>(mapper.lastReader)
    }

    @Test
    fun mapperParsesBeanThroughTokenPath() {
        val mapper = ProbeMapper()

        val person = mapper.parse("name: A\nage: 18\nactive: true\ntags: [x, y]\n", Person::class.java)

        assertEquals("A", person.name)
        assertEquals(18, person.age)
        assertTrue(person.active)
        assertEquals(listOf("x", "y"), person.tags)
        assertIs<YamlTokenReader>(mapper.lastReader)
    }

    @Test
    fun mapperParseMatchesNodePathForMap() {
        val mapper = YamlMapper()
        val document = "a: 1\nb: [x, true, null]\nc:\n  d: 3.5\ndate: 2020-01-02\n"

        val viaToken = mapper.parse(document, Map::class.java)
        val viaNode = mapper.fromNode(YamlReader(document).read(), Map::class.java)

        assertEquals(viaNode, viaToken)
    }

    @Test
    fun mapperFallsBackToNodeTokenReaderWhenLimitsConfigured() {
        val limited = ProbeMapper(DocumentMapperConfig.builder().maxStringLength(100).build())

        val map = limited.parse("a: 1", Map::class.java)

        assertEquals(1, (map["a"] as NumberNode).toInt())
        assertIs<NodeTokenReader>(limited.lastReader)

        val failing = ProbeMapper(DocumentMapperConfig.builder().maxStringLength(3).build())
        assertFailsWith<DocumentSerializationException> {
            failing.parse("a: longvalue", Map::class.java)
        }

        val depthLimited = ProbeMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build())
        assertFailsWith<YamlParseException> {
            depthLimited.parse("a:\n  b:\n    c: 1\n", Map::class.java)
        }

        val countLimited = ProbeMapper(DocumentMapperConfig.builder().maxNodeCount(2).build())
        assertFailsWith<DocumentSerializationException> {
            countLimited.parse("a: 1\nb: 2", Map::class.java)
        }
        assertEquals(1, (countLimited.parse("a: 1", Map::class.java)["a"] as NumberNode).toInt())
    }

    @Test
    fun mapperUsesMaxAliasesFromConfig() {
        val text = "base: &base {a: 1}\none: *base\ntwo: *base\n"

        assertFailsWith<YamlParseException> {
            YamlMapper(YamlConfig.builder().maxAliases(1).build()).parse(text, Map::class.java)
        }
        YamlMapper(YamlConfig.builder().maxAliases(2).build()).parse(text, Map::class.java)
    }

    @Test
    fun fallbackPathKeepsAliasLimit() {
        val config = YamlConfig(DocumentMapperConfig.builder().maxNodeDepth(5).build(), pretty = false, maxAliases = 1)

        assertFailsWith<YamlParseException> {
            YamlMapper(config).parse("base: &base {a: 1}\none: *base\ntwo: *base\n", Map::class.java)
        }
    }

    @Test
    fun parseAllToNodesUsesMaxAliasesConfig() {
        val mapper = YamlMapper(YamlConfig.builder().maxAliases(1).build())

        assertFailsWith<YamlParseException> {
            mapper.parseAllToNodes("base: &base {a: 1}\none: *base\ntwo: *base\n")
        }
    }

    @Test
    fun maxDepthLimit() {
        val text = "a:\n  b:\n    c: 1\n"

        assertFailsWith<YamlParseException> { YamlTokenReader(text, maxDepth = 2) }
        YamlTokenReader(text, maxDepth = 3)
    }

    @Test
    fun maxAliasesLimit() {
        val text = "base: &base {a: 1}\none: *base\ntwo: *base\n"

        assertFailsWith<YamlParseException> { YamlTokenReader(text, maxAliases = 1) }
        YamlTokenReader(text, maxAliases = 2)
    }

    @Test
    fun maxStringLengthLimit() {
        assertFailsWith<YamlParseException> { YamlTokenReader("a: longvalue\n", maxStringLength = 3) }
        assertFailsWith<YamlParseException> { YamlTokenReader("longfieldname: 1\n", maxStringLength = 3) }

        YamlTokenReader("a: b\n", maxStringLength = 1)
    }

    @Test
    fun exhaustedReaderReturnsNull() {
        val reader = YamlTokenReader("a: 1\n")

        while (reader.nextToken() != null) {
            // drain
        }
        assertNull(reader.nextToken())
        assertNull(reader.currentToken)
    }

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

    private class Person(
        var name: String = "",
        var age: Int = 0,
        var active: Boolean = false,
        var tags: List<String> = emptyList(),
    )

    private class ProbeMapper(
        config: DocumentMapperConfig = DocumentMapperConfig.empty(),
    ) : YamlMapper(config) {

        var tokenReaderCalls: Int = 0
            private set

        var lastReader: DocumentTokenReader? = null
            private set

        override fun createTextTokenReader(source: String): DocumentTokenReader {
            tokenReaderCalls++
            val reader = super.createTextTokenReader(source)
            lastReader = reader
            return reader
        }
    }
}
