package com.mingliqiye.document.jpa.hibernate

import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.exception.DocumentException
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaException
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaLazyLoadException
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaMappingException
import org.hibernate.Hibernate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 懒加载策略、异常体系与 Session 生命周期测试。 */
class HibernateLazyLoadTest {

    @Test
    fun failPolicyRejectsUninitializedAssociation() {
        val organization = HibernateTestDb.seedOrganization()
        val mapper = HibernateDocumentMapper(
            config = HibernateConfig.builder().lazyLoadPolicy(HibernateLazyLoadPolicy.FAIL).build(),
        )

        HibernateTestDb.inSession { session ->
            val department = session.find(HibernateTestDepartment::class.java, organization.departmentId)!!
            assertFalse(Hibernate.isInitialized(department.employees))

            val exception = assertFailsWith<HibernateJpaLazyLoadException> { mapper.toNode(department, session) }

            assertTrue(exception.message!!.contains("not initialized"))
            assertFalse(Hibernate.isInitialized(department.employees))
        }
    }

    @Test
    fun failPolicyRejectsUninitializedElementCollection() {
        val organization = HibernateTestDb.seedOrganization()
        val mapper = HibernateDocumentMapper(
            config = HibernateConfig.builder().lazyLoadPolicy(HibernateLazyLoadPolicy.FAIL).build(),
        )

        HibernateTestDb.inSession { session ->
            val employee = session.find(HibernateTestEmployee::class.java, organization.aliceId)!!
            assertFalse(Hibernate.isInitialized(employee.tags))

            val exception = assertFailsWith<HibernateJpaLazyLoadException> { mapper.toNode(employee, session) }

            assertTrue(exception.message!!.contains("not initialized"))
            assertFalse(Hibernate.isInitialized(employee.tags))
        }
    }

    @Test
    fun closedEntityManagerIsRejectedByToNode() {
        val session = HibernateTestDb.sessionFactory.openSession()
        session.close()
        val note = HibernateTestNote().apply {
            id = 1L
            text = "closed"
        }

        val exception = assertFailsWith<HibernateJpaMappingException> { mapper().toNode(note, session) }

        assertTrue(exception.message!!.contains("closed"))
    }

    @Test
    fun closedEntityManagerIsRejectedByFromNode() {
        val session = HibernateTestDb.sessionFactory.openSession()
        session.close()
        val node = ObjectNode.of("id" to NumberNode.of(1))

        val exception = assertFailsWith<HibernateJpaMappingException> {
            mapper().fromNode(node, HibernateTestNote::class.java, session)
        }

        assertTrue(exception.message!!.contains("closed"))
    }

    @Test
    fun closedEntityManagerIsRejectedByFindAsNode() {
        val session = HibernateTestDb.sessionFactory.openSession()
        session.close()

        assertFailsWith<HibernateJpaMappingException> {
            mapper().findAsNode(HibernateTestNote::class.java, 1L, session)
        }
    }

    @Test
    fun detachedGraphCanBeMappedWithoutEntityManagerAfterClose() {
        val noteId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestNote().apply {
                    id = noteId
                    text = "detached"
                },
            )
        }
        val detached = HibernateTestDb.inReadSession { session ->
            session.get(HibernateTestNote::class.java, noteId)!!
        }

        val node = mapper().toNode(detached)

        assertEquals("detached", node["note_text"]!!.asString())
    }

    @Test
    fun lazyCollectionBoundToClosedSessionFailsWithLazyException() {
        val organization = HibernateTestDb.seedOrganization()
        val stale = HibernateTestDb.inReadSession { session ->
            session.find(HibernateTestDepartment::class.java, organization.departmentId)!!
        }
        assertFalse(Hibernate.isInitialized(stale.employees))

        HibernateTestDb.inSession { freshSession ->
            assertFailsWith<HibernateJpaLazyLoadException> { mapper().toNode(stale, freshSession) }
        }
    }

    @Test
    fun exceptionHierarchyExtendsDocumentException() {
        val lazyException: Any = HibernateJpaLazyLoadException("x")
        val mappingException: Any = HibernateJpaMappingException("x")
        val baseException: Any = HibernateJpaException("x")
        assertTrue(lazyException is HibernateJpaMappingException)
        assertTrue(mappingException is HibernateJpaException)
        assertTrue(baseException is DocumentException)
    }

    private fun mapper(): HibernateDocumentMapper = HibernateDocumentMapper()
}
