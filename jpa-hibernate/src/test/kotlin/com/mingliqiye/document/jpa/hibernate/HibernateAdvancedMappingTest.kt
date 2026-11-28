package com.mingliqiye.document.jpa.hibernate

import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.JpaConfig
import com.mingliqiye.document.jpa.JpaDocumentMapper
import com.mingliqiye.document.jpa.exception.JpaMappingException
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaMappingException
import org.hibernate.Hibernate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `@Convert` / Map 关联 / 多态在 Hibernate 感知映射中的集成测试。 */
class HibernateAdvancedMappingTest {

    private val mapper = HibernateDocumentMapper()

    // ------------------------------------------------------------------
    // @Convert
    // ------------------------------------------------------------------

    @Test
    fun mapsConvertedAttributeThroughSession() {
        val paymentId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestPayment().apply {
                    id = paymentId
                    amount = HibernateTestMoney(BigDecimal("12.50"), "USD")
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val payment = session.find(HibernateTestPayment::class.java, paymentId)!!
            val node = mapper.toNode(payment, session)

            assertEquals("12.50 USD", node["amount"]!!.asString())

            val copy = mapper.fromNode(node, HibernateTestPayment::class.java, session)
            assertEquals(HibernateTestMoney(BigDecimal("12.50"), "USD"), copy.amount)
        }
    }

    @Test
    fun appliesAutoApplyConverterConfiguredThroughJpaConfig() {
        val contactId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestContact().apply {
                    id = contactId
                    email = HibernateTestEmail("Alice@Example.com")
                },
            )
        }

        val hibernateMapper = HibernateDocumentMapper(
            jpaConfig = JpaConfig.builder().autoApplyConverter(HibernateTestEmailConverter::class.java).build(),
        )

        HibernateTestDb.inSession { session ->
            val contact = session.find(HibernateTestContact::class.java, contactId)!!
            val node = hibernateMapper.toNode(contact, session)

            assertEquals("ALICE@EXAMPLE.COM", node["email"]!!.asString())

            val copy = hibernateMapper.fromNode(node, HibernateTestContact::class.java, session)
            assertEquals(HibernateTestEmail("alice@example.com"), copy.email)
        }
    }

    // ------------------------------------------------------------------
    // Map 关联
    // ------------------------------------------------------------------

    @Test
    fun serializesMapAssociationThroughSession() {
        val fixture = seedTeam()

        HibernateTestDb.inSession { session ->
            val team = session.find(HibernateTestTeam::class.java, fixture.teamId)!!
            assertFalse(Hibernate.isInitialized(team.membersByName))

            val node = mapper.toNode(team, session)

            assertTrue(Hibernate.isInitialized(team.membersByName))
            assertTrue(Hibernate.isInitialized(team.membersByKey))
            val members = node["membersByName"]!!.asObject()
            assertEquals(setOf("alice", "bob"), members.keys)
            assertEquals("alice", members["alice"]!!.asObject()["member_name"]!!.asString())
            assertEquals(fixture.aliceId, members["alice"]!!.asObject()["id"]!!.asNumber().toLong())

            val byKey = node["membersByKey"]!!.asObject()
            assertEquals(setOf(fixture.aliceId.toString(), fixture.bobId.toString()), byKey.keys)
        }
    }

    @Test
    fun roundTripsMapAssociationWithSession() {
        val fixture = seedTeam()

        HibernateTestDb.inSession { session ->
            val team = session.find(HibernateTestTeam::class.java, fixture.teamId)!!
            val node = mapper.toNode(team, session)

            val copy = mapper.fromNode(node, HibernateTestTeam::class.java, session)

            assertEquals("core", copy.name)
            assertEquals(setOf("alice", "bob"), copy.membersByName.keys)
            assertEquals("bob", copy.membersByName["bob"]!!.name)
            assertEquals(setOf(fixture.aliceId, fixture.bobId), copy.membersByKey.keys)
            assertEquals("alice", copy.membersByKey[fixture.aliceId]!!.name)
        }
    }

    @Test
    fun mapKeyFormatMatchesPureJpaMapping() {
        val fixture = seedTeam()
        val jpaMapper = JpaDocumentMapper(JpaConfig.builder().associationDepth(1).build())

        HibernateTestDb.inSession { session ->
            val team = session.find(HibernateTestTeam::class.java, fixture.teamId)!!
            val hibernateNode = mapper.toNode(team, session)
            val jpaNode = jpaMapper.toNode(team)

            assertEquals(
                jpaNode["membersByName"]!!.asObject().keys,
                hibernateNode["membersByName"]!!.asObject().keys,
            )
            assertEquals(
                jpaNode["membersByKey"]!!.asObject().keys,
                hibernateNode["membersByKey"]!!.asObject().keys,
            )
            for (key in jpaNode["membersByName"]!!.asObject().keys) {
                assertEquals(
                    jpaNode["membersByName"]!!.asObject()[key]!!.asObject()["member_name"]!!.asString(),
                    hibernateNode["membersByName"]!!.asObject()[key]!!.asObject()["member_name"]!!.asString(),
                )
            }
        }
    }

    @Test
    fun rejectsMapKeyCollisionOnRead() {
        val member = ObjectNode.of(
            "id" to NumberNode.of(1),
            "member_name" to StringNode.of("alice"),
        )
        val node = ObjectNode.of(
            "id" to NumberNode.of(99),
            "membersByKey" to ObjectNode.of(
                "01" to member,
                "1" to member,
            ),
        )

        HibernateTestDb.inSession { session ->
            val exception = assertFailsWith<HibernateJpaMappingException> {
                mapper.fromNode(node, HibernateTestTeam::class.java, session)
            }
            assertTrue(exception.message!!.contains("collision"), "message was ${exception.message}")
        }
    }

    // ------------------------------------------------------------------
    // 多态
    // ------------------------------------------------------------------

    @Test
    fun writesPolymorphicEntityThroughSession() {
        val catId = HibernateTestDb.nextId()
        val dogId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestCat().apply {
                    id = catId
                    name = "tom"
                    lives = 7
                },
            )
            session.persist(
                HibernateTestDog().apply {
                    id = dogId
                    name = "rex"
                    barkVolume = 5
                },
            )
        }

        HibernateTestDb.inSession { session ->
            val cat = session.find(HibernateTestAnimal::class.java, catId)
            val dog = session.find(HibernateTestAnimal::class.java, dogId)
            assertTrue(cat is HibernateTestCat, "expected HibernateTestCat but was ${cat!!.javaClass.name}")
            assertTrue(dog is HibernateTestDog, "expected HibernateTestDog but was ${dog!!.javaClass.name}")

            val catNode = mapper.toNode(cat, session)
            assertEquals("cat", catNode["animal_kind"]!!.asString())
            assertEquals("tom", catNode["animal_name"]!!.asString())
            assertEquals(7, catNode["lives"]!!.asNumber().toInt())

            val dogNode = mapper.toNode(dog, session)
            assertEquals("dog", dogNode["animal_kind"]!!.asString())
            assertEquals(5, dogNode["bark_volume"]!!.asNumber().toInt())
        }
    }

    @Test
    fun readsPolymorphicEntityThroughSession() {
        val catId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(
                HibernateTestCat().apply {
                    id = catId
                    name = "tom"
                    lives = 7
                },
            )
        }

        val polymorphicMapper = HibernateDocumentMapper(
            jpaConfig = JpaConfig.builder()
                .entityType(HibernateTestCat::class.java)
                .entityType(HibernateTestDog::class.java)
                .build(),
        )

        HibernateTestDb.inSession { session ->
            val cat = session.find(HibernateTestCat::class.java, catId)!!
            val node = polymorphicMapper.toNode(cat, session)

            val copy = polymorphicMapper.fromNode(node, HibernateTestAnimal::class.java, session)

            assertTrue(copy is HibernateTestCat, "expected HibernateTestCat but was ${copy.javaClass.name}")
            assertEquals("tom", copy.name)
            assertEquals(7, (copy as HibernateTestCat).lives)
        }
    }

    @Test
    fun rejectsUnknownDiscriminatorThroughSession() {
        val polymorphicMapper = HibernateDocumentMapper(
            jpaConfig = JpaConfig.builder()
                .entityType(HibernateTestCat::class.java)
                .entityType(HibernateTestDog::class.java)
                .build(),
        )
        val node = ObjectNode.of(
            "animal_kind" to StringNode.of("bird"),
            "id" to NumberNode.of(1),
        )

        HibernateTestDb.inSession { session ->
            val exception = assertFailsWith<JpaMappingException> {
                polymorphicMapper.fromNode(node, HibernateTestAnimal::class.java, session)
            }
            assertTrue(exception.message!!.contains("bird"), "message was ${exception.message}")
        }
    }

    @Test
    fun keepsConvertedNullAttributeAsNull() {
        val paymentId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            session.persist(HibernateTestPayment().apply { id = paymentId })
        }

        HibernateTestDb.inSession { session ->
            val payment = session.find(HibernateTestPayment::class.java, paymentId)!!
            val node = mapper.toNode(payment, session)

            assertTrue(node["amount"]!!.isNull)
            val copy = mapper.fromNode(node, HibernateTestPayment::class.java, session)
            assertNull(copy.amount)
        }
    }

    private fun seedTeam(): TeamFixture {
        val teamId = HibernateTestDb.nextId()
        val aliceId = HibernateTestDb.nextId()
        val bobId = HibernateTestDb.nextId()
        HibernateTestDb.inSession { session ->
            val alice = HibernateTestMember().apply {
                id = aliceId
                name = "alice"
            }
            val bob = HibernateTestMember().apply {
                id = bobId
                name = "bob"
            }
            val team = HibernateTestTeam().apply {
                id = teamId
                name = "core"
            }
            alice.team = team
            bob.team = team
            team.membersByName = linkedMapOf("alice" to alice, "bob" to bob)
            team.membersByKey = linkedMapOf(aliceId to alice, bobId to bob)
            session.persist(team)
        }
        return TeamFixture(teamId, aliceId, bobId)
    }
}

/** [HibernateAdvancedMappingTest.seedTeam] 的返回值。 */
private class TeamFixture(
    val teamId: Long,
    val aliceId: Long,
    val bobId: Long,
)
