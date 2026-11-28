package com.mingliqiye.document.jpa.hibernate

import com.mingliqiye.document.jpa.JpaConfig
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaMappingException
import jakarta.persistence.EntityManager
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [HibernateDocumentMapper.toNode] 的 Hibernate 运行期行为测试。 */
class HibernateDocumentMapperTest {

    private val mapper = HibernateDocumentMapper()

    @Test
    fun serializesDetachedEntityWithoutEntityManager() {
        val note = HibernateTestNote().apply {
            id = 1L
            text = "hello"
            status = HibernateTestStatus.ACTIVE
            address = HibernateTestAddress().apply {
                city = "beijing"
                zipCode = "100000"
            }
        }

        val node = mapper.toNode(note)

        assertEquals("hello", node["note_text"]!!.asString())
        assertEquals("ACTIVE", node["status"]!!.asString())
        val address = node["address"]!!.asObject()
        assertEquals("beijing", address["city"]!!.asString())
        assertEquals("100000", address["zip_code"]!!.asString())
    }

    @Test
    fun serializesLoadedEntityThroughSession() {
        val noteId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestNote().apply {
                    id = noteId
                    text = "with-session"
                    status = HibernateTestStatus.LEAVE
                    address = HibernateTestAddress().apply { city = "shenzhen" }
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val note = session.find(HibernateTestNote::class.java, noteId)!!
            val node = mapper.toNode(note, session)
            assertEquals("with-session", node["note_text"]!!.asString())
            assertEquals("LEAVE", node["status"]!!.asString())
            assertEquals("shenzhen", node["address"]!!.asObject()["city"]!!.asString())
        }
    }

    @Test
    fun serializesLoadedEntityThroughEntityManagerOverload() {
        val noteId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestNote().apply {
                    id = noteId
                    text = "entity-manager"
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val entityManager: EntityManager = session
            val note = entityManager.find(HibernateTestNote::class.java, noteId)!!
            val node = mapper.toNode(note, entityManager)
            assertEquals("entity-manager", node["note_text"]!!.asString())
            assertEquals(noteId, node["id"]!!.asNumber().toLong())
        }
    }

    @Test
    fun initializesLazyOneToManyInsideSession() {
        val organization = HibernateTestDb.seedOrganization()

        HibernateTestDb.inSession { session ->
            val department = session.find(HibernateTestDepartment::class.java, organization.departmentId)!!
            assertFalse(Hibernate.isInitialized(department.employees))

            val node = mapper.toNode(department, session)

            assertTrue(Hibernate.isInitialized(department.employees))
            val employees = node["employees"]!!.asArray()
            assertEquals(2, employees.size)
            assertEquals(
                setOf("alice", "bob"),
                employees.map { it.asObject()["full_name"]!!.asString() }.toSet(),
            )
        }
    }

    @Test
    fun removesLazyManyToOneProxyDuringSerialization() {
        val organization = HibernateTestDb.seedOrganization()

        HibernateTestDb.inSession { session ->
            val employee = session.find(HibernateTestEmployee::class.java, organization.aliceId)!!
            assertTrue(employee.department is HibernateProxy)

            val node = mapper.toNode(employee, session)

            assertTrue(Hibernate.isInitialized(employee.department!!))
            val department = node["department"]!!.asObject()
            assertEquals(organization.departmentId, department["id"]!!.asNumber().toLong())
            assertEquals(organization.departmentName, department["dept_name"]!!.asString())
        }
    }

    @Test
    fun serializesRootHibernateProxyFromGetReference() {
        val organization = HibernateTestDb.seedOrganization()

        HibernateTestDb.inSession { session ->
            val proxy = session.getReference(HibernateTestDepartment::class.java, organization.departmentId)
            assertTrue(proxy is HibernateProxy)
            assertFalse(Hibernate.isInitialized(proxy))

            val node = mapper.toNode(proxy, session)

            assertTrue(Hibernate.isInitialized(proxy))
            assertEquals(organization.departmentId, node["id"]!!.asNumber().toLong())
            assertEquals(organization.departmentName, node["dept_name"]!!.asString())
            assertEquals(2, node["employees"]!!.asArray().size)
        }
    }

    @Test
    fun respectsAssociationDepthZeroWithoutInitializingAssociations() {
        val organization = HibernateTestDb.seedOrganization()
        val noAssociations = HibernateDocumentMapper(JpaConfig.builder().associationDepth(0).build())

        HibernateTestDb.inSession { session ->
            val employee = session.find(HibernateTestEmployee::class.java, organization.aliceId)!!
            assertFalse(Hibernate.isInitialized(employee.department!!))

            val node = noAssociations.toNode(employee, session)

            assertFalse(node.containsKey("department"))
            assertFalse(Hibernate.isInitialized(employee.department!!))
        }
    }

    @Test
    fun mapsLazyElementCollectionsThroughInitialize() {
        val organization = HibernateTestDb.seedOrganization()

        HibernateTestDb.inSession { session ->
            val employee = session.find(HibernateTestEmployee::class.java, organization.aliceId)!!
            assertFalse(Hibernate.isInitialized(employee.tags))
            assertFalse(Hibernate.isInitialized(employee.statuses))

            val node = mapper.toNode(employee, session)

            assertTrue(Hibernate.isInitialized(employee.tags))
            assertEquals(listOf("core", "backend"), node["tags"]!!.asArray().map { it.asString() })
            assertEquals(
                listOf("ACTIVE", "LEAVE"),
                node["statuses"]!!.asArray().map { it.asString() },
            )
        }
    }

    @Test
    fun includesNullAssociationWhenNullPolicyIsInclude() {
        val employee = HibernateTestEmployee().apply {
            id = 1L
            name = "nobody"
        }

        HibernateTestDb.inSession { session ->
            val node = mapper.toNode(employee, session)
            assertTrue(node.containsKey("department"))
            assertTrue(node["department"]!!.isNull)
        }
    }

    @Test
    fun detectsBidirectionalCycleWhenDepthIsSufficient() {
        val organization = HibernateTestDb.seedOrganization()
        val deep = HibernateDocumentMapper(JpaConfig.builder().associationDepth(2).build())

        HibernateTestDb.inSession { session ->
            val department = session.find(HibernateTestDepartment::class.java, organization.departmentId)!!

            val exception = assertFailsWith<HibernateJpaMappingException> { deep.toNode(department, session) }

            assertTrue(exception.message!!.contains("Circular"))
        }
    }

    @Test
    fun omitsCyclicReferencesWithOmitPolicy() {
        val organization = HibernateTestDb.seedOrganization()
        val omitCycle = HibernateDocumentMapper(
            JpaConfig.builder().associationDepth(2).build(),
            HibernateConfig.builder().cyclePolicy(HibernateCyclePolicy.OMIT).build(),
        )

        HibernateTestDb.inSession { session ->
            val department = session.find(HibernateTestDepartment::class.java, organization.departmentId)!!

            val node = omitCycle.toNode(department, session)
            val employees = node["employees"]!!.asArray()

            assertEquals(2, employees.size)
            for (index in 0 until employees.size) {
                assertFalse(employees[index].asObject().containsKey("department"))
            }
        }
    }

    @Test
    fun omitsSelfReferenceWithOmitPolicy() {
        val treeNode = HibernateTestTreeNode().apply {
            id = 1L
            name = "root"
        }
        treeNode.parent = treeNode
        val omitCycle = HibernateDocumentMapper(
            JpaConfig.builder().associationDepth(2).build(),
            HibernateConfig.builder().cyclePolicy(HibernateCyclePolicy.OMIT).build(),
        )

        HibernateTestDb.inSession { session ->
            val node = omitCycle.toNode(treeNode, session)

            assertEquals("root", node["node_name"]!!.asString())
            assertFalse(node.containsKey("parent"))
        }
    }

    @Test
    fun detectsSelfReferenceCycleWithFailPolicy() {
        val treeNode = HibernateTestTreeNode().apply {
            id = 1L
            name = "root"
        }
        treeNode.parent = treeNode
        val deep = HibernateDocumentMapper(JpaConfig.builder().associationDepth(2).build())

        HibernateTestDb.inSession { session ->
            val exception = assertFailsWith<HibernateJpaMappingException> { deep.toNode(treeNode, session) }
            assertTrue(exception.message!!.contains("Circular"))
        }
    }

    @Test
    fun writesAssociationFieldsAfterPlainFields() {
        val note = HibernateTestNote().apply {
            id = 7L
            text = "ordered"
        }

        val node = mapper.toNode(note)

        assertEquals(listOf("id", "note_text", "status", "address"), node.keys.toList())
    }
}
