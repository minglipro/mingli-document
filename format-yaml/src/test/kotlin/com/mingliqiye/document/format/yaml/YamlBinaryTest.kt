package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.yaml.exception.YamlParseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * typed binary：`!!binary` 与 [BinaryNode] 的互转。
 *
 * SnakeYAML 的安全构造器把 `!!binary` 解码为 `byte[]`；读取侧必须映射为
 * [BinaryNode]，写出侧必须写回 `!!binary` Base64 标量，任意字节序列往返不变。
 */
class YamlBinaryTest {

    @Test
    fun snakeYamlBinaryTagBecomesBinaryNode() {
        val node = YamlReader("data: !!binary AQID\n").read().asObject()["data"]!!

        assertEquals(NodeType.BINARY, node.type)
        assertTrue(node.isBinary)
        assertContentEquals(byteArrayOf(1, 2, 3), node.asBinary())
    }

    @Test
    fun binaryIsNoLongerBase64StringNode() {
        val node = YamlReader("data: !!binary AQID\n").read().asObject()["data"]!!

        assertFalse(node.isString)
        assertNull(node.asStringOrNull())
    }

    @Test
    fun readerSupportsLiteralBlockBinary() {
        val node = YamlReader("data: !!binary |-\n  AQID\n").read().asObject()["data"]!!

        assertContentEquals(byteArrayOf(1, 2, 3), node.asBinary())
    }

    @Test
    fun binaryNodeRoundTrip() {
        val bytes = ByteArray(256) { it.toByte() }
        val node = ObjectNode.of("data" to BinaryNode.of(bytes))

        val compact = node.toYamlString()
        val pretty = node.toYamlString(pretty = true)
        assertTrue(compact.contains("!!binary"))
        assertTrue(pretty.contains("!!binary"))

        val fromCompact = YamlReader(compact).read()
        val fromPretty = YamlReader(pretty).read()
        assertEquals(node, fromCompact)
        assertEquals(node, fromPretty)
        assertContentEquals(bytes, fromCompact.asObject()["data"]!!.asBinary())
        assertContentEquals(bytes, fromPretty.asObject()["data"]!!.asBinary())
    }

    @Test
    fun emptyBinaryRoundTrip() {
        val node = ObjectNode.of("data" to BinaryNode.of(ByteArray(0)))

        val restored = YamlReader(node.toYamlString()).read().asObject()["data"]!!

        assertTrue(restored.isBinary)
        assertContentEquals(ByteArray(0), restored.asBinary())
        assertEquals(node, YamlReader(node.toYamlString(pretty = true)).read())
    }

    @Test
    fun rootBinaryNodeRoundTrip() {
        val bytes = byteArrayOf(7, 8, -1, 0)
        val node = BinaryNode.of(bytes)

        val text = node.toYamlString()
        assertTrue(text.contains("!!binary"))

        val restored = YamlReader(text).read()
        assertTrue(restored.isBinary)
        assertContentEquals(bytes, restored.asBinary())
    }

    @Test
    fun binaryInsideCollectionRoundTrip() {
        val node = ObjectNode.of(
            "list" to ArrayNode.of(BinaryNode.of(byteArrayOf(-1, 0, 1)), StringNode.of("text"), NullNode.INSTANCE),
        )

        val restored = YamlReader(node.toYamlString(pretty = true)).read()

        assertEquals(node, restored)
        val list = restored.asObject()["list"]!!.asArray()
        assertContentEquals(byteArrayOf(-1, 0, 1), list[0].asBinary())
    }

    @Test
    fun mapperRoundTripsByteArrayProperty() {
        val mapper = YamlMapper()
        val blob = Blob(byteArrayOf(1, 2, 3, -1))

        val text = mapper.format(blob)
        assertTrue(text.contains("!!binary"))

        val restored = mapper.parse(text, Blob::class.java)
        assertContentEquals(blob.data, restored.data)
    }

    @Test
    fun mapperToNodeUsesBinaryNode() {
        val mapper = YamlMapper()

        val node = mapper.toNode(byteArrayOf(1, 2))

        assertTrue(node.isBinary)
        assertContentEquals(byteArrayOf(1, 2), node.asBinary())
    }

    @Test
    fun invalidBinaryPayloadThrowsParseException() {
        assertFailsWith<YamlParseException> {
            YamlReader("data: !!binary '***'\n").read()
        }
    }

    private class Blob(
        var data: ByteArray = ByteArray(0),
    )
}
