package com.mingliqiye.document.format.ion

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.serialization.DocumentMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [IonMapper] 测试。
 */
class IonMapperTest {

    @Test
    fun mapperMapsBeanToIonAndBack() {
        val mapper: DocumentMapper = IonMapper()
        assertEquals("ion", mapper.formatName)

        val person = Person().apply {
            name = "小于"
            age = 18
            tags = listOf("a", "b")
            address = Address().apply { city = "杭州" }
        }

        val ion = mapper.format(person)
        assertTrue(ion.startsWith("{"))
        assertTrue(ion.contains("小于"))

        val restored = mapper.parse(ion, Person::class.java)
        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals("杭州", restored.address?.city)
    }

    @Test
    fun nestedCollectionsRoundTrip() {
        val mapper = IonMapper()
        val people = listOf(
            Person().apply {
                name = "a"
                tags = listOf("x")
            },
            Person().apply {
                name = "b"
                tags = listOf("y", "z")
            },
        )

        val ion = mapper.format(people)
        assertTrue(ion.startsWith("["))
        val restored = mapper.parse(ion, DocumentTypeReference.of<List<Person>>())
        assertEquals(listOf("a", "b"), restored.map { it.name })
        assertEquals(listOf("y", "z"), restored[1].tags)
    }

    @Test
    fun prettyOutputAndBuilder() {
        val mapper = IonMapper.builder().build()
        assertEquals("ion", mapper.formatName)

        val pretty = mapper.formatPretty(Person().apply { name = "小于" })
        assertTrue(pretty.contains("\n"))
        assertEquals("小于", mapper.parse(pretty, Person::class.java).name)
    }

    @Test
    fun mapValuesAreSupported() {
        val mapper = IonMapper()
        val node = mapper.toNode(mapOf("count" to 2, "nested" to mapOf("ok" to true)))

        val ion = mapper.format(node)
        assertEquals(node, IonReader(ion).read())
    }

    @Test
    fun commonConfigConstructorIsSupported() {
        val mapper = IonMapper(
            com.mingliqiye.document.serialization.DocumentMapperConfig.builder()
                .writeNulls(false)
                .build(),
        )

        val ion = mapper.format(Person())
        assertTrue(ion.startsWith("{"))
        assertTrue(!ion.contains("address"))
    }

    private class Person {
        var name: String? = null
        var age: Int = 0
        var tags: List<String> = emptyList()
        var address: Address? = null
    }

    private class Address {
        var city: String? = null
    }
}
