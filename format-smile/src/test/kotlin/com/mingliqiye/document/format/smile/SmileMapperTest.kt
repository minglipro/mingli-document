package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
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

    @Test
    fun parseByteArrayUsesTokenDirectBeanPath() {
        val mapper = RecordingSmileMapper()
        val bytes = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "age" to NumberNode.of(28),
            "tags" to ArrayNode.of(StringNode.of("x"), StringNode.of("y")),
            "address" to ObjectNode.of("city" to StringNode.of("杭州")),
            "unknown" to ObjectNode.of(
                "nested" to ArrayNode.of(NumberNode.of(1), BooleanNode.of(true)),
            ),
        ).toSmileBytes()

        val person = mapper.parse(bytes, Person::class.java)

        assertEquals(1, mapper.tokenReaderCalls)
        assertEquals("小于", person.name)
        assertEquals(28, person.age)
        assertEquals(listOf("x", "y"), person.tags)
        assertEquals("杭州", person.address?.city)

        val listBytes = SmileMapper().formatBytes(listOf(Person().apply { name = "a" }))
        val people = mapper.parse(listBytes, DocumentTypeReference.of<List<Person>>())
        assertEquals(2, mapper.tokenReaderCalls)
        assertEquals("a", people[0].name)
    }

    @Test
    fun nodePathIsKeptAlongsideTokenPath() {
        val mapper = SmileMapper()
        val bytes = mapper.formatBytes(
            Person().apply {
                name = "节点"
                age = 5
                tags = listOf("n")
                address = Address().apply { city = "上海" }
            },
        )

        val node = SmileReader(bytes).read()
        val restored = mapper.fromNode(node, Person::class.java)
        assertEquals("节点", restored.name)
        assertEquals(5, restored.age)
        assertEquals(listOf("n"), restored.tags)
        assertEquals("上海", restored.address?.city)

        assertEquals(node, SmileReader(mapper.formatBytes(node)).read())
    }

    @Test
    fun formatBytesWritesBeanThroughTokenPath() {
        val mapper = SmileMapper()
        val person = Person().apply {
            name = "小于"
            age = 24
            tags = listOf("a", "b")
            address = Address().apply { city = "杭州" }
        }

        val bytes = mapper.formatBytes(person)
        // Token 直写与 SmileWriter 的 Node 写出路径产生相同 Smile 字节。
        assertContentEquals(mapper.toNode(person).toSmileBytes(), bytes)

        val restored = mapper.parse(bytes, Person::class.java)
        assertEquals("小于", restored.name)
        assertEquals(24, restored.age)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals("杭州", restored.address?.city)
    }

    @Test
    fun nestedBeanAndByteArrayRoundTripThroughTokenWriter() {
        val mapper = SmileMapper()
        val holder = NestedHolder().apply {
            title = "根"
            person = Person().apply {
                name = "嵌套"
                age = 30
                tags = listOf("x")
                address = Address().apply { city = "苏州" }
            }
            data = byteArrayOf(0, 1, 2, 127, -1)
        }

        val bytes = mapper.formatBytes(holder)
        val restored = mapper.parse(bytes, NestedHolder::class.java)
        assertEquals("根", restored.title)
        assertEquals("嵌套", restored.person?.name)
        assertEquals(30, restored.person?.age)
        assertEquals(listOf("x"), restored.person?.tags)
        assertEquals("苏州", restored.person?.address?.city)
        assertContentEquals(byteArrayOf(0, 1, 2, 127, -1), restored.data)
    }

    private class NestedHolder {
        var title: String? = null
        var person: Person? = null
        var data: ByteArray? = null
    }

    private class RecordingSmileMapper : SmileMapper() {
        var tokenReaderCalls: Int = 0
            private set

        override fun createTokenReader(source: ByteArray): DocumentTokenReader {
            tokenReaderCalls++
            return super.createTokenReader(source)
        }
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
