package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.cbor.exception.CborException
import com.mingliqiye.document.serialization.BinaryDocumentMapper
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

    @Test
    fun parseByteArrayUsesTokenDirectBeanPath() {
        val mapper = RecordingCborMapper()
        val bytes = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "age" to NumberNode.of(28),
            "tags" to ArrayNode.of(StringNode.of("x"), StringNode.of("y")),
            "address" to ObjectNode.of("city" to StringNode.of("杭州")),
            "data" to BinaryNode.of(byteArrayOf(1, 2, 3)),
            "unknown" to ObjectNode.of(
                "nested" to ArrayNode.of(NumberNode.of(1), BooleanNode.of(true)),
            ),
        ).toCborBytes()

        val person = mapper.parse(bytes, Person::class.java)

        assertEquals(1, mapper.tokenReaderCalls)
        assertEquals("小于", person.name)
        assertEquals(28, person.age)
        assertEquals(listOf("x", "y"), person.tags)
        assertEquals("杭州", person.address?.city)
        assertContentEquals(byteArrayOf(1, 2, 3), person.data)

        val listBytes = CborMapper().formatBytes(listOf(Person().apply { name = "a" }))
        val people = mapper.parse(listBytes, DocumentTypeReference.of<List<Person>>())
        assertEquals(2, mapper.tokenReaderCalls)
        assertEquals("a", people[0].name)
    }

    @Test
    fun nodePathIsKeptAlongsideTokenPath() {
        val mapper = CborMapper()
        val bytes = mapper.formatBytes(
            Person().apply {
                name = "节点"
                age = 5
                tags = listOf("n")
                address = Address().apply { city = "上海" }
                data = byteArrayOf(7, 8)
            },
        )

        val node = CborReader(bytes).read()
        val restored = mapper.fromNode(node, Person::class.java)
        assertEquals("节点", restored.name)
        assertEquals(5, restored.age)
        assertEquals(listOf("n"), restored.tags)
        assertEquals("上海", restored.address?.city)
        assertContentEquals(byteArrayOf(7, 8), restored.data)

        assertEquals(node, CborReader(mapper.formatBytes(node)).read())
    }

    @Test
    fun formatBytesUsesStreamingTokenWriter() {
        val mapper = CborMapper()
        val bytes = mapper.formatBytes(
            Person().apply {
                name = "token"
                age = 9
                tags = listOf("t")
            },
        )

        // Token 直写使用 CBOR 不定长 map（0xBF ... 0xFF），证明未经过 Node 定长写出。
        assertEquals(0xBF, bytes.first().toInt() and 0xFF)
        assertEquals(0xFF, bytes.last().toInt() and 0xFF)

        val node = CborReader(bytes).read().asObject()
        assertEquals("token", node["name"]?.asString())
        assertEquals(9, node["age"]?.asNumber()?.toInt())
        assertEquals(1, node["tags"]?.asArray()?.size)
    }

    @Test
    fun formatBytesRoundTripsBeanWithBinaryAndNestedBean() {
        val mapper = CborMapper()
        val person = Person().apply {
            name = "直写"
            age = 33
            tags = listOf("a", "b")
            address = Address().apply { city = "南京" }
            data = byteArrayOf(0, 1, 2, 127, -1)
        }

        val restored = mapper.parse(mapper.formatBytes(person), Person::class.java)
        assertEquals("直写", restored.name)
        assertEquals(33, restored.age)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals("南京", restored.address?.city)
        assertContentEquals(byteArrayOf(0, 1, 2, 127, -1), restored.data)
    }

    @Test
    fun formatBytesRejectsNonFiniteFloatingPoint() {
        val holder = DoubleHolder().apply { value = Double.NaN }

        assertFailsWith<CborException> {
            CborMapper().formatBytes(holder)
        }
    }

    private class RecordingCborMapper : CborMapper() {
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
        var data: ByteArray? = null
    }

    private class Address {
        var city: String? = null
    }

    private class DoubleHolder {
        var value: Double = 0.0
    }
}
