package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.StringNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [YamlWriteFeature] 测试：对齐 上游实现 YAML 2.15 `YAMLGenerator.Feature` 的行为。
 */
class YamlWriteFeatureTest {

    private fun prettyMapper(vararg features: YamlWriteFeature): YamlMapper = YamlMapper(
        YamlConfig.builder().pretty(true).apply {
            features.forEach { enable(it) }
        }.build(),
    )

    @Test
    fun writeDocStartMarkerEnabledByDefault() {
        val text = YamlMapper().format(linkedMapOf("a" to 1))

        assertTrue(text.startsWith("---"))
    }

    @Test
    fun writeDocStartMarkerCanBeDisabled() {
        val mapper = YamlMapper(
            YamlConfig.builder().disable(YamlWriteFeature.WRITE_DOC_START_MARKER).build(),
        )

        assertFalse(mapper.format(linkedMapOf("a" to 1)).startsWith("---"))
        assertTrue(YamlMapper(YamlConfig.builder().pretty(true).build()).format(linkedMapOf("a" to 1)).startsWith("---"))
    }

    @Test
    fun stringsAreQuotedByDefault() {
        val text = prettyMapper().format(linkedMapOf("s" to "abc", "empty" to ""))

        assertTrue(text.contains("s: \"abc\""))
        assertTrue(text.contains("empty: \"\""))
    }

    @Test
    fun minimizeQuotesKeepsSafeStringsPlain() {
        val text = prettyMapper(YamlWriteFeature.MINIMIZE_QUOTES).format(linkedMapOf("s" to "abc"))

        assertTrue(text.contains("s: abc"))
        assertFalse(text.contains("\"abc\""))
    }

    @Test
    fun minimizeQuotesStillQuotesReservedAndQuotableValues() {
        val text = prettyMapper(YamlWriteFeature.MINIMIZE_QUOTES).format(
            linkedMapOf(
                "bool" to "true",
                "null" to "null",
                "comma" to "a,b",
                "hash" to "a #b",
                "colon" to "a: b",
            ),
        )

        assertTrue(text.contains("bool: \"true\""), text)
        assertTrue(text.contains("'null': \"null\""), text)
        assertTrue(text.contains("comma: \"a,b\""), text)
        assertTrue(text.contains("hash: \"a #b\""), text)
        assertTrue(text.contains("colon: \"a: b\""), text)
    }

    @Test
    fun alwaysQuoteNumbersAsStrings() {
        val minimizeOnly = prettyMapper(YamlWriteFeature.MINIMIZE_QUOTES).format(linkedMapOf("n" to "123"))
        val quoted = prettyMapper(
            YamlWriteFeature.MINIMIZE_QUOTES,
            YamlWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS,
        ).format(linkedMapOf("n" to "123"))

        assertTrue(minimizeOnly.contains("n: 123"))
        assertTrue(quoted.contains("n: \"123\""))
    }

    @Test
    fun minimizeQuotesNumericStringBecomesNumberOnRead() {
        // 上游实现 行为：MINIMIZE_QUOTES 且未开启 ALWAYS_QUOTE_NUMBERS_AS_STRINGS 时，
        // 数字样字符串输出为 plain，读回变成数字。
        val text = prettyMapper(YamlWriteFeature.MINIMIZE_QUOTES).format(linkedMapOf("n" to "123"))

        assertIs<NumberNode>(YamlReader(text).read().asObject()["n"])
    }

    @Test
    fun literalBlockStyleUsesBlockForMultilineStrings() {
        val defaultText = prettyMapper().format(linkedMapOf("text" to "line1\nline2"))
        val literalText = prettyMapper(YamlWriteFeature.LITERAL_BLOCK_STYLE).format(linkedMapOf("text" to "line1\nline2"))

        assertTrue(defaultText.contains("\\n"))
        assertTrue(literalText.contains("|-"))
        assertEquals("line1\nline2", YamlReader(literalText).read().asObject()["text"]!!.asString())
    }

    @Test
    fun minimizeQuotesImpliesLiteralBlockForMultiline() {
        val text = prettyMapper(YamlWriteFeature.MINIMIZE_QUOTES).format(linkedMapOf("text" to "line1\nline2"))

        assertTrue(text.contains("|-"))
    }

    @Test
    fun indentArraysAndIndicator() {
        val defaultText = prettyMapper().format(linkedMapOf("list" to listOf(1, 2)))
        val indented = prettyMapper(YamlWriteFeature.INDENT_ARRAYS).format(linkedMapOf("list" to listOf(1, 2)))
        val withIndicator = prettyMapper(YamlWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
            .format(linkedMapOf("list" to listOf(1, 2)))

        assertTrue(defaultText.contains("list:\n- 1"))
        assertTrue(indented.contains("list:\n - 1"))
        assertTrue(withIndicator.contains("list:\n  - 1"))
    }

    @Test
    fun usePlatformLineBreaks() {
        val platform = prettyMapper(YamlWriteFeature.USE_PLATFORM_LINE_BREAKS).format(linkedMapOf("a" to 1))
        val defaultText = prettyMapper().format(linkedMapOf("a" to 1))

        assertTrue(platform.contains(System.lineSeparator()))
        assertFalse(platform.replace(System.lineSeparator(), "").contains("\n"))
        assertFalse(defaultText.contains("\r"))
    }

    @Test
    fun splitLinesCanBeDisabled() {
        val value = linkedMapOf("text" to "word ".repeat(40).trim())
        val split = prettyMapper().format(value)
        val noSplit = YamlMapper(
            YamlConfig.builder().pretty(true).disable(YamlWriteFeature.SPLIT_LINES).build(),
        ).format(value)

        assertTrue(split.lines().size > 3)
        assertEquals(2, noSplit.lines().size)
    }

    @Test
    fun allowLongKeys() {
        val longKey = "k".repeat(140)
        val defaultText = prettyMapper().format(linkedMapOf(longKey to 1))
        val allowedText = prettyMapper(YamlWriteFeature.ALLOW_LONG_KEYS).format(linkedMapOf(longKey to 1))

        assertTrue(defaultText.contains("? $longKey"))
        assertFalse(allowedText.contains("? $longKey"))
        assertTrue(allowedText.contains("$longKey: 1"))
    }

    @Test
    fun canonicalOutputUsesExplicitTags() {
        val text = prettyMapper(YamlWriteFeature.CANONICAL_OUTPUT).format(
            linkedMapOf("name" to "abc", "age" to 18, "list" to listOf(1)),
        )

        assertTrue(text.contains("!!map"))
        assertTrue(text.contains("!!str"))
        assertTrue(text.contains("!!int"))
        assertTrue(text.contains("!!seq"))
        assertEquals(
            YamlReader(text).read(),
            YamlMapper().toNode(linkedMapOf("name" to "abc", "age" to 18, "list" to listOf(1))),
        )
    }

    @Test
    fun explicitEndWritesDocumentEndMarker() {
        val mapper = YamlMapper(YamlConfig.builder().explicitEnd(true).build())

        assertTrue(mapper.format(linkedMapOf("a" to 1)).trimEnd().endsWith("..."))
        assertFalse(YamlMapper().format(linkedMapOf("a" to 1)).trimEnd().endsWith("..."))
    }

    @Test
    fun indentConfig() {
        val mapper = YamlMapper(YamlConfig.builder().pretty(true).indent(4).build())
        val text = mapper.format(linkedMapOf("a" to linkedMapOf("b" to 1)))

        assertTrue(text.contains("\n    b: 1"))
    }

    @Test
    fun lineWidthConfig() {
        val value = linkedMapOf("text" to "line line line line line line line line line line line line line line")
        val narrow = YamlMapper(YamlConfig.builder().pretty(true).lineWidth(20).build()).format(value)
        val wide = YamlMapper(YamlConfig.builder().pretty(true).lineWidth(120).build()).format(value)

        assertTrue(narrow.lines().size > wide.lines().size)
    }

    @Test
    fun allowUnicodeFalseEscapesUnicode() {
        val mapper = YamlMapper(YamlConfig.builder().pretty(true).allowUnicode(false).build())
        val text = mapper.format(linkedMapOf("name" to "小于"))

        assertTrue(text.contains("\\u5c0f"))
        assertEquals("小于", YamlReader(text).read().asObject()["name"]!!.asString())
    }

    @Test
    fun formatAllHonorsDocStartMarkerFeature() {
        val values = listOf(linkedMapOf("a" to 1), linkedMapOf("b" to 2))
        val withMarker = YamlMapper(YamlConfig.builder().pretty(true).build()).formatAll(values)
        val noMarker = YamlMapper(
            YamlConfig.builder().pretty(true).disable(YamlWriteFeature.WRITE_DOC_START_MARKER).build(),
        ).formatAll(values)

        assertEquals(2, Regex("(?m)^---").findAll(withMarker).count())
        assertEquals(1, Regex("(?m)^---").findAll(noMarker).count())
        assertTrue(YamlMapper().parseAllToNodes(withMarker).size == 2)
    }

    @Test
    fun defaultWriteFeaturesMatchJackson() {
        assertEquals(YamlConfig.DEFAULT_WRITE_FEATURES, YamlConfig.builder().build().writeFeatures)
        assertTrue(YamlConfig.builder().build().isWriteEnabled(YamlWriteFeature.WRITE_DOC_START_MARKER))
        assertTrue(YamlConfig.builder().build().isWriteEnabled(YamlWriteFeature.SPLIT_LINES))
        assertTrue(!YamlConfig.builder().build().isWriteEnabled(YamlWriteFeature.MINIMIZE_QUOTES))
        assertTrue(!YamlConfig.builder().build().isWriteEnabled(YamlWriteFeature.CANONICAL_OUTPUT))
    }

    @Test
    fun nativeTypeAndObjectIdFeaturesAreAcceptedWithoutOutputChange() {
        // 这两个 上游实现 dataformat 级特性依赖 type id/object id；本库 Node 模型不携带，
        // 保留常量与默认值以便 上游实现 配置迁移，写出结果不变（见 README 未支持项）。
        val defaultText = YamlMapper(YamlConfig.builder().pretty(true).build()).format(linkedMapOf("a" to 1))
        val disabledText = YamlMapper(
            YamlConfig.builder()
                .pretty(true)
                .disable(YamlWriteFeature.USE_NATIVE_OBJECT_ID, YamlWriteFeature.USE_NATIVE_TYPE_ID)
                .build(),
        ).format(linkedMapOf("a" to 1))

        assertEquals(defaultText, disabledText)
    }

    @Test
    fun mapperWriterOutputMatchesCodecDefaults() {
        val node = YamlMapper().toNode(linkedMapOf("name" to "abc"))

        assertEquals(YamlCodec.write(node, pretty = true), YamlMapper(YamlConfig.builder().pretty(true).build()).format(node))
    }

    @Test
    fun invalidWriterConfigurationIsRejected() {
        assertFailsWith<IllegalArgumentException> { YamlConfig.builder().indent(1) }
        assertFailsWith<IllegalArgumentException> { YamlConfig.builder().indent(10) }
        assertFailsWith<IllegalArgumentException> { YamlConfig.builder().lineWidth(-1) }
    }

    @Test
    fun stringValuesRoundTripWithQuotingFeatures() {
        val node = YamlMapper().toNode(
            linkedMapOf(
                "plain" to "abc",
                "empty" to "",
                "reserved" to "true",
                "multiline" to "a\nb",
            ),
        )
        val minimize = YamlMapper(
            YamlConfig.builder().pretty(true).enable(YamlWriteFeature.MINIMIZE_QUOTES).build(),
        )

        assertEquals(node, YamlReader(YamlMapper().format(node)).read())
        assertEquals(
            linkedMapOf(
                "plain" to "abc",
                "empty" to "",
                "reserved" to "true",
                "multiline" to "a\nb",
            ),
            simplify(YamlReader(minimize.format(node)).read()),
        )
        // 未开启 ALWAYS_QUOTE_NUMBERS_AS_STRINGS 时数字字符串读回为数字。
        val numeric = minimize.format(linkedMapOf("numeric" to "123"))
        assertIs<NumberNode>(YamlReader(numeric).read().asObject()["numeric"])
    }

    private fun simplify(node: com.mingliqiye.document.core.Node): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        for ((key, value) in node.asObject().asMap()) {
            result[key] = when {
                value.isString -> value.asString()
                value.isNull -> null
                else -> value
            }
        }
        return result
    }

    @Test
    fun emptyStringValueIsStringNode() {
        assertIs<StringNode>(YamlReader("a: \"\"\n").read().asObject()["a"])
    }
}
