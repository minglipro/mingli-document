package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.smile.exception.SmileException
import com.mingliqiye.document.serialization.DocumentTokenType
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [SmileTokenWriter] 测试。
 */
class SmileTokenWriterTest {

    @Test
    fun writesObjectArrayAndNestedTokens() {
        val writer = SmileTokenWriter()
        try {
            writer.startObject()
            writer.fieldName("name")
            writer.writeString("小于")
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
            writer.fieldName("active")
            writer.writeBoolean(true)
            writer.fieldName("missing")
            writer.writeNull()
            writer.endObject()

            val bytes = writer.toByteArray()
            assertTrue(bytes.isNotEmpty())
            assertEquals(0x3A.toByte(), bytes[0])

            val node = SmileReader(bytes).read().asObject()
            assertEquals("小于", node["name"]?.asString())
            assertEquals(listOf("a", "b"), node["tags"]?.asArray()?.map { it.asString() })
            assertEquals("杭州", node["address"]?.asObject()?.get("city")?.asString())
            assertEquals(true, node["active"]?.asBoolean())
            assertTrue(node["missing"]!!.isNull)
        } finally {
            writer.close()
        }
    }

    @Test
    fun writesNumbersWithSameClassificationAsSmileWriter() {
        val node = ObjectNode.of(
            "count" to NumberNode.of(18),
            "negative" to NumberNode.of(-1),
            "big" to NumberNode.of(BigInteger("123456789012345678901234567890")),
            "pi" to NumberNode.of(BigDecimal("3.14")),
        )
        val writer = SmileTokenWriter()
        try {
            writer.writeNode(node)
            val tokenBytes = writer.toByteArray()

            // Token 直写（数字按 raw 分类）与 SmileWriter 的 Node 路径输出逐字节一致。
            assertContentEquals(node.toSmileBytes(), tokenBytes)
            assertEquals(node, SmileReader(tokenBytes).read())
        } finally {
            writer.close()
        }
    }

    @Test
    fun writesBinaryAsSmileEmbeddedObject() {
        val payload = ByteArray(24) { (it * 11).toByte() }
        val writer = SmileTokenWriter()
        try {
            writer.startObject()
            writer.fieldName("data")
            writer.writeBinary(payload)
            writer.endObject()
            val bytes = writer.toByteArray()

            assertContentEquals(payload, SmileReader(bytes).read().asObject()["data"]?.asBinary())

            val reader = SmileTokenReader(bytes)
            assertEquals(DocumentTokenType.START_OBJECT, reader.nextToken())
            assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
            assertEquals(DocumentTokenType.BINARY, reader.nextToken())
            assertContentEquals(payload, reader.binaryValue)
        } finally {
            writer.close()
        }
    }

    @Test
    fun toByteArrayFlushesAndCloseIsIdempotent() {
        val writer = SmileTokenWriter()
        writer.startArray()
        writer.writeNumber("1")
        writer.writeBoolean(false)
        writer.endArray()
        writer.close()
        writer.close()

        val array = SmileReader(writer.toByteArray()).read().asArray()
        assertEquals(1, array[0].asNumber().toInt())
        assertEquals(false, array[1].asBoolean())
    }

    @Test
    fun invalidNumberRawThrowsSmileException() {
        val writer = SmileTokenWriter()
        try {
            assertFailsWith<SmileException> {
                writer.writeNumber("not-a-number")
            }
        } finally {
            writer.close()
        }
    }
}
