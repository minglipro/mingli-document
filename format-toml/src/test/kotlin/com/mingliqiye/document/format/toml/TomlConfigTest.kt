package com.mingliqiye.document.format.toml

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [TomlConfig] / [TomlConfigBuilder] / [TomlMapperBuilder] 配置测试。
 */
class TomlConfigTest {

    @Test
    fun defaultsAlignJackson() {
        val config = TomlConfig.builder().build()

        assertEquals(512, TomlConfig.DEFAULT_MAX_DEPTH)
        assertEquals(512, config.maxDepth)
        assertTrue(config.readFeatures.isEmpty())
        assertTrue(config.writeFeatures.isEmpty())
        assertFalse(config.parseJavaTime)
        assertFalse(config.validateDateTime)
        assertFalse(config.failOnNullWrite)
        assertFalse(config.isEnabled(TomlReadFeature.PARSE_JAVA_TIME))
        assertFalse(config.isEnabled(TomlReadFeature.VALIDATE_DATE_TIME))
        assertFalse(config.isEnabled(TomlWriteFeature.FAIL_ON_NULL_WRITE))

        assertEquals(TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_TABLE, config.commentConfig.orphanCommentPolicy)
        assertTrue(config.commentConfig.preserveBlankLines)
        assertTrue(config.commentConfig.trailingNewline)

        val plain = TomlConfig.from(DocumentMapperConfig.empty())
        assertTrue(plain.readFeatures.isEmpty())
        assertTrue(plain.writeFeatures.isEmpty())
        assertEquals(512, plain.maxDepth)
    }

    @Test
    fun readFeatureEnableDisableConfigure() {
        val enabled = TomlConfig.builder()
            .enable(TomlReadFeature.PARSE_JAVA_TIME)
            .enable(TomlReadFeature.VALIDATE_DATE_TIME)
            .build()
        assertTrue(enabled.parseJavaTime)
        assertTrue(enabled.validateDateTime)
        assertTrue(enabled.isEnabled(TomlReadFeature.PARSE_JAVA_TIME))

        val disabled = TomlConfig.builder()
            .enable(TomlReadFeature.PARSE_JAVA_TIME)
            .disable(TomlReadFeature.PARSE_JAVA_TIME)
            .configure(TomlReadFeature.VALIDATE_DATE_TIME, false)
            .build()
        assertFalse(disabled.parseJavaTime)
        assertFalse(disabled.validateDateTime)
    }

    @Test
    fun writeFeatureEnableDisableConfigure() {
        val enabled = TomlConfig.builder().enable(TomlWriteFeature.FAIL_ON_NULL_WRITE).build()
        assertTrue(enabled.failOnNullWrite)
        assertTrue(enabled.isEnabled(TomlWriteFeature.FAIL_ON_NULL_WRITE))

        val disabled = TomlConfig.builder()
            .enable(TomlWriteFeature.FAIL_ON_NULL_WRITE)
            .disable(TomlWriteFeature.FAIL_ON_NULL_WRITE)
            .build()
        assertFalse(disabled.failOnNullWrite)

        val configured = TomlConfig.builder().configure(TomlWriteFeature.FAIL_ON_NULL_WRITE, true).build()
        assertTrue(configured.failOnNullWrite)
    }

    @Test
    fun maxDepthValidation() {
        assertFailsWith<IllegalArgumentException> { TomlConfig.builder().maxDepth(0) }
        assertFailsWith<IllegalArgumentException> { TomlConfig.builder().maxDepth(-1) }
        assertEquals(64, TomlConfig.builder().maxDepth(64).build().maxDepth)
    }

    @Test
    fun commentConfigMergesIntoMainConfig() {
        val built = TomlConfig.builder()
            .orphanCommentPolicy(TomlOrphanCommentPolicy.DROP)
            .preserveBlankLines(false)
            .trailingNewline(false)
            .build()
        assertEquals(TomlOrphanCommentPolicy.DROP, built.commentConfig.orphanCommentPolicy)
        assertFalse(built.commentConfig.preserveBlankLines)
        assertFalse(built.commentConfig.trailingNewline)

        val merged = TomlConfig.builder()
            .commentConfig(TomlCommentConfig(TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_FILE, true, false))
            .build()
        assertEquals(TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_FILE, merged.commentConfig.orphanCommentPolicy)
        assertTrue(merged.commentConfig.preserveBlankLines)
        assertFalse(merged.commentConfig.trailingNewline)

        // 现有 TomlCommentConfigBuilder 保留，并可基于主配置合并后继续覆盖。
        val viaCommentBuilder = TomlCommentConfig.builder()
            .base(merged.commentConfig)
            .trailingNewline(true)
            .build()
        assertTrue(viaCommentBuilder.preserveBlankLines)
        assertTrue(viaCommentBuilder.trailingNewline)
        assertEquals(merged.commentConfig.orphanCommentPolicy, TomlCommentConfig.from(merged).orphanCommentPolicy)
    }

    @Test
    fun commonDocumentMapperConfigForwarded() {
        val validator = DocumentBeanValidator { _ -> }
        val resolver = RemoteReferenceResolver { uri -> StringNode.of(uri) }
        val config = TomlConfig.builder()
            .missingFieldPolicy(MissingFieldPolicy.FAIL)
            .failOnUnknownFields(true)
            .writeNulls(false)
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .fileCharset(Charsets.UTF_16LE)
            .atomicFileWrite(true)
            .createParentDirectories(true)
            .maxInputSize(1024L)
            .maxStringLength(32)
            .maxNodeDepth(8)
            .maxNodeCount(64L)
            .resolveReferences(true)
            .writeReferences(true)
            .remoteReferenceResolver(resolver)
            .beanValidator(validator)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build()

        assertEquals(MissingFieldPolicy.FAIL, config.missingFieldPolicy)
        assertTrue(config.failOnUnknownFields)
        assertFalse(config.writeNulls)
        assertEquals(PropertyNamingStrategy.SNAKE_CASE, config.namingStrategy)
        assertEquals(Charsets.UTF_16LE, config.fileCharset)
        assertTrue(config.atomicFileWrite)
        assertTrue(config.createParentDirectories)
        assertEquals(1024L, config.maxInputSize)
        assertEquals(32, config.maxStringLength)
        assertEquals(8, config.maxNodeDepth)
        assertEquals(64L, config.maxNodeCount)
        assertTrue(config.resolveReferences)
        assertTrue(config.writeReferences)
        assertNotNull(config.remoteReferenceResolver)
        assertNotNull(config.beanValidator)
        assertTrue(config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
    }

    @Test
    fun mapperBuilderMergesTomlOptions() {
        val mapper = TomlMapper.builder()
            .maxDepth(64)
            .enable(TomlWriteFeature.FAIL_ON_NULL_WRITE)
            .enable(TomlReadFeature.PARSE_JAVA_TIME)
            .trailingNewline(false)
            .build()
        assertEquals(64, mapper.tomlConfig.maxDepth)
        assertTrue(mapper.isEnabled(TomlWriteFeature.FAIL_ON_NULL_WRITE))
        assertTrue(mapper.isEnabled(TomlReadFeature.PARSE_JAVA_TIME))
        assertFalse(mapper.tomlConfig.commentConfig.trailingNewline)

        // 通过通用 config() 传入 TomlConfig：以其为基线，构建器覆盖项优先。
        val base = TomlConfig.builder()
            .maxDepth(32)
            .enable(TomlReadFeature.VALIDATE_DATE_TIME)
            .orphanCommentPolicy(TomlOrphanCommentPolicy.DROP)
            .build()
        val merged = TomlMapper.builder()
            .configure(TomlReadFeature.VALIDATE_DATE_TIME, false)
            .preserveBlankLines(false)
            .config(base)
            .build()
        assertEquals(32, merged.tomlConfig.maxDepth)
        assertFalse(merged.isEnabled(TomlReadFeature.VALIDATE_DATE_TIME))
        assertEquals(TomlOrphanCommentPolicy.DROP, merged.tomlConfig.commentConfig.orphanCommentPolicy)
        assertFalse(merged.tomlConfig.commentConfig.preserveBlankLines)

        // 构造器显式 maxDepth：保留已有 TomlConfig 的特性配置。
        val withDepth = TomlMapper(
            TomlConfig.builder().enable(TomlReadFeature.PARSE_JAVA_TIME).build(),
            64,
        )
        assertEquals(64, withDepth.tomlConfig.maxDepth)
        assertTrue(withDepth.isEnabled(TomlReadFeature.PARSE_JAVA_TIME))
    }
}
