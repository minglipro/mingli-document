package com.mingliqiye.document.format.smile

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SmileConfig] / [SmileConfigBuilder] 配置项测试。
 */
class SmileConfigTest {

    @Test
    fun defaultFeaturesMatchJackson() {
        val config = SmileConfig.builder().build()

        assertEquals(setOf(SmileReadFeature.REQUIRE_HEADER), config.readFeatures)
        assertEquals(
            setOf(
                SmileWriteFeature.WRITE_HEADER,
                SmileWriteFeature.ENCODE_BINARY_AS_7BIT,
                SmileWriteFeature.CHECK_SHARED_NAMES,
            ),
            config.writeFeatures,
        )

        assertTrue(config.isEnabled(SmileReadFeature.REQUIRE_HEADER))
        assertFalse(config.isEnabled(SmileReadFeature.STRICT_DUPLICATE_DETECTION))
        assertTrue(config.isEnabled(SmileWriteFeature.WRITE_HEADER))
        assertFalse(config.isEnabled(SmileWriteFeature.WRITE_END_MARKER))
        assertTrue(config.isEnabled(SmileWriteFeature.ENCODE_BINARY_AS_7BIT))
        assertTrue(config.isEnabled(SmileWriteFeature.CHECK_SHARED_NAMES))
        assertFalse(config.isEnabled(SmileWriteFeature.CHECK_SHARED_STRING_VALUES))
        assertFalse(config.isEnabled(SmileWriteFeature.LENIENT_UTF_ENCODING))
        assertFalse(config.isEnabled(SmileWriteFeature.STRICT_DUPLICATE_DETECTION))
    }

    @Test
    fun enableDisableConfigureRoundTrip() {
        val config = SmileConfig.builder()
            .configure(SmileReadFeature.REQUIRE_HEADER, false)
            .enable(SmileReadFeature.STRICT_DUPLICATE_DETECTION)
            .configure(SmileWriteFeature.WRITE_HEADER, false)
            .enable(SmileWriteFeature.WRITE_END_MARKER)
            .disable(SmileWriteFeature.CHECK_SHARED_NAMES)
            .build()

        assertFalse(config.isEnabled(SmileReadFeature.REQUIRE_HEADER))
        assertTrue(config.isEnabled(SmileReadFeature.STRICT_DUPLICATE_DETECTION))
        assertFalse(config.isEnabled(SmileWriteFeature.WRITE_HEADER))
        assertTrue(config.isEnabled(SmileWriteFeature.WRITE_END_MARKER))
        assertFalse(config.isEnabled(SmileWriteFeature.CHECK_SHARED_NAMES))
        assertTrue(config.isEnabled(SmileWriteFeature.ENCODE_BINARY_AS_7BIT))
    }

    @Test
    fun commonConfigConvenienceMethodsAreSupported() {
        val config = SmileConfig.builder()
            .missingFieldPolicy(MissingFieldPolicy.SET_EMPTY)
            .failOnUnknownFields(true)
            .writeNulls(false)
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .fileCharset(Charset.forName("ISO-8859-1"))
            .atomicFileWrite(true)
            .createParentDirectories(true)
            .maxInputSize(1024L)
            .maxStringLength(32)
            .maxNodeDepth(8)
            .maxNodeCount(64L)
            .resolveReferences(true)
            .writeReferences(true)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .failOnNullForPrimitives(true)
            .build()

        assertEquals(MissingFieldPolicy.SET_EMPTY, config.missingFieldPolicy)
        assertTrue(config.failOnUnknownFields)
        assertFalse(config.writeNulls)
        assertEquals(PropertyNamingStrategy.SNAKE_CASE, config.namingStrategy)
        assertEquals(Charset.forName("ISO-8859-1"), config.fileCharset)
        assertTrue(config.atomicFileWrite)
        assertTrue(config.createParentDirectories)
        assertEquals(1024L, config.maxInputSize)
        assertEquals(32, config.maxStringLength)
        assertEquals(8, config.maxNodeDepth)
        assertEquals(64L, config.maxNodeCount)
        assertTrue(config.resolveReferences)
        assertTrue(config.writeReferences)
        assertTrue(config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
    }

    @Test
    fun inconsistentHeaderSettingsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            SmileConfig.builder()
                .disable(SmileWriteFeature.WRITE_HEADER)
                .enable(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
                .build()
        }
        assertFailsWith<IllegalArgumentException> {
            SmileConfig.builder()
                .disable(SmileWriteFeature.WRITE_HEADER)
                .disable(SmileWriteFeature.ENCODE_BINARY_AS_7BIT)
                .build()
        }

        // 有头部时允许共享字符串值与原始二进制组合。
        SmileConfig.builder()
            .enable(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
            .disable(SmileWriteFeature.ENCODE_BINARY_AS_7BIT)
            .build()
    }
}
