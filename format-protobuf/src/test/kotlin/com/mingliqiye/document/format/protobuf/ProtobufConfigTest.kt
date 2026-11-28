package com.mingliqiye.document.format.protobuf

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [ProtobufConfig] / [ProtobufConfigBuilder] 配置测试。
 *
 * 覆盖 上游实现 对齐默认值、读取/写出特性开关、未知字段策略推导、通用
 * [DocumentMapperConfig] 便捷方法转发，以及配置到 [ProtobufMapper] 的装配。
 */
class ProtobufConfigTest {

    private val sample = ProtobufTestFixtures.proto3Sample

    private val extendedBase = ProtobufTestFixtures.extendedBase

    private val extendedRegistry = ProtobufTestFixtures.extendedRegistry

    private val namingDescriptor = ProtobufSchema.builder("User", "cfg.test")
        .field("user_name", 1, ProtobufType.STRING, required = false)
        .build()
        .toDescriptor()

    @Test
    fun defaultsAlignJackson() {
        val config = ProtobufConfig.of()
        assertEquals(ProtobufUnknownFieldPolicy.FAIL, config.unknownFieldPolicy)
        assertEquals(0, config.maxDepth)
        assertTrue(config.extensions.isEmpty)
        assertEquals(emptySet(), config.readFeatures)
        assertEquals(emptySet(), config.writeFeatures)
        assertEquals(ProtobufUnknownFieldPolicy.FAIL, config.effectiveUnknownFieldPolicy)
        assertFalse(config.ignoreUndefinedFields)
        assertFalse(config.ignoreUnknownFields)
        assertFalse(config.isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED))
        assertFalse(config.isEnabled(ProtobufWriteFeature.IGNORE_UNKNOWN))
        assertEquals(ProtobufConfig.DEFAULT_READ_FEATURES, config.readFeatures)
        assertEquals(ProtobufConfig.DEFAULT_WRITE_FEATURES, config.writeFeatures)

        val built = ProtobufConfig.builder().build()
        assertEquals(config.unknownFieldPolicy, built.unknownFieldPolicy)
        assertEquals(config.maxDepth, built.maxDepth)
        assertEquals(config.readFeatures, built.readFeatures)
        assertEquals(config.writeFeatures, built.writeFeatures)
    }

    @Test
    fun featureInventoryMirrorsJacksonCoreBehaviors() {
        // 上游实现 protobuf 没有格式专属 Feature；这里只封装其在解析/生成器中真正查询的两项核心特性。
        assertEquals(listOf("IGNORE_UNDEFINED"), ProtobufReadFeature.values().map { it.legacyName })
        assertEquals(listOf("IGNORE_UNKNOWN"), ProtobufWriteFeature.values().map { it.legacyName })
    }

    @Test
    fun builderTogglesFeatures() {
        val config = ProtobufConfig.builder()
            .enable(ProtobufReadFeature.IGNORE_UNDEFINED)
            .enable(ProtobufWriteFeature.IGNORE_UNKNOWN)
            .build()
        assertTrue(config.isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED))
        assertTrue(config.isEnabled(ProtobufWriteFeature.IGNORE_UNKNOWN))
        assertTrue(config.ignoreUndefinedFields)
        assertTrue(config.ignoreUnknownFields)

        val disabled = ProtobufConfig.builder()
            .enable(ProtobufReadFeature.IGNORE_UNDEFINED)
            .disable(ProtobufReadFeature.IGNORE_UNDEFINED)
            .enable(ProtobufWriteFeature.IGNORE_UNKNOWN)
            .disable(ProtobufWriteFeature.IGNORE_UNKNOWN)
            .build()
        assertFalse(disabled.isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED))
        assertFalse(disabled.isEnabled(ProtobufWriteFeature.IGNORE_UNKNOWN))

        val configured = ProtobufConfig.builder()
            .configure(ProtobufReadFeature.IGNORE_UNDEFINED, true)
            .configure(ProtobufWriteFeature.IGNORE_UNKNOWN, true)
            .configure(ProtobufReadFeature.IGNORE_UNDEFINED, false)
            .build()
        assertFalse(configured.isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED))
        assertTrue(configured.isEnabled(ProtobufWriteFeature.IGNORE_UNKNOWN))

        val replaced = ProtobufConfig.builder()
            .readFeatures(setOf(ProtobufReadFeature.IGNORE_UNDEFINED))
            .writeFeatures(setOf(ProtobufWriteFeature.IGNORE_UNKNOWN))
            .readFeatures(emptySet())
            .build()
        assertEquals(emptySet(), replaced.readFeatures)
        assertEquals(setOf(ProtobufWriteFeature.IGNORE_UNKNOWN), replaced.writeFeatures)
    }

    @Test
    fun effectivePolicyFollowsFeatureAndPreserve() {
        assertEquals(
            ProtobufUnknownFieldPolicy.IGNORE,
            ProtobufConfig.builder()
                .unknownFieldPolicy(ProtobufUnknownFieldPolicy.FAIL)
                .enable(ProtobufReadFeature.IGNORE_UNDEFINED)
                .build()
                .effectiveUnknownFieldPolicy,
        )
        assertEquals(
            ProtobufUnknownFieldPolicy.FAIL,
            ProtobufConfig.builder()
                .unknownFieldPolicy(ProtobufUnknownFieldPolicy.FAIL)
                .build()
                .effectiveUnknownFieldPolicy,
        )
        assertEquals(
            ProtobufUnknownFieldPolicy.IGNORE,
            ProtobufConfig.builder()
                .unknownFieldPolicy(ProtobufUnknownFieldPolicy.IGNORE)
                .build()
                .effectiveUnknownFieldPolicy,
        )
        assertEquals(
            ProtobufUnknownFieldPolicy.PRESERVE,
            ProtobufConfig.builder()
                .unknownFieldPolicy(ProtobufUnknownFieldPolicy.PRESERVE)
                .enable(ProtobufReadFeature.IGNORE_UNDEFINED)
                .build()
                .effectiveUnknownFieldPolicy,
        )
    }

    @Test
    fun ofAndFromFactories() {
        val of = ProtobufConfig.of(ProtobufUnknownFieldPolicy.IGNORE, 3, extendedRegistry)
        assertEquals(ProtobufUnknownFieldPolicy.IGNORE, of.unknownFieldPolicy)
        assertEquals(3, of.maxDepth)
        assertSame(extendedRegistry, of.extensions)

        val from = ProtobufConfig.from(
            DocumentMapperConfig.builder().writeNulls(false).build(),
            readFeatures = setOf(ProtobufReadFeature.IGNORE_UNDEFINED),
            writeFeatures = setOf(ProtobufWriteFeature.IGNORE_UNKNOWN),
            maxDepth = 2,
        )
        assertFalse(from.writeNulls)
        assertEquals(2, from.maxDepth)
        assertEquals(setOf(ProtobufReadFeature.IGNORE_UNDEFINED), from.readFeatures)
        assertEquals(setOf(ProtobufWriteFeature.IGNORE_UNKNOWN), from.writeFeatures)

        assertFailsWith<IllegalArgumentException> { ProtobufConfig.of(maxDepth = -1) }
        assertFailsWith<IllegalArgumentException> { ProtobufConfig.builder().maxDepth(-1).build() }
    }

    @Test
    fun builderForwardsGenericOptions() {
        val charset = Charset.forName("GBK")
        val config = ProtobufConfig.builder()
            .missingFieldPolicy(MissingFieldPolicy.SET_EMPTY)
            .failOnUnknownFields(true)
            .writeNulls(false)
            .namingStrategy(PropertyNamingStrategy.KEBAB_CASE)
            .fileCharset(charset)
            .atomicFileWrite(true)
            .createParentDirectories(true)
            .maxInputSize(1024)
            .maxStringLength(64)
            .maxNodeDepth(8)
            .maxNodeCount(4096)
            .resolveReferences(true)
            .writeReferences(true)
            .remoteReferenceResolver(null)
            .beanValidator(null)
            .build()

        assertEquals(MissingFieldPolicy.SET_EMPTY, config.missingFieldPolicy)
        assertTrue(config.failOnUnknownFields)
        assertFalse(config.writeNulls)
        assertEquals(PropertyNamingStrategy.KEBAB_CASE, config.namingStrategy)
        assertEquals(charset, config.fileCharset)
        assertTrue(config.atomicFileWrite)
        assertTrue(config.createParentDirectories)
        assertEquals(1024, config.maxInputSize)
        assertEquals(64, config.maxStringLength)
        assertEquals(8, config.maxNodeDepth)
        assertEquals(4096, config.maxNodeCount)
        assertTrue(config.resolveReferences)
        assertTrue(config.writeReferences)
        assertEquals(null, config.remoteReferenceResolver)
        assertEquals(null, config.beanValidator)
    }

    @Test
    fun builderForwardsDeserializationFeatures() {
        val enabled = ProtobufConfig.builder()
            .failOnNullForPrimitives(true)
            .build()
        assertTrue(enabled.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))

        val disabled = ProtobufConfig.builder()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build()
        assertFalse(disabled.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
    }

    @Test
    fun baseConfigIsReused() {
        val base = DocumentMapperConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .writeNulls(false)
            .build()
        val config = ProtobufConfig.builder().base(base).build()
        assertEquals(PropertyNamingStrategy.SNAKE_CASE, config.namingStrategy)
        assertFalse(config.writeNulls)
        assertEquals(ProtobufUnknownFieldPolicy.FAIL, config.unknownFieldPolicy)
    }

    @Test
    fun baseAndConvenienceMethodsAreMutuallyExclusive() {
        assertFailsWith<IllegalStateException> {
            ProtobufConfig.builder()
                .base(DocumentMapperConfig.empty())
                .writeNulls(false)
                .build()
        }
    }

    @Test
    fun mapperExposesConfig() {
        val config = ProtobufConfig.builder()
            .unknownFieldPolicy(ProtobufUnknownFieldPolicy.IGNORE)
            .maxDepth(4)
            .extensions(extendedRegistry)
            .enable(ProtobufReadFeature.IGNORE_UNDEFINED)
            .build()
        val mapper = ProtobufMapper(extendedBase, config)

        assertSame(config, mapper.protobufConfig)
        assertEquals(ProtobufUnknownFieldPolicy.IGNORE, mapper.unknownFieldPolicy)
        assertEquals(4, mapper.maxDepth)
        assertSame(extendedRegistry, mapper.extensions)
        assertTrue(mapper.isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED))
        assertFalse(mapper.isEnabled(ProtobufWriteFeature.IGNORE_UNKNOWN))
    }

    @Test
    fun legacyConstructorParametersBecomeConfig() {
        val mapper = ProtobufMapper(extendedBase, ProtobufUnknownFieldPolicy.IGNORE, maxDepth = 5)
        assertEquals(ProtobufUnknownFieldPolicy.IGNORE, mapper.protobufConfig.unknownFieldPolicy)
        assertEquals(5, mapper.protobufConfig.maxDepth)
        assertEquals(ProtobufConfig.DEFAULT_READ_FEATURES, mapper.protobufConfig.readFeatures)
        assertEquals(ProtobufConfig.DEFAULT_WRITE_FEATURES, mapper.protobufConfig.writeFeatures)

        val withExtensions = ProtobufMapper(extendedBase, extendedRegistry)
        assertSame(extendedRegistry, withExtensions.extensions)
        assertEquals(ProtobufUnknownFieldPolicy.FAIL, withExtensions.unknownFieldPolicy)

        val fromSchema = ProtobufMapper(
            ProtobufSchema.builder("CfgOnly", "cfg.test").field("id", 1, ProtobufType.INT32, required = false).build(),
            ProtobufConfig.of(maxDepth = 1),
        )
        assertEquals(1, fromSchema.maxDepth)
    }

    @Test
    fun configExtensionsReachMapper() {
        val node = ObjectNode.of("ext.nickname" to StringNode.of("小于"))
        val registryMapper = ProtobufMapper(extendedBase, extendedRegistry)
        val bytes = registryMapper.formatBytes(node)

        val configured = ProtobufMapper(
            extendedBase,
            ProtobufConfig.builder().extensions(extendedRegistry).build(),
        )
        assertEquals(node, configured.parse(bytes, Node::class.java).asObject())
        assertFailsWith<com.mingliqiye.document.format.protobuf.exception.ProtobufParseException> {
            ProtobufMapper(extendedBase).parse(bytes, Node::class.java)
        }
    }

    @Test
    fun namingStrategyConfigAffectsBeanRoundTrip() {
        val config = ProtobufConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .build()
        val mapper = ProtobufMapper(namingDescriptor, config)
        val bean = NamingBean().apply { userName = "小于" }

        val bytes = mapper.formatBytes(bean)
        assertEquals("小于", mapper.parse(bytes, NamingBean::class.java).userName)

        assertFailsWith<com.mingliqiye.document.format.protobuf.exception.ProtobufException> {
            ProtobufMapper(namingDescriptor).formatBytes(bean)
        }
    }

    @Test
    fun writeNullsConfigAffectsNode() {
        val defaultNode = ProtobufMapper(namingDescriptor).toNode(NamingBean())
        assertTrue(defaultNode.asObject().containsKey("userName"))

        val configured = ProtobufMapper(
            namingDescriptor,
            ProtobufConfig.builder().writeNulls(false).build(),
        )
        assertFalse(configured.toNode(NamingBean()).asObject().containsKey("userName"))
    }

    @Test
    fun maxInputSizeConfigIsEnforced() {
        val bytes = ProtobufMapper(sample).formatBytes(ObjectNode.of("age" to NumberNode.of(1)))
        val mapper = ProtobufMapper(sample, ProtobufConfig.builder().maxInputSize(1).build())
        assertFailsWith<DocumentSerializationException> { mapper.parse(bytes, Node::class.java) }
        assertFailsWith<DocumentSerializationException> { mapper.parseToNode(bytes, sample) }
    }

    @Test
    fun maxNodeDepthConfigIsEnforced() {
        val mapper = ProtobufMapper(
            sample,
            ProtobufConfig.builder().maxNodeDepth(1).build(),
        )
        assertFailsWith<DocumentSerializationException> {
            mapper.toNode(ObjectNode.of("child" to ObjectNode.of("label" to StringNode.of("x"))))
        }
    }

    @Test
    fun missingFieldPolicyConfigIsEnforced() {
        val bytes = ProtobufMapper(sample).formatBytes(ObjectNode.of("age" to NumberNode.of(1)))
        val mapper = ProtobufMapper(
            sample,
            ProtobufConfig.builder().missingFieldPolicy(MissingFieldPolicy.FAIL).build(),
        )
        assertFailsWith<DocumentSerializationException> { mapper.parse(bytes, SampleBean::class.java) }
    }

    @Test
    fun factoryUsesConfig() {
        val config = ProtobufConfig.builder()
            .extensions(extendedRegistry)
            .enable(ProtobufReadFeature.IGNORE_UNDEFINED)
            .build()
        val factory = ProtobufMapperFactory(config)
        assertSame(config, factory.protobufConfig)

        val first = factory.mapper(extendedBase)
        assertSame(config, first.protobufConfig)
        assertTrue(first.isEnabled(ProtobufReadFeature.IGNORE_UNDEFINED))
        assertSame(first, factory.mapper(extendedBase))
    }

    private class NamingBean {
        var userName: String? = null
    }

    private class SampleBean {
        var age: Int = 0
        var name: String? = null
    }
}
