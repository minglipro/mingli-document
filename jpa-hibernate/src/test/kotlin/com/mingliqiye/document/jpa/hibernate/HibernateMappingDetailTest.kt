package com.mingliqiye.document.jpa.hibernate

import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import org.hibernate.Hibernate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 关系 @Convert / `@MapKeyJoinColumn` 实体键 / 复合主键关联引用解析集成测试。 */
class HibernateMappingDetailTest {

    private val mapper = HibernateDocumentMapper()

    // ------------------------------------------------------------------
    // 关系属性 @Convert
    // ------------------------------------------------------------------

    @Test
    fun serializesConvertedRelationshipThroughSession() {
        val employeeId = HibernateTestDb.nextId()
        val linkId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            val employee = HibernateTestEmployee().apply {
                id = employeeId
                name = "alice"
            }
            session.persist(employee)
            session.persist(
                HibernateTestConvertedLink().apply {
                    id = linkId
                    this.employee = employee
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val link = session.find(HibernateTestConvertedLink::class.java, linkId)!!
            val node = mapper.toNode(link, session)

            assertTrue(node["employee"]!!.isNumber, "expected converted id number but was ${node["employee"]!!.type}")
            assertEquals(employeeId, node["employee"]!!.asNumber().toLong())

            val copy = mapper.fromNode(node, HibernateTestConvertedLink::class.java, session)
            assertEquals(employeeId, copy.employee!!.id)
        }
    }

    @Test
    fun convertedRelationshipIsNotExpandedByHibernateTraversal() {
        val linkId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            val employee = HibernateTestEmployee().apply {
                id = HibernateTestDb.nextId()
                name = "bob"
            }
            session.persist(employee)
            session.persist(
                HibernateTestConvertedLink().apply {
                    id = linkId
                    this.employee = employee
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val node = mapper.toNode(session.find(HibernateTestConvertedLink::class.java, linkId)!!, session)
            assertFalse(node["employee"]!!.isObject)
        }
    }

    // ------------------------------------------------------------------
    // @MapKeyJoinColumn 实体 Map 键
    // ------------------------------------------------------------------

    @Test
    fun serializesEntityMapKeyThroughSession() {
        val memberId = HibernateTestDb.nextId()
        val workloadId = HibernateTestDb.nextId()
        val projectId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            val member = HibernateTestMember().apply {
                id = memberId
                name = "alice"
            }
            session.persist(member)
            val project = HibernateTestProject().apply {
                id = projectId
                name = "apollo"
            }
            session.persist(
                HibernateTestWorkload().apply {
                    id = workloadId
                    projectByMember = linkedMapOf(member to project)
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val workload = session.find(HibernateTestWorkload::class.java, workloadId)!!
            assertFalse(Hibernate.isInitialized(workload.projectByMember))

            val node = mapper.toNode(workload, session)

            assertTrue(Hibernate.isInitialized(workload.projectByMember))
            val mapNode = node["projectByMember"]!!.asObject()
            assertEquals(setOf(memberId.toString()), mapNode.keys)
            assertEquals("apollo", mapNode[memberId.toString()]!!.asObject()["project_name"]!!.asString())
        }
    }

    @Test
    fun roundTripsEntityMapKeyToManagedInstance() {
        val memberId = HibernateTestDb.nextId()
        val workloadId = HibernateTestDb.nextId()
        val projectId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            val member = HibernateTestMember().apply {
                id = memberId
                name = "carol"
            }
            session.persist(member)
            session.persist(
                HibernateTestWorkload().apply {
                    id = workloadId
                    projectByMember = linkedMapOf(
                        member to HibernateTestProject().apply {
                            id = projectId
                            name = "zeus"
                        },
                    )
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val workload = session.find(HibernateTestWorkload::class.java, workloadId)!!
            val node = mapper.toNode(workload, session)

            val copy = mapper.fromNode(node, HibernateTestWorkload::class.java, session)
            val key = copy.projectByMember.keys.single()
            val managedMember = session.find(HibernateTestMember::class.java, memberId)!!

            assertSame(managedMember, key)
            assertEquals("zeus", copy.projectByMember[key]!!.name)
        }
    }

    // ------------------------------------------------------------------
    // 复合主键关联引用解析
    // ------------------------------------------------------------------

    @Test
    fun resolvesEmbeddedIdAssociationReference() {
        val linkId = HibernateTestDb.nextId()
        val orderKey = HibernateTestOrderKey().apply {
            orderNo = "A-1"
            region = "EU"
        }
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestCompositeOrder().apply {
                    key = orderKey
                    amount = "42.00"
                },
            )
            session.persist(
                HibernateTestCompositeOrderLink().apply {
                    id = linkId
                    order = HibernateTestCompositeOrder().apply { key = orderKey }
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val node = ObjectNode.of(
                "id" to NumberNode.of(linkId),
                "order" to ObjectNode.of(
                    "key" to ObjectNode.of(
                        "order_no" to StringNode.of("A-1"),
                        "region_code" to StringNode.of("EU"),
                    ),
                ),
            )

            val copy = mapper.fromNode(node, HibernateTestCompositeOrderLink::class.java, session)
            val managedOrder = session.find(HibernateTestCompositeOrder::class.java, orderKey)

            assertSame(managedOrder, copy.order)
        }
    }

    @Test
    fun resolvesIdClassAssociationReference() {
        val linkId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestIdClassBook().apply {
                    shelf = 1
                    slot = 2
                    title = "kotlin"
                },
            )
            session.persist(
                HibernateTestBookLink().apply {
                    id = linkId
                    book = HibernateTestIdClassBook().apply {
                        shelf = 1
                        slot = 2
                    }
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val node = ObjectNode.of(
                "id" to NumberNode.of(linkId),
                "book" to ObjectNode.of(
                    "shelf" to NumberNode.of(1),
                    "slot" to NumberNode.of(2),
                ),
            )

            val copy = mapper.fromNode(node, HibernateTestBookLink::class.java, session)
            val queried = session.find(
                HibernateTestIdClassBook::class.java,
                HibernateTestBookKey().apply {
                    shelf = 1
                    this.slot = 2
                },
            )

            assertSame(queried, copy.book)
        }
    }
}
