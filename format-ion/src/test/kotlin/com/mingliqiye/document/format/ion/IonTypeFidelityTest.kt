package com.mingliqiye.document.format.ion

import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.ion.exception.IonException
import com.mingliqiye.document.serialization.MissingFieldPolicy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ion 类型保真测试。
 */
class IonTypeFidelityTest {

    private val typeField = "\$ionType"
    private val valueField = "value"

    @Test
    fun textTimestampAndSymbolRoundTrip() {
        val text = """{at: 2024-01-02T03:04:05Z, sym: 'hello', plain: "hello"}"""

        val node = IonReader(text, preserveTypes = true).read()
        val obj = node.asObject()
        assertEquals("timestamp", obj["at"]?.asObject()?.get(typeField)?.asString())
        assertEquals("2024-01-02T03:04:05Z", obj["at"]?.asObject()?.get(valueField)?.asString())
        assertEquals("symbol", obj["sym"]?.asObject()?.get(typeField)?.asString())
        assertEquals("hello", obj["sym"]?.asObject()?.get(valueField)?.asString())
        assertTrue(obj["plain"]!!.isString)

        val mapper = IonMapper(IonConfig.builder().preserveTypes(true).build())
        val ion = mapper.format(node)
        assertTrue(ion.contains("2024-01-02T03:04:05Z"))
        assertEquals(node, IonReader(ion, preserveTypes = true).read())

        // 顶层标量同样保留类型。
        val topTimestamp = IonReader("2024-01-02T03:04:05Z", preserveTypes = true).read()
        assertEquals(topTimestamp, IonReader(mapper.format(topTimestamp), preserveTypes = true).read())
        val topSymbol = IonReader("'top-level'", preserveTypes = true).read()
        assertEquals(topSymbol, IonReader(mapper.format(topSymbol), preserveTypes = true).read())
    }

    @Test
    fun binaryClobAndBlobRoundTrip() {
        val text = """{c: {{"hi"}}, b: {{aGVsbG8=}}}"""

        val node = IonReader(text, preserveTypes = true).read()
        val obj = node.asObject()
        assertEquals("clob", obj["c"]?.asObject()?.get(typeField)?.asString())
        assertContentEquals("hi".toByteArray(Charsets.UTF_8), obj["c"]?.asObject()?.get(valueField)?.asBinary())
        assertEquals("blob", obj["b"]?.asObject()?.get(typeField)?.asString())
        assertContentEquals("hello".toByteArray(Charsets.UTF_8), obj["b"]?.asObject()?.get(valueField)?.asBinary())

        val mapper = IonMapper(IonConfig.builder().preserveTypes(true).build())
        val binary = mapper.formatBytes(node)
        assertTrue(binary.size > 4)
        assertEquals(0xE0.toByte(), binary[0])
        assertEquals(node, IonBinaryReader(binary, preserveTypes = true).read())

        // 文本路径同样识别 clob / blob 包装对象。
        assertEquals(node, IonReader(mapper.format(node), preserveTypes = true).read())

        // 关闭保真时，二进制 clob / blob 都还原为 BinaryNode。
        val plain = IonBinaryReader(binary).read().asObject()
        assertTrue(plain["c"]!!.isBinary)
        assertTrue(plain["b"]!!.isBinary)
    }

    @Test
    fun defaultConfigKeepsLegacyMapping() {
        val text = """{at: 2024-01-02T03:04:05Z, sym: 'x', c: {{"hi"}}}"""
        val obj = IonReader(text).read().asObject()
        assertEquals("2024-01-02T03:04:05Z", obj["at"]?.asString())
        assertEquals("x", obj["sym"]?.asString())
        assertTrue(obj["c"]!!.isBinary)

        val mapper = IonMapper()
        val node = mapper.parse(text, Node::class.java)
        assertEquals(obj, node)
        val ion = mapper.format(node)
        assertEquals(node, IonReader(ion).read())
        assertFalse(IonConfig.builder().build().preserveTypes)
    }

    @Test
    fun mapperConfigPlumbing() {
        val config = IonConfig.builder().preserveTypes(true).build()
        assertTrue(config.preserveTypes)
        assertEquals(
            MissingFieldPolicy.SET_EMPTY,
            IonConfig.builder().missingFieldPolicy(MissingFieldPolicy.SET_EMPTY).build().missingFieldPolicy,
        )

        val mapper = IonMapper(config)
        assertEquals("ion", mapper.formatName)

        val text = """{at: 2024-01-02T03:04:05Z, sym: 'hello'}"""
        val node = mapper.parse(text, Node::class.java)
        assertTrue(node.asObject()["at"]!!.isObject)
        assertEquals("symbol", node.asObject()["sym"]?.asObject()?.get(typeField)?.asString())

        val textRoundTrip = mapper.parse(mapper.format(node), Node::class.java)
        assertEquals(node, textRoundTrip)

        // 二进制解析在保真模式下回退到 Node 路径，因此包装对象被保留。
        val binary = mapper.formatBytes(node)
        val binaryRoundTrip = mapper.parse(binary, Node::class.java)
        assertEquals(node, binaryRoundTrip)

        val built = IonMapper.builder().config(config).build()
        assertEquals(node, IonReader(built.format(node), preserveTypes = true).read())
    }

    @Test
    fun ordinaryObjectsWithIonTypeFieldStayObjects() {
        val wrongValue = ObjectNode.of(
            typeField to StringNode.of("timestamp"),
            valueField to NumberNode.of(18),
        )
        assertEquals(wrongValue, textRoundTrip(wrongValue, preserveTypes = true))
        assertEquals(wrongValue, binaryRoundTrip(wrongValue, preserveTypes = true))

        val unknownType = ObjectNode.of(
            typeField to StringNode.of("custom"),
            valueField to StringNode.of("x"),
        )
        assertEquals(unknownType, textRoundTrip(unknownType, preserveTypes = true))
        assertEquals(unknownType, binaryRoundTrip(unknownType, preserveTypes = true))

        val extraField = ObjectNode.of(
            typeField to StringNode.of("timestamp"),
            valueField to StringNode.of("2024-01-02T03:04:05Z"),
            "note" to StringNode.of("extra"),
        )
        assertEquals(extraField, textRoundTrip(extraField, preserveTypes = true))
        assertEquals(extraField, binaryRoundTrip(extraField, preserveTypes = true))
    }

    @Test
    fun invalidTimestampWrapperThrows() {
        val node = ObjectNode.of(
            typeField to StringNode.of("timestamp"),
            valueField to StringNode.of("not-a-timestamp"),
        )

        assertFailsWith<IonException> {
            textRoundTrip(node, preserveTypes = true)
        }
        assertFailsWith<IonException> {
            binaryRoundTrip(node, preserveTypes = true)
        }
    }

    @Test
    fun blobAndClobAreStillBinaryNodeWhenFidelityDisabled() {
        val node = ObjectNode.of(
            "b" to BinaryNode.of(byteArrayOf(1, 2, 3)),
        )

        assertTrue(textRoundTrip(node, preserveTypes = false).asObject()["b"]!!.isBinary)
        assertTrue(binaryRoundTrip(node, preserveTypes = false).asObject()["b"]!!.isBinary)
    }

    private fun textRoundTrip(node: Node, preserveTypes: Boolean): Node {
        val mapper = mapper(preserveTypes)
        return IonReader(mapper.format(node), preserveTypes = preserveTypes).read()
    }

    private fun binaryRoundTrip(node: Node, preserveTypes: Boolean): Node {
        val mapper = mapper(preserveTypes)
        return IonBinaryReader(mapper.formatBytes(node), preserveTypes = preserveTypes).read()
    }

    private fun mapper(preserveTypes: Boolean): IonMapper = if (preserveTypes) {
        IonMapper(IonConfig.builder().preserveTypes(true).build())
    } else {
        IonMapper()
    }
}
