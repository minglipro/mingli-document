package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.exception.JpaConfigException
import com.mingliqiye.document.jpa.exception.JpaMappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `@MapKeyJoinColumn`：实体作为 Map 键时的标识符文本映射测试。 */
class JpaMapEntityKeyTest {

    private val mapper = JpaDocumentMapper()

    @Test
    fun writesSingleIdEntityKeysAsIdentifierText() {
        val entity = EntityKeyMapEntity().apply {
            id = 1L
            employees = linkedMapOf(
                employee(5L) to department(50L, "core"),
                employee(6L) to department(60L, "ops"),
            )
        }

        val node = mapper.toNode(entity)
        val employees = node["employees"]!!.asObject()

        assertEquals(listOf("5", "6"), employees.keys.toList())
        assertEquals(50L, employees["5"]!!.asObject()["id"]!!.asNumber().toLong())
        assertEquals("core", employees["5"]!!.asObject()["name"]!!.asString())
    }

    @Test
    fun roundTripsSingleIdEntityKeys() {
        val entity = EntityKeyMapEntity().apply {
            id = 2L
            employees = linkedMapOf(employee(7L) to department(70L, "core"))
        }

        val copy = mapper.fromNode(mapper.toNode(entity), EntityKeyMapEntity::class.java)
        val key = copy.employees.keys.single()

        assertEquals(7L, key.id)
        assertEquals("core", copy.employees[key]!!.name)
    }

    @Test
    fun writesEmbeddedIdEntityKeysAsCanonicalText() {
        val entity = EntityKeyMapEntity().apply {
            id = 3L
            orders = linkedMapOf(
                order("A-1", "EU") to department(80L, "core"),
            )
        }

        val node = mapper.toNode(entity)

        assertEquals(setOf("orderNo=A-1&region=EU"), node["orders"]!!.asObject().keys)
    }

    @Test
    fun roundTripsEmbeddedIdEntityKeysWithEscaping() {
        val entity = EntityKeyMapEntity().apply {
            id = 4L
            orders = linkedMapOf(
                order("a&b=c", "r%1") to department(90L, "ops"),
            )
        }

        val node = mapper.toNode(entity)
        val keyText = node["orders"]!!.asObject().keys.single()

        assertEquals("orderNo=a%26b%3Dc&region=r%251", keyText)

        val copy = mapper.fromNode(node, EntityKeyMapEntity::class.java)
        val key = copy.orders.keys.single()
        assertEquals("a&b=c", key.key!!.orderNo)
        assertEquals("r%1", key.key!!.region)
        assertEquals("ops", copy.orders[key]!!.name)
    }

    @Test
    fun writesAndRoundTripsIdClassEntityKeys() {
        val entity = IdClassKeyMapEntity().apply {
            id = 10L
            books = linkedMapOf(
                book(1, 2) to department(100L, "core"),
                book(3, 4) to department(200L, "ops"),
            )
        }

        val node = mapper.toNode(entity)
        val books = node["books"]!!.asObject()

        assertEquals(listOf("shelf=1&slot=2", "shelf=3&slot=4"), books.keys.toList())
        assertEquals("core", books["shelf=1&slot=2"]!!.asObject()["name"]!!.asString())

        val copy = mapper.fromNode(node, IdClassKeyMapEntity::class.java)
        val firstKey = copy.books.keys.first { it.shelf == 1 }
        assertEquals(2, firstKey.slot)
        val secondKey = copy.books.keys.first { it.shelf == 3 }
        assertEquals("ops", copy.books[secondKey]!!.name)
    }

    @Test
    fun exposesIdClassEntityKeyText() {
        assertEquals("shelf=1&slot=2", mapper.entityKeyText(book(1, 2), KeyedBookEntity::class.java))

        val restored = mapper.entityKeyFromText("shelf=5&slot=6", KeyedBookEntity::class.java)
        assertEquals(5, (restored as KeyedBookEntity).shelf)
        assertEquals(6, restored.slot)
    }

    @Test
    fun rejectsInvalidSingleIdKeyText() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(5),
            "employees" to ObjectNode.of(
                "abc" to ObjectNode.of("id" to NumberNode.of(50)),
            ),
        )

        val exception = assertFailsWith<JpaMappingException> {
            mapper.fromNode(node, EntityKeyMapEntity::class.java)
        }

        assertTrue(exception.message!!.contains("not a valid"), "message was ${exception.message}")
    }

    @Test
    fun rejectsCollidingEntityKeysAfterNormalization() {
        val departmentNode = ObjectNode.of("id" to NumberNode.of(50))
        val node = ObjectNode.of(
            "id" to NumberNode.of(6),
            "employees" to ObjectNode.of(
                "01" to departmentNode,
                "1" to departmentNode,
            ),
        )

        val exception = assertFailsWith<JpaMappingException> {
            mapper.fromNode(node, EntityKeyMapEntity::class.java)
        }

        assertTrue(exception.message!!.contains("collision"), "message was ${exception.message}")
    }

    @Test
    fun rejectsMissingCompositeKeyComponent() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(7),
            "orders" to ObjectNode.of(
                "orderNo=A-1" to ObjectNode.of("id" to NumberNode.of(60)),
            ),
        )

        val exception = assertFailsWith<JpaMappingException> {
            mapper.fromNode(node, EntityKeyMapEntity::class.java)
        }

        assertTrue(exception.message!!.contains("missing components"), "message was ${exception.message}")
    }

    @Test
    fun rejectsUnknownCompositeKeyComponent() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(8),
            "orders" to ObjectNode.of(
                "orderNo=A-1&unknown=1" to ObjectNode.of("id" to NumberNode.of(60)),
            ),
        )

        val exception = assertFailsWith<JpaMappingException> {
            mapper.fromNode(node, EntityKeyMapEntity::class.java)
        }

        assertTrue(exception.message!!.contains("unknown component"), "message was ${exception.message}")
    }

    @Test
    fun rejectsMapKeyJoinColumnOnBasicKey() {
        val exception = assertFailsWith<JpaConfigException> { mapper.toNode(MapKeyJoinColumnOnBasicKeyEntity()) }

        assertTrue(exception.message!!.contains("@MapKeyJoinColumn"), "message was ${exception.message}")
    }

    @Test
    fun exposesEntityKeyTextHelpers() {
        assertEquals("5", mapper.entityKeyText(employee(5L), EmployeeEntity::class.java))

        val employee = mapper.entityKeyFromText("9", EmployeeEntity::class.java)
        assertEquals(9L, (employee as EmployeeEntity).id)

        val order = order("A-1", "EU")
        assertEquals(
            "orderNo=A-1&region=EU",
            mapper.entityKeyText(order, OrderEntity::class.java),
        )
        val restored = mapper.entityKeyFromText("orderNo=A-1&region=EU", OrderEntity::class.java)
        assertEquals("A-1", (restored as OrderEntity).key!!.orderNo)
        assertEquals("EU", restored.key!!.region)
    }

    @Test
    fun readsEntityKeyMapFromStringNodeValues() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(9),
            "employees" to ObjectNode.of(
                "11" to StringNode.of("not-an-object"),
            ),
        )

        val exception = assertFailsWith<JpaMappingException> {
            mapper.fromNode(node, EntityKeyMapEntity::class.java)
        }

        assertTrue(exception.message!!.contains("Expected object node"), "message was ${exception.message}")
    }

    private fun employee(id: Long): EmployeeEntity = EmployeeEntity().apply {
        this.id = id
    }

    private fun department(id: Long, name: String): DepartmentEntity = DepartmentEntity().apply {
        this.id = id
        this.name = name
    }

    private fun book(shelf: Int, slot: Int): KeyedBookEntity = KeyedBookEntity().apply {
        this.shelf = shelf
        this.slot = slot
    }

    private fun order(orderNo: String, region: String): OrderEntity = OrderEntity().apply {
        key = OrderKey().apply {
            this.orderNo = orderNo
            this.region = region
        }
    }
}
