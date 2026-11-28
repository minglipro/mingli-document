package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.cbor.exception.CborParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * CBOR sequence 多值读写测试。
 */
class CborSequenceTest {

    @Test
    fun emptyInputYieldsEmptyList() {
        assertTrue(CborSequenceReader(ByteArray(0)).readAll().isEmpty())
        assertTrue(CborSequenceReader.parseAll(ByteArray(0)).isEmpty())
        assertTrue(ByteArray(0).toCborNodes().isEmpty())
        assertTrue(CborMapper().parseAllToNodes(ByteArray(0)).isEmpty())
        assertTrue(CborMapper().parseAll(ByteArray(0), SequenceBean::class.java).isEmpty())
    }

    @Test
    fun singleItemSequenceMatchesSingleValueReader() {
        val node = ObjectNode.of(
            "name" to StringNode.of("only"),
            "values" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
        )
        val bytes = node.toCborBytes()

        assertEquals(listOf(node), CborSequenceReader(bytes).readAll())
        assertEquals(listOf(node), CborSequenceReader.parseAll(bytes))
        assertEquals(listOf(node), bytes.toCborNodes())

        // 单值入口语义不变：多个数据项仍然报错。
        assertFailsWith<CborParseException> {
            CborReader(bytes + bytes).read()
        }
    }

    @Test
    fun multipleItemsKeepOrderAndRoundTrip() {
        val nodes: List<Node> = listOf(
            ObjectNode.of("a" to NumberNode.of(1)),
            ArrayNode.of(StringNode.of("x"), BooleanNode.of(true)),
            NumberNode.of(18),
            StringNode.of("小于"),
            BinaryNode.of(byteArrayOf(0, 1, 2, -1)),
            NullNode.INSTANCE,
        )
        val bytes = nodes.toCborSequenceBytes()

        // sequence 字节就是各数据项自描述编码的首尾相接。
        val expectedBytes = nodes.fold(ByteArray(0)) { accumulated, node -> accumulated + node.toCborBytes() }
        assertContentEquals(expectedBytes, bytes)
        assertEquals(nodes, CborSequenceReader(bytes).readAll())
        assertEquals(nodes, CborSequenceReader.parseAll(bytes))
        assertEquals(nodes, bytes.toCborNodes())
        assertEquals(nodes, CborSequenceWriter().writeAll(nodes).toCborNodes())
        assertEquals(nodes, CborSequenceWriter.formatAll(nodes).toCborNodes())
    }

    @Test
    fun emptySequenceWritesEmptyBytes() {
        assertContentEquals(ByteArray(0), emptyList<Node>().toCborSequenceBytes())
        assertContentEquals(ByteArray(0), CborSequenceWriter().writeAll(emptyList()))
        assertContentEquals(ByteArray(0), CborSequenceWriter.formatAll(emptyList()))

        val mapper = CborMapper()
        assertContentEquals(ByteArray(0), mapper.formatAll(emptyList()))
        assertTrue(mapper.parseAllToNodes(mapper.formatAll(emptyList())).isEmpty())
    }

    @Test
    fun maxItemsLimitsItemCount() {
        val bytes = listOf(
            NumberNode.of(1),
            NumberNode.of(2),
            NumberNode.of(3),
        ).toCborSequenceBytes()

        assertEquals(3, CborSequenceReader(bytes).readAll().size)
        assertEquals(3, CborSequenceReader(bytes, maxItems = 0).readAll().size)
        // 3 项时 maxItems=2 触发；maxItems=3 恰好通过。
        assertFailsWith<CborParseException> {
            CborSequenceReader(bytes, maxItems = 2).readAll()
        }
        assertEquals(3, CborSequenceReader(bytes, maxItems = 3).readAll().size)
        assertFailsWith<CborParseException> {
            CborSequenceReader.parseAll(bytes, maxItems = 2)
        }
        assertEquals(3, CborSequenceReader.parseAll(bytes, maxItems = 3).size)
        assertFailsWith<CborParseException> {
            CborMapper().parseAllToNodes(bytes, maxItems = 2)
        }
        assertEquals(3, CborMapper().parseAllToNodes(bytes, maxItems = 3).size)
    }

    @Test
    fun depthLimitAppliesToEachItemSeparately() {
        // 每项深度 2（根数组 + 内层数组）；两个数据项共享同一 maxDepth 计数起点。
        val nested = ArrayNode.of(ArrayNode.of(StringNode.of("deep")))
        val bytes = listOf(nested, nested).toCborSequenceBytes()

        assertEquals(2, CborSequenceReader(bytes, maxDepth = 2).readAll().size)
        assertFailsWith<CborParseException> {
            CborSequenceReader(bytes, maxDepth = 1).readAll()
        }
        assertFailsWith<CborParseException> {
            CborSequenceReader.parseAll(bytes, maxDepth = 1)
        }
    }

    @Test
    fun malformedOrTruncatedSequenceThrows() {
        val first = NumberNode.of(1).toCborBytes()

        // 合法项之后的数组声明了 2 个元素但数据截断。
        assertFailsWith<CborParseException> {
            CborSequenceReader(first + byteArrayOf(0x82.toByte())).readAll()
        }
        // 合法项之后的非法字节。
        assertFailsWith<CborParseException> {
            CborSequenceReader(first + byteArrayOf(0xFF.toByte())).readAll()
        }
        // 第一项就非法。
        assertFailsWith<CborParseException> {
            CborSequenceReader(byteArrayOf(0x1C.toByte())).readAll()
        }
        // 任一数据项包含 NaN 浮点时整体报错。
        assertFailsWith<CborParseException> {
            CborSequenceReader(first + hex("f97e00")).readAll()
        }
        assertFailsWith<CborParseException> {
            CborMapper().parseAllToNodes(first + byteArrayOf(0x82.toByte()))
        }
    }

    @Test
    fun tagPreservationAppliesToEachItem() {
        // tag(100) "abc"、1、tag(1000) {"a":1}
        val bytes = hex("d86463616263") + hex("01") + hex("d903e8a1616101")

        val preserved = CborSequenceReader(bytes, preserveTags = true).readAll()
        assertEquals(
            listOf(
                wrapper(100, StringNode.of("abc")),
                NumberNode.of(1),
                wrapper(1000, ObjectNode.of("a" to NumberNode.of(1))),
            ),
            preserved,
        )
        assertEquals(preserved, CborSequenceReader.parseAll(bytes, preserveTags = true))
        assertContentEquals(bytes, preserved.toCborSequenceBytes(preserveTags = true))
        assertEquals(
            preserved,
            CborSequenceReader(preserved.toCborSequenceBytes(preserveTags = true), preserveTags = true).readAll(),
        )

        // 默认模式忽略 tag，类型映射与单值读取一致。
        assertEquals(
            listOf(StringNode.of("abc"), NumberNode.of(1), ObjectNode.of("a" to NumberNode.of(1))),
            CborSequenceReader(bytes).readAll(),
        )
        // 非保真写出会把包装对象当普通对象写出。
        assertEquals(
            listOf(wrapper(100, StringNode.of("abc"))),
            CborSequenceReader(listOf(wrapper(100, StringNode.of("abc"))).toCborSequenceBytes()).readAll(),
        )
    }

    @Test
    fun canonicalSequenceWriterSortsMapKeys() {
        val nodes = listOf(ObjectNode.of("b" to NumberNode.of(2), "a" to NumberNode.of(1)))
        val canonicalBytes = nodes.toCborSequenceBytes(canonical = true)

        assertEquals("a2616101616202", canonicalBytes.toHex())
        assertEquals(nodes, canonicalBytes.toCborNodes())
    }

    @Test
    fun mapperParsesBeansFromSequence() {
        val mapper = CborMapper()
        val beans = listOf(
            SequenceBean().apply {
                name = "a"
                age = 1
            },
            SequenceBean().apply {
                name = "b"
                age = 2
            },
        )
        val bytes = mapper.formatAll(beans)

        val restored = mapper.parseAll(bytes, SequenceBean::class.java)
        assertEquals(listOf("a", "b"), restored.map { it.name })
        assertEquals(listOf(1, 2), restored.map { it.age })
        assertEquals(listOf("a", "b"), mapper.parseAll<SequenceBean>(bytes).map { it.name })
        assertEquals(
            listOf("a", "b"),
            mapper.parseAll(bytes, DocumentTypeReference.of(SequenceBean::class.java)).map { it.name },
        )
        assertEquals(2, mapper.parseAllToNodes(bytes).size)
        // 只有一个数据项时列表长度为 1。
        assertEquals(1, mapper.parseAllToNodes(mapper.formatBytes(beans[0])).size)
    }

    @Test
    fun mapperFormatAllUsesTokenFastPath() {
        val mapper = CborMapper()
        val bytes = mapper.formatAll(
            listOf(
                SequenceBean().apply { name = "x" },
                SequenceBean().apply { name = "y" },
            ),
        )

        // Token 直写使用 CBOR 不定长 map（0xBF ... 0xFF），不是 Node 定长写法。
        assertEquals(0xBF, bytes.first().toInt() and 0xFF)
        assertEquals(0xFF, bytes.last().toInt() and 0xFF)
        assertEquals(listOf("x", "y"), mapper.parseAll(bytes, SequenceBean::class.java).map { it.name })
    }

    @Test
    fun mapperHonorsConfigDepthLimitForSequences() {
        val nestedBytes = listOf(ArrayNode.of(ArrayNode.of(NumberNode.of(1)))).toCborSequenceBytes()
        val limited = CborMapper(DocumentMapperConfig.builder().maxNodeDepth(1).build())

        assertFailsWith<CborParseException> {
            limited.parseAllToNodes(nestedBytes)
        }
        assertEquals(1, CborMapper().parseAllToNodes(nestedBytes).size)
    }

    @Test
    fun mapperPreserveTagsAppliesToSequences() {
        val mapper = CborMapper(CborConfig.builder().preserveTags(true).build())
        val tagged = hex("d86463616263") + hex("05")

        assertEquals(
            listOf(wrapper(100, StringNode.of("abc")), NumberNode.of(5)),
            mapper.parseAllToNodes(tagged),
        )
        assertContentEquals(
            tagged,
            mapper.formatAll(listOf(wrapper(100, StringNode.of("abc")), NumberNode.of(5))),
        )
    }

    @Test
    fun maxItemsIsIndependentOfDepth() {
        val bytes = listOf(NumberNode.of(1), NumberNode.of(2)).toCborSequenceBytes()

        // 标量数据项的 maxDepth=1 可以通过，maxItems=1 在 2 项时触发。
        assertFailsWith<CborParseException> {
            CborSequenceReader(bytes, maxDepth = 1, maxItems = 1).readAll()
        }
        assertEquals(2, CborSequenceReader(bytes, maxDepth = 1, maxItems = 2).readAll().size)
    }

    private fun wrapper(tag: Int, value: Node): ObjectNode = ObjectNode.of(
        CborConfig.TAG_FIELD to NumberNode.of(tag),
        CborConfig.VALUE_FIELD to value,
    )

    private fun hex(value: String): ByteArray {
        val normalized = value.replace(" ", "")
        check(normalized.length % 2 == 0) { "Hex string must have an even length" }
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }

    private class SequenceBean {
        var name: String? = null
        var age: Int = 0
    }
}
