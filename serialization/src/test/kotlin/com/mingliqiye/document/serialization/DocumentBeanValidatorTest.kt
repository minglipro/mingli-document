package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.codegen.DocumentRuntimeCodecFactory
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [DocumentBeanValidator] 根对象校验扩展点测试。
 *
 * 覆盖 Hook 调用时机、嵌套调用去重、异常包装与未配置校验器时的行为兼容性。
 */
class DocumentBeanValidatorTest {

    private class RecordingValidator : DocumentBeanValidator {
        val validated = mutableListOf<Any>()

        override fun validate(value: Any) {
            validated += value
        }
    }

    private class ThrowingValidator(private val failure: Exception) : DocumentBeanValidator {
        override fun validate(value: Any): Unit = throw failure
    }

    @Test
    fun toNodeValidatesRootExactlyOnce() {
        val recorder = RecordingValidator()
        val mapper = mapperWith(recorder)
        val root = Parent().apply { child = Child().apply { name = "c" } }

        val node = mapper.toNode(root)

        assertEquals(1, recorder.validated.size)
        assertSame(root, recorder.validated.single())
        assertEquals("c", node.asObject()["child"]?.asObject()?.get("name")?.asString())
    }

    @Test
    fun fromNodeValidatesResultExactlyOnce() {
        val recorder = RecordingValidator()
        val mapper = mapperWith(recorder)
        val node = ObjectNode.of(
            "child" to ObjectNode.of("name" to StringNode.of("c")),
        )

        val result = mapper.fromNode(node, Parent::class.java)

        assertEquals(1, recorder.validated.size)
        assertSame(result, recorder.validated.single())
    }

    @Test
    fun jsonFormatAndParseUseRootValidation() {
        val recorder = RecordingValidator()
        val mapper = mapperWith(recorder)
        val root = Parent().apply { child = Child().apply { name = "c" } }

        val text = mapper.format(root)
        assertEquals(1, recorder.validated.size)
        assertSame(root, recorder.validated.single())

        val parsed = mapper.parse(text, Parent::class.java)
        assertEquals(2, recorder.validated.size)
        assertSame(parsed, recorder.validated.last())
    }

    @Test
    fun genericFromNodeValidatesResult() {
        val recorder = RecordingValidator()
        val mapper = mapperWith(recorder)
        val node = ObjectNode.of("name" to StringNode.of("c"))

        val child = mapper.fromNode(node, Child::class.java)

        assertEquals(1, recorder.validated.size)
        assertSame(child, recorder.validated.single())
    }

    @Test
    fun runtimeCodecConverterDoesNotRevalidateNestedValues() {
        val recorder = RecordingValidator()
        val factory = DocumentRuntimeCodecFactory()
        val mapper = JsonMapper.builder()
            .addConverter(factory.converter(Parent::class.java))
            .config(DocumentMapperConfig.builder().beanValidator(recorder).build())
            .build()
        val root = Parent().apply { child = Child().apply { name = "c" } }

        val text = mapper.format(root)

        assertTrue(text.contains("child"))
        // 生成式编解码器在字段转换时会回调 mapper.toNode(child)：只有根对象被校验一次。
        assertEquals(1, recorder.validated.size)
        assertSame(root, recorder.validated.single())

        val restored = mapper.parse(text, Parent::class.java)
        assertEquals(2, recorder.validated.size)
        assertSame(restored, recorder.validated.last())
    }

    @Test
    fun validatorFailuresAreWrappedPreservingCause() {
        val failure = IllegalStateException("bad root")
        val mapper = mapperWith(ThrowingValidator(failure))

        val serialized = assertFailsWith<DocumentSerializationException> {
            mapper.toNode(Parent())
        }
        assertSame(failure, serialized.cause)
        assertTrue(serialized.message!!.contains("Parent"))

        val deserialized = assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(ObjectNode.of(), Parent::class.java)
        }
        assertSame(failure, deserialized.cause)
        assertTrue(deserialized.message!!.contains("Parent"))
    }

    @Test
    fun documentSerializationExceptionFromValidatorIsPropagated() {
        val failure = DocumentSerializationException("constraint violation")
        val mapper = mapperWith(ThrowingValidator(failure))

        val caught = assertFailsWith<DocumentSerializationException> {
            mapper.toNode(Parent())
        }
        assertSame(failure, caught)
    }

    @Test
    fun nullRootIsNotValidated() {
        val recorder = RecordingValidator()
        val mapper = mapperWith(recorder)

        assertSame(NullNode.INSTANCE, mapper.toNode(null))
        assertTrue(recorder.validated.isEmpty())

        recorder.validated.clear()
        assertNull(mapper.fromNode(NullNode.INSTANCE, String::class.java))
        assertTrue(recorder.validated.isEmpty())
    }

    @Test
    fun noValidatorConfiguredDoesNotValidate() {
        val config = DocumentMapperConfig.builder().build()
        assertNull(config.beanValidator)

        val mapper = JsonMapper(config)
        val root = Parent().apply { child = Child().apply { name = "c" } }
        val text = mapper.format(root)
        assertEquals("c", mapper.parse(text, Parent::class.java).child?.name)
    }

    @Test
    fun builderAndCopiedConfigPropagateValidator() {
        val recorder = RecordingValidator()
        val config = DocumentMapperConfig.builder()
            .beanValidator(recorder)
            .build()

        assertSame(recorder, config.beanValidator)
        assertSame(recorder, CopiedConfig(config).beanValidator)

        val mapper = JsonMapper.builder().config(config).build()
        val root = Parent()
        mapper.toNode(root)
        assertEquals(1, recorder.validated.size)
        assertSame(root, recorder.validated.single())
    }

    private fun mapperWith(validator: DocumentBeanValidator): JsonMapper = JsonMapper(
        DocumentMapperConfig.builder()
            .beanValidator(validator)
            .build(),
    )

    private class Parent {
        var child: Child? = null
    }

    private class Child {
        var name: String? = null
    }
}

/**
 * 验证 [DocumentMapperConfig] 受保护复制构造器保留 [DocumentMapperConfig.beanValidator]。
 */
private class CopiedConfig(base: DocumentMapperConfig) : DocumentMapperConfig(base)
