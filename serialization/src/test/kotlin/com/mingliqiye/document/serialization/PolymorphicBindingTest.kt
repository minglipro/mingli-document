package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentTypeInfo
import com.mingliqiye.document.annotation.DocumentTypeName
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 多态数据绑定测试。
 *
 * 覆盖接口/抽象类字段、List/Map 泛型元素、嵌套 Bean、顶层 toNode/fromNode、
 * 类型属性名覆盖、显式 [DocumentTypeName] 优先级，以及缺失/未知类型等错误场景。
 */
class PolymorphicBindingTest {

    private val mapper = JsonMapper(
        DocumentMapperConfig.builder()
            .subtype(Pet::class.java, "feline", Cat::class.java)
            .subtype(Pet::class.java, "dog", Dog::class.java)
            .subtype(Vehicle::class.java, "car", Car::class.java)
            .subtype(Vehicle::class.java, "bike", Bike::class.java)
            .subtype(Tool::class.java, "tool-hammer", Hammer::class.java, "toolKind")
            .build(),
    )

    @Test
    fun interfaceFieldWritesDocumentTypeInfoProperty() {
        val owner = PetOwner().apply {
            pet = Cat().apply {
                name = "Tom"
                lives = 8
            }
        }

        val json = mapper.format(owner)
        assertTrue(json.contains("\"kind\":\"cat\""), json)

        val node = mapper.toNode(owner).asObject()
        assertEquals("cat", node["pet"]?.asObject()?.get("kind")?.asString())

        val restored = mapper.parse(json, PetOwner::class.java)
        val pet = catOf(restored.pet)
        assertEquals("Tom", pet.name)
        assertEquals(8, pet.lives)

        val fromNode = mapper.fromNode(node, PetOwner::class.java)
        assertEquals(8, catOf(fromNode.pet).lives)

        val topLevel = mapper.toNode(Cat().apply { name = "root" }).asObject()
        assertEquals("cat", topLevel["kind"]?.asString())
        assertEquals("root", topLevel["name"]?.asString())
        val topLevelRestored: Pet = mapper.fromNode(topLevel, Pet::class.java)
        assertEquals("root", catOf(topLevelRestored).name)
    }

    @Test
    fun abstractClassFieldUsesDefaultTypeProperty() {
        val owner = PetOwner().apply {
            vehicle = Car().apply {
                brand = "b"
                wheels = 4
            }
        }

        val json = mapper.format(owner)
        assertTrue(json.contains("\"type\":\"car\""), json)

        val restored = mapper.parse(json, PetOwner::class.java)
        val car = restored.vehicle as? Car ?: fail("expected Car but was ${restored.vehicle?.javaClass?.name}")
        assertEquals("b", car.brand)
        assertEquals(4, car.wheels)

        val fromNode = mapper.fromNode(mapper.toNode(owner), PetOwner::class.java)
        assertTrue(fromNode.vehicle is Car, "expected Car but was ${fromNode.vehicle?.javaClass?.name}")
    }

    @Test
    fun listAndMapGenericElementsArePolymorphic() {
        val owner = PetOwner().apply {
            pets = listOf(
                Cat().apply { name = "A" },
                Dog().apply { name = "B" },
            )
            garage = mapOf(
                "a" to Car().apply { brand = "x" },
                "b" to Bike(),
            )
        }

        val json = mapper.format(owner)
        assertTrue(json.contains("\"kind\":\"cat\""), json)
        assertTrue(json.contains("\"kind\":\"dog\""), json)
        assertTrue(json.contains("\"type\":\"car\""), json)
        assertTrue(json.contains("\"type\":\"bike\""), json)

        val restored = mapper.parse(json, PetOwner::class.java)
        val pets = assertNotNull(restored.pets)
        assertEquals(2, pets.size)
        assertEquals("A", catOf(pets[0]).name)
        val dog = pets[1] as? Dog ?: fail("expected Dog but was ${pets[1].javaClass.name}")
        assertEquals("B", dog.name)
        val garage = assertNotNull(restored.garage)
        assertEquals("x", (garage["a"] as? Car)?.brand)
        assertTrue(garage["b"] is Bike)

        val nodeRestored = mapper.fromNode(mapper.toNode(owner), PetOwner::class.java)
        assertEquals("A", catOf(assertNotNull(nodeRestored.pets)[0]).name)
        assertTrue(assertNotNull(nodeRestored.garage)["b"] is Bike)
    }

    @Test
    fun nestedBeansKeepPolymorphicInformation() {
        val cat = Cat().apply {
            name = "Tom"
            friend = Dog().apply { name = "Rex" }
        }

        val json = mapper.format(cat)
        assertTrue(json.contains("\"kind\":\"cat\""), json)
        assertTrue(json.contains("\"kind\":\"dog\""), json)

        val restored = mapper.parse(json, Pet::class.java)
        val restoredCat = catOf(restored)
        assertEquals("Tom", restoredCat.name)
        val friend = restoredCat.friend as? Dog
            ?: fail("expected Dog but was ${restoredCat.friend?.javaClass?.name}")
        assertEquals("Rex", friend.name)
    }

    @Test
    fun topLevelConvertSupportsGenericCollectionOfBaseType() {
        val cat = Cat().apply { name = "Kit" }
        val pets: List<Pet> = mapper.convert(
            listOf(cat, Dog().apply { name = "D" }),
            DocumentTypeReference.listType(Pet::class.java),
        )

        assertEquals(2, pets.size)
        assertEquals("Kit", catOf(pets[0]).name)
        assertTrue(pets[1] is Dog)
    }

    @Test
    fun explicitDocumentTypeNameWinsOverRegisteredName() {
        assertEquals("cat", mapper.toNode(Cat()).asObject()["kind"]?.asString())

        val byAlias = mapper.parse("""{"kind":"feline","name":"alias"}""", Pet::class.java)
        assertEquals("alias", catOf(byAlias).name)

        val byAnnotation = mapper.parse("""{"kind":"cat","name":"annotation"}""", Pet::class.java)
        assertEquals("annotation", catOf(byAnnotation).name)
    }

    @Test
    fun typePropertyOverrideBeatsDocumentTypeInfo() {
        val hammer = Hammer().apply {
            label = "h"
            weight = 2
        }

        val json = mapper.format(hammer)
        assertTrue(json.contains("\"toolKind\":\"hammer\""), json)
        assertFalse(json.contains("toolType"), json)

        val restored = mapper.parse(json, Tool::class.java)
        assertTrue(restored is Hammer, "expected Hammer but was ${restored?.javaClass?.name}")

        assertTrue(mapper.parse("""{"toolKind":"tool-hammer"}""", Tool::class.java) is Hammer)
    }

    @Test
    fun missingTypePropertyFailsWithFieldAndBaseInfo() {
        val exception = assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"pet":{"name":"Tom"}}""", PetOwner::class.java)
        }
        val message = assertNotNull(exception.message)
        assertTrue(message.contains("kind"), message)
        assertTrue(message.contains(Pet::class.java.name), message)
        assertTrue(message.contains("pet"), message)

        val nodeException = assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(
                ObjectNode.from(mapOf("name" to StringNode.of("Tom"))),
                Pet::class.java,
            )
        }
        assertTrue(assertNotNull(nodeException.message).contains("kind"))
    }

    @Test
    fun unknownTypeNameFailsWithFieldAndTypeInfo() {
        val exception = assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"pet":{"kind":"bird","name":"Tom"}}""", PetOwner::class.java)
        }
        val message = assertNotNull(exception.message)
        assertTrue(message.contains("bird"), message)
        assertTrue(message.contains("kind"), message)
        assertTrue(message.contains(Pet::class.java.name), message)
        assertTrue(message.contains("cat"), message)

        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(
                ObjectNode.from(mapOf("kind" to StringNode.of("bird"))),
                Pet::class.java,
            )
        }
    }

    @Test
    fun unregisteredSubtypeFailsSerialization() {
        val owner = PetOwner().apply {
            pet = UnregisteredPet().apply { name = "x" }
        }

        val exception = assertFailsWith<DocumentSerializationException> { mapper.format(owner) }
        val message = assertNotNull(exception.message)
        assertTrue(message.contains(UnregisteredPet::class.java.name), message)
        assertTrue(message.contains(Pet::class.java.name), message)
    }

    @Test
    fun typePropertyIsNotTreatedAsUnknownField() {
        val strict = JsonMapper(
            DocumentMapperConfig.builder()
                .failOnUnknownFields(true)
                .subtype(Pet::class.java, "feline", Cat::class.java)
                .subtype(Pet::class.java, "dog", Dog::class.java)
                .build(),
        )
        val owner = PetOwner().apply { pet = Cat().apply { name = "strict" } }

        val json = strict.format(owner)
        val restored = strict.parse(json, PetOwner::class.java)
        assertEquals("strict", catOf(restored.pet).name)

        val nodeRestored = strict.fromNode(strict.toNode(owner), PetOwner::class.java)
        assertEquals("strict", catOf(nodeRestored.pet).name)
    }

    @Test
    fun subtypeRegistrationIsValidated() {
        assertFailsWith<IllegalArgumentException> {
            DocumentMapperConfig.builder()
                .subtype(Pet::class.java, "invalid", Vehicle::class.java)
                .build()
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentMapperConfig.builder().subtype(Pet::class.java, " ", Cat::class.java)
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentMapperConfig.builder()
                .subtype(Pet::class.java, "dup", Cat::class.java)
                .subtype(Pet::class.java, "dup", Dog::class.java)
                .build()
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentMapperConfig.builder().typeProperty(Pet::class.java, " ")
        }
    }

    private fun catOf(value: Pet?): Cat = value as? Cat
        ?: fail("expected Cat but was ${value?.javaClass?.name}")

    @DocumentTypeInfo(property = "kind")
    interface Pet {
        var name: String?
    }

    @DocumentTypeName("cat")
    class Cat : Pet {
        override var name: String? = null
        var lives: Int = 0
        var friend: Pet? = null
    }

    class Dog : Pet {
        override var name: String? = null
        var weight: Int = 0
    }

    class UnregisteredPet : Pet {
        override var name: String? = null
    }

    abstract class Vehicle {
        var brand: String? = null
    }

    class Car : Vehicle() {
        var wheels: Int = 0
    }

    class Bike : Vehicle() {
        var gears: Int = 0
    }

    @DocumentTypeInfo(property = "toolType")
    abstract class Tool {
        var label: String? = null
    }

    @DocumentTypeName("hammer")
    class Hammer : Tool() {
        var weight: Int = 0
    }

    class PetOwner {
        var pet: Pet? = null
        var vehicle: Vehicle? = null
        var pets: List<Pet>? = null
        var garage: Map<String, Vehicle>? = null
    }
}
