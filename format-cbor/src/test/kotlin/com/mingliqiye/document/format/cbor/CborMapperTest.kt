package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.serialization.BinaryDocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [CborMapper] 测试。
 */
class CborMapperTest {

    @Test
    fun binaryMapperMapsBeanToCborAndBack() {
        val mapper: BinaryDocumentMapper = CborMapper()
        assertEquals("cbor", mapper.formatName)

        val person = Person().apply {
            name = "小于"
            age = 18
            tags = listOf("a", "b")
            address = Address().apply { city = "杭州" }
            data = byteArrayOf(1, 2, 3)
        }

        val bytes = mapper.formatBytes(person)
        assertTrue(bytes.isNotEmpty())

        val restored = mapper.parse(bytes, Person::class.java)
        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals("杭州", restored.address?.city)
        assertContentEquals(byteArrayOf(1, 2, 3), restored.data)
    }

    @Test
    fun nestedBeansAndCollectionsRoundTrip() {
        val mapper = CborMapper()
        val people = listOf(
            Person().apply {
                name = "a"
                tags = listOf("x")
                address = Address().apply { city = "A" }
            },
            Person().apply {
                name = "b"
                tags = listOf("y", "z")
            },
        )

        val restored = mapper.parse(
            mapper.formatBytes(people),
            DocumentTypeReference.of<List<Person>>(),
        )
        assertEquals(listOf("a", "b"), restored.map { it.name })
        assertEquals(listOf("x"), restored[0].tags)
        assertEquals(listOf("y", "z"), restored[1].tags)
        assertEquals("A", restored[0].address?.city)
    }

    @Test
    fun textApisAreNotSupported() {
        val mapper: BinaryDocumentMapper = CborMapper()

        assertFailsWith<DocumentSerializationException> {
            mapper.format(Person())
        }
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("{}", Person::class.java)
        }
    }

    @Test
    fun builderAndConfigConstructorAreSupported() {
        val built = CborMapper.builder().build()
        assertEquals("cbor", built.formatName)
        val person = Person().apply { name = "built" }
        assertEquals("built", built.parse(built.formatBytes(person), Person::class.java).name)

        val configured = CborMapper.builder()
            .config(DocumentMapperConfig.builder().writeNulls(false).build())
            .build()
        val node = configured.toNode(Person().apply { name = "no-nulls" }).asObject()
        assertFalse(node.containsKey("address"))
        assertFalse(node.containsKey("data"))

        val withConstructorConfig = CborMapper(
            DocumentMapperConfig.builder().writeNulls(false).build(),
        )
        assertTrue(withConstructorConfig.toNode(Person()).isObject)
    }

    private class Person {
        var name: String? = null
        var age: Int = 0
        var tags: List<String> = emptyList()
        var address: Address? = null
        var data: ByteArray? = null
    }

    private class Address {
        var city: String? = null
    }
}
