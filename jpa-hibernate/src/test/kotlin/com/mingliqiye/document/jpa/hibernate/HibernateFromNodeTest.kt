package com.mingliqiye.document.jpa.hibernate

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.JpaConfig
import com.mingliqiye.document.jpa.exception.JpaMappingException
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaMappingException
import jakarta.persistence.EntityManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [HibernateDocumentMapper.fromNode] 与按标识符加载辅助 API 测试。 */
class HibernateFromNodeTest {

    private val mapper = HibernateDocumentMapper()

    @Test
    fun roundTripsDetachedGraphWithoutEntityManager() {
        val note = HibernateTestNote().apply {
            id = 11L
            text = "round-trip"
            status = HibernateTestStatus.ACTIVE
            address = HibernateTestAddress().apply {
                city = "hangzhou"
                zipCode = "310000"
            }
        }

        val restored = mapper.fromNode(mapper.toNode(note), HibernateTestNote::class.java)

        assertEquals(note.id, restored.id)
        assertEquals(note.text, restored.text)
        assertEquals(note.status, restored.status)
        assertEquals(note.address!!.city, restored.address!!.city)
        assertEquals(note.address!!.zipCode, restored.address!!.zipCode)
    }

    @Test
    fun resolvesManyToOneReferenceWithSession() {
        val organization = HibernateTestDb.seedOrganization()
        val node = ObjectNode.of(
            "id" to NumberNode.of(HibernateTestDb.nextId()),
            "full_name" to StringNode.of("charlie"),
            "department" to ObjectNode.of("id" to NumberNode.of(organization.departmentId)),
        )

        HibernateTestDb.inSession { session ->
            val managedDepartment = session.find(HibernateTestDepartment::class.java, organization.departmentId)!!

            val employee = mapper.fromNode(node, HibernateTestEmployee::class.java, session)

            assertSame(managedDepartment, employee.department)
            assertEquals("charlie", employee.name)
        }
    }

    @Test
    fun resolvesManyToOneReferenceWithEntityManagerOverload() {
        val organization = HibernateTestDb.seedOrganization()
        val node = ObjectNode.of(
            "full_name" to StringNode.of("dave"),
            "department" to ObjectNode.of("id" to NumberNode.of(organization.departmentId)),
        )

        HibernateTestDb.inSession { session ->
            val entityManager: EntityManager = session
            val managedDepartment = entityManager.find(HibernateTestDepartment::class.java, organization.departmentId)!!

            val employee = mapper.fromNode(node, HibernateTestEmployee::class.java, entityManager)

            assertSame(managedDepartment, employee.department)
        }
    }

    @Test
    fun resolvesCollectionReferencesWithSession() {
        val organization = HibernateTestDb.seedOrganization()
        val node = ObjectNode.of(
            "id" to NumberNode.of(HibernateTestDb.nextId()),
            "dept_name" to StringNode.of("restored"),
            "employees" to ArrayNode.of(
                ObjectNode.of("id" to NumberNode.of(organization.aliceId)),
                ObjectNode.of("id" to NumberNode.of(organization.bobId)),
            ),
        )

        HibernateTestDb.inSession { session ->
            val department = mapper.fromNode(node, HibernateTestDepartment::class.java, session)

            assertEquals("restored", department.name)
            assertEquals(2, department.employees.size)
            assertSame(
                session.find(HibernateTestEmployee::class.java, organization.aliceId),
                department.employees[0],
            )
            assertSame(
                session.find(HibernateTestEmployee::class.java, organization.bobId),
                department.employees[1],
            )
        }
    }

    @Test
    fun buildsDetachedAssociationWhenNodeContainsData() {
        val organization = HibernateTestDb.seedOrganization()
        val node = ObjectNode.of(
            "full_name" to StringNode.of("erin"),
            "department" to ObjectNode.of(
                "id" to NumberNode.of(organization.departmentId),
                "dept_name" to StringNode.of("detached-department"),
            ),
        )

        HibernateTestDb.inSession { session ->
            val employee = mapper.fromNode(node, HibernateTestEmployee::class.java, session)

            assertEquals("detached-department", employee.department!!.name)
            assertNotSame(
                session.find(HibernateTestDepartment::class.java, organization.departmentId),
                employee.department,
            )
        }
    }

    @Test
    fun fallsBackToDetachedInstanceWhenReferenceIsMissing() {
        val missingId = HibernateTestDb.nextId()
        val node = ObjectNode.of(
            "full_name" to StringNode.of("frank"),
            "department" to ObjectNode.of("id" to NumberNode.of(missingId)),
        )

        HibernateTestDb.inSession { session ->
            val employee = mapper.fromNode(node, HibernateTestEmployee::class.java, session)

            assertEquals(missingId, employee.department!!.id)
            assertFalse(employee.department!!.employees.isNotEmpty())
        }
    }

    @Test
    fun skipsAssociationsBeyondDepthOnRead() {
        val organization = HibernateTestDb.seedOrganization()
        val shallow = HibernateDocumentMapper(JpaConfig.builder().associationDepth(0).build())
        val node = ObjectNode.of(
            "id" to NumberNode.of(HibernateTestDb.nextId()),
            "dept_name" to StringNode.of("shallow"),
            "employees" to ArrayNode.of(
                ObjectNode.of("id" to NumberNode.of(organization.aliceId)),
            ),
        )

        HibernateTestDb.inSession { session ->
            val department = shallow.fromNode(node, HibernateTestDepartment::class.java, session)

            assertEquals("shallow", department.name)
            assertTrue(department.employees.isEmpty())
        }
    }

    @Test
    fun rejectsNullForRequiredAssociation() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(1),
            "target" to NullNode.INSTANCE,
        )

        HibernateTestDb.inSession { session ->
            assertFailsWith<HibernateJpaMappingException> {
                mapper.fromNode(node, HibernateTestRequiredLink::class.java, session)
            }
        }
    }

    @Test
    fun reportsUnknownPropertiesThroughJpaMapping() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(1),
            "missing_property" to StringNode.of("x"),
        )

        HibernateTestDb.inSession { session ->
            assertFailsWith<JpaMappingException> {
                mapper.fromNode(node, HibernateTestNote::class.java, session)
            }
        }
    }

    @Test
    fun findAsNodeInitializesLazyAssociations() {
        val organization = HibernateTestDb.seedOrganization()

        HibernateTestDb.inSession { session ->
            val node = mapper.findAsNode(HibernateTestDepartment::class.java, organization.departmentId, session)

            assertEquals(organization.departmentName, node!!["dept_name"]!!.asString())
            assertEquals(2, node["employees"]!!.asArray().size)
        }
    }

    @Test
    fun findAsNodeLoadsEntityByIdentifier() {
        val noteId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestNote().apply {
                    id = noteId
                    text = "find-me"
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val node = mapper.findAsNode(HibernateTestNote::class.java, noteId, session)

            assertEquals("find-me", node!!["note_text"]!!.asString())
            assertNull(mapper.findAsNode(HibernateTestNote::class.java, HibernateTestDb.nextId(), session))
        }
    }

    @Test
    fun getAsNodeLoadsEntityBySessionGet() {
        val noteId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestNote().apply {
                    id = noteId
                    text = "get-me"
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val node = mapper.getAsNode(HibernateTestNote::class.java, noteId, session)

            assertEquals("get-me", node!!["note_text"]!!.asString())
            assertNull(mapper.getAsNode(HibernateTestNote::class.java, HibernateTestDb.nextId(), session))
        }
    }
}
