package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.exception.JpaConfigException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 关系属性上的显式 `@Convert` 测试（整个关联值按基本属性写/读）。 */
class JpaRelationshipConvertTest {

    private val mapper = JpaDocumentMapper()

    @Test
    fun writesAssociationValuesAsConvertedScalars() {
        val entity = ConvertedEmployeeLinkEntity().apply {
            id = 1L
            employee = employee(5L, "alice")
            mentor = employee(6L, "bob")
            team = mutableListOf(employee(7L, "carol"), employee(8L, "dave"))
        }

        val node = mapper.toNode(entity)

        assertEquals(5L, node["employee"]!!.asNumber().toLong())
        assertEquals(6L, node["mentor"]!!.asNumber().toLong())
        assertEquals("7,8", node["team"]!!.asString())
    }

    @Test
    fun relationshipsAreNotExpandedWhenConverted() {
        val entity = ConvertedEmployeeLinkEntity().apply {
            id = 2L
            employee = employee(5L, "alice")
        }

        val node = mapper.toNode(entity)

        assertTrue(node["employee"]!!.isNumber)
        assertEquals(5L, node["employee"]!!.asNumber().toLong())
    }

    @Test
    fun roundTripsConvertedRelationships() {
        val entity = ConvertedEmployeeLinkEntity().apply {
            id = 3L
            employee = employee(9L, "alice")
            team = mutableListOf(employee(10L, "bob"), employee(11L, "carol"))
        }

        val copy = mapper.fromNode(mapper.toNode(entity), ConvertedEmployeeLinkEntity::class.java)

        assertEquals(9L, copy.employee!!.id)
        assertEquals(listOf(10L, 11L), copy.team.map { it.id })
        assertNull(copy.mentor)
    }

    @Test
    fun readsConvertedRelationshipFromScalarNode() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(4),
            "employee" to NumberNode.of(12),
            "team" to StringNode.of("13,14"),
        )

        val copy = mapper.fromNode(node, ConvertedEmployeeLinkEntity::class.java)

        assertEquals(12L, copy.employee!!.id)
        assertEquals(listOf(13L, 14L), copy.team.map { it.id })
    }

    @Test
    fun appliesConverterForNullRelationship() {
        val node = mapper.toNode(
            ConvertedEmployeeLinkEntity().apply { id = 5L },
        )

        assertTrue(node["employee"]!!.isNull)

        val copy = mapper.fromNode(node, ConvertedEmployeeLinkEntity::class.java)
        assertNull(copy.employee)
    }

    @Test
    fun convertedRelationshipIgnoresAssociationDepth() {
        val shallow = JpaDocumentMapper(JpaConfig.builder().associationDepth(0).build())
        val entity = ConvertedEmployeeLinkEntity().apply {
            id = 6L
            employee = employee(15L, "alice")
        }

        val node = shallow.toNode(entity)

        assertEquals(15L, node["employee"]!!.asNumber().toLong())
    }

    @Test
    fun rejectsRelationshipConverterTypeMismatch() {
        val exception = assertFailsWith<JpaConfigException> { mapper.toNode(BadRelationshipConvertEntity()) }

        assertTrue(exception.message!!.contains("relationship property"), "message was ${exception.message}")
    }

    @Test
    fun rejectsAttributeNameOnRelationship() {
        val exception = assertFailsWith<JpaConfigException> {
            mapper.toNode(AttributeNameOnRelationshipEntity())
        }

        assertTrue(exception.message!!.contains("attributeName"), "message was ${exception.message}")
    }

    private fun employee(id: Long, name: String): EmployeeEntity = EmployeeEntity().apply {
        this.id = id
        this.name = name
    }
}
