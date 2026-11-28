package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.exception.JpaMappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JpaAssociationTest {

    private val mapper = JpaDocumentMapper()

    @Test
    fun serializesCollectionAssociationAtFirstDepth() {
        val department = departmentFixture()

        val node = mapper.toNode(department)
        val employees = node["employees"]!!.asArray()

        assertEquals(2, employees.size)
        assertEquals(1L, employees[0].asObject()["id"]!!.asNumber().toLong())
        assertEquals("alice", employees[0].asObject()["name"]!!.asString())
        assertFalse(employees[0].asObject().containsKey("department"))
        assertFalse(employees[0].asObject().containsKey("mentor"))
    }

    @Test
    fun roundTripsCollectionAssociation() {
        val department = departmentFixture()
        val copy = mapper.fromNode(mapper.toNode(department), DepartmentEntity::class.java)

        assertEquals("engineering", copy.name)
        assertEquals(listOf(1L, 2L), copy.employees.map { it.id })
        assertEquals(listOf("alice", "bob"), copy.employees.map { it.name })
        assertNull(copy.employees[0].department)
    }

    @Test
    fun serializesSingleValuedAssociations() {
        val employee = EmployeeEntity().apply {
            id = 1L
            name = "alice"
            mentor = EmployeeEntity().apply {
                id = 2L
                name = "bob"
            }
        }

        val node = mapper.toNode(employee)
        val mentor = node["mentor"]!!.asObject()

        assertEquals(2L, mentor["id"]!!.asNumber().toLong())
        assertEquals("bob", mentor["name"]!!.asString())
        assertTrue(node["department"]!!.isNull)

        val copy = mapper.fromNode(node, EmployeeEntity::class.java)
        assertEquals("bob", copy.mentor!!.name)
    }

    @Test
    fun expandsNestedAssociationsUpToConfiguredDepth() {
        val deep = JpaDocumentMapper(JpaConfig.builder().associationDepth(2).build())
        val employee = EmployeeEntity().apply {
            id = 1L
            name = "alice"
            department = DepartmentEntity().apply {
                id = 9L
                name = "engineering"
            }
        }

        val node = deep.toNode(employee)
        val department = node["department"]!!.asObject()

        assertEquals("engineering", department["name"]!!.asString())
        assertTrue(department.containsKey("employees"))
        assertEquals(0, department["employees"]!!.asArray().size)

        val copy = deep.fromNode(node, EmployeeEntity::class.java)
        assertEquals("engineering", copy.department!!.name)
        assertEquals(0, copy.department!!.employees.size)
    }

    @Test
    fun respectsAssociationDepthLimit() {
        val chain = TreeNodeEntity().apply {
            id = 1L
            name = "a"
            parent = TreeNodeEntity().apply {
                id = 2L
                name = "b"
                parent = TreeNodeEntity().apply {
                    id = 3L
                    name = "c"
                }
            }
        }

        val shallow = mapper.toNode(chain)
        val level1 = shallow["parent"]!!.asObject()
        assertEquals("b", level1["name"]!!.asString())
        assertFalse(level1.containsKey("parent"))

        val deeper = JpaDocumentMapper(JpaConfig.builder().associationDepth(2).build()).toNode(chain)
        val level1Deeper = deeper["parent"]!!.asObject()
        assertEquals("c", level1Deeper["parent"]!!.asObject()["name"]!!.asString())
        assertFalse(level1Deeper["parent"]!!.asObject().containsKey("parent"))
    }

    @Test
    fun zeroDepthOmitsAssociations() {
        val noAssociations = JpaDocumentMapper(JpaConfig.builder().associationDepth(0).build())
        val employee = EmployeeEntity().apply {
            id = 1L
            department = departmentFixture()
            mentor = EmployeeEntity().apply { id = 2L }
        }

        val node = noAssociations.toNode(employee)

        assertFalse(node.containsKey("department"))
        assertFalse(node.containsKey("mentor"))
    }

    @Test
    fun skipsAssociationContentWhenDepthIsExhaustedOnRead() {
        val noAssociations = JpaDocumentMapper(JpaConfig.builder().associationDepth(0).build())
        val node = ObjectNode.of(
            "id" to NumberNode.of(1),
            "department" to ObjectNode.of("name" to StringNode.of("engineering")),
        )

        val employee = noAssociations.fromNode(node, EmployeeEntity::class.java)

        assertEquals(1L, employee.id)
        assertNull(employee.department)
    }

    @Test
    fun rejectsNullForRequiredAssociation() {
        val node = ObjectNode.of("mentor" to NullNode.INSTANCE)

        assertFailsWith<JpaMappingException> {
            mapper.fromNode(node, RequiredMentorEntity::class.java)
        }
    }

    @Test
    fun detectsSelfReferenceCycle() {
        val node = TreeNodeEntity().apply {
            id = 1L
            parent = this
        }
        val deep = JpaDocumentMapper(JpaConfig.builder().associationDepth(2).build())

        val exception = assertFailsWith<JpaMappingException> { deep.toNode(node) }
        assertTrue(exception.message!!.contains("Circular"))
    }

    @Test
    fun detectsTwoEntityCycle() {
        val first = TreeNodeEntity().apply { id = 1L }
        val second = TreeNodeEntity().apply { id = 2L }
        first.parent = second
        second.parent = first
        val deep = JpaDocumentMapper(JpaConfig.builder().associationDepth(3).build())

        val exception = assertFailsWith<JpaMappingException> { deep.toNode(first) }
        assertTrue(exception.message!!.contains("Circular"))
    }

    @Test
    fun mapsElementCollections() {
        val tagged = TaggedEntity().apply {
            id = 1L
            tags = mutableListOf("x", "y")
            statuses = mutableListOf(AccountStatus.ACTIVE, AccountStatus.SUSPENDED)
        }

        val node = mapper.toNode(tagged)

        assertEquals(2, node["tags"]!!.asArray().size)
        assertEquals("x", node["tags"]!!.asArray()[0].asString())
        assertEquals("SUSPENDED", node["statuses"]!!.asArray()[1].asString())

        val copy = mapper.fromNode(node, TaggedEntity::class.java)
        assertEquals(listOf("x", "y"), copy.tags)
        assertEquals(listOf(AccountStatus.ACTIVE, AccountStatus.SUSPENDED), copy.statuses)
    }

    @Test
    fun rejectsInvalidElementCollectionContent() {
        assertFailsWith<JpaMappingException> {
            mapper.fromNode(ObjectNode.of("tags" to StringNode.of("x")), TaggedEntity::class.java)
        }
        assertFailsWith<JpaMappingException> {
            mapper.fromNode(
                ObjectNode.of("statuses" to ArrayNode.of(StringNode.of("NOPE"))),
                TaggedEntity::class.java,
            )
        }
    }

    @Test
    fun mapsPlainCollectionAndMapProperties() {
        val loose = LooseEntity().apply {
            id = 1L
            labels = mutableListOf("a", "b")
            numbers = mutableListOf(1L, 2L)
            attributes = linkedMapOf("k" to "v")
            counters = linkedMapOf("hits" to 3)
        }

        val node = mapper.toNode(loose)

        assertEquals("b", node["labels"]!!.asArray()[1].asString())
        assertEquals(2L, node["numbers"]!!.asArray()[1].asNumber().toLong())
        assertEquals("v", node["attributes"]!!.asObject()["k"]!!.asString())
        assertEquals(3, node["counters"]!!.asObject()["hits"]!!.asNumber().toInt())

        val copy = mapper.fromNode(node, LooseEntity::class.java)
        assertEquals(listOf("a", "b"), copy.labels)
        assertEquals(listOf(1L, 2L), copy.numbers)
        assertEquals(mapOf("k" to "v"), copy.attributes)
        assertEquals(mapOf("hits" to 3), copy.counters)
    }

    private fun departmentFixture(): DepartmentEntity {
        val department = DepartmentEntity().apply {
            id = 10L
            name = "engineering"
        }
        department.employees = mutableListOf(
            EmployeeEntity().apply {
                id = 1L
                name = "alice"
                this.department = department
            },
            EmployeeEntity().apply {
                id = 2L
                name = "bob"
                this.department = department
            },
        )
        return department
    }
}
