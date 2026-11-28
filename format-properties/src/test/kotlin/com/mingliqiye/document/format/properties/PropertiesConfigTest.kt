package com.mingliqiye.document.format.properties

import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [PropertiesConfig] / [PropertiesConfigBuilder] 测试：默认值对齐 上游实现
 * `JavaPropsSchema`、特性开关、通用配置转发与校验。
 */
class PropertiesConfigTest {

    @Test
    fun defaultsAlignJacksonJavaPropsSchema() {
        val config = PropertiesConfig.empty()

        assertEquals(".", config.pathSeparator)
        assertEquals('\u0000', config.pathSeparatorEscapeChar)
        assertEquals(PropertiesIndexMarker.DEFAULT, config.indexMarker)
        assertEquals(1, config.firstArrayOffset)
        assertEquals("=", config.keyValueSeparator)
        assertEquals("\n", config.lineEnding)
        assertEquals("", config.lineIndentation)
        assertEquals("", config.header)
        assertNull(config.prefix)
        assertEquals(512, config.maxDepth)
        assertEquals(PropertiesConfig.DEFAULT_READ_FEATURES, config.readFeatures)
        assertEquals(PropertiesConfig.DEFAULT_WRITE_FEATURES, config.writeFeatures)

        // 派生开关：上游实现 默认读取开启简单下标与下标标记；写出默认“简单下标”写法。
        assertTrue(config.parseSimpleIndexes)
        assertTrue(config.allowIndexMarkers)
        assertTrue(config.decodePercentEscapedSegments)
        assertFalse(config.writeIndexUsingMarkers)
        assertTrue(config.escapePercentEncodedSegments)
        assertFalse(config.escapeNonAscii)

        assertEquals(".", PropertiesConfig.DEFAULT_PATH_SEPARATOR)
        assertEquals('\u0000', PropertiesConfig.DEFAULT_PATH_SEPARATOR_ESCAPE_CHAR)
        assertEquals(1, PropertiesConfig.DEFAULT_FIRST_ARRAY_OFFSET)
        assertEquals("=", PropertiesConfig.DEFAULT_KEY_VALUE_SEPARATOR)
        assertEquals("\n", PropertiesConfig.DEFAULT_LINE_ENDING)
        assertEquals(512, PropertiesConfig.DEFAULT_MAX_DEPTH)
        assertEquals(512, PropertiesMapper.DEFAULT_MAX_DEPTH)
        assertEquals("[", PropertiesIndexMarker.DEFAULT.start)
        assertEquals("]", PropertiesIndexMarker.DEFAULT.end)
    }

    @Test
    fun featuresCanBeToggled() {
        val config = PropertiesConfig.builder()
            .disable(PropertiesReadFeature.PARSE_SIMPLE_INDEXES)
            .disable(PropertiesReadFeature.ALLOW_INDEX_MARKERS)
            .disable(PropertiesReadFeature.DECODE_PERCENT_ESCAPED_SEGMENTS)
            .enable(PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS)
            .enable(PropertiesWriteFeature.ESCAPE_NON_ASCII)
            .disable(PropertiesWriteFeature.ESCAPE_PERCENT_ENCODED_SEGMENTS)
            .build()

        assertFalse(config.parseSimpleIndexes)
        assertFalse(config.allowIndexMarkers)
        assertFalse(config.decodePercentEscapedSegments)
        assertTrue(config.writeIndexUsingMarkers)
        assertTrue(config.escapeNonAscii)
        assertFalse(config.escapePercentEncodedSegments)

        val reEnabled = PropertiesConfig.builder()
            .configure(PropertiesReadFeature.PARSE_SIMPLE_INDEXES, true)
            .configure(PropertiesReadFeature.ALLOW_INDEX_MARKERS, true)
            .configure(PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS, false)
            .build()
        assertTrue(reEnabled.parseSimpleIndexes)
        assertTrue(reEnabled.allowIndexMarkers)
        assertFalse(reEnabled.writeIndexUsingMarkers)
    }

    @Test
    fun jacksonNamedConvenienceMethods() {
        val config = PropertiesConfig.builder()
            .parseSimpleIndexes(false)
            .writeIndexUsingMarkers(true)
            .withoutIndexMarker()
            .build()

        assertFalse(config.parseSimpleIndexes)
        assertNull(config.indexMarker)
        // 未配置下标标记时，即使开启 WRITE_INDEX_USING_MARKERS 也回退简单写法。
        assertFalse(config.writeIndexUsingMarkers)
    }

    @Test
    fun builderConfiguresFormatOptions() {
        val marker = PropertiesIndexMarker.create("<<", ">>")
        val config = PropertiesConfig.builder()
            .pathSeparator("->")
            .pathSeparatorEscapeChar('#')
            .indexMarker(marker)
            .firstArrayOffset(3)
            .keyValueSeparator(": ")
            .lineEnding("\r\n")
            .lineIndentation("  ")
            .header("# head\n")
            .prefix("app")
            .maxDepth(64)
            .build()

        assertEquals("->", config.pathSeparator)
        assertEquals('#', config.pathSeparatorEscapeChar)
        assertSame(marker, config.indexMarker)
        assertEquals(3, config.firstArrayOffset)
        assertEquals(": ", config.keyValueSeparator)
        assertEquals("\r\n", config.lineEnding)
        assertEquals("  ", config.lineIndentation)
        assertEquals("# head\n", config.header)
        assertEquals("app", config.prefix)
        assertEquals(64, config.maxDepth)

        val reset = PropertiesConfig.builder()
            .pathSeparator(".")
            .withoutPathSeparator()
            .lineIndentation(" ")
            .withoutLineIndentation()
            .header("# x")
            .withoutHeader()
            .prefix("p")
            .withoutPrefix()
            .indexMarker("[", "]")
            .withoutIndexMarker()
            .build()
        assertEquals("", reset.pathSeparator)
        assertEquals("", reset.lineIndentation)
        assertEquals("", reset.header)
        assertNull(reset.prefix)
        assertNull(reset.indexMarker)
    }

    @Test
    fun builderForwardsCommonDocumentMapperConfig() {
        val validator = DocumentBeanValidator { }
        val resolver = RemoteReferenceResolver { uri -> throw IllegalStateException(uri) }
        val config = PropertiesConfig.builder()
            .missingFieldPolicy(MissingFieldPolicy.FAIL)
            .failOnUnknownFields(true)
            .writeNulls(false)
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .fileCharset(Charsets.ISO_8859_1)
            .atomicFileWrite(true)
            .createParentDirectories(true)
            .maxInputSize(1024L)
            .maxStringLength(16)
            .maxNodeDepth(8)
            .maxNodeCount(100L)
            .resolveReferences(true)
            .writeReferences(true)
            .remoteReferenceResolver(resolver)
            .beanValidator(validator)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .subtype(Animal::class.java, "dog", Dog::class.java)
            .build()

        assertEquals(MissingFieldPolicy.FAIL, config.missingFieldPolicy)
        assertTrue(config.failOnUnknownFields)
        assertFalse(config.writeNulls)
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
        assertSame(resolver, config.remoteReferenceResolver)
        assertSame(validator, config.beanValidator)
        assertTrue(config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))

        val disabled = PropertiesConfig.builder()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .failOnNullForPrimitives(false)
            .build()
        assertFalse(disabled.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
    }

    @Test
    fun fromWrapsAndCopiesConfig() {
        val plain = DocumentMapperConfig.builder()
            .missingFieldPolicy(MissingFieldPolicy.FAIL)
            .maxNodeDepth(8)
            .build()
        val wrapped = PropertiesConfig.from(plain)

        assertEquals(".", wrapped.pathSeparator)
        assertEquals(512, wrapped.maxDepth)
        assertEquals(MissingFieldPolicy.FAIL, wrapped.missingFieldPolicy)
        assertEquals(8, wrapped.maxNodeDepth)

        val custom = PropertiesConfig.builder()
            .pathSeparator("->")
            .indexMarker("<<", ">>")
            .header("# h\n")
            .maxDepth(9)
            .build()
        assertSame(custom, PropertiesConfig.from(custom))

        val overridden = PropertiesConfig.from(custom, 20)
        assertEquals(20, overridden.maxDepth)
        assertEquals("->", overridden.pathSeparator)
        assertEquals("<<", overridden.indexMarker?.start)
        assertEquals("# h\n", overridden.header)
    }

    @Test
    fun invalidConfigRejected() {
        assertFailsWith<IllegalArgumentException> { PropertiesConfig.builder().maxDepth(0) }
        assertFailsWith<IllegalArgumentException> { PropertiesConfig.builder().maxDepth(-1) }
        assertFailsWith<IllegalArgumentException> { PropertiesIndexMarker.create("", "]") }
        assertFailsWith<IllegalArgumentException> { PropertiesIndexMarker.create("[", "") }
        assertFailsWith<IllegalArgumentException> {
            PropertiesMapper(DocumentMapperConfig.empty(), 0)
        }
    }

    @Test
    fun beanValidatorAndNodeLimitsForwardedToMapper() {
        var validated = false
        val validator = DocumentBeanValidator { validated = true }
        val mapper = PropertiesMapper(
            PropertiesConfig.builder()
                .beanValidator(validator)
                .build(),
        )

        mapper.parse("name=小于", NodeMapHolder::class.java)
        assertTrue(validated)

        val limited = PropertiesMapper(PropertiesConfig.builder().maxNodeDepth(1).build())
        assertFailsWith<com.mingliqiye.document.serialization.exception.DocumentSerializationException> {
            limited.parse("a=1", Map::class.java)
        }
    }

    @Test
    fun forwardedNamingStrategyAndCharsetTakeEffect() {
        val namingMapper = PropertiesMapper(
            PropertiesConfig.builder().namingStrategy(PropertyNamingStrategy.SNAKE_CASE).build(),
        )
        val user = NamedUser().apply { userName = "小于" }

        val text = namingMapper.format(user)

        assertTrue(text.contains("user_name=小于"), text)
        assertEquals("小于", namingMapper.parse("user_name=小于", NamedUser::class.java).userName)

        val charsets = PropertiesMapper(
            PropertiesConfig.builder().fileCharset(Charsets.ISO_8859_1).build(),
        )
        val bytes = charsets.formatBytes(mapOf("name" to "abc"))
        assertEquals("abc", (charsets.parse(bytes, Map::class.java)["name"] as StringNode).value)
    }

    private interface Animal

    private class Dog : Animal

    private class NodeMapHolder {
        var name: String? = null
    }

    private class NamedUser {
        var userName: String? = null
    }
}
