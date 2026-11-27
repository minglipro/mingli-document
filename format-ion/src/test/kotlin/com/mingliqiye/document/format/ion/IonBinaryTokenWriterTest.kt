package com.mingliqiye.document.format.ion

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [IonBinaryTokenWriter] 测试。
 */
class IonBinaryTokenWriterTest {

    @Test
    fun writesStructWithScalarsArraysAndBinary() {
        val writer = IonBinaryTokenWriter()
        writer.startObject()
        writer.fieldName("name")
        writer.writeString("小于")
        writer.fieldName("age")
        writer.writeNumber("18")
        writer.fieldName("pi")
        writer.writeNumber("3.14")
        writer.fieldName("active")
        writer.writeBoolean(true)
        writer.fieldName("missing")
        writer.writeNull()
        writer.fieldName("data")
        writer.writeBinary(byteArrayOf(1, 2, -1))
        writer.fieldName("tags")
        writer.startArray()
        writer.writeString("a")
        writer.writeString("b")
        writer.endArray()
        writer.fieldName("address")
        writer.startObject()
        writer.fieldName("city")
        writer.writeString("杭州")
        writer.endObject()
        writer.endObject()

        val bytes = writer.toByteArray()
        assertTrue(bytes.size > 4)
        assertEquals(0xE0.toByte(), bytes[0])
        assertEquals(0x01.toByte(), bytes[1])
        assertEquals(0x00.toByte(), bytes[2])
        assertEquals(0xEA.toByte(), bytes[3])

        val obj = IonBinaryReader.parse(bytes).asObject()
        assertEquals("小于", obj["name"]?.asString())
        assertEquals(18, obj["age"]?.asNumber()?.toInt())
        assertEquals(0, BigDecimal("3.14").compareTo(obj["pi"]!!.asNumber().value))
        assertEquals(true, obj["active"]?.asBoolean())
        assertTrue(obj["missing"]!!.isNull)
        assertContentEquals(byteArrayOf(1, 2, -1), obj["data"]?.asBinary())
        assertEquals(listOf("a", "b"), obj["tags"]?.asArray()?.asList()?.map { it.asString() })
        assertEquals("杭州", obj["address"]?.asObject()?.get("city")?.asString())
    }

    @Test
    fun numbersFollowIntAndDecimalRules() {
        val writer = IonBinaryTokenWriter()
        writer.startArray()
        writer.writeInt(1)
        writer.writeLong(2L)
        writer.writeDouble(1.5)
        writer.writeNumber("999999999999999999999999")
        writer.endArray()

        val array = IonBinaryReader.parse(writer.toByteArray()).asArray()
        assertEquals(4, array.size)
        assertEquals(1, array[0].asNumber().toInt())
        assertEquals(2L, array[1].asNumber().toLong())
        assertEquals(0, BigDecimal("1.5").compareTo(array[2].asNumber().value))
        assertEquals("999999999999999999999999", array[3].asNumber().raw)
    }

    @Test
    fun writeNodeWritesNodeTree() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "list" to ArrayNode.of(NumberNode.of(1), BooleanNode.of(true), NullNode.INSTANCE),
            "blob" to BinaryNode.of(byteArrayOf(9, 8)),
        )

        val writer = IonBinaryTokenWriter()
        writer.writeNode(node)

        assertEquals(node, IonBinaryReader.parse(writer.toByteArray()))
    }

    @Test
    fun closeIsIdempotentAndRequiredBeforeReadingBytes() {
        val writer = IonBinaryTokenWriter()
        writer.writeNumber("1")
        writer.close()
        writer.close()

        assertTrue(writer.toByteArray().isNotEmpty())
    }
}
