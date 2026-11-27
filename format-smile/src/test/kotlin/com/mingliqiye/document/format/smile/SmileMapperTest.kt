package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SmileMapper] 测试。
 */
class SmileMapperTest {

    @Test
    fun mapperMapsBeanToSmileBytesAndBack() {
        val mapper = SmileMapper()
        assertEquals("smile", mapper.formatName)

        val person = Person().apply {
            name = "小于"
            age = 18
            tags = listOf("a", "b")
            address = Address().apply { city = "杭州" }
        }

        val bytes = mapper.formatBytes(person)
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes.size > 4)

        val restored = mapper.parse(bytes, Person::class.java)
        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals("杭州", restored.address?.city)
    }

    @Test
    fun nestedCollectionsRoundTrip() {
        val mapper = SmileMapper()
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

        val bytes = mapper.formatBytes(people)
        val restored = mapper.parse(bytes, DocumentTypeReference.of<List<Person>>())
        assertEquals(listOf("a", "b"), restored.map { it.name })
        assertEquals(listOf("y", "z"), restored[1].tags)
    }

    @Test
    fun byteArrayPropertiesRoundTripThroughBinaryNode() {
        val mapper = SmileMapper()
        val holder = ByteArrayHolder().apply { data = byteArrayOf(0, 1, 2, 127, -1) }

        val bytes = mapper.formatBytes(holder)
        val restored = mapper.parse(bytes, ByteArrayHolder::class.java)
        assertContentEquals(byteArrayOf(0, 1, 2, 127, -1), restored.data)
    }

    @Test
    fun binaryOnlyMapperRejectsTextApi() {
        val mapper = SmileMapper()

        assertFailsWith<DocumentSerializationException> {
            mapper.format(Person())
        }
        assertFailsWith<DocumentSerializationException> {
            mapper.formatPretty(Person())
        }
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("{}", Person::class.java)
        }
    }

    @Test
    fun builderIsSupported() {
        val mapper = SmileMapper.builder().build()

        assertEquals("smile", mapper.formatName)
        val node = mapper.toNode(mapOf("count" to 2, "nested" to mapOf("ok" to true)))
        assertEquals(node, SmileReader(mapper.formatBytes(node)).read())
    }

    @Test
    fun commonConfigConstructorIsSupported() {
        val mapper = SmileMapper(
            DocumentMapperConfig.builder()
                .writeNulls(false)
                .build(),
        )

        val bytes = mapper.formatBytes(Person().apply { name = "小于" })
        val node = SmileReader(bytes).read().asObject()
        assertFalse(node.containsKey("address"))
        assertTrue(node.containsKey("name"))
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

    private class ByteArrayHolder {
        var data: ByteArray? = null
    }
}
