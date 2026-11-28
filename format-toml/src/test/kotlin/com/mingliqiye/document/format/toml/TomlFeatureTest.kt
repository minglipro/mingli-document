package com.mingliqiye.document.format.toml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.toml.exception.TomlException
import com.mingliqiye.document.format.toml.exception.TomlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TOML 特性行为测试：
 * - [TomlWriteFeature.FAIL_ON_NULL_WRITE]（默认关闭：null -> `""`；开启：报错）；
 * - [TomlReadFeature.PARSE_JAVA_TIME]（默认关闭：源码原文；开启：`java.time` 规范化字符串）；
 * - [TomlReadFeature.VALIDATE_DATE_TIME]（字符串模式额外校验）；
 * - 注释保留写出与主配置合并。
 */
class TomlFeatureTest {

    @Test
    fun nullWrittenAsEmptyStringByDefault() {
        val node = ObjectNode.of(
            "name" to NullNode.INSTANCE,
            "list" to ArrayNode.of(NullNode.INSTANCE, StringNode.of("x")),
            "nested" to ObjectNode.of("nil" to NullNode.INSTANCE),
        )

        assertEquals(
            "name = \"\"\nlist = [ \"\", \"x\" ]\nnested = { nil = \"\" }\n",
            node.toTomlString(),
        )
        val restored = TomlReader(node.toTomlString()).read().asObject()
        assertEquals("", restored["name"]?.asString())
        assertEquals("", restored["list"]?.asArray()?.get(0)?.asString())
    }

    @Test
    fun failOnNullWriteRejectsNullValues() {
        val node = ObjectNode.of(
            "name" to NullNode.INSTANCE,
            "list" to ArrayNode.of(NullNode.INSTANCE),
            "nested" to ObjectNode.of("nil" to NullNode.INSTANCE),
        )

        val error = assertFailsWith<TomlException> {
            node.toTomlString(setOf(TomlWriteFeature.FAIL_ON_NULL_WRITE))
        }
        assertTrue(error.message.orEmpty().contains("FAIL_ON_NULL_WRITE"))

        // 不含 null 时开启特性仍可正常写出。
        assertEquals(
            "a = 1\n",
            ObjectNode.of("a" to NumberNode.of(1)).toTomlString(setOf(TomlWriteFeature.FAIL_ON_NULL_WRITE)),
        )
    }

    @Test
    fun mapperBuilderAppliesFailOnNullWrite() {
        val strict = TomlMapper.builder().enable(TomlWriteFeature.FAIL_ON_NULL_WRITE).build()
        assertFailsWith<TomlException> {
            strict.format(ObjectNode.of("a" to NullNode.INSTANCE))
        }
        assertEquals("a = \"\"\n", TomlMapper().format(ObjectNode.of("a" to NullNode.INSTANCE)))
        assertEquals(
            "a = \"\"\n",
            TomlMapper.builder().disable(TomlWriteFeature.FAIL_ON_NULL_WRITE).build()
                .format(ObjectNode.of("a" to NullNode.INSTANCE)),
        )
    }

    @Test
    fun sourceTextPreservedInAllContainers() {
        // 注意：tomlj 1.0.0 不接受数组中以逗号分隔的多个日期时间字面量，
        // 因此数组用例只放单个日期时间（解析器限制，与本模块无关）。
        val source = """
            top = 1979-05-27T07:32:00
            arr = [ 1979-05-27 ]
            timeArray = [ 07:32:00 ]
            inline = { t = 07:32:00, d = 1979-05-27 07:32:00.5 }
            [table]
            z = 1979-05-27T07:32:00Z
        """.trimIndent()

        val root = TomlReader(source).read().asObject()
        assertEquals("1979-05-27T07:32:00", root["top"]?.asString())
        assertEquals("1979-05-27", root["arr"]?.asArray()?.get(0)?.asString())
        assertEquals("07:32:00", root["timeArray"]?.asArray()?.get(0)?.asString())
        assertEquals("07:32:00", root["inline"]?.asObject()?.get("t")?.asString())
        assertEquals("1979-05-27T07:32:00.5", root["inline"]?.asObject()?.get("d")?.asString())
        assertEquals("1979-05-27T07:32:00Z", root["table"]?.asObject()?.get("z")?.asString())
    }

    @Test
    fun parseJavaTimeCanonicalizesValues() {
        val source = """
            dateTime = 1979-05-27T07:32:00
            time = 07:32:00
            offset = 1979-05-27T07:32:00.123Z
            date = 1979-05-27
        """.trimIndent()

        val root = TomlReader(source, readFeatures = setOf(TomlReadFeature.PARSE_JAVA_TIME)).read().asObject()
        // java.time 规范化：整分秒被省略。
        assertEquals("1979-05-27T07:32", root["dateTime"]?.asString())
        assertEquals("07:32", root["time"]?.asString())
        assertEquals("1979-05-27T07:32:00.123Z", root["offset"]?.asString())
        assertEquals("1979-05-27", root["date"]?.asString())
    }

    @Test
    fun parseJavaTimeAffectsTreeAndTokenPaths() {
        val source = "d = 1979-05-27T07:32:00\n"

        assertEquals(
            "1979-05-27T07:32:00",
            TomlMapper().parse(source, Node::class.java).asObject()["d"]?.asString(),
        )

        val mapper = TomlMapper.builder().enable(TomlReadFeature.PARSE_JAVA_TIME).build()
        assertTrue(mapper.isEnabled(TomlReadFeature.PARSE_JAVA_TIME))
        assertEquals("1979-05-27T07:32", mapper.parse(source, Node::class.java).asObject()["d"]?.asString())
        // readTree 走 Token 路径，特性同样生效。
        assertEquals("1979-05-27T07:32", mapper.readTree(source).asObject()["d"]?.asString())
    }

    @Test
    fun validateDateTimeFeatureValidatesTemporalText() {
        val features = setOf(TomlReadFeature.VALIDATE_DATE_TIME)
        val reader = TomlReader("d = 1979-05-27T07:32:00\n", readFeatures = features)

        assertEquals("1979-05-27T07:32:00", reader.read().asObject()["d"]?.asString())

        // 非法日期时间在 tomlj 解析阶段即被拒绝（默认与开启校验行为一致）。
        assertFailsWith<TomlParseException> {
            TomlReader("d = 1979-02-30T07:32:00\n", readFeatures = features).read()
        }
        assertFailsWith<TomlParseException> {
            TomlReader("d = 1979-02-30T07:32:00\n").read()
        }
    }

    @Test
    fun readerCompanionAndExtensionAcceptReadFeatures() {
        val features = setOf(TomlReadFeature.PARSE_JAVA_TIME)
        assertEquals(
            "1979-05-27T07:32",
            TomlReader.parse("d = 1979-05-27T07:32:00", readFeatures = features).asObject()["d"]?.asString(),
        )
        assertEquals(
            "1979-05-27T07:32",
            "d = 1979-05-27T07:32:00".toTomlNode(readFeatures = features).asObject()["d"]?.asString(),
        )
    }

    @Test
    fun mapperFormatCommentedUsesMergedCommentConfig() {
        val source = "# header\ntitle = \"demo\"\n\n# trailer\n"
        val mapper = TomlMapper.builder()
            .trailingNewline(false)
            .preserveBlankLines(false)
            .build()
        val document = mapper.parseCommented(source)
        val written = mapper.formatCommented(document)

        assertTrue(written.contains("title = \"demo\""))
        assertFalse(written.endsWith("\n"))
        // 显式配置优先于 Mapper 配置。
        assertTrue(mapper.formatCommented(document, TomlCommentConfig(trailingNewline = true)).endsWith("\n"))
    }

    @Test
    fun commentedDocumentNullUsesWriteFeatures() {
        val document = TomlCommentedDocument.of(ObjectNode.of("a" to NullNode.INSTANCE))

        assertEquals("a = \"\"\n", document.toTomlString())
        assertEquals("a = \"\"", document.toTomlString(TomlCommentConfig(trailingNewline = false)))
        assertFailsWith<TomlException> {
            document.toTomlString(TomlCommentConfig(), setOf(TomlWriteFeature.FAIL_ON_NULL_WRITE))
        }
    }
}
