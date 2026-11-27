package com.mingliqiye.document.format.ion

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.ion.exception.IonParseException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 二进制 Ion 读写测试。
 */
class IonBinaryTest {

    @Test
    fun binaryRoundTripPreservesNodeTree() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于 & <ion>"),
            "age" to NumberNode.of(18),
            "pi" to NumberNode.of(BigDecimal("3.14")),
            "active" to BooleanNode.of(true),
            "missing" to NullNode.INSTANCE,
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "address" to ObjectNode.of("city" to StringNode.of("杭州")),
            "data" to BinaryNode.of(byteArrayOf(0, 1, 2, -1)),
            "matrix" to ArrayNode.of(
                ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
                ObjectNode.of("nested" to NullNode.INSTANCE),
            ),
        )

        val bytes = node.toIonBytes()
        assertTrue(bytes.size > 4)
        assertEquals(0xE0.toByte(), bytes[0])
        assertEquals(0x01.toByte(), bytes[1])
        assertEquals(0x00.toByte(), bytes[2])
        assertEquals(0xEA.toByte(), bytes[3])

        val restored = IonBinaryReader.parse(bytes)
        assertEquals(node, restored)
        assertContentEquals(byteArrayOf(0, 1, 2, -1), restored.asObject()["data"]?.asBinary())
    }

    @Test
    fun binaryReaderCompanionAndByteArrayExtension() {
        val bytes = NumberNode.of(42).toIonBytes()

        assertEquals(42, IonBinaryReader.parse(bytes).asNumber().toInt())
        assertEquals(42, bytes.toIonNode().asNumber().toInt())
    }

    @Test
    fun emptyBytesThrow() {
        assertFailsWith<IonParseException> {
            IonBinaryReader(ByteArray(0)).read()
        }
    }

    @Test
    fun invalidBytesThrow() {
        assertFailsWith<IonParseException> {
            IonBinaryReader(byteArrayOf(0x7F, 0x00, 0x00, 0x00)).read()
        }
    }

    @Test
    fun invalidBinaryVersionMarkerBytesThrow() {
        val badBytes = byteArrayOf(0xE0.toByte(), 0x01, 0x00, 0xEA.toByte(), 0xFF.toByte(), 0xFF.toByte())

        assertFailsWith<IonParseException> {
            IonBinaryReader(badBytes).read()
        }
    }

    @Test
    fun truncatedBinaryThrows() {
        val bytes = ObjectNode.of("a" to NumberNode.of(1)).toIonBytes()

        assertFailsWith<IonParseException> {
            IonBinaryReader(bytes.copyOf(bytes.size - 2)).read()
        }
    }

    @Test
    fun multipleTopLevelValuesThrow() {
        val first = NumberNode.of(1).toIonBytes()
        val second = NumberNode.of(2).toIonBytes()

        assertFailsWith<IonParseException> {
            IonBinaryReader(first + second).read()
        }
    }

    @Test
    fun mapperFormatBytesAndByteParse() {
        val mapper = IonMapper()
        val holder = ByteArrayHolder().apply { data = byteArrayOf(9, 8, 7, 0, -1) }

        val bytes = mapper.formatBytes(holder)
        val restored = mapper.parse(bytes, ByteArrayHolder::class.java)

        assertContentEquals(byteArrayOf(9, 8, 7, 0, -1), restored.data)
    }

    @Test
    fun mapperBeanRoundTripWithNestedCollections() {
        val mapper = IonMapper()
        val person = Person().apply {
            name = "小于"
            age = 18
            tags = listOf("a", "b")
            address = Address().apply { city = "杭州" }
        }

        val bytes = mapper.formatBytes(person)
        val restored = mapper.parse(bytes, Person::class.java)
        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals("杭州", restored.address?.city)

        val second = Person().apply {
            name = "b"
            tags = listOf("y", "z")
        }
        val listBytes = mapper.formatBytes(listOf(person, second))
        val restoredList = mapper.parse(listBytes, DocumentTypeReference.of<List<Person>>())
        assertEquals(listOf("小于", "b"), restoredList.map { it.name })
        assertEquals(listOf("y", "z"), restoredList[1].tags)
    }

    @Test
    fun textIonBehaviorIsPreserved() {
        val node = ObjectNode.of(
            "data" to BinaryNode.of(byteArrayOf(9, 8, 7)),
            "name" to StringNode.of("小于"),
        )

        val text = node.toIonString()
        assertTrue(text.contains("{{"))
        assertEquals(node, text.toIonNode())
    }

    private class ByteArrayHolder {
        var data: ByteArray? = null
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
