package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.yaml.exception.YamlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [YamlReadFeature] 测试：对齐 上游实现 YAML 2.15 `YAMLParser.Feature` 的行为，
 * 覆盖 Node 路径、原生 Token 路径（`YamlMapper.parse`）与多文档路径。
 */
class YamlReadFeatureTest {

    @Test
    fun emptyPlainScalarDefaultsToNull() {
        assertTrue(YamlReader("a:\n").read().asObject()["a"]!!.isNull)
        assertTrue(YamlReader("a: ~\n").read().asObject()["a"]!!.isNull)
        assertTrue(YamlReader("a: null\n").read().asObject()["a"]!!.isNull)
    }

    @Test
    fun emptyStringAsNullDisabledKeepsEmptyString() {
        val node = YamlReader("a:\n", readFeatures = emptySet()).read().asObject()["a"]!!

        assertIs<StringNode>(node)
        assertEquals("", node.asString())
    }

    @Test
    fun quotedEmptyStringIsAlwaysEmptyString() {
        val node = YamlReader("a: ''\n").read().asObject()["a"]!!

        assertIs<StringNode>(node)
        assertEquals("", node.asString())
    }

    @Test
    fun emptyStringAsNullAffectsMapperTokenPath() {
        val tokenMapper = YamlMapper(YamlConfig.builder().disable(YamlReadFeature.EMPTY_STRING_AS_NULL).build())
        val viaToken = tokenMapper.parse("a:\n", Map::class.java)

        assertEquals("", (viaToken["a"] as StringNode).value)
        assertNull(YamlMapper().parse("a:\n", Map::class.java)["a"])
    }

    @Test
    fun emptyStringAsNullAffectsMapperNodeFallbackPath() {
        val fallbackMapper = YamlMapper(
            YamlConfig.builder()
                .maxNodeDepth(10)
                .disable(YamlReadFeature.EMPTY_STRING_AS_NULL)
                .build(),
        )

        val viaNode = fallbackMapper.parse("a:\n", Map::class.java)

        assertEquals("", (viaNode["a"] as StringNode).value)
    }

    @Test
    fun booleanLikeWordsDefaultToBoolean() {
        val node = YamlReader("yes_word: yes\nno_word: no\non_word: on\noff_word: off\ntrue_word: true\n").read()

        assertEquals(true, node.asObject()["yes_word"]!!.asBoolean())
        assertEquals(false, node.asObject()["no_word"]!!.asBoolean())
        assertEquals(true, node.asObject()["on_word"]!!.asBoolean())
        assertEquals(false, node.asObject()["off_word"]!!.asBoolean())
        assertEquals(true, node.asObject()["true_word"]!!.asBoolean())
    }

    @Test
    fun parseBooleanLikeWordsAsStringsKeepsWordsAsStrings() {
        val features = setOf(YamlReadFeature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS)
        val node = YamlReader("yes_word: yes\nno_word: no\ntrue_word: true\n", readFeatures = features).read()

        assertEquals("yes", node.asObject()["yes_word"]!!.asString())
        assertEquals("no", node.asObject()["no_word"]!!.asString())
        assertIs<BooleanNode>(node.asObject()["true_word"])
    }

    @Test
    fun parseBooleanLikeWordsAsStringsAffectsMapperTokenPath() {
        val mapper = YamlMapper(
            YamlConfig.builder().enable(YamlReadFeature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS).build(),
        )

        val map = mapper.parse("a: yes\nb: true\n", Map::class.java)

        assertEquals("yes", (map["a"] as StringNode).value)
        assertIs<BooleanNode>(map["b"])
    }

    @Test
    fun explicitInvalidBoolTagReadsAsString() {
        // 上游实现 行为：!!bool 无法匹配的值退回字符串。
        assertEquals("maybe", YamlReader("a: !!bool maybe\n").read().asObject()["a"]!!.asString())

        val map = YamlMapper().parse("a: !!bool maybe\n", Map::class.java)
        assertEquals("maybe", (map["a"] as StringNode).value)
    }

    @Test
    fun readFeaturesApplyToMultiDocumentReader() {
        val features = setOf(YamlReadFeature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS)
        val documents = YamlMultiDocumentReader("a: yes\n---\nb: true\n", readFeatures = features).readAll()

        assertEquals("yes", documents[0].asObject()["a"]!!.asString())
        assertIs<BooleanNode>(documents[1].asObject()["b"])
    }

    @Test
    fun mapperReadFeaturesApplyToParseAllToNodes() {
        val mapper = YamlMapper(YamlConfig.builder().disable(YamlReadFeature.EMPTY_STRING_AS_NULL).build())

        val documents = mapper.parseAllToNodes("---\na:\n---\nb: ''\n")

        assertEquals(2, documents.size)
        assertEquals("", documents[0].asObject()["a"]!!.asString())
        assertEquals("", documents[1].asObject()["b"]!!.asString())
    }

    @Test
    fun allowDuplicateKeysRejectsByDefaultAndAllowsWhenEnabled() {
        assertFailsWith<YamlParseException> {
            YamlReader("a: 1\na: 2\n").read()
        }
        val node = YamlReader("a: 1\na: 2\n", allowDuplicateKeys = true).read()

        assertEquals(2, node.asObject()["a"]!!.asNumber().toInt())
        val mapper = YamlMapper(YamlConfig.builder().allowDuplicateKeys(true).build())
        assertEquals(2, (mapper.parse("a: 1\na: 2\n", Map::class.java)["a"] as com.mingliqiye.document.core.NumberNode).toInt())
    }

    @Test
    fun codePointLimitIsEnforced() {
        assertFailsWith<YamlParseException> {
            YamlReader("a: 1234567890\n", codePointLimit = 5).read()
        }
        YamlReader("a: 1\n", codePointLimit = 100)

        val mapper = YamlMapper(YamlConfig.builder().codePointLimit(5).build())
        assertFailsWith<YamlParseException> {
            mapper.parse("a: 1234567890\n", Map::class.java)
        }
    }

    @Test
    fun readerDefaultsMatchJackson() {
        assertEquals(YamlConfig.DEFAULT_READ_FEATURES, YamlConfig.builder().build().readFeatures)
        assertTrue(YamlConfig.builder().build().emptyStringAsNull)
        assertTrue(!YamlConfig.builder().build().parseBooleanLikeWordsAsStrings)
    }
}
