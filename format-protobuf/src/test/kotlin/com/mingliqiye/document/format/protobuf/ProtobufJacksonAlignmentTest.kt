package com.mingliqiye.document.format.protobuf

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.protobuf.exception.ProtobufException
import com.mingliqiye.document.format.protobuf.exception.ProtobufParseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 上游实现 `Protobuf 底层库` 行为对齐测试。
 *
 * 上游实现 protobuf 没有格式专属 Feature 枚举，解析/写出行为由核心特性控制；本测试逐项验证
 * [ProtobufReadFeature.IGNORE_UNDEFINED]（上游实现 `StreamReadFeature.IGNORE_UNDEFINED`）与
 * [ProtobufWriteFeature.IGNORE_UNKNOWN]（上游实现 `JsonGenerator.Feature.IGNORE_UNKNOWN`）
 * 的开关行为、默认值，以及它们与 [ProtobufUnknownFieldPolicy] 的组合语义。
 */
class ProtobufJacksonAlignmentTest {

    private val sample = ProtobufTestFixtures.proto3Sample

    private val knownOnly: ObjectNode = ObjectNode.of("age" to NumberNode.of(1))

    @Test
    fun readDefaultRejectsUndefinedFields() {
        val mapper = ProtobufMapper(sample)
        assertEquals(ProtobufConfig.DEFAULT_READ_FEATURES, mapper.protobufConfig.readFeatures)
        assertFalse(mapper.isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED))
        assertFailsWith<ProtobufParseException> {
            mapper.parse(bytesWithUndefinedField(), Node::class.java)
        }
    }

    @Test
    fun readIgnoreUndefinedFeatureSkipsUnknownFields() {
        val mapper = ProtobufMapper(
            sample,
            ProtobufConfig.builder().enable(ProtobufReadFeature.IGNORE_UNDEFINED).build(),
        )
        assertTrue(mapper.isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED))
        val decoded = mapper.parse(bytesWithUndefinedField(), Node::class.java).asObject()
        assertEquals(knownOnly, decoded)
    }

    @Test
    fun readIgnoreUndefinedFeatureCanBeDisabledAgain() {
        val mapper = ProtobufMapper(
            sample,
            ProtobufConfig.builder()
                .configure(ProtobufReadFeature.IGNORE_UNDEFINED, true)
                .configure(ProtobufReadFeature.IGNORE_UNDEFINED, false)
                .build(),
        )
        assertFalse(mapper.isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED))
        assertFailsWith<ProtobufParseException> {
            mapper.parse(bytesWithUndefinedField(), Node::class.java)
        }
    }

    @Test
    fun readPreservePolicyWinsOverIgnoreUndefined() {
        val mapper = ProtobufMapper(
            sample,
            ProtobufConfig.builder()
                .unknownFieldPolicy(ProtobufUnknownFieldPolicy.PRESERVE)
                .enable(ProtobufReadFeature.IGNORE_UNDEFINED)
                .build(),
        )
        assertEquals(ProtobufUnknownFieldPolicy.PRESERVE, mapper.protobufConfig.effectiveUnknownFieldPolicy)
        val node = mapper.parse(bytesWithUndefinedField(), Node::class.java).asObject()
        assertEquals(knownOnly, ObjectNode.of("age" to assertNotNull(node["age"])))
        assertNotNull(node[ProtobufUnknownFields.FIELD_NAME])

        val roundTripped = mapper.parse(mapper.formatBytes(node), Node::class.java).asObject()
        assertEquals(node, roundTripped)
    }

    @Test
    fun readExplicitIgnorePolicyWorksWithoutFeature() {
        val mapper = ProtobufMapper(sample, ProtobufUnknownFieldPolicy.IGNORE)
        assertEquals(knownOnly, mapper.parse(bytesWithUndefinedField(), Node::class.java).asObject())
    }

    @Test
    fun writeDefaultRejectsUnknownFieldNames() {
        val mapper = ProtobufMapper(sample)
        assertEquals(ProtobufConfig.DEFAULT_WRITE_FEATURES, mapper.protobufConfig.writeFeatures)
        assertFalse(mapper.isEnabled(ProtobufWriteFeature.IGNORE_UNKNOWN))
        assertFailsWith<ProtobufException> { mapper.formatBytes(nodeWithUnknownField()) }
    }

    @Test
    fun writeIgnoreUnknownFeatureSkipsUnknownFields() {
        val mapper = ProtobufMapper(
            sample,
            ProtobufConfig.builder().enable(ProtobufWriteFeature.IGNORE_UNKNOWN).build(),
        )
        assertTrue(mapper.isEnabled(ProtobufWriteFeature.IGNORE_UNKNOWN))
        val reference = ProtobufMapper(sample).formatBytes(knownOnly)
        assertContentEquals(reference, mapper.formatBytes(nodeWithUnknownField()))
    }

    @Test
    fun writeIgnoreUnknownFeatureSkipsNestedUnknownFields() {
        val mapper = ProtobufMapper(
            sample,
            ProtobufConfig.builder().enable(ProtobufWriteFeature.IGNORE_UNKNOWN).build(),
        )
        val expected = ObjectNode.of("child" to ObjectNode.of("label" to StringNode.of("x")))
        val decoded = mapper.parse(mapper.formatBytes(nestedNodeWithUnknownField()), Node::class.java).asObject()
        assertEquals(expected, decoded)
        assertFailsWith<ProtobufException> {
            ProtobufMapper(sample).formatBytes(nestedNodeWithUnknownField())
        }
    }

    @Test
    fun writeIgnoreUnknownFeatureCanBeDisabledAgain() {
        val mapper = ProtobufMapper(
            sample,
            ProtobufConfig.builder()
                .configure(ProtobufWriteFeature.IGNORE_UNKNOWN, true)
                .configure(ProtobufWriteFeature.IGNORE_UNKNOWN, false)
                .build(),
        )
        assertFalse(mapper.isEnabled(ProtobufWriteFeature.IGNORE_UNKNOWN))
        assertFailsWith<ProtobufException> { mapper.formatBytes(nodeWithUnknownField()) }
    }

    @Test
    fun writeIgnoreUnknownDoesNotBypassPreserveMarker() {
        val mapper = ProtobufMapper(
            sample,
            ProtobufConfig.builder().enable(ProtobufWriteFeature.IGNORE_UNKNOWN).build(),
        )
        assertFailsWith<ProtobufException> {
            mapper.formatBytes(ObjectNode.of(ProtobufUnknownFields.FIELD_NAME to ObjectNode.EMPTY))
        }
    }

    @Test
    fun explicitDescriptorApisHonorFeatures() {
        val mapper = ProtobufMapper(
            sample,
            ProtobufConfig.builder()
                .enable(ProtobufReadFeature.IGNORE_UNDEFINED)
                .enable(ProtobufWriteFeature.IGNORE_UNKNOWN)
                .build(),
        )
        assertEquals(knownOnly, mapper.parseToNode(bytesWithUndefinedField(), sample).asObject())
        assertContentEquals(
            ProtobufMapper(sample).formatBytes(knownOnly),
            mapper.formatBytes(nodeWithUnknownField(), sample),
        )
    }

    @Test
    fun factoryHonorsConfiguredFeatures() {
        val factory = ProtobufMapperFactory(
            ProtobufConfig.builder()
                .enable(ProtobufReadFeature.IGNORE_UNDEFINED)
                .enable(ProtobufWriteFeature.IGNORE_UNKNOWN)
                .build(),
        )
        val mapper = factory.mapper(sample)
        assertEquals(knownOnly, mapper.parse(bytesWithUndefinedField(), Node::class.java).asObject())
        assertContentEquals(
            ProtobufMapper(sample).formatBytes(knownOnly),
            mapper.formatBytes(nodeWithUnknownField()),
        )
    }

    @Test
    fun legacyMapperConstructorKeepsJacksonDefaults() {
        val mapper = ProtobufMapper(sample)
        assertEquals(ProtobufUnknownFieldPolicy.FAIL, mapper.protobufConfig.effectiveUnknownFieldPolicy)
        assertEquals(ProtobufConfig.DEFAULT_READ_FEATURES, mapper.protobufConfig.readFeatures)
        assertEquals(ProtobufConfig.DEFAULT_WRITE_FEATURES, mapper.protobufConfig.writeFeatures)
        assertFailsWith<ProtobufParseException> { mapper.parse(bytesWithUndefinedField(), Node::class.java) }
        assertFailsWith<ProtobufException> { mapper.formatBytes(nodeWithUnknownField()) }
    }

    private fun bytesWithUndefinedField(): ByteArray = ProtobufTestFixtures.bytesWithUnknownFields(
        sample,
        mapOf("age" to 1),
        ProtobufTestFixtures.varintUnknown(100, 7),
    )

    private fun nodeWithUnknownField(): ObjectNode = ObjectNode.of(
        "age" to NumberNode.of(1),
        "unknown" to StringNode.of("x"),
    )

    private fun nestedNodeWithUnknownField(): ObjectNode = ObjectNode.of(
        "child" to ObjectNode.of(
            "label" to StringNode.of("x"),
            "missing" to NumberNode.of(1),
        ),
    )
}
