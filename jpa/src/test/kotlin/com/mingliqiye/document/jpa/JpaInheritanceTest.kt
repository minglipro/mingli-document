package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.exception.JpaMappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `@Inheritance` / `@DiscriminatorColumn` / `@DiscriminatorValue` 多态映射测试。 */
class JpaInheritanceTest {

    private val mapper = JpaDocumentMapper()

    private val polymorphicMapper: JpaDocumentMapper = JpaDocumentMapper(
        JpaConfig.builder()
            .entityType(CatEntity::class.java)
            .entityType(DogEntity::class.java)
            .entityType(LeafNodeEntity::class.java)
            .build(),
    )

    @Test
    fun writesSubclassPropertiesWithDiscriminator() {
        val cat = CatEntity().apply {
            id = 1L
            name = "tom"
            lives = 7
        }

        val node = mapper.toNode(cat)

        assertEquals("cat", node["animal_kind"]!!.asString())
        assertEquals(1L, node["id"]!!.asNumber().toLong())
        assertEquals("tom", node["name"]!!.asString())
        assertEquals(7, node["lives"]!!.asNumber().toInt())
        assertEquals("animal_kind", node.keys.first())
    }

    @Test
    fun writesDefaultDiscriminatorColumnAndEntityNameValue() {
        val car = CarEntity().apply {
            id = 2L
            brand = "byd"
            doors = 2
        }

        val node = mapper.toNode(car)

        assertEquals("CarEntity", node["DTYPE"]!!.asString())
        assertEquals(2, node["doors"]!!.asNumber().toInt())
    }

    @Test
    fun writesIntegerDiscriminatorAsNumber() {
        val ticket = OnlineTicketEntity().apply {
            id = 3L
            email = "a@example.com"
        }

        val node = mapper.toNode(ticket)

        assertEquals(1, node["ticket_kind"]!!.asNumber().toInt())
        assertEquals("a@example.com", node["email"]!!.asString())
    }

    @Test
    fun writesDefaultEntityNameWhenDiscriminatorValueMissing() {
        val node = mapper.toNode(
            ClickEventEntity().apply {
                id = 4L
                x = 10
            },
        )

        assertEquals("ClickEventEntity", node["event_type"]!!.asString())
        assertEquals(10, node["x"]!!.asNumber().toInt())
    }

    @Test
    fun roundTripsSubclassesByDiscriminator() {
        val cat = CatEntity().apply {
            id = 11L
            name = "tom"
            lives = 7
        }
        val dog = DogEntity().apply {
            id = 12L
            name = "rex"
            barkVolume = 11
        }

        val catCopy = polymorphicMapper.fromNode(polymorphicMapper.toNode(cat), AnimalEntity::class.java)
        val dogCopy = polymorphicMapper.fromNode(polymorphicMapper.toNode(dog), AnimalEntity::class.java)

        assertTrue(catCopy is CatEntity, "expected CatEntity but was ${catCopy.javaClass.name}")
        assertEquals(7, (catCopy as CatEntity).lives)
        assertEquals("tom", catCopy.name)

        assertTrue(dogCopy is DogEntity, "expected DogEntity but was ${dogCopy.javaClass.name}")
        assertEquals(11, (dogCopy as DogEntity).barkVolume)
        assertEquals("rex", dogCopy.name)
    }

    @Test
    fun roundTripsPolymorphicAssociationCollection() {
        val zoo = ZooEntity().apply {
            id = 21L
            name = "zoo"
            animals = mutableListOf(
                CatEntity().apply {
                    id = 1L
                    name = "tom"
                    lives = 7
                },
                DogEntity().apply {
                    id = 2L
                    name = "rex"
                    barkVolume = 3
                },
            )
        }

        val node = polymorphicMapper.toNode(zoo)
        val animals = node["animals"]!!.asArray()
        assertEquals(2, animals.size)
        assertEquals("cat", animals[0].asObject()["animal_kind"]!!.asString())
        assertEquals("dog", animals[1].asObject()["animal_kind"]!!.asString())

        val copy = polymorphicMapper.fromNode(node, ZooEntity::class.java)
        assertEquals(2, copy.animals.size)
        assertTrue(copy.animals[0] is CatEntity)
        assertTrue(copy.animals[1] is DogEntity)
        assertEquals(3, (copy.animals[1] as DogEntity).barkVolume)
    }

    @Test
    fun rejectsUnknownDiscriminatorValue() {
        val node = ObjectNode.of(
            "animal_kind" to StringNode.of("bird"),
            "id" to NumberNode.of(1),
            "name" to StringNode.of("tweety"),
        )

        val exception = assertFailsWith<JpaMappingException> {
            polymorphicMapper.fromNode(node, AnimalEntity::class.java)
        }

        assertTrue(exception.message!!.contains("Unknown discriminator value 'bird'"), "message was ${exception.message}")
        assertTrue(exception.message!!.contains("cat"), "message was ${exception.message}")
        assertTrue(exception.message!!.contains("dog"), "message was ${exception.message}")
    }

    @Test
    fun hintsRegistrationWhenNoSubtypesRegistered() {
        val node = ObjectNode.of(
            "animal_kind" to StringNode.of("cat"),
            "id" to NumberNode.of(1),
        )

        val exception = assertFailsWith<JpaMappingException> {
            mapper.fromNode(node, AnimalEntity::class.java)
        }

        assertTrue(exception.message!!.contains("entityType"), "message was ${exception.message}")
    }

    @Test
    fun fallsBackToDeclaredConcreteTypeWithoutDiscriminator() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(31),
            "lives" to NumberNode.of(5),
        )

        val copy = polymorphicMapper.fromNode(node, CatEntity::class.java)

        assertEquals(31L, copy.id)
        assertEquals(5, copy.lives)
    }

    @Test
    fun rejectsMissingDiscriminatorForAbstractDeclaredType() {
        val node = ObjectNode.of(
            "id" to NumberNode.of(32),
            "name" to StringNode.of("anonymous"),
        )

        val exception = assertFailsWith<JpaMappingException> {
            polymorphicMapper.fromNode(node, AnimalEntity::class.java)
        }

        assertTrue(exception.message!!.contains("missing"), "message was ${exception.message}")
        assertTrue(exception.message!!.contains("animal_kind"), "message was ${exception.message}")
    }

    @Test
    fun readsIntegerDiscriminatorBackToSubclass() {
        val readMapper = JpaDocumentMapper(
            JpaConfig.builder().entityType(OnlineTicketEntity::class.java).build(),
        )
        val node = ObjectNode.of(
            "ticket_kind" to NumberNode.of(1),
            "id" to NumberNode.of(41),
            "email" to StringNode.of("b@example.com"),
        )

        val copy = readMapper.fromNode(node, TicketEntity::class.java)

        assertTrue(copy is OnlineTicketEntity, "expected OnlineTicketEntity but was ${copy.javaClass.name}")
        assertEquals("b@example.com", (copy as OnlineTicketEntity).email)
    }

    @Test
    fun readsDefaultEntityNameDiscriminatorBackToSubclass() {
        val readMapper = JpaDocumentMapper(
            JpaConfig.builder().entityType(ClickEventEntity::class.java).build(),
        )
        val node = ObjectNode.of(
            "event_type" to StringNode.of("ClickEventEntity"),
            "id" to NumberNode.of(42),
            "x" to NumberNode.of(3),
        )

        val copy = readMapper.fromNode(node, BaseEventEntity::class.java)

        assertTrue(copy is ClickEventEntity, "expected ClickEventEntity but was ${copy.javaClass.name}")
        assertEquals(3, (copy as ClickEventEntity).x)
    }

    @Test
    fun exposesDiscriminatorHelpers() {
        assertEquals("cat", mapper.discriminatorValue(CatEntity::class.java))
        assertEquals("animal_kind", mapper.discriminatorFieldName(AnimalEntity::class.java))
        assertNull(mapper.discriminatorValue(AccountEntity::class.java))
        assertNull(mapper.discriminatorFieldName(AccountEntity::class.java))
    }

    @Test
    fun detectsCyclesInPolymorphicAssociation() {
        val deep = JpaDocumentMapper(
            JpaConfig.builder()
                .associationDepth(2)
                .entityType(LeafNodeEntity::class.java)
                .build(),
        )
        val leaf = LeafNodeEntity().apply { id = 51L }
        leaf.parent = leaf

        val exception = assertFailsWith<JpaMappingException> { deep.toNode(leaf) }

        assertTrue(exception.message!!.contains("Circular reference"), "message was ${exception.message}")
    }

    @Test
    fun keepsPolymorphicCollectionsStableAcrossRounds() {
        val zoo = ZooEntity().apply {
            id = 61L
            animals = mutableListOf(
                CatEntity().apply {
                    id = 1L
                    name = "tom"
                },
            )
        }

        val first = polymorphicMapper.toNode(zoo)
        val second = polymorphicMapper.toNode(polymorphicMapper.fromNode(first, ZooEntity::class.java))

        assertEquals(first["animals"]!!.asArray().size, second["animals"]!!.asArray().size)
        assertEquals(
            first["animals"]!!.asArray()[0].asObject()["animal_kind"]!!.asString(),
            second["animals"]!!.asArray()[0].asObject()["animal_kind"]!!.asString(),
        )
    }

    @Test
    fun writesPolymorphicAssociationAsArrayNode() {
        val zoo = ZooEntity().apply { id = 62L }
        val node = mapper.toNode(zoo)

        val animals = node["animals"]
        assertTrue(animals is ArrayNode, "expected ArrayNode but was ${animals!!.type}")
        assertEquals(0, animals.asArray().size)
    }
}
