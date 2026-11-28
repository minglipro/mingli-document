package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JSON 配置对齐 上游实现 2.15.3 的 feature 测试。
 *
 * 覆盖读取特性、写出特性、默认值、legacy 名称映射、Builder 通用配置转发与
 * 各 API 路径（String/ByteArray/流式/Node.toJsonString）的行为一致性。
 */
class JsonFeatureTest {

    // ---------------------------------------------------------------------
    // 默认值与映射
    // ---------------------------------------------------------------------

    @Test
    fun defaultsMatchJacksonCore() {
        val config = JsonConfig.builder().build()

        assertEquals(emptySet(), config.readFeatures)
        assertEquals(
            setOf(
                JsonWriteFeature.QUOTE_FIELD_NAMES,
                JsonWriteFeature.WRITE_NAN_AS_STRINGS,
                JsonWriteFeature.WRITE_HEX_UPPER_CASE,
            ),
            config.writeFeatures,
        )
        assertEquals(JsonConfig.DEFAULT_WRITE_FEATURES, config.writeFeatures)
        assertEquals(JsonConfig.DEFAULT_READ_FEATURES, config.readFeatures)
        // 历史选项默认值保持不变
        assertTrue(config.allowComments)
        assertEquals(DuplicateKeyPolicy.LAST_WINS, config.duplicateKeyPolicy)
        assertEquals(JsonConfig.DEFAULT_MAX_DEPTH, config.maxDepth)
        assertTrue(!config.pretty)

        // 与历史选项联动的两项
        assertTrue(config.isEnabled(JsonReadFeature.ALLOW_JAVA_COMMENTS))
        assertTrue(!config.isEnabled(JsonReadFeature.STRICT_DUPLICATE_DETECTION))
        // 其余读取特性默认关闭
        JsonReadFeature.values()
            .filter { it != JsonReadFeature.ALLOW_JAVA_COMMENTS }
            .forEach { assertTrue(!config.isEnabled(it), "read feature ${it.name} should default off") }
        assertTrue(config.isEnabled(JsonWriteFeature.QUOTE_FIELD_NAMES))
        assertTrue(config.isEnabled(JsonWriteFeature.WRITE_NAN_AS_STRINGS))
        assertTrue(config.isEnabled(JsonWriteFeature.WRITE_HEX_UPPER_CASE))
        assertTrue(!config.isEnabled(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS))
        assertTrue(!config.isEnabled(JsonWriteFeature.ESCAPE_NON_ASCII))
        assertTrue(!config.isEnabled(JsonWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN))
        assertEquals(512, JsonConfig.DEFAULT_MAX_DEPTH)
    }

    @Test
    fun legacyFeatureNameMappingIsComplete() {
        assertEquals(
            listOf(
                "ALLOW_COMMENTS",
                "ALLOW_YAML_COMMENTS",
                "ALLOW_SINGLE_QUOTES",
                "ALLOW_UNQUOTED_FIELD_NAMES",
                "ALLOW_UNQUOTED_CONTROL_CHARS",
                "ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER",
                "ALLOW_NUMERIC_LEADING_ZEROS",
                "ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS",
                "ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS",
                "ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS",
                "ALLOW_NON_NUMERIC_NUMBERS",
                "ALLOW_MISSING_VALUES",
                "ALLOW_TRAILING_COMMA",
                "STRICT_DUPLICATE_DETECTION",
            ),
            JsonReadFeature.values().map { it.legacyName },
        )
        assertEquals(
            listOf(
                "QUOTE_FIELD_NAMES",
                "QUOTE_NON_NUMERIC_NUMBERS",
                "WRITE_NUMBERS_AS_STRINGS",
                "WRITE_BIGDECIMAL_AS_PLAIN",
                "ESCAPE_NON_ASCII",
                "WRITE_HEX_UPPER_CASE",
            ),
            JsonWriteFeature.values().map { it.legacyName },
        )
    }

    @Test
    fun legacyOptionsAndFeaturesStayInSync() {
        val strict = JsonConfig.builder().disable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build()
        assertTrue(!strict.allowComments)
        assertTrue(!strict.isEnabled(JsonReadFeature.ALLOW_JAVA_COMMENTS))

        val comments = JsonConfig.builder().allowComments(false).enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build()
        assertTrue(comments.allowComments)

        val strictDuplicates = JsonConfig.builder().enable(JsonReadFeature.STRICT_DUPLICATE_DETECTION).build()
        assertEquals(DuplicateKeyPolicy.FAIL, strictDuplicates.duplicateKeyPolicy)
        assertTrue(strictDuplicates.isEnabled(JsonReadFeature.STRICT_DUPLICATE_DETECTION))

        val disabled = JsonConfig.builder()
            .enable(JsonReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(JsonReadFeature.STRICT_DUPLICATE_DETECTION)
            .build()
        assertEquals(DuplicateKeyPolicy.LAST_WINS, disabled.duplicateKeyPolicy)

        // 最后一次配置生效：先开 strict 再显式改 FIRST_WINS 时 strict 关闭
        val firstWins = JsonConfig.builder()
            .enable(JsonReadFeature.STRICT_DUPLICATE_DETECTION)
            .duplicateKeyPolicy(DuplicateKeyPolicy.FIRST_WINS)
            .build()
        assertTrue(!firstWins.isEnabled(JsonReadFeature.STRICT_DUPLICATE_DETECTION))

        val configured = JsonConfig.builder()
            .configure(JsonReadFeature.ALLOW_YAML_COMMENTS, true)
            .configure(JsonWriteFeature.ESCAPE_NON_ASCII, true)
            .build()
        assertTrue(configured.isEnabled(JsonReadFeature.ALLOW_YAML_COMMENTS))
        assertTrue(configured.isEnabled(JsonWriteFeature.ESCAPE_NON_ASCII))
    }

    @Test
    fun builderForwardsGenericDocumentOptions() {
        val resolver = RemoteReferenceResolver { uri -> StringNode.of(uri) }
        val validator = DocumentBeanValidator { _ -> }
        val config = JsonConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .fileCharset(Charsets.ISO_8859_1)
            .atomicFileWrite(true)
            .createParentDirectories(true)
            .maxInputSize(1024)
            .maxStringLength(16)
            .maxNodeDepth(8)
            .maxNodeCount(100)
            .resolveReferences(true)
            .writeReferences(true)
            .remoteReferenceResolver(resolver)
            .beanValidator(validator)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build()

        assertEquals(PropertyNamingStrategy.SNAKE_CASE, config.namingStrategy)
        assertEquals(Charsets.ISO_8859_1, config.fileCharset)
        assertTrue(config.atomicFileWrite)
        assertTrue(config.createParentDirectories)
        assertEquals(1024L, config.maxInputSize)
        assertEquals(16, config.maxStringLength)
        assertEquals(8, config.maxNodeDepth)
        assertEquals(100L, config.maxNodeCount)
        assertTrue(config.resolveReferences)
        assertTrue(config.writeReferences)
        assertEquals(resolver, config.remoteReferenceResolver)
        assertEquals(validator, config.beanValidator)
        assertTrue(config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
    }

    @Test
    fun genericOptionsReachJsonMapperBehavior() {
        val named = JsonMapper(JsonConfig.builder().namingStrategy(PropertyNamingStrategy.SNAKE_CASE).build())
        assertEquals("""{"user_name":"a"}""", named.format(NamedPerson()))

        val limited = JsonMapper(JsonConfig.builder().maxInputSize(5).build())
        assertFailsWith<DocumentSerializationException> {
            limited.parse("""{"a":1}""", Node::class.java)
        }
    }

    @Test
    fun writeNullsMapsToJsonIncludeNullPolicy() {
        // JsonInclude.Include.ALWAYS（默认）
        assertEquals(
            """{"age":1,"name":null}""",
            JsonMapper().format(NullableBean()),
        )
        // JsonInclude.Include.NON_NULL
        assertEquals(
            """{"age":1}""",
            JsonMapper(JsonConfig.builder().writeNulls(false).build()).format(NullableBean()),
        )
    }

    @Test
    fun deserializationFeatureIsForwardedToJsonMapper() {
        // 默认关闭：null 赋给基本类型不报错
        assertEquals(0, JsonMapper().parse("""{"age":null}""", Person::class.java).age)
        val strict = JsonMapper(
            JsonConfig.builder().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build(),
        )
        assertFailsWith<DocumentSerializationException> { strict.parse("""{"age":null}""", Person::class.java) }
    }

    @Test
    fun enumNumbersAreRejectedEquivalentlyToFailOnNumbersForEnums() {
        // serialization 层的 enum 只接受 JSON 字符串，数字一律失败，
        // 行为等价于 上游实现 DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS 恒开启。
        assertFailsWith<DocumentSerializationException> {
            JsonMapper().parse("""{"status":1}""", StatusBean::class.java)
        }
        assertEquals(Status.ACTIVE, JsonMapper().parse("""{"status":"ACTIVE"}""", StatusBean::class.java).status)
    }

    // ---------------------------------------------------------------------
    // 读取特性
    // ---------------------------------------------------------------------

    private fun reader(source: String, vararg features: JsonReadFeature): Node = JsonReader(source, readFeatures = features.toSet()).read()

    private fun mapper(vararg features: JsonReadFeature): JsonMapper = JsonMapper(
        JsonConfig.builder()
            .apply { features.forEach { enable(it) } }
            .build(),
    )

    @Test
    fun javaCommentsFeatureTogglesLegacySwitch() {
        assertEquals(1, reader("""{"a":1} // c""").asObject()["a"]!!.asNumber().toInt())
        assertEquals(1, reader("{# c\n\"a\":1}", JsonReadFeature.ALLOW_YAML_COMMENTS).asObject()["a"]!!.asNumber().toInt())

        val strict = JsonMapper(JsonConfig.builder().disable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build())
        assertFailsWith<JsonParseException> { strict.parse("""{"a":1} // c""", Node::class.java) }
    }

    @Test
    fun allowYamlComments() {
        assertFailsWith<JsonParseException> { reader("{\n# c\n\"a\":1}") }
        val node = reader("{\n# c\n\"a\":1\n}", JsonReadFeature.ALLOW_YAML_COMMENTS)
        assertEquals(1, node.asObject()["a"]!!.asNumber().toInt())
        // 行内注释
        assertEquals(
            1,
            reader(
                """{"a":1 # c
        }""",
                JsonReadFeature.ALLOW_YAML_COMMENTS,
            ).asObject()["a"]!!.asNumber().toInt(),
        )
    }

    @Test
    fun allowSingleQuotes() {
        assertFailsWith<JsonParseException> { reader("{'a':'b'}") }
        val node = reader("{'a':'b'}", JsonReadFeature.ALLOW_SINGLE_QUOTES)
        assertEquals("b", node.asObject()["a"]!!.asString())
        assertEquals("x'y", reader("""{'a':'x\'y'}""", JsonReadFeature.ALLOW_SINGLE_QUOTES).asObject()["a"]!!.asString())

        val bean = mapper(JsonReadFeature.ALLOW_SINGLE_QUOTES).parse("{'name':'小于'}", Person::class.java)
        assertEquals("小于", bean.name)
    }

    @Test
    fun allowUnquotedFieldNames() {
        assertFailsWith<JsonParseException> { reader("{name:\"a\"}") }
        val node = reader("""{name:"小于",age:18}""", JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
        assertEquals("小于", node.asObject()["name"]!!.asString())

        val bean = mapper(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES).parse("""{name:"小于",age:18}""", Person::class.java)
        assertEquals("小于", bean.name)
        assertEquals(18, bean.age)
    }

    @Test
    fun allowUnescapedControlChars() {
        val json = "{\"name\":\"a\nb\"}"
        assertFailsWith<JsonParseException> { reader(json) }
        val node = reader(json, JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        assertEquals("a\nb", node.asObject()["name"]!!.asString())
    }

    @Test
    fun allowBackslashEscapingAnyCharacter() {
        assertFailsWith<JsonParseException> { reader("""{"a":"x\qy"}""") }
        val node = reader("""{"a":"x\qy"}""", JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
        assertEquals("xqy", node.asObject()["a"]!!.asString())
    }

    @Test
    fun allowLeadingZerosForNumbers() {
        assertFailsWith<JsonParseException> { reader("[0123]") }
        val number = reader("[0123]", JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS).asArray()[0].asNumber()
        assertEquals("0123", number.raw)
        assertEquals(123, number.toInt())
    }

    @Test
    fun allowLeadingPlusSignForNumbers() {
        assertFailsWith<JsonParseException> { reader("[+1]") }
        assertEquals(1, reader("[+1]", JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS).asArray()[0].asNumber().toInt())
        assertEquals(100, reader("[+1e2]", JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS).asArray()[0].asNumber().toInt())
    }

    @Test
    fun allowLeadingDecimalPointForNumbers() {
        assertFailsWith<JsonParseException> { reader("[.5]") }
        assertEquals(0.5, reader("[.5]", JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS).asArray()[0].asNumber().toDouble())
    }

    @Test
    fun allowTrailingDecimalPointForNumbers() {
        assertFailsWith<JsonParseException> { reader("[1.]") }
        val number = reader("[1.]", JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS).asArray()[0].asNumber()
        assertEquals(1.0, number.toDouble())
    }

    @Test
    fun allowNonNumericNumbers() {
        assertFailsWith<JsonParseException> { reader("[NaN]") }
        val array = reader(
            "[NaN,Infinity,-Infinity,+INF,-INF,+Infinity]",
            JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS,
        ).asArray()
        assertTrue(array[0].asNumber().toDouble().isNaN())
        assertEquals("NaN", array[0].asNumber().raw)
        assertEquals(Double.POSITIVE_INFINITY, array[1].asNumber().toDouble())
        assertEquals("Infinity", array[1].asNumber().raw)
        assertEquals(Double.NEGATIVE_INFINITY, array[2].asNumber().toDouble())
        assertEquals("-Infinity", array[2].asNumber().raw)
        assertEquals("Infinity", array[3].asNumber().raw)
        assertEquals("-Infinity", array[4].asNumber().raw)
        assertEquals("Infinity", array[5].asNumber().raw)

        val bean = mapper(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).parse("""{"value":NaN}""", DoubleBean::class.java)
        assertTrue(bean.value.isNaN())
    }

    @Test
    fun allowMissingValues() {
        assertFailsWith<JsonParseException> { reader("[1,,2]") }
        val feature = JsonReadFeature.ALLOW_MISSING_VALUES

        val first = reader("[1,,2]", feature).asArray()
        assertEquals(3, first.size)
        assertTrue(!first[0].isNull)
        assertTrue(first[1].isNull)
        assertTrue(!first[2].isNull)

        val second = reader("[,1]", feature).asArray()
        assertEquals(2, second.size)
        assertTrue(second[0].isNull)
        assertEquals(1, second[1].asNumber().toInt())

        val third = reader("[1,]", feature).asArray()
        assertEquals(2, third.size)
        assertTrue(third[1].isNull)

        assertEquals(2, reader("[,]", feature).asArray().size)
        assertEquals(3, reader("[,,]", feature).asArray().size)
        // 与尾逗号同时开启时尾逗号优先
        assertEquals(
            1,
            reader("[1,]", feature, JsonReadFeature.ALLOW_TRAILING_COMMA).asArray().size,
        )

        val list = mapper(feature).parse("""[,"a"]""", DocumentTypeReference.listType(String::class.java))
        assertEquals(2, list.size)
        assertNull(list[0])
        assertEquals("a", list[1])
    }

    @Test
    fun allowTrailingComma() {
        assertFailsWith<JsonParseException> { reader("[1,]") }
        assertFailsWith<JsonParseException> { reader("""{"a":1,}""") }

        val feature = JsonReadFeature.ALLOW_TRAILING_COMMA
        assertEquals(1, reader("[1,]", feature).asArray().size)
        assertEquals(1, reader("""{"a":1,}""", feature).asObject()["a"]!!.asNumber().toInt())

        val bean = mapper(feature).parse("""{"name":"小于","age":18,}""", Person::class.java)
        assertEquals("小于", bean.name)
        assertEquals(18, bean.age)
    }

    @Test
    fun strictDuplicateDetectionFeature() {
        val strict = mapper(JsonReadFeature.STRICT_DUPLICATE_DETECTION)
        assertFailsWith<JsonParseException> { strict.parse("""{"a":1,"a":2}""", Node::class.java) }
        assertEquals(2, JsonMapper().parse("""{"a":1,"a":2}""", Node::class.java).asObject()["a"]!!.asNumber().toInt())

        // 与 上游实现 一样，strict 也可用于 JsonReader
        assertFailsWith<JsonParseException> {
            reader("""{"a":1,"a":2}""", JsonReadFeature.STRICT_DUPLICATE_DETECTION)
        }
    }

    @Test
    fun readFeaturesApplyToBytesAndStreamingApis() {
        val singleQuotes = mapper(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        val bean = singleQuotes.parse("{'name':'小于'}".toByteArray(Charsets.UTF_8), Person::class.java)
        assertEquals("小于", bean.name)

        val trailingComma = mapper(JsonReadFeature.ALLOW_TRAILING_COMMA)
        val people = ArrayList<Person?>()
        trailingComma.parseArray(
            ByteArrayInputStream("""[{"name":"a","age":1},{"name":"b","age":2},]""".toByteArray(Charsets.UTF_8)),
            Person::class.java,
        ) { people.add(it) }
        assertEquals(2, people.size)
        assertEquals("b", people[1]!!.name)

        val lines = ArrayList<Person?>()
        singleQuotes.parseLines(StringReader("{'name':'x'}\n{'name':'y'}\n"), Person::class.java) { lines.add(it) }
        assertEquals(2, lines.size)
        assertEquals("y", lines[1]!!.name)
    }

    @Test
    fun missingValuesApplyToStreamingArrayApi() {
        val mapper = mapper(JsonReadFeature.ALLOW_MISSING_VALUES)
        val elements = ArrayList<Node?>()
        mapper.parseArray(ByteArrayInputStream("[1,,2]".toByteArray(Charsets.UTF_8)), Node::class.java) {
            elements.add(it)
        }
        assertEquals(3, elements.size)
        assertNull(elements[1])
        assertEquals(2, elements[2]!!.asNumber().toInt())
    }

    // ---------------------------------------------------------------------
    // 写出特性
    // ---------------------------------------------------------------------

    private fun writer(vararg features: JsonWriteFeature): JsonMapper = JsonMapper(
        JsonConfig.builder()
            .apply { features.forEach { enable(it) } }
            .build(),
    )

    @Test
    fun defaultWriteFeaturesMatchJackson() {
        val mapper = JsonMapper()
        // 大写十六进制转义（WRITE_HEX_UPPER_CASE 默认开启）
        assertEquals("\"\\u001F\"", mapper.format(StringNode.of("\u001F")))
        // ESCAPE_NON_ASCII 默认关闭：非 ASCII 原样输出
        assertEquals("\"é中😀\"", mapper.format(StringNode.of("é中😀")))
        // QUOTE_FIELD_NAMES 默认开启
        assertEquals("""{"a":1}""", mapper.format(ObjectNode.of("a" to NumberNode.of(1))))
    }

    @Test
    fun quoteFieldNamesToggle() {
        val node = ObjectNode.of("a" to NumberNode.of(1))
        assertEquals("""{"a":1}""", JsonMapper().format(node))

        val disabled = JsonMapper(JsonConfig.builder().disable(JsonWriteFeature.QUOTE_FIELD_NAMES).build())
        assertEquals("""{a:1}""", disabled.format(node))
    }

    @Test
    fun writeNumbersAsStrings() {
        val mapper = writer(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS)
        assertTrue(mapper.format(Person().also { it.age = 18 }).contains("\"age\":\"18\""))
        assertEquals("""["1","2.5"]""", mapper.format(reader("[1,2.5]")))
        assertEquals("""{"value":"1.5"}""", mapper.format(DoubleBean().also { it.value = 1.5 }))
    }

    @Test
    fun escapeNonAscii() {
        val mapper = writer(JsonWriteFeature.ESCAPE_NON_ASCII)
        assertEquals("\"\\u00E9\\u4E2D\\uD83D\\uDE00\"", mapper.format(StringNode.of("é中😀")))
    }

    @Test
    fun writeHexUpperCaseToggle() {
        val lower = writer(JsonWriteFeature.ESCAPE_NON_ASCII)
            .format(StringNode.of("é"))
        assertEquals("\"\\u00E9\"", lower)

        val lowerCase = JsonMapper(
            JsonConfig.builder()
                .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
                .disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE)
                .build(),
        ).format(StringNode.of("é"))
        assertEquals("\"\\u00e9\"", lowerCase)

        val controlLower = JsonMapper(JsonConfig.builder().disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE).build())
            .format(StringNode.of("\u001F"))
        assertEquals("\"\\u001f\"", controlLower)
    }

    @Test
    fun writeBigDecimalAsPlain() {
        val node = ObjectNode.of("n" to NumberNode.ofRaw("1E+5"))
        assertEquals("""{"n":1E+5}""", JsonMapper().format(node))
        assertEquals("""{"n":100000}""", writer(JsonWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).format(node))
        assertEquals(
            """{"n":100000}""",
            node.toJsonString(writeFeatures = JsonConfig.DEFAULT_WRITE_FEATURES + JsonWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN),
        )
        // BigDecimal 值本身在 serialization 层即输出普通表示
        assertEquals("""{"n":100000}""", JsonMapper().format(ObjectNode.of("n" to NumberNode.of(BigDecimal("1E+5")))))
    }

    @Test
    fun writeNanAsStrings() {
        val node = ObjectNode.of("n" to NumberNode.ofRaw("NaN"))
        assertEquals("""{"n":"NaN"}""", JsonMapper().format(node))
        assertEquals(
            """{"n":NaN}""",
            JsonMapper(JsonConfig.builder().disable(JsonWriteFeature.WRITE_NAN_AS_STRINGS).build()).format(node),
        )
        assertEquals("""["Infinity","-Infinity"]""", JsonMapper().format(reader("[Infinity,-Infinity]", JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)))
    }

    @Test
    fun writeFeaturesApplyToBytesStreamAndLineApis() {
        val numbers = writer(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS)
        val bytes = numbers.formatBytes(Person().also { it.age = 18 })
        assertTrue(String(bytes, Charsets.UTF_8).contains("\"age\":\"18\""))

        val escaped = writer(JsonWriteFeature.ESCAPE_NON_ASCII)
        val output = ByteArrayOutputStream()
        escaped.format(StringNode.of("中"), output)
        assertTrue(String(output.toByteArray(), Charsets.UTF_8).contains("\\u4E2D"))

        val arrayOutput = ByteArrayOutputStream()
        numbers.formatArray(listOf(1, 2), arrayOutput)
        assertEquals("""["1","2"]""", String(arrayOutput.toByteArray(), Charsets.UTF_8))

        val linesOutput = ByteArrayOutputStream()
        numbers.formatLines(listOf(1, 2), linesOutput)
        assertEquals("\"1\"\n\"2\"\n", String(linesOutput.toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun nodeToJsonStringAcceptsWriteFeatures() {
        assertEquals(
            "\"\\u4E2D\"",
            StringNode.of("中").toJsonString(
                writeFeatures = JsonConfig.DEFAULT_WRITE_FEATURES + JsonWriteFeature.ESCAPE_NON_ASCII,
            ),
        )
        assertEquals(
            "\"中\"",
            StringNode.of("中").toJsonString(),
        )
        assertEquals(
            """{a:"1"}""",
            ObjectNode.of("a" to NumberNode.of(1)).toJsonString(
                writeFeatures = setOf(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS),
            ),
        )
    }

    @Test
    fun writeNanAsStringsAppliesToBeanStreamingPath() {
        val nan = DoubleBean().also { it.value = Double.NaN }
        val infinity = DoubleBean().also { it.value = Double.POSITIVE_INFINITY }
        assertEquals("""{"value":"NaN"}""", JsonMapper().format(nan))
        assertEquals("""{"value":"Infinity"}""", JsonMapper().format(infinity))
        assertEquals(
            """{"value":NaN}""",
            JsonMapper(JsonConfig.builder().disable(JsonWriteFeature.WRITE_NAN_AS_STRINGS).build()).format(nan),
        )
        assertTrue(String(JsonMapper().formatBytes(nan), Charsets.UTF_8).contains("\"NaN\""))

        // toNode 路径仍受 NumberNode 只能保存有限 BigDecimal 限制
        assertFailsWith<IllegalArgumentException> { JsonMapper().toNode(nan) }
    }

    // ---------------------------------------------------------------------
    // 测试用 Bean
    // ---------------------------------------------------------------------

    private class Person {
        var name: String = ""
        var age: Int = 0
    }

    private class DoubleBean {
        var value: Double = 0.0
    }

    private class NamedPerson {
        var userName: String = "a"
    }

    private class NullableBean {
        var name: String? = null
        var age: Int = 1
    }

    private enum class Status {
        ACTIVE,
    }

    private class StatusBean {
        var status: Status = Status.ACTIVE
    }
}
