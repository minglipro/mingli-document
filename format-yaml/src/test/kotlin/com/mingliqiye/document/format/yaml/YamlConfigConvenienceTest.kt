package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.format.yaml.exception.YamlParseException
import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [YamlConfigBuilder] 公共便捷方法测试（与 JSON 任务同一组 DocumentMapperConfig 选项）。
 */
class YamlConfigConvenienceTest {

    @Test
    fun snakeyamlDefaults() {
        val config = YamlConfig.builder().build()

        assertEquals(2, config.indent)
        assertEquals(0, config.lineWidth)
        assertTrue(config.allowUnicode)
        assertTrue(!config.explicitEnd)
        assertNull(config.defaultFlowStyle)
        assertTrue(!config.allowDuplicateKeys)
        assertEquals(0, config.codePointLimit)
    }

    @Test
    fun snakeyamlOptionsAreApplied() {
        val config = YamlConfig.builder()
            .indent(4)
            .lineWidth(40)
            .allowUnicode(false)
            .explicitEnd(true)
            .allowDuplicateKeys(true)
            .codePointLimit(1024)
            .defaultFlowStyle(YamlFlowStyle.BLOCK)
            .build()

        assertEquals(4, config.indent)
        assertEquals(40, config.lineWidth)
        assertTrue(!config.allowUnicode)
        assertTrue(config.explicitEnd)
        assertTrue(config.allowDuplicateKeys)
        assertEquals(1024, config.codePointLimit)
        assertEquals(YamlFlowStyle.BLOCK, config.defaultFlowStyle)

        val mapper = YamlMapper(config)
        val text = mapper.format(linkedMapOf("a" to 1))
        assertTrue(text.contains("\n"))
        assertTrue(!text.contains("{"))
    }

    @Test
    fun defaultFlowStyleBlockOverridesLegacyFlowLayout() {
        val mapper = YamlMapper(YamlConfig.builder().defaultFlowStyle(YamlFlowStyle.BLOCK).build())
        val text = mapper.format(linkedMapOf("a" to linkedMapOf("b" to 1)))

        assertTrue(text.contains("\n"))
        assertTrue(text.contains("a:\n"))
    }

    @Test
    fun namingStrategyIsForwarded() {
        val mapper = YamlMapper(YamlConfig.builder().namingStrategy(PropertyNamingStrategy.SNAKE_CASE).build())
        val bean = UserBean()
        bean.userName = "abc"

        val text = mapper.format(bean)

        assertTrue(text.contains("user_name"))
        assertEquals("abc", mapper.parse(text, UserBean::class.java).userName)
    }

    @Test
    fun fileCharsetIsForwarded() {
        val mapper = YamlMapper(YamlConfig.builder().fileCharset(StandardCharsets.UTF_16).build())
        val bean = UserBean()
        bean.userName = "小于"

        val bytes = mapper.formatBytes(bean)
        val restored = mapper.parse(bytes, UserBean::class.java)

        assertEquals("小于", restored.userName)
        assertTrue(bytes.size > mapper.format(bean).length)
    }

    @Test
    fun maxInputSizeIsForwarded() {
        val mapper = YamlMapper(YamlConfig.builder().maxInputSize(10).build())

        val error = assertFailsWith<DocumentSerializationException> {
            mapper.parse("userName: 1234567890\n", UserBean::class.java)
        }

        assertTrue(error.message.orEmpty().contains("maxInputSize"))
    }

    @Test
    fun maxStringLengthIsForwarded() {
        val mapper = YamlMapper(YamlConfig.builder().maxStringLength(10).build())

        val error = assertFailsWith<DocumentSerializationException> {
            mapper.parse("userName: abcdefghijk\n", UserBean::class.java)
        }

        assertTrue(error.message.orEmpty().contains("maxStringLength"))
        assertEquals("abcdefghi", mapper.parse("userName: abcdefghi\n", UserBean::class.java).userName)
    }

    @Test
    fun maxNodeDepthIsForwarded() {
        val mapper = YamlMapper(YamlConfig.builder().maxNodeDepth(2).build())

        assertFailsWith<YamlParseException> {
            mapper.parse("a:\n  b:\n    c: 1\n", UserBean::class.java)
        }
    }

    @Test
    fun maxNodeCountIsForwarded() {
        val mapper = YamlMapper(YamlConfig.builder().maxNodeCount(2).build())

        assertFailsWith<DocumentSerializationException> {
            mapper.parse("a: 1\nb: 2\n", UserBean::class.java)
        }
    }

    @Test
    fun failOnNullForPrimitivesIsForwarded() {
        val mapper = YamlMapper(
            YamlConfig.builder().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build(),
        )

        assertFailsWith<DocumentSerializationException> {
            mapper.parse("age:\n", CounterBean::class.java)
        }
    }

    @Test
    fun deserializationFeatureConfigureIsForwarded() {
        val enabled = YamlConfig.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
            .build()
        val disabled = YamlConfig.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build()

        assertTrue(enabled.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
        assertTrue(!disabled.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))

        val mapper = YamlMapper(YamlConfig.builder().failOnNullForPrimitives(true).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("age:\n", CounterBean::class.java)
        }
    }

    @Test
    fun beanValidatorIsForwarded() {
        val validated = ArrayList<Any>()
        val validator = DocumentBeanValidator { value ->
            validated.add(value)
            if (value is CounterBean && value.age < 0) {
                throw DocumentSerializationException("age must not be negative")
            }
        }
        val mapper = YamlMapper(YamlConfig.builder().beanValidator(validator).build())
        val bean = CounterBean()
        bean.age = 1

        mapper.format(bean)
        assertTrue(validated.isNotEmpty())

        assertFailsWith<DocumentSerializationException> {
            mapper.parse("age: -1\n", CounterBean::class.java)
        }
    }

    @Test
    fun atomicFileWriteAndCreateParentDirectoriesAreForwarded() {
        val root = Files.createTempDirectory("yaml-config")
        try {
            val file = root.resolve("nested/dir/user.yaml")
            val mapper = YamlMapper(
                YamlConfig.builder()
                    .atomicFileWrite(true)
                    .createParentDirectories(true)
                    .build(),
            )
            val bean = UserBean()
            bean.userName = "abc"

            mapper.format(bean, file)

            assertTrue(Files.exists(file))
            assertEquals("abc", mapper.parse(file, UserBean::class.java).userName)
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun writeAndResolveReferencesAreForwarded() {
        val shared = linkedMapOf("x" to 1)
        val source = linkedMapOf("a" to shared, "b" to shared)
        val writer = YamlMapper(YamlConfig.builder().writeReferences(true).build())

        val node = writer.toNode(source)

        assertTrue(writer.format(node).contains("\$ref"))

        val resolver = YamlMapper(
            YamlConfig.builder()
                .writeReferences(true)
                .resolveReferences(true)
                .build(),
        )
        val resolvedHolder = resolver.fromNode(node, RefHolder::class.java)

        assertEquals(resolvedHolder.a, resolvedHolder.b)
        assertTrue((resolvedHolder.b as com.mingliqiye.document.core.ObjectNode).containsKey("x"))
    }

    private class RefHolder {
        var a: Any? = null
        var b: Any? = null
    }

    @Test
    fun remoteReferenceResolverAcceptsNull() {
        val config = YamlConfig.builder().remoteReferenceResolver(null).build()

        assertNull(config.remoteReferenceResolver)
    }

    @Test
    fun readWriteFeatureEnableDisableConfigure() {
        val config = YamlConfig.builder()
            .disable(YamlReadFeature.EMPTY_STRING_AS_NULL)
            .enable(YamlReadFeature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS)
            .configure(YamlWriteFeature.MINIMIZE_QUOTES, true)
            .configure(YamlWriteFeature.SPLIT_LINES, false)
            .build()

        assertTrue(!config.emptyStringAsNull)
        assertTrue(config.parseBooleanLikeWordsAsStrings)
        assertTrue(config.isWriteEnabled(YamlWriteFeature.MINIMIZE_QUOTES))
        assertTrue(!config.isWriteEnabled(YamlWriteFeature.SPLIT_LINES))
    }

    @Test
    fun multiFeatureEnableAndDisable() {
        val config = YamlConfig.builder()
            .disable(YamlWriteFeature.WRITE_DOC_START_MARKER, YamlWriteFeature.SPLIT_LINES)
            .enable(YamlReadFeature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS)
            .build()

        assertTrue(!config.isWriteEnabled(YamlWriteFeature.WRITE_DOC_START_MARKER))
        assertTrue(!config.isWriteEnabled(YamlWriteFeature.SPLIT_LINES))
        assertTrue(config.parseBooleanLikeWordsAsStrings)
    }

    private class UserBean {
        var userName: String = ""
    }

    private class CounterBean {
        var age: Int = 0
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) {
            return
        }
        val stream = Files.walk(path)
        try {
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        } finally {
            stream.close()
        }
    }
}
