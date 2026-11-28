package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.exception.DocumentException
import com.mingliqiye.document.jpa.exception.JpaConfigException
import com.mingliqiye.document.jpa.exception.JpaException
import com.mingliqiye.document.jpa.exception.JpaMappingException
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JpaDocumentMapperTest {

    private val mapper = JpaDocumentMapper()

    @Test
    fun mapsScalarPropertiesAndColumnNames() {
        val account = AccountEntity().apply {
            id = 7L
            userName = "alice"
            displayName = "Alice"
            age = 30
            balance = BigDecimal("12.50")
            active = true
            status = AccountStatus.SUSPENDED
            statusOrdinal = AccountStatus.ACTIVE
        }

        val node = mapper.toNode(account)

        assertEquals(7L, node["id"]!!.asNumber().toLong())
        assertEquals("alice", node["user_name"]!!.asString())
        assertEquals("Alice", node["displayName"]!!.asString())
        assertEquals(30, node["age"]!!.asNumber().toInt())
        assertEquals(0, BigDecimal("12.50").compareTo(node["balance"]!!.asNumber().value))
        assertTrue(node["active"]!!.asBoolean())
        assertEquals("SUSPENDED", node["status"]!!.asString())
        assertEquals(AccountStatus.ACTIVE.ordinal, node["statusOrdinal"]!!.asNumber().toInt())
    }

    @Test
    fun roundTripsAllSupportedScalarTypes() {
        val account = accountFixture()
        val copy = mapper.fromNode(mapper.toNode(account), AccountEntity::class.java)

        assertEquals(account.id, copy.id)
        assertEquals(account.userName, copy.userName)
        assertEquals(account.displayName, copy.displayName)
        assertEquals(account.age, copy.age)
        assertEquals(0, account.balance!!.compareTo(copy.balance!!))
        assertEquals(account.active, copy.active)
        assertEquals(account.status, copy.status)
        assertEquals(account.statusOrdinal, copy.statusOrdinal)
        assertEquals(account.registeredOn, copy.registeredOn)
        assertEquals(account.registeredTime, copy.registeredTime)
        assertEquals(account.registeredAt, copy.registeredAt)
        assertEquals(account.localDate, copy.localDate)
        assertEquals(account.localDateTime, copy.localDateTime)
        assertEquals(account.instant, copy.instant)
        assertEquals(account.uuid, copy.uuid)
    }

    @Test
    fun formatsTemporalAndJavaTimeValues() {
        val node = mapper.toNode(accountFixture())

        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").matches(node["registeredOn"]!!.asString()))
        assertEquals("13:45:30", node["registeredTime"]!!.asString())
        assertTrue(node["registeredAt"]!!.asString().contains("T"))
        assertEquals("2024-01-31", node["localDate"]!!.asString())
        assertEquals(LocalDateTime.of(2024, 1, 31, 12, 0).toString(), node["localDateTime"]!!.asString())
        assertEquals("2024-01-31T12:00:00Z", node["instant"]!!.asString())
        assertEquals("3f2504e0-4f89-11d3-9a0c-0305e82c3301", node["uuid"]!!.asString())
    }

    @Test
    fun includesNullsByDefault() {
        val node = mapper.toNode(AccountEntity().apply { id = 1L })

        assertTrue(node.containsKey("status"))
        assertTrue(node["status"]!!.isNull)
        assertTrue(node["balance"]!!.isNull)
    }

    @Test
    fun omitsNullsWhenConfigured() {
        val omitting = JpaDocumentMapper(JpaConfig.builder().nullPolicy(JpaNullPolicy.OMIT).build())
        val node = omitting.toNode(AccountEntity().apply { id = 1L })

        assertFalse(node.containsKey("status"))
        assertFalse(node.containsKey("balance"))
        assertEquals(1L, node["id"]!!.asNumber().toLong())
    }

    @Test
    fun readsNullNodeIntoNullableProperty() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(3),
            "status" to NullNode.INSTANCE,
        )

        val account = mapper.fromNode(node, AccountEntity::class.java)

        assertEquals(3L, account.id)
        assertNull(account.status)
    }

    @Test
    fun excludesTransientPropertiesByDefault() {
        val node = mapper.toNode(AccountEntity())

        assertFalse(node.containsKey("cachedToken"))
        assertFalse(node.containsKey("computedToken"))
        assertFalse(node.containsKey("cachedTags"))
    }

    @Test
    fun includesTransientPropertiesWhenConfigured() {
        val including = JpaDocumentMapper(JpaConfig.builder().includeTransient(true).build())
        val account = AccountEntity().apply {
            id = 5L
            userName = "alice"
            cachedToken = "token"
            cachedTags = mutableListOf("x", "y")
        }

        val node = including.toNode(account)

        assertEquals("token", node["cachedToken"]!!.asString())
        assertEquals("computed", node["computedToken"]!!.asString())
        assertEquals(2, node["cachedTags"]!!.asArray().size)
        assertTrue(including.fromNode(node, AccountEntity::class.java).cachedToken == "token")
        assertEquals(listOf("x", "y"), including.fromNode(node, AccountEntity::class.java).cachedTags)
    }

    @Test
    fun transientPropertiesAreUnknownWhenNotIncluded() {
        val node = ObjectNode.of("cachedToken" to StringNode.of("token"))

        assertFailsWith<JpaMappingException> { mapper.fromNode(node, AccountEntity::class.java) }
    }

    @Test
    fun readsPropertyNameAndColumnNameAliases() {
        val byPropertyName = ObjectNode.of(
            "id" to NumberNode.of(2),
            "userName" to StringNode.of("bob"),
        )
        val byColumnName = ObjectNode.of(
            "id" to NumberNode.of(3),
            "user_name" to StringNode.of("carol"),
        )

        assertEquals("bob", mapper.fromNode(byPropertyName, AccountEntity::class.java).userName)
        assertEquals("carol", mapper.fromNode(byColumnName, AccountEntity::class.java).userName)
    }

    @Test
    fun failsOnUnknownPropertyByDefault() {
        val node = ObjectNode.of("id" to NumberNode.of(1), "mystery" to StringNode.of("?"))

        assertFailsWith<JpaMappingException> { mapper.fromNode(node, AccountEntity::class.java) }
    }

    @Test
    fun ignoresUnknownPropertyWhenConfigured() {
        val lenient = JpaDocumentMapper(
            JpaConfig.builder().unknownPropertyPolicy(JpaUnknownPropertyPolicy.IGNORE).build(),
        )
        val node = ObjectNode.of("id" to NumberNode.of(1), "mystery" to StringNode.of("?"))

        assertEquals(1L, lenient.fromNode(node, AccountEntity::class.java).id)
    }

    @Test
    fun rejectsNullForNonNullableColumn() {
        val node = ObjectNode.of("user_name" to NullNode.INSTANCE)

        assertFailsWith<JpaMappingException> { mapper.fromNode(node, AccountEntity::class.java) }
    }

    @Test
    fun rejectsNullForPrimitiveProperty() {
        val node = ObjectNode.of("age" to NullNode.INSTANCE)

        assertFailsWith<JpaMappingException> { mapper.fromNode(node, AccountEntity::class.java) }
    }

    @Test
    fun rejectsInvalidEnumValues() {
        assertFailsWith<JpaMappingException> {
            mapper.fromNode(ObjectNode.of("status" to StringNode.of("NOPE")), AccountEntity::class.java)
        }
        assertFailsWith<JpaMappingException> {
            mapper.fromNode(ObjectNode.of("statusOrdinal" to NumberNode.of(9)), AccountEntity::class.java)
        }
    }

    @Test
    fun missingPropertiesKeepConstructorDefaults() {
        val account = mapper.fromNode(ObjectNode.EMPTY, AccountEntity::class.java)

        assertNull(account.id)
        assertEquals(0, account.age)
        assertTrue(account.cachedTags.isNotEmpty())
    }

    @Test
    fun mapsEmbeddedObject() {
        val customer = CustomerEntity().apply {
            id = 1L
            address = Address().apply {
                city = "Shanghai"
                zipCode = "200000"
            }
        }

        val node = mapper.toNode(customer)
        val address = node["address"]!!.asObject()

        assertEquals("Shanghai", address["city"]!!.asString())
        assertEquals("200000", address["zip_code"]!!.asString())

        val copy = mapper.fromNode(node, CustomerEntity::class.java)
        assertEquals("Shanghai", copy.address!!.city)
        assertEquals("200000", copy.address!!.zipCode)
    }

    @Test
    fun mapsEmbeddedId() {
        val order = OrderEntity().apply {
            key = OrderKey().apply {
                orderNo = "A-1"
                region = "cn"
            }
            total = BigDecimal("9.99")
        }

        val node = mapper.toNode(order)
        val key = node["key"]!!.asObject()

        assertEquals("A-1", key["order_no"]!!.asString())
        assertEquals("cn", key["region"]!!.asString())

        val copy = mapper.fromNode(node, OrderEntity::class.java)
        assertEquals("A-1", copy.key!!.orderNo)
        assertEquals("cn", copy.key!!.region)
        assertEquals(0, order.total!!.compareTo(copy.total!!))
    }

    @Test
    fun mapsMappedSuperclassPropertiesFirst() {
        val audit = AuditEntity().apply {
            id = 9L
            createdAt = Date.from(Instant.parse("2024-02-01T00:00:00Z"))
            auditNote = "transient"
            message = "hello"
        }

        val node = mapper.toNode(audit)

        assertEquals(listOf("id", "created_at", "message"), node.keys.toList())
        val copy = mapper.fromNode(node, AuditEntity::class.java)
        assertEquals(9L, copy.id)
        assertEquals("hello", copy.message)
    }

    @Test
    fun appliesNamingStrategyToOutputAndAcceptsAliasesOnInput() {
        val snake = JpaDocumentMapper(
            JpaConfig.builder().namingStrategy(PropertyNamingStrategy.SNAKE_CASE).build(),
        )
        val node = snake.toNode(AccountEntity().apply { displayName = "Alice" })

        assertEquals("Alice", node["display_name"]!!.asString())
        assertEquals(
            "Alice",
            snake.fromNode(ObjectNode.of("displayName" to StringNode.of("Alice")), AccountEntity::class.java).displayName,
        )
    }

    @Test
    fun exposesEntityMetadata() {
        assertEquals("accounts", mapper.tableName(AccountEntity::class.java))
        assertEquals("AccountEntity", mapper.entityName(AccountEntity::class.java))
        assertEquals("custom_account", mapper.entityName(NamedEntity::class.java))
        assertNull(mapper.tableName(NamedEntity::class.java))
        assertNull(mapper.tableName(PlainPojo::class.java))
        assertTrue(mapper.isEntity(AccountEntity::class.java))
        assertTrue(mapper.isEntity(AuditEntity::class.java))
        assertFalse(mapper.isEntity(PlainPojo::class.java))
    }

    @Test
    fun mapsCollectionsOfEntities() {
        val accounts = listOf(
            AccountEntity().apply {
                id = 1L
                userName = "alice"
            },
            AccountEntity().apply {
                id = 2L
                userName = "bob"
            },
        )

        val nodes = mapper.toNodes(accounts)
        val copies = mapper.fromNodes(nodes, AccountEntity::class.java)

        assertEquals(2, nodes.size)
        assertEquals(listOf(1L, 2L), copies.map { it.id })
    }

    @Test
    fun rejectsWrongNodeType() {
        assertFailsWith<JpaMappingException> { mapper.fromNode(StringNode.of("x"), AccountEntity::class.java) }
        assertFailsWith<JpaMappingException> { mapper.fromNodes(StringNode.of("x"), AccountEntity::class.java) }
        assertFailsWith<JpaMappingException> {
            mapper.fromNodes(ObjectNode.EMPTY, AccountEntity::class.java)
        }
    }

    @Test
    fun rejectsMissingNoArgConstructor() {
        assertFailsWith<JpaMappingException> {
            mapper.fromNode(ObjectNode.EMPTY, ConstructorOnlyEntity::class.java)
        }
    }

    @Test
    fun rejectsUnsupportedValueType() {
        val entity = OpaqueEntity().apply { opaque = OpaqueValue() }

        assertFailsWith<JpaMappingException> { mapper.toNode(entity) }
    }

    @Test
    fun exceptionHierarchyExtendsDocumentException() {
        assertTrue(DocumentException::class.java.isAssignableFrom(JpaException::class.java))
        assertTrue(JpaException::class.java.isAssignableFrom(JpaMappingException::class.java))
        assertTrue(DocumentException::class.java.isAssignableFrom(JpaConfigException::class.java))
        assertTrue(mapper.isEntity(PlainPojo::class.java).not())
    }

    private fun accountFixture(): AccountEntity = AccountEntity().apply {
        id = 1L
        userName = "alice"
        displayName = "Alice"
        age = 30
        balance = BigDecimal("12.50")
        active = true
        status = AccountStatus.SUSPENDED
        statusOrdinal = AccountStatus.ACTIVE
        registeredOn = java.sql.Date.valueOf("2024-01-31")
        registeredTime = java.sql.Time.valueOf("13:45:30")
        registeredAt = Date.from(Instant.parse("2024-01-31T12:00:00Z"))
        localDate = LocalDate.of(2024, 1, 31)
        localDateTime = LocalDateTime.of(2024, 1, 31, 12, 0)
        instant = Instant.parse("2024-01-31T12:00:00Z")
        uuid = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301")
    }
}
