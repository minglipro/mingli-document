package com.mingliqiye.document.validation

import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [JakartaDocumentBeanValidator] 单元测试。
 *
 * 覆盖 `@NotNull` / `@Size` / `@Pattern`、嵌套对象级联、集合元素约束、
 * 异常消息格式与工厂创建。
 */
class JakartaDocumentBeanValidatorTest {

    private val validator: JakartaDocumentBeanValidator = JakartaDocumentBeanValidator.create()

    @Test
    fun validBeanPassesAllConstraints() {
        validator.validate(validPerson())
    }

    @Test
    fun notNullViolationReportsFieldPathAndMessage() {
        val person = validPerson().apply { name = null }

        val failure = assertFailsWith<DocumentSerializationException> { validator.validate(person) }

        assertTrue(failure.message!!.contains("name: name must not be null"), failure.message)
        assertTrue(failure.message!!.contains("Bean validation failed for"), failure.message)
        assertTrue(failure.message!!.contains("Person"), failure.message)
    }

    @Test
    fun sizeViolationReportsFieldPathAndMessage() {
        val person = validPerson().apply { tags = mutableListOf() }

        val failure = assertFailsWith<DocumentSerializationException> { validator.validate(person) }

        assertTrue(
            failure.message!!.contains("tags: tags size must be between 1 and 3"),
            failure.message,
        )
    }

    @Test
    fun patternViolationReportsFieldPathAndMessage() {
        val person = validPerson().apply { code = "ABC" }

        val failure = assertFailsWith<DocumentSerializationException> { validator.validate(person) }

        assertTrue(
            failure.message!!.contains("code: code must match [a-z]+"),
            failure.message,
        )
    }

    @Test
    fun nestedObjectViolationUsesNestedPropertyPath() {
        val person = validPerson().apply { address = Address() }

        val failure = assertFailsWith<DocumentSerializationException> { validator.validate(person) }

        assertTrue(
            failure.message!!.contains("address.street: street must not be null"),
            failure.message,
        )
    }

    @Test
    fun collectionElementViolationUsesIndexedPath() {
        val bag = TagBag().apply { tags = mutableListOf(null, "ok") }

        val failure = assertFailsWith<DocumentSerializationException> { validator.validate(bag) }
        val message = failure.message!!

        assertTrue(message.contains("tags[0]"), message)
        assertTrue(message.contains("element must not be null"), message)
    }

    @Test
    fun multipleViolationsAreCollectedInStableOrder() {
        val person = validPerson().apply {
            name = null
            code = "ABC"
            age = null
        }

        val failure = assertFailsWith<DocumentSerializationException> { validator.validate(person) }
        val message = failure.message!!

        assertTrue(message.contains("; "), message)
        assertTrue(message.contains("name: name must not be null"), message)
        assertTrue(message.contains("code: code must match [a-z]+"), message)
        assertTrue(message.contains("age: "), message)
        val ageIndex = message.indexOf("age: ")
        val codeIndex = message.indexOf("code: ")
        val nameIndex = message.indexOf("name: ")
        assertTrue(ageIndex in 0 until codeIndex, message)
        assertTrue(codeIndex in 0 until nameIndex, message)
    }

    @Test
    fun explicitJakartaValidatorCanBeWrapped() {
        val jakartaValidator = Validation.buildDefaultValidatorFactory().validator
        val wrapper = JakartaDocumentBeanValidator(jakartaValidator)

        wrapper.validate(validPerson())
        assertFailsWith<DocumentSerializationException> {
            wrapper.validate(validPerson().apply { name = null })
        }
    }

    @Test
    fun constraintViolationExceptionIsConvertedWithCause() {
        val jakartaValidator = Validation.buildDefaultValidatorFactory().validator
        val failure = ConstraintViolationException(jakartaValidator.validate(validPerson().apply { name = null }))

        val converted = JakartaDocumentBeanValidator.toDocumentSerializationException(failure)

        assertSame(failure, converted.cause)
        assertTrue(converted.message!!.contains("name: name must not be null"), converted.message)
    }

    @Test
    fun violationMessageHelperFormatsFieldPaths() {
        val jakartaValidator = Validation.buildDefaultValidatorFactory().validator
        val violations = jakartaValidator.validate(validPerson().apply { name = null })

        val message = JakartaDocumentBeanValidator.message(violations, validPerson())

        assertTrue(message.contains("Bean validation failed for"), message)
        assertTrue(message.contains("name: name must not be null"), message)
    }
}

/**
 * [JakartaDocumentBeanValidator] 与 `JsonMapper` 的集成测试。
 */
class JakartaDocumentBeanValidatorMapperTest {

    private val mapper = JsonMapper(
        DocumentMapperConfig.builder()
            .beanValidator(JakartaDocumentBeanValidator.create())
            .build(),
    )

    @Test
    fun formatAndToNodeValidateRootObject() {
        val failure = assertFailsWith<DocumentSerializationException> {
            mapper.format(invalidPerson())
        }
        assertTrue(failure.message!!.contains("name: name must not be null"), failure.message)

        assertFailsWith<DocumentSerializationException> { mapper.formatPretty(invalidPerson()) }
        assertFailsWith<DocumentSerializationException> { mapper.toNode(invalidPerson()) }
    }

    @Test
    fun parseAndFromNodeValidateRootObject() {
        assertFailsWith<DocumentSerializationException> {
            mapper.parse(
                """{"name":null,"age":18,"tags":["a"],"code":"abc"}""",
                Person::class.java,
            )
        }

        val node = JsonMapper().toNode(invalidPerson())
        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(node, Person::class.java)
        }
    }

    @Test
    fun validObjectRoundTrips() {
        val text = mapper.format(validPerson())
        val restored = mapper.parse(text, Person::class.java)

        assertEquals("小于", restored.name)
        assertEquals(18, restored.age.toInt())
        assertEquals(listOf("a"), restored.tags)
        assertEquals("abc", restored.code)
        assertEquals("杭州", restored.address.street)
    }

    @Test
    fun mapperWithoutValidatorSkipsBeanValidation() {
        val plain = JsonMapper()

        val text = plain.format(invalidPerson())

        assertTrue(text.contains("\"name\":null"), text)
    }
}

private fun validPerson(): Person = Person().apply {
    name = "小于"
    age = 18
    tags = mutableListOf("a")
    code = "abc"
    address = Address().apply { street = "杭州" }
}

private fun invalidPerson(): Person = validPerson().apply { name = null }
