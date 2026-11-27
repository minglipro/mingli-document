package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.serialization.DocumentTokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * JSON 加固能力测试。
 *
 * 覆盖 byte API（UTF-8 / 非法 UTF-8）、重复 key 策略（树 / Token / Bean 三条路径）
 * 以及对象/数组嵌套深度限制（含深嵌套不触发 StackOverflow）。
 */
class JsonHardeningTest {

    // ---------------------------------------------------------------------
    // byte API
    // ---------------------------------------------------------------------

    @Test
    fun formatBytesAndParseBytesRoundTripBean() {
        val mapper = JsonMapper()
        val person = Person()
        person.name = "小于😀"
        person.age = 18

        val bytes = mapper.formatBytes(person)

        assertEquals(mapper.format(person), String(bytes, Charsets.UTF_8))
        assertTrue(String(bytes, Charsets.UTF_8).contains("小于😀"))

        val restored = mapper.parse(bytes, Person::class.java)
        assertEquals("小于😀", restored.name)
        assertEquals(18, restored.age)
    }

    @Test
    fun parseBytesSupportsTypeReferenceAndNode() {
        val mapper = JsonMapper()
        val first = Person()
        first.name = "甲"
        first.age = 1
        val second = Person()
        second.name = "乙"
        second.age = 2

        val listBytes = mapper.formatBytes(listOf(first, second))
        val people = mapper.parse(listBytes, DocumentTypeReference.listType(Person::class.java))
        assertEquals(2, people.size)
        assertEquals("甲", people[0].name)
        assertEquals("乙", people[1].name)

        val node = mapper.toNode(mapOf("text" to "小于", "n" to 1))
        val nodeBytes = mapper.formatBytes(node)
        assertEquals(node, mapper.parse(nodeBytes, Node::class.java))
    }

    @Test
    fun formatBytesHonorsPrettyConfig() {
        val mapper = JsonMapper(JsonConfig.builder().pretty(true).build())
        val text = String(mapper.formatBytes(mapOf("a" to 1)), Charsets.UTF_8)
        assertTrue(text.contains("\n"))
    }

    @Test
    fun parseBytesRejectsInvalidUtf8() {
        val mapper = JsonMapper()
        // {"name":" 之后是截断的二字节 UTF-8 序列（0xC3 后缺少续字节）
        val truncated = byteArrayOf(
            0x7B, 0x22, 0x6E, 0x61, 0x6D, 0x65, 0x22, 0x3A, 0x22,
            0xC3.toByte(), 0x22, 0x7D,
        )
        val truncatedError = assertFailsWith<JsonParseException> {
            mapper.parse(truncated, Person::class.java)
        }
        assertTrue(truncatedError.message.orEmpty().contains("UTF-8"))

        assertFailsWith<JsonParseException> {
            mapper.parse(truncated, DocumentTypeReference.of(Person::class.java))
        }
        assertFailsWith<JsonParseException> {
            mapper.parse(byteArrayOf(0x80.toByte()), Node::class.java)
        }
        assertFailsWith<JsonParseException> {
            mapper.parse(byteArrayOf(0xFF.toByte()), Node::class.java)
        }
    }

    @Test
    fun parseBytesRejectsEmptyInput() {
        assertFailsWith<JsonParseException> {
            JsonMapper().parse(ByteArray(0), Node::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // 重复 key：树路径
    // ---------------------------------------------------------------------

    @Test
    fun treeParserDuplicateKeyPolicies() {
        val json = """{"a":1,"b":2,"a":3}"""

        val lastWins = JsonReader(json).read().asObject()
        assertEquals(2, lastWins.size)
        assertEquals(3, lastWins["a"]!!.asNumber().toInt())
        assertEquals(2, lastWins["b"]!!.asNumber().toInt())

        val firstWins = JsonReader(json, duplicateKeyPolicy = DuplicateKeyPolicy.FIRST_WINS).read().asObject()
        assertEquals(2, firstWins.size)
        assertEquals(1, firstWins["a"]!!.asNumber().toInt())
        assertEquals(listOf("a", "b"), firstWins.keys.toList())

        assertFailsWith<JsonParseException> {
            JsonReader(json, duplicateKeyPolicy = DuplicateKeyPolicy.FAIL).read()
        }
        assertFailsWith<JsonParseException> {
            JsonReader("""{"o":{"x":1,"x":2}}""", duplicateKeyPolicy = DuplicateKeyPolicy.FAIL).read()
        }
        assertEquals(
            2,
            JsonReader("""{"o":{"x":1,"x":2}}""")
                .read().asObject()["o"]!!.asObject()["x"]!!.asNumber().toInt(),
        )
    }

    // ---------------------------------------------------------------------
    // 重复 key：Token 路径
    // ---------------------------------------------------------------------

    @Test
    fun tokenReaderDuplicateKeyPolicies() {
        val json = """{"a":1,"b":2,"a":3}"""

        val lastWins = drainJsonTokens(JsonTokenReader(json))
        assertEquals(2, fieldNames(lastWins).count { it == "a" })

        val firstWins = drainJsonTokens(JsonTokenReader(json, duplicateKeyPolicy = DuplicateKeyPolicy.FIRST_WINS))
        assertEquals(listOf("a", "b"), fieldNames(firstWins))
        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NUMBER,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NUMBER,
                DocumentTokenType.END_OBJECT,
            ),
            firstWins.map { it.first },
        )

        assertFailsWith<JsonParseException> {
            drainJsonTokens(JsonTokenReader(json, duplicateKeyPolicy = DuplicateKeyPolicy.FAIL))
        }
    }

    @Test
    fun tokenReaderFirstWinsSkipsComplexDuplicateValue() {
        val json = """{"a":{"x":1},"a":{"y":2},"b":3}"""
        val tokens = drainJsonTokens(JsonTokenReader(json, duplicateKeyPolicy = DuplicateKeyPolicy.FIRST_WINS))

        assertEquals(listOf("a", "x", "b"), fieldNames(tokens))
        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NUMBER,
                DocumentTokenType.END_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NUMBER,
                DocumentTokenType.END_OBJECT,
            ),
            tokens.map { it.first },
        )
        assertFailsWith<JsonParseException> {
            drainJsonTokens(
                JsonTokenReader("""{"a":1,"a":}""", duplicateKeyPolicy = DuplicateKeyPolicy.FIRST_WINS),
            )
        }

        // 被跳过的重复值含转义字符、花括号、逗号时位置需与后续字段保持同步
        val escaped = drainJsonTokens(
            JsonTokenReader(
                """{"a":1,"a":"x}y\",{z\"","b":2}""",
                duplicateKeyPolicy = DuplicateKeyPolicy.FIRST_WINS,
            ),
        )
        assertEquals(listOf("a", "b"), fieldNames(escaped))
        assertEquals(2, escaped.count { it.first == DocumentTokenType.NUMBER })
        assertEquals(6, escaped.size)
    }

    // ---------------------------------------------------------------------
    // 重复 key：Bean / Map 路径（JsonMapper Token 直读）
    // ---------------------------------------------------------------------

    @Test
    fun mapperBeanPathDuplicateKeyPolicies() {
        val json = """{"name":"first","name":"second","age":1}"""

        assertEquals("second", JsonMapper().parse(json, Person::class.java).name)

        val firstWinsMapper = JsonMapper(JsonConfig.builder().duplicateKeyPolicy(DuplicateKeyPolicy.FIRST_WINS).build())
        assertEquals("first", firstWinsMapper.parse(json, Person::class.java).name)

        val failMapper = JsonMapper(JsonConfig.builder().duplicateKeyPolicy(DuplicateKeyPolicy.FAIL).build())
        assertFailsWith<JsonParseException> { failMapper.parse(json, Person::class.java) }

        val mapType = DocumentTypeReference.mapType(String::class.java, Int::class.java)
        assertEquals(1, firstWinsMapper.parse("""{"a":1,"a":2}""", mapType)["a"])
        assertEquals(2, JsonMapper().parse("""{"a":1,"a":2}""", mapType)["a"])
        assertEquals(
            1,
            firstWinsMapper.parse("""{"a":1,"a":2}""", Node::class.java).asObject()["a"]!!.asNumber().toInt(),
        )

        // byte API 复用同一策略
        val duplicateBytes = """{"a":1,"a":2}""".toByteArray(Charsets.UTF_8)
        val failMapperBytes = JsonMapper(JsonConfig.builder().duplicateKeyPolicy(DuplicateKeyPolicy.FAIL).build())
        assertFailsWith<JsonParseException> { failMapperBytes.parse(duplicateBytes, Node::class.java) }
        val firstWinsNode = firstWinsMapper.parse(duplicateBytes, Node::class.java)
        assertEquals(1, firstWinsNode.asObject()["a"]!!.asNumber().toInt())
    }

    // ---------------------------------------------------------------------
    // 深度限制
    // ---------------------------------------------------------------------

    @Test
    fun treeParserMaxDepthBoundaries() {
        val maxDepth = 3

        // maxDepth-1 与 maxDepth 都应通过
        JsonReader(nestedArrays(maxDepth - 1), maxDepth = maxDepth).read()
        JsonReader(nestedArrays(maxDepth), maxDepth = maxDepth).read()

        // maxDepth+1 应失败
        assertFailsWith<JsonParseException> {
            JsonReader(nestedArrays(maxDepth + 1), maxDepth = maxDepth).read()
        }

        // 对象与数组统一计数：{"a":[{"b":[]}]} 深度为 4
        JsonReader("""{"a":[{"b":[]}]}""", maxDepth = 4).read()
        assertFailsWith<JsonParseException> {
            JsonReader("""{"a":[{"b":[]}]}""", maxDepth = 3).read()
        }
    }

    @Test
    fun tokenParserAndBeanPathMaxDepthBoundaries() {
        val mapper = JsonMapper(JsonConfig.builder().maxDepth(3).build())

        mapper.parse(nestedArrays(2), Node::class.java)
        mapper.parse(nestedArrays(3), Node::class.java)
        assertFailsWith<JsonParseException> { mapper.parse(nestedArrays(4), Node::class.java) }
        assertFailsWith<JsonParseException> {
            mapper.parse(nestedArrays(4).toByteArray(Charsets.UTF_8), Node::class.java)
        }

        // Bean 路径同样受 Token 读取器深度限制
        assertFailsWith<JsonParseException> {
            mapper.parse("""{"child":{"child":{"child":{"child":{}}}}}""", NestedBean::class.java)
        }
    }

    @Test
    fun firstWinsDuplicateValueStillHonorsMaxDepth() {
        val mapper = JsonMapper(
            JsonConfig.builder()
                .duplicateKeyPolicy(DuplicateKeyPolicy.FIRST_WINS)
                .maxDepth(2)
                .build(),
        )
        // 重复字段值 [[1]] 叠加根对象深度后为 3，超过 maxDepth=2
        assertFailsWith<JsonParseException> {
            mapper.parse("""{"a":1,"a":[[1]]}""", Node::class.java)
        }
    }

    @Test
    fun beanPathHonorsConfiguredMaxDepth() {
        val mapper = JsonMapper(JsonConfig.builder().maxDepth(64).build())
        // 100 层对象嵌套：在 65 层处由 Token 读取器抛出受控异常，不递归到 StackOverflow
        assertFailsWith<JsonParseException> {
            mapper.parse(nestedObjects(100), NestedBean::class.java)
        }
        mapper.parse(nestedObjects(64), NestedBean::class.java)
    }

    @Test
    fun deepNestingThrowsControlledExceptionInsteadOfStackOverflow() {
        val deep = nestedArrays(10_000)

        val treeError = assertFailsWith<JsonParseException> { JsonReader(deep).read() }
        assertTrue(treeError.message.orEmpty().contains("Maximum nesting depth"))

        // Token 读取器为显式状态机，深嵌套在 maxDepth 处直接抛错
        assertFailsWith<JsonParseException> { drainJsonTokens(JsonTokenReader(deep)) }

        val mapper = JsonMapper()
        assertFailsWith<JsonParseException> { mapper.parse(deep, Node::class.java) }
        assertFailsWith<JsonParseException> {
            mapper.parse(deep.toByteArray(Charsets.UTF_8), Node::class.java)
        }
    }

    // ---------------------------------------------------------------------
    // 配置默认值与校验
    // ---------------------------------------------------------------------

    @Test
    fun configDefaultsAndMaxDepthValidation() {
        val defaults = JsonConfig.builder().build()
        assertEquals(DuplicateKeyPolicy.LAST_WINS, defaults.duplicateKeyPolicy)
        assertEquals(JsonConfig.DEFAULT_MAX_DEPTH, defaults.maxDepth)
        assertEquals(512, JsonConfig.DEFAULT_MAX_DEPTH)

        assertFailsWith<IllegalArgumentException> { JsonConfig.builder().maxDepth(0) }
        assertFailsWith<IllegalArgumentException> { JsonConfig.builder().maxDepth(-1) }

        val custom = JsonConfig.builder()
            .duplicateKeyPolicy(DuplicateKeyPolicy.FAIL)
            .maxDepth(8)
            .build()
        assertEquals(DuplicateKeyPolicy.FAIL, custom.duplicateKeyPolicy)
        assertEquals(8, custom.maxDepth)
    }

    // ---------------------------------------------------------------------
    // 辅助
    // ---------------------------------------------------------------------

    private fun nestedArrays(depth: Int): String = "[".repeat(depth) + "1" + "]".repeat(depth)

    /** 固定 depth 层对象嵌套，例如 depth=2 生成 `{"child":{}}`。 */
    private fun nestedObjects(depth: Int): String = "{\"child\":".repeat(depth - 1) + "{}" + "}".repeat(depth - 1)

    private fun fieldNames(tokens: List<Pair<DocumentTokenType, String?>>): List<String> = tokens.filter { it.first == DocumentTokenType.FIELD_NAME }.map { it.second ?: "" }

    private fun drainJsonTokens(reader: JsonTokenReader): List<Pair<DocumentTokenType, String?>> {
        val tokens = ArrayList<Pair<DocumentTokenType, String?>>()
        while (true) {
            val type = reader.nextToken() ?: break
            val text = when (type) {
                DocumentTokenType.FIELD_NAME, DocumentTokenType.STRING, DocumentTokenType.NUMBER -> reader.textValue
                else -> null
            }
            tokens.add(type to text)
        }
        return tokens
    }

    private class Person {
        var name: String = ""
        var age: Int = 0
    }

    private class NestedBean {
        var child: NestedBean? = null
    }
}
