package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.exception.JpaConfigException
import com.mingliqiye.document.jpa.exception.JpaMappingException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** `@Convert` / autoApply `AttributeConverter` 映射测试。 */
class JpaConverterTest {

    private val mapper = JpaDocumentMapper()

    @Test
    fun roundTripsExplicitConverters() {
        val entity = ConvertedEntity().apply {
            id = 1L
            price = Money(BigDecimal("12.50"), "USD")
            fee = Money(BigDecimal("3.00"), "EUR")
            rating = Rating(5)
            charges = mutableListOf(Money(BigDecimal("1.00"), "USD"), Money(BigDecimal("2.50"), "CNY"))
        }

        val node = mapper.toNode(entity)

        assertEquals("12.50 USD", node["price"]!!.asString())
        assertEquals("3.00 EUR", node["fee_amount"]!!.asString())
        assertEquals(5, node["rating"]!!.asNumber().toInt())
        val charges = node["charges"]!!.asArray()
        assertEquals(2, charges.size)
        assertEquals("1.00 USD", charges[0].asString())

        val copy = mapper.fromNode(node, ConvertedEntity::class.java)
        assertEquals(entity.id, copy.id)
        assertEquals(entity.price, copy.price)
        assertEquals(entity.fee, copy.fee)
        assertEquals(entity.rating, copy.rating)
        assertEquals(entity.charges, copy.charges)
    }

    @Test
    fun appliesConverterOnPropertyAccessGetter() {
        val entity = PropertyConvertedEntity().apply {
            id = 2L
            price = Money(BigDecimal("9.99"), "CNY")
        }

        val node = mapper.toNode(entity)

        assertEquals("9.99 CNY", node["price"]!!.asString())
        val copy = mapper.fromNode(node, PropertyConvertedEntity::class.java)
        assertEquals(Money(BigDecimal("9.99"), "CNY"), copy.price)
    }

    @Test
    fun appliesAutoApplyConverterByPropertyType() {
        val autoMapper = autoAppliedMapper()
        val entity = AutoAppliedEntity().apply {
            id = 3L
            email = Email("Alice@Example.com")
            alias = Email("bob@example.com")
            status = AccountStatus.SUSPENDED
            convertedStatus = AccountStatus.ACTIVE
        }

        val node = autoMapper.toNode(entity)

        assertEquals("ALICE@EXAMPLE.COM", node["email"]!!.asString())
        assertEquals("raw:bob@example.com", node["alias"]!!.asString())
        assertEquals("SUSPENDED", node["status"]!!.asString())
        assertEquals("status:ACTIVE", node["convertedStatus"]!!.asString())

        val copy = autoMapper.fromNode(node, AutoAppliedEntity::class.java)
        assertEquals(Email("alice@example.com"), copy.email)
        assertEquals(Email("bob@example.com"), copy.alias)
        assertEquals(AccountStatus.SUSPENDED, copy.status)
        assertEquals(AccountStatus.ACTIVE, copy.convertedStatus)
    }

    @Test
    fun requiresAutoApplyConverterRegistration() {
        val node = runCatching { mapper.toNode(AutoAppliedEntity().apply { email = Email("x@y") }) }

        val exception = node.exceptionOrNull()
        assertTrue(exception is JpaMappingException, "expected JpaMappingException but was $exception")
        assertTrue(exception.message!!.contains("Unsupported value type"), "message was ${exception.message}")
    }

    @Test
    fun disableConversionDisablesAutoApply() {
        val entity = AutoAppliedEntity().apply {
            id = 4L
            raw = Email("raw@example.com")
        }

        val exception = assertFailsWith<JpaMappingException> { autoAppliedMapper().toNode(entity) }

        assertTrue(exception.message!!.contains("Unsupported value type"), "message was ${exception.message}")
    }

    @Test
    fun passesNullAttributesToConverters() {
        NullTrackingConverterState.reset()
        val entity = NullConvertedEntity().apply { id = 5L }

        val node = mapper.toNode(entity)

        assertTrue(node["value"]!!.isNull)
        assertEquals(1, NullTrackingConverterState.databaseColumnCalls.get())

        val copy = mapper.fromNode(
            ObjectNode.of("id" to NumberNode.of(5), "value" to NullNode.INSTANCE),
            NullConvertedEntity::class.java,
        )
        assertNull(copy.value)
        assertEquals(1, NullTrackingConverterState.entityAttributeCalls.get())
    }

    @Test
    fun converterCanMapNullDatabaseValueToEntityValue() {
        val node = mapper.toNode(NullAliasedEntity().apply { id = 1L })

        assertTrue(node["value"]!!.isNull)

        val copy = mapper.fromNode(node, NullAliasedEntity::class.java)

        assertEquals("nil-entity", copy.value)
        assertTrue(mapper.toNode(copy)["value"]!!.isNull)
    }

    @Test
    fun invokesConverterForNonNullValues() {
        NullTrackingConverterState.reset()
        val entity = NullConvertedEntity().apply {
            id = 6L
            value = "hello"
        }

        val node = mapper.toNode(entity)

        assertEquals("hello", node["value"]!!.asString())
        assertEquals(1, NullTrackingConverterState.databaseColumnCalls.get())

        val copy = mapper.fromNode(node, NullConvertedEntity::class.java)
        assertEquals("hello", copy.value)
        assertEquals(1, NullTrackingConverterState.entityAttributeCalls.get())
    }

    @Test
    fun wrapsConverterFailureInMappingException() {
        val entity = BoomConvertedEntity().apply {
            id = 7L
            value = "boom!"
        }

        val writeException = assertFailsWith<JpaMappingException> { mapper.toNode(entity) }
        assertTrue(writeException.cause is IllegalStateException, "cause was ${writeException.cause}")

        val node = ObjectNode.of("id" to NumberNode.of(7), "value" to StringNode.of("boom!"))
        val readException = assertFailsWith<JpaMappingException> {
            mapper.fromNode(node, BoomConvertedEntity::class.java)
        }
        assertTrue(readException.cause is IllegalStateException, "cause was ${readException.cause}")
    }

    @Test
    fun converterReturningNullWritesNullNode() {
        val entity = DroppingConvertedEntity().apply {
            id = 8L
            value = "drop"
        }

        val node = mapper.toNode(entity)

        assertTrue(node.containsKey("value"))
        assertTrue(node["value"]!!.isNull)

        val copy = mapper.fromNode(node, DroppingConvertedEntity::class.java)
        assertNull(copy.value)
    }

    @Test
    fun rejectsConverterThatDoesNotImplementAttributeConverter() {
        val exception = assertFailsWith<JpaConfigException> {
            JpaDocumentMapper(JpaConfig.builder().autoApplyConverter(NotAConverter::class.java).build())
        }
        assertTrue(exception.message!!.contains("AttributeConverter"), "message was ${exception.message}")
    }

    @Test
    fun rejectsConverterWithoutNoArgumentConstructor() {
        val exception = assertFailsWith<JpaConfigException> {
            JpaDocumentMapper(
                JpaConfig.builder().autoApplyConverter(NoDefaultConstructorConverter::class.java).build(),
            )
        }
        assertTrue(exception.message!!.contains("no-argument constructor"), "message was ${exception.message}")
    }

    @Test
    fun rejectsDuplicateAutoApplyConvertersForSameType() {
        val exception = assertFailsWith<JpaConfigException> {
            JpaDocumentMapper(
                JpaConfig.builder()
                    .autoApplyConverter(EmailConverter::class.java)
                    .autoApplyConverter(RawEmailConverter::class.java)
                    .build(),
            )
        }
        assertTrue(exception.message!!.contains("Multiple auto-apply"), "message was ${exception.message}")
        assertTrue(exception.message!!.contains(Email::class.java.name), "message was ${exception.message}")
    }

    @Test
    fun rejectsExplicitConverterTypeMismatch() {
        val exception = assertFailsWith<JpaConfigException> { mapper.toNode(MismatchedConverterEntity()) }
        assertTrue(exception.message!!.contains("declared as"), "message was ${exception.message}")
    }

    @Test
    fun rejectsConvertOnRelationshipAttribute() {
        val exception = assertFailsWith<JpaConfigException> { mapper.toNode(BadConvertedAssociationEntity()) }
        assertTrue(exception.message!!.contains("relationship attribute"), "message was ${exception.message}")
    }

    @Test
    fun writesNullNodeForNullableConvertedValue() {
        val node = mapper.toNode(ConvertedEntity().apply { id = 9L })

        assertNotNull(node["price"])
        assertTrue(node["price"]!!.isNull)
        assertSame(NullNode.INSTANCE.type, node["price"]!!.type)
    }

    private fun autoAppliedMapper(): JpaDocumentMapper = JpaDocumentMapper(
        JpaConfig.builder()
            .autoApplyConverter(EmailConverter::class.java)
            .autoApplyConverter(StatusLabelConverter::class.java)
            .build(),
    )
}
