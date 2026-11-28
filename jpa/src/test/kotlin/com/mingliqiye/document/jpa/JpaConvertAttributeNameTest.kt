package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.exception.JpaConfigException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `@Convert(attributeName = "...")` 嵌套属性路径转换测试。 */
class JpaConvertAttributeNameTest {

    private val mapper = JpaDocumentMapper()

    @Test
    fun convertsEmbeddedAttributeByPath() {
        val entity = ConvertedProfileEntity().apply {
            id = 1L
            address = Address().apply {
                city = "shanghai"
                zipCode = "200000"
            }
        }

        val node = mapper.toNode(entity)
        val address = node["address"]!!.asObject()

        assertEquals("db:200000", address["zip_code"]!!.asString())
        assertEquals("shanghai", address["city"]!!.asString())

        val copy = mapper.fromNode(node, ConvertedProfileEntity::class.java)
        assertEquals("200000", copy.address!!.zipCode)
        assertEquals("shanghai", copy.address!!.city)
    }

    @Test
    fun convertsNestedEmbeddedAttributeByDottedPath() {
        val entity = NestedConvertedProfileEntity().apply {
            id = 2L
            address = GeoAddress().apply {
                city = "beijing"
                geo = GeoPoint().apply {
                    lat = "39.9"
                    lng = "116.4"
                }
            }
        }

        val node = mapper.toNode(entity)
        val geo = node["address"]!!.asObject()["geo"]!!.asObject()

        assertEquals("db:39.9", geo["lat"]!!.asString())
        assertEquals("116.4", geo["lng"]!!.asString())

        val copy = mapper.fromNode(node, NestedConvertedProfileEntity::class.java)
        assertEquals("39.9", copy.address!!.geo!!.lat)
        assertEquals("116.4", copy.address!!.geo!!.lng)
    }

    @Test
    fun appliesClassLevelAttributeNamePath() {
        val entity = ClassLevelConvertedEntity().apply {
            id = 3L
            address = Address().apply { zipCode = "310000" }
        }

        val node = mapper.toNode(entity)

        assertEquals("db:310000", node["address"]!!.asObject()["zip_code"]!!.asString())

        val copy = mapper.fromNode(node, ClassLevelConvertedEntity::class.java)
        assertEquals("310000", copy.address!!.zipCode)
    }

    @Test
    fun convertsEmbeddedIdComponentByPath() {
        val entity = ConvertedOrderEntity().apply {
            key = ConvertedOrderKey().apply {
                orderNo = "A-1"
                region = "eu"
            }
        }

        val node = mapper.toNode(entity)
        val key = node["key"]!!.asObject()

        assertEquals("db:A-1", key["orderNo"]!!.asString())
        assertEquals("EU", key["region"]!!.asString())

        val copy = mapper.fromNode(node, ConvertedOrderEntity::class.java)
        assertEquals("A-1", copy.key!!.orderNo)
        assertEquals("eu", copy.key!!.region)
    }

    @Test
    fun attributeNameOverrideWinsOverEmbeddedClassConverter() {
        val entity = PriorityConvertedEntity().apply {
            id = 4L
            holder = AmountHolder().apply { amount = moneyOf("12.50", "USD") }
        }

        val node = mapper.toNode(entity)
        val amount = node["holder"]!!.asObject()["amount"]!!.asString()

        assertEquals("USD:12.50", amount)

        val copy = mapper.fromNode(node, PriorityConvertedEntity::class.java)
        assertEquals(moneyOf("12.50", "USD"), copy.holder!!.amount)
    }

    @Test
    fun attributeNameOverrideWinsOverAutoApplyConverter() {
        val autoApplyMapper = JpaDocumentMapper(
            JpaConfig.builder().autoApplyConverter(MoneyConverter::class.java).build(),
        )
        val entity = AutoApplyAmountEntity().apply {
            id = 5L
            holder = AutoAmountHolder().apply { amount = moneyOf("7.00", "EUR") }
        }

        val node = autoApplyMapper.toNode(entity)

        assertEquals("EUR:7.00", node["holder"]!!.asObject()["amount"]!!.asString())
        val copy = autoApplyMapper.fromNode(node, AutoApplyAmountEntity::class.java)
        assertEquals(moneyOf("7.00", "EUR"), copy.holder!!.amount)
    }

    @Test
    fun disableConversionOverridesEmbeddedClassConverter() {
        val entity = DisabledOverrideEntity().apply {
            id = 6L
            holder = PrefixedHolder().apply { value = "plain" }
        }

        val node = mapper.toNode(entity)

        assertEquals("plain", node["holder"]!!.asObject()["value"]!!.asString())

        val copy = mapper.fromNode(node, DisabledOverrideEntity::class.java)
        assertEquals("plain", copy.holder!!.value)
    }

    @Test
    fun nullEmbeddedValueAndNullConvertedPropertyStayNull() {
        val node = mapper.toNode(ConvertedProfileEntity().apply { id = 7L })
        assertTrue(node["address"]!!.isNull)

        val holderNode = mapper.toNode(
            ConvertedProfileEntity().apply {
                id = 8L
                address = Address().apply { city = "x" }
            },
        )
        assertTrue(holderNode["address"]!!.asObject()["zip_code"]!!.isNull)

        val copy = mapper.fromNode(holderNode, ConvertedProfileEntity::class.java)
        assertNull(copy.address!!.zipCode)
    }

    @Test
    fun readsNullNodeThroughAttributeNameConverter() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(9),
            "address" to ObjectNode.of(
                "zip_code" to NullNode.INSTANCE,
                "city" to StringNode.of("x"),
            ),
        )

        val copy = mapper.fromNode(node, ConvertedProfileEntity::class.java)

        assertNull(copy.address!!.zipCode)
    }

    @Test
    fun rejectsUnknownAttributePath() {
        val exception = assertFailsWith<JpaConfigException> { mapper.toNode(InvalidConvertPathEntity()) }

        assertTrue(exception.message!!.contains("missingField"), "message was ${exception.message}")
    }

    @Test
    fun rejectsPathThroughNonEmbeddedProperty() {
        val exception = assertFailsWith<JpaConfigException> {
            mapper.toNode(InvalidConvertPathThroughScalarEntity())
        }

        assertTrue(exception.message!!.contains("not an @Embedded"), "message was ${exception.message}")
    }

    @Test
    fun rejectsConverterTypeMismatchOnPath() {
        val exception = assertFailsWith<JpaConfigException> { mapper.toNode(MismatchedConvertPathEntity()) }

        assertTrue(exception.message!!.contains("declared as"), "message was ${exception.message}")
    }

    @Test
    fun rejectsAttributeNameOnScalarProperty() {
        val exception = assertFailsWith<JpaConfigException> { mapper.toNode(AttributeNameOnScalarEntity()) }

        assertTrue(exception.message!!.contains("attributeName"), "message was ${exception.message}")
    }

    @Test
    fun supportsExplicitMoneyConverterInEmbeddedClass() {
        val entity = AmountHolder().apply { amount = Money(BigDecimal("1.25"), "CNY") }

        // 直接映射嵌入类本身（无 entity 包装）也应可用。
        val node = mapper.toNode(
            EmbeddedHolderEntity().apply {
                id = 10L
                holder = entity
            },
        )

        assertEquals("1.25 CNY", node["holder"]!!.asObject()["amount"]!!.asString())
    }
}
