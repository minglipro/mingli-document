package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DeserializationFeature] 与 [DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES] 测试。
 *
 * 覆盖：
 * - 默认全部关闭、Builder 的 enable/disable/configure/failOnNullForPrimitives、
 *   复制构造器携带特性；
 * - 关闭时 `null` → 基本类型使用 Java 默认值，不抛异常；
 * - 开启时 fromNode / fromTokens / parse 三条 Bean 路径、字段注入、`@DocumentCreator`
 *   构造器、Builder、基本类型数组元素都会抛 [DocumentSerializationException]，
 *   且异常信息包含路径与类型；
 * - 包装类型 `Integer` / 可空 Kotlin 属性不受影响。
 */
class DeserializationFeatureTest {

    private val feature = DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES

    @Test
    fun defaultConfigDisablesAllFeatures() {
        val config = DocumentMapperConfig.empty()
        assertTrue(config.deserializationFeatures.isEmpty())
        assertFalse(config.isEnabled(feature))
        assertFalse(config.isEnabled(feature, false))
        assertTrue(config.isEnabled(feature, true), "未显式配置时应返回调用方给定的默认值")
    }

    @Test
    fun builderEnableDisableConfigureAndConvenience() {
        val enabled = DocumentMapperConfig.builder()
            .enable(feature)
            .build()
        assertTrue(enabled.isEnabled(feature))
        assertTrue(enabled.isEnabled(feature, false))
        assertEquals(setOf(feature), enabled.deserializationFeatures)

        val disabledAgain = DocumentMapperConfig.builder()
            .enable(feature)
            .disable(feature)
            .build()
        assertFalse(disabledAgain.isEnabled(feature))

        val configured = DocumentMapperConfig.builder()
            .configure(feature, true)
            .build()
        assertTrue(configured.isEnabled(feature))

        val convenience = DocumentMapperConfig.builder()
            .failOnNullForPrimitives(true)
            .build()
        assertTrue(convenience.isEnabled(feature))

        val off = DocumentMapperConfig.builder()
            .failOnNullForPrimitives(false)
            .build()
        assertFalse(off.isEnabled(feature))
    }

    @Test
    fun copyConstructorCarriesFeatures() {
        val base = DocumentMapperConfig.builder()
            .failOnNullForPrimitives(true)
            .build()
        val copied = CopiedConfig(base)
        assertTrue(copied.isEnabled(feature))
        assertEquals(base.deserializationFeatures, copied.deserializationFeatures)

        val copiedEmpty = CopiedConfig(DocumentMapperConfig.empty())
        assertTrue(copiedEmpty.deserializationFeatures.isEmpty())
    }

    @Test
    fun disabledUsesPrimitiveDefaultsForFieldInjection() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().failOnNullForPrimitives(false).build())

        val fromNode = mapper.fromNode(ObjectNode.of("age" to NullNode.INSTANCE), User::class.java)
        assertEquals(0, fromNode.age)

        val parsed = mapper.parse("""{"age":null}""", User::class.java)
        assertEquals(0, parsed.age)

        val fromTokens = mapper.fromTokens(
            ListTokenReader(
                listOf(
                    DocumentToken.startObject(),
                    DocumentToken.fieldName("age"),
                    DocumentToken.nullValue(),
                    DocumentToken.endObject(),
                ),
            ),
            User::class.java,
        )
        assertEquals(0, fromTokens.age)
    }

    @Test
    fun enabledRejectsNullPrimitiveOnFromNodePath() {
        val mapper = enabledMapper()

        val failure = assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(ObjectNode.of("age" to NullNode.INSTANCE), User::class.java)
        }
        assertTrue(failure.message.orEmpty().contains("$.age"), failure.message.orEmpty())
        assertTrue(failure.message.orEmpty().contains("int"), failure.message.orEmpty())
        assertTrue(failure.message.orEmpty().contains("FAIL_ON_NULL_FOR_PRIMITIVES"), failure.message.orEmpty())
    }

    @Test
    fun enabledRejectsNullPrimitiveOnParseTokenPath() {
        val mapper = enabledMapper()

        val failure = assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"age":null}""", User::class.java)
        }
        assertTrue(failure.message.orEmpty().contains("$.age"), failure.message.orEmpty())
        assertTrue(failure.message.orEmpty().contains("int"), failure.message.orEmpty())
    }

    @Test
    fun enabledRejectsNullPrimitiveOnFromTokensPath() {
        val mapper = enabledMapper()

        val failure = assertFailsWith<DocumentSerializationException> {
            mapper.fromTokens(
                ListTokenReader(
                    listOf(
                        DocumentToken.startObject(),
                        DocumentToken.fieldName("age"),
                        DocumentToken.nullValue(),
                        DocumentToken.endObject(),
                    ),
                ),
                User::class.java,
            )
        }
        assertTrue(failure.message.orEmpty().contains("$.age"), failure.message.orEmpty())
        assertTrue(failure.message.orEmpty().contains("int"), failure.message.orEmpty())
    }

    @Test
    fun enabledRejectsNullPrimitiveBooleanAndWrappedTypeIsUnaffected() {
        val mapper = enabledMapper()

        val booleanFailure = assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(ObjectNode.of("flag" to NullNode.INSTANCE), PrimitiveDefaults::class.java)
        }
        assertTrue(booleanFailure.message.orEmpty().contains("$.flag"), booleanFailure.message.orEmpty())
        assertTrue(booleanFailure.message.orEmpty().contains("boolean"), booleanFailure.message.orEmpty())

        val wrapped = mapper.fromNode(
            ObjectNode.of(
                "boxed" to NullNode.INSTANCE,
                "flag" to BooleanNode.of(true),
                "count" to NumberNode.of(3),
            ),
            PrimitiveDefaults::class.java,
        )
        assertNull(wrapped.boxed)
        assertTrue(wrapped.flag)
        assertEquals(3, wrapped.count)
    }

    @Test
    fun disabledUsesPrimitiveDefaultsForBoolean() {
        val holder = JsonMapper().fromNode(
            ObjectNode.of("flag" to NullNode.INSTANCE),
            PrimitiveDefaults::class.java,
        )
        assertFalse(holder.flag)
    }

    @Test
    fun enabledRejectsNullPrimitiveForDocumentCreatorConstructor() {
        val failure = assertFailsWith<DocumentSerializationException> {
            enabledMapper().parse("""{"age":null}""", ImmutableUser::class.java)
        }
        assertTrue(failure.message.orEmpty().contains("$.age"), failure.message.orEmpty())
        assertTrue(failure.message.orEmpty().contains("int"), failure.message.orEmpty())
    }

    @Test
    fun disabledUsesPrimitiveDefaultForDocumentCreatorConstructor() {
        val user = JsonMapper().parse("""{"age":null}""", ImmutableUser::class.java)
        assertEquals(0, user.age)
    }

    @Test
    fun enabledRejectsNullPrimitiveForBuilderProperty() {
        val failure = assertFailsWith<DocumentSerializationException> {
            enabledMapper().parse("""{"age":null}""", BuilderUser::class.java)
        }
        assertTrue(failure.message.orEmpty().contains("$.age"), failure.message.orEmpty())
        assertTrue(failure.message.orEmpty().contains("int"), failure.message.orEmpty())
    }

    @Test
    fun disabledUsesPrimitiveDefaultForBuilderProperty() {
        val user = JsonMapper().parse("""{"age":null}""", BuilderUser::class.java)
        assertEquals(0, user.age)
    }

    @Test
    fun enabledRejectsNullPrimitiveArrayElement() {
        val failure = assertFailsWith<DocumentSerializationException> {
            enabledMapper().fromNode(ArrayNode.of(NullNode.INSTANCE), IntArray::class.java)
        }
        assertTrue(failure.message.orEmpty().contains("$[0]"), failure.message.orEmpty())
        assertTrue(failure.message.orEmpty().contains("int"), failure.message.orEmpty())
    }

    @Test
    fun disabledUsesPrimitiveDefaultForArrayElement() {
        val values = JsonMapper().fromNode(ArrayNode.of(NullNode.INSTANCE), IntArray::class.java) as IntArray
        assertEquals(1, values.size)
        assertEquals(0, values[0])
    }

    private fun enabledMapper(): JsonMapper = JsonMapper(
        DocumentMapperConfig.builder()
            .failOnNullForPrimitives(true)
            .build(),
    )

    private class PrimitiveDefaults {
        var flag: Boolean = true
        var count: Int = 5
        var boxed: Int? = 9
    }

    /** 复制构造器链路：[DocumentMapperConfig] 的 protected 拷贝构造器必须携带特性集合。 */
    private class CopiedConfig(base: DocumentMapperConfig) : DocumentMapperConfig(base)

    private class ListTokenReader(
        private val tokens: List<DocumentToken>,
    ) : DocumentTokenReader {

        private var index = -1
        private var current: DocumentToken? = null

        override val currentToken: DocumentTokenType?
            get() = current?.type

        override val textValue: String?
            get() = current?.text

        override val booleanValue: Boolean?
            get() = current?.booleanValue

        override val binaryValue: ByteArray?
            get() = current?.binaryValue

        override fun nextToken(): DocumentTokenType? {
            index++
            if (index >= tokens.size) {
                current = null
                return null
            }
            current = tokens[index]
            return current?.type
        }
    }
}
