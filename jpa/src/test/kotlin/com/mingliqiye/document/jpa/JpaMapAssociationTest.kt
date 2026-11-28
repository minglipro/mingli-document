package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.exception.JpaConfigException
import com.mingliqiye.document.jpa.exception.JpaMappingException
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Map 关联 / Map `@ElementCollection` 映射测试。 */
class JpaMapAssociationTest {

    private val mapper = JpaDocumentMapper()

    @Test
    fun roundTripsStringKeyEntityMap() {
        val entity = MapAssociationEntity().apply {
            id = 1L
            name = "core"
            membersByName = linkedMapOf(
                "alice" to employee(1L, "alice"),
                "bob" to employee(2L, "bob"),
            )
        }

        val node = mapper.toNode(entity)
        val members = node["membersByName"]!!.asObject()

        assertEquals(listOf("alice", "bob"), members.keys.toList())
        assertEquals(1L, members["alice"]!!.asObject()["id"]!!.asNumber().toLong())
        assertEquals("bob", members["bob"]!!.asObject()["name"]!!.asString())

        val copy = mapper.fromNode(node, MapAssociationEntity::class.java)
        assertEquals(listOf("alice", "bob"), copy.membersByName.keys.toList())
        assertEquals(1L, copy.membersByName["alice"]!!.id)
        assertEquals("bob", copy.membersByName["bob"]!!.name)
    }

    @Test
    fun roundTripsLongKeyEntityMap() {
        val entity = MapAssociationEntity().apply {
            id = 2L
            membersById = linkedMapOf(
                10L to employee(10L, "carol"),
                20L to employee(20L, "dave"),
            )
        }

        val node = mapper.toNode(entity)

        assertEquals(setOf("10", "20"), node["membersById"]!!.asObject().keys)

        val copy = mapper.fromNode(node, MapAssociationEntity::class.java)
        assertEquals(setOf(10L, 20L), copy.membersById.keys)
        assertEquals("carol", copy.membersById[10L]!!.name)
    }

    @Test
    fun roundTripsMapKeyNameEntityMap() {
        val entity = MapKeyNameEntity().apply {
            id = 3L
            mentorsByName = linkedMapOf(
                "alice" to employee(1L, "alice"),
                "bob" to employee(2L, "bob"),
            )
        }

        val node = mapper.toNode(entity)
        val mentors = node["mentorsByName"]!!.asObject()

        assertEquals(setOf("alice", "bob"), mentors.keys)
        assertEquals("alice", mentors["alice"]!!.asObject()["name"]!!.asString())

        val copy = mapper.fromNode(node, MapKeyNameEntity::class.java)
        assertEquals(setOf("alice", "bob"), copy.mentorsByName.keys)
        assertEquals("bob", copy.mentorsByName["bob"]!!.name)
    }

    @Test
    fun resolvesDeferredMapKeyTypeFromPrimaryKey() {
        val entity = MapKeyNameEntity().apply {
            id = 4L
            byPrimaryKey = linkedMapOf<Any, EmployeeEntity>(
                7L to employee(7L, "seven"),
                8L to employee(8L, "eight"),
            )
        }

        val node = mapper.toNode(entity)

        assertEquals(setOf("7", "8"), node["byPrimaryKey"]!!.asObject().keys)

        val copy = mapper.fromNode(node, MapKeyNameEntity::class.java)
        assertEquals(setOf<Any>(7L, 8L), copy.byPrimaryKey.keys)
        assertEquals("eight", copy.byPrimaryKey[8L]!!.name)
    }

    @Test
    fun emptyMapBecomesEmptyObject() {
        val node = mapper.toNode(MapAssociationEntity().apply { id = 5L })

        assertTrue(node["membersByName"]!!.isObject)
        assertEquals(0, node["membersByName"]!!.asObject().size)
    }

    @Test
    fun appliesAssociationDepthToMapValues() {
        val root = MapNodeEntity().apply {
            id = 1L
            name = "root"
        }
        root.children["child"] = MapNodeEntity().apply {
            id = 2L
            name = "child"
            children["grand"] = MapNodeEntity().apply {
                id = 3L
                name = "grand"
            }
        }

        val shallow = mapper.toNode(root)
        val child = shallow["children"]!!.asObject()["child"]!!.asObject()
        assertEquals("child", child["name"]!!.asString())
        assertFalse(child.containsKey("children"))

        val deep = JpaDocumentMapper(JpaConfig.builder().associationDepth(2).build()).toNode(root)
        val deepChild = deep["children"]!!.asObject()["child"]!!.asObject()
        assertTrue(deepChild["children"]!!.asObject().containsKey("grand"))
    }

    @Test
    fun detectsCyclesThroughMapAssociations() {
        val deep = JpaDocumentMapper(JpaConfig.builder().associationDepth(2).build())
        val root = MapNodeEntity().apply {
            id = 1L
            name = "root"
        }
        val child = MapNodeEntity().apply {
            id = 2L
            name = "child"
        }
        root.children["child"] = child
        child.children["root"] = root

        val exception = assertFailsWith<JpaMappingException> { deep.toNode(root) }

        assertTrue(exception.message!!.contains("Circular reference"), "message was ${exception.message}")
    }

    @Test
    fun rejectsCollidingKeysOnRead() {
        val employeeNode = employeeNode(1L, "alice")
        val node = ObjectNode.of(
            "id" to NumberNode.of(6),
            "membersById" to ObjectNode.of(
                "01" to employeeNode,
                "1" to employeeNode,
            ),
        )

        val exception = assertFailsWith<JpaMappingException> {
            mapper.fromNode(node, MapAssociationEntity::class.java)
        }

        assertTrue(exception.message!!.contains("Map key collision"), "message was ${exception.message}")
    }

    @Test
    fun rejectsInvalidKeyTextOnRead() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(7),
            "membersById" to ObjectNode.of("abc" to employeeNode(1L, "alice")),
        )

        val exception = assertFailsWith<JpaMappingException> {
            mapper.fromNode(node, MapAssociationEntity::class.java)
        }

        assertTrue(exception.message!!.contains("not a valid"), "message was ${exception.message}")
    }

    @Test
    fun roundTripsMapElementCollections() {
        val date = Date.from(LocalDate.of(2024, 1, 31).atStartOfDay(ZoneId.systemDefault()).toInstant())
        val entity = MapElementCollectionEntity().apply {
            id = 8L
            attributes = linkedMapOf("color" to "red", "size" to "XL")
            scores = linkedMapOf(1L to 10, 2L to 20)
            addresses = linkedMapOf(
                "home" to Address().apply {
                    city = "shanghai"
                    zipCode = "200000"
                },
            )
            statusNotes = linkedMapOf(
                AccountStatus.ACTIVE to "active note",
                AccountStatus.SUSPENDED to "suspended note",
            )
            statusOrdinals = linkedMapOf(AccountStatus.ACTIVE to 1, AccountStatus.SUSPENDED to 2)
            dayNotes = linkedMapOf(date to "meeting")
        }

        val node = mapper.toNode(entity)

        assertEquals(setOf("color", "size"), node["attributes"]!!.asObject().keys)
        assertEquals("red", node["attributes"]!!.asObject()["color"]!!.asString())
        assertEquals(setOf("1", "2"), node["scores"]!!.asObject().keys)
        assertEquals("shanghai", node["addresses"]!!.asObject()["home"]!!.asObject()["city"]!!.asString())
        assertEquals(setOf("ACTIVE", "SUSPENDED"), node["statusNotes"]!!.asObject().keys)
        assertEquals(setOf("0", "1"), node["statusOrdinals"]!!.asObject().keys)
        assertEquals("2024-01-31", node["dayNotes"]!!.asObject().keys.single())

        val copy = mapper.fromNode(node, MapElementCollectionEntity::class.java)
        assertEquals(entity.attributes, copy.attributes)
        assertEquals(entity.scores, copy.scores)
        assertEquals("shanghai", copy.addresses["home"]!!.city)
        assertEquals("suspended note", copy.statusNotes[AccountStatus.SUSPENDED])
        assertEquals(2, copy.statusOrdinals[AccountStatus.SUSPENDED])
        assertEquals(date, copy.dayNotes.keys.single())
    }

    @Test
    fun rejectsUnsupportedMapKeyType() {
        val exception = assertFailsWith<JpaConfigException> { mapper.toNode(BadMapKeyEntity()) }

        assertTrue(exception.message!!.contains("Unsupported Map key type"), "message was ${exception.message}")
    }

    @Test
    fun rejectsMapKeyAnnotationOnNonMapProperty() {
        val exception = assertFailsWith<JpaConfigException> { mapper.toNode(BadMapKeyAnnotationEntity()) }

        assertTrue(exception.message!!.contains("only supported on Map"), "message was ${exception.message}")
    }

    @Test
    fun readsNullAssociationValueAsNullElement() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(9),
            "membersByName" to ObjectNode.of(
                "alice" to NullNode.INSTANCE,
            ),
        )

        val copy = mapper.fromNode(node, MapAssociationEntity::class.java)

        assertEquals(1, copy.membersByName.size)
        assertTrue(copy.membersByName.containsKey("alice"))
        assertEquals(null, copy.membersByName["alice"])
    }

    private fun employee(id: Long, name: String): EmployeeEntity = EmployeeEntity().apply {
        this.id = id
        this.name = name
    }

    private fun employeeNode(id: Long, name: String): ObjectNode = ObjectNode.of(
        "id" to NumberNode.of(id),
        "name" to StringNode.of(name),
    )
}
