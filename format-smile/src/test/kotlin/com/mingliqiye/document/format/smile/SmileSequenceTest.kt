package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.smile.exception.SmileParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Smile 多消息流读写测试。
 */
class SmileSequenceTest {

    @Test
    fun emptyInputYieldsEmptyList() {
        assertTrue(SmileSequenceReader(ByteArray(0)).readAll().isEmpty())
        assertTrue(SmileSequenceReader.parseAll(ByteArray(0)).isEmpty())
        assertTrue(ByteArray(0).toSmileNodes().isEmpty())
        assertTrue(SmileMapper().parseAllToNodes(ByteArray(0)).isEmpty())
        assertTrue(SmileMapper().parseAll(ByteArray(0), SequenceBean::class.java).isEmpty())
    }

    @Test
    fun singleItemSequenceMatchesSingleValueReader() {
        val node = ObjectNode.of(
            "name" to StringNode.of("only"),
            "values" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
        )
        val bytes = node.toSmileBytes()

        assertEquals(listOf(node), SmileSequenceReader(bytes).readAll())
        assertEquals(listOf(node), SmileSequenceReader.parseAll(bytes))
        assertEquals(listOf(node), bytes.toSmileNodes())

        // 单值入口语义不变：多个根值仍然报错。
        assertFailsWith<SmileParseException> {
            SmileReader(listOf(node, node).toSmileSequenceBytes()).read()
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
        val bytes = nodes.toSmileSequenceBytes()

        assertEquals(nodes, SmileSequenceReader(bytes).readAll())
        assertEquals(nodes, SmileSequenceReader.parseAll(bytes))
        assertEquals(nodes, bytes.toSmileNodes())
        assertEquals(nodes, SmileSequenceWriter().writeAll(nodes).toSmileNodes())
        assertEquals(nodes, SmileSequenceWriter.formatAll(nodes).toSmileNodes())
        // Smile 头部只在流开头写一次，多消息流以头部开始。
        assertEquals(0x3A.toByte(), bytes[0])
    }

    @Test
    fun emptySequenceWritesEmptyBytes() {
        assertContentEquals(ByteArray(0), emptyList<Node>().toSmileSequenceBytes())
        assertContentEquals(ByteArray(0), SmileSequenceWriter().writeAll(emptyList()))
        assertContentEquals(ByteArray(0), SmileSequenceWriter.formatAll(emptyList()))

        val mapper = SmileMapper()
        assertContentEquals(ByteArray(0), mapper.formatAll(emptyList()))
        assertTrue(mapper.parseAllToNodes(mapper.formatAll(emptyList())).isEmpty())
    }

    @Test
    fun maxItemsLimitsMessageCount() {
        val bytes = listOf(
            NumberNode.of(1),
            NumberNode.of(2),
            NumberNode.of(3),
        ).toSmileSequenceBytes()

        assertEquals(3, SmileSequenceReader(bytes).readAll().size)
        assertEquals(3, SmileSequenceReader(bytes, maxItems = 0).readAll().size)
        // 3 个根值时 maxItems=2 触发；maxItems=3 恰好通过。
        assertFailsWith<SmileParseException> {
            SmileSequenceReader(bytes, maxItems = 2).readAll()
        }
        assertEquals(3, SmileSequenceReader(bytes, maxItems = 3).readAll().size)
        assertFailsWith<SmileParseException> {
            SmileSequenceReader.parseAll(bytes, maxItems = 2)
        }
        assertEquals(3, SmileSequenceReader.parseAll(bytes, maxItems = 3).size)
        assertFailsWith<SmileParseException> {
            SmileMapper().parseAllToNodes(bytes, maxItems = 2)
        }
        assertEquals(3, SmileMapper().parseAllToNodes(bytes, maxItems = 3).size)
    }

    @Test
    fun depthLimitAppliesToEachMessageSeparately() {
        // 每个根值深度 2（根数组 + 内层数组）；两个消息共享同一 maxDepth 计数起点。
        val nested = ArrayNode.of(ArrayNode.of(StringNode.of("deep")))
        val bytes = listOf(nested, nested).toSmileSequenceBytes()

        assertEquals(2, SmileSequenceReader(bytes, maxDepth = 2).readAll().size)
        assertFailsWith<SmileParseException> {
            SmileSequenceReader(bytes, maxDepth = 1).readAll()
        }
        assertFailsWith<SmileParseException> {
            SmileSequenceReader.parseAll(bytes, maxDepth = 1)
        }
    }

    @Test
    fun malformedOrTruncatedSequenceThrows() {
        val first = NumberNode.of(1).toSmileBytes()

        // 合法根值之后的数据被截断（Smile 头部 + 对象内容被截掉末尾两字节）。
        val objectBytes = ObjectNode.of("name" to StringNode.of("abcdef")).toSmileBytes()
        val truncated = objectBytes.copyOf(objectBytes.size - 2)
        assertFailsWith<SmileParseException> {
            SmileSequenceReader(truncated).readAll()
        }
        // 合法项之后的非法字节。
        assertFailsWith<SmileParseException> {
            SmileSequenceReader(first + byteArrayOf(1, 2, 3, 4)).readAll()
        }
        // 第一项就非法。
        assertFailsWith<SmileParseException> {
            SmileSequenceReader(byteArrayOf(1, 2, 3, 4)).readAll()
        }
        assertFailsWith<SmileParseException> {
            SmileMapper().parseAllToNodes(truncated)
        }
    }

    @Test
    fun mapperParsesBeansFromSequence() {
        val mapper = SmileMapper()
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
        // 只有一个根值时列表长度为 1。
        assertEquals(1, mapper.parseAllToNodes(mapper.formatBytes(beans[0])).size)
    }

    @Test
    fun mapperFormatAllUsesTokenFastPathAndSingleHeader() {
        val mapper = SmileMapper()
        val bytes = mapper.formatAll(
            listOf(
                SequenceBean().apply { name = "x" },
                SequenceBean().apply { name = "y" },
            ),
        )
        val single = mapper.formatBytes(SequenceBean().apply { name = "x" })

        // 多消息流只写一次 Smile 头部：多消息流长度小于两个独立文档之和。
        assertTrue(bytes.size < single.size * 2)
        assertEquals(0x3A.toByte(), bytes[0])
        assertEquals(listOf("x", "y"), mapper.parseAll(bytes, SequenceBean::class.java).map { it.name })
    }

    @Test
    fun mapperHonorsConfigDepthLimitForSequences() {
        val nestedBytes = listOf(ArrayNode.of(ArrayNode.of(NumberNode.of(1)))).toSmileSequenceBytes()
        val limited = SmileMapper(DocumentMapperConfig.builder().maxNodeDepth(1).build())

        assertFailsWith<SmileParseException> {
            limited.parseAllToNodes(nestedBytes)
        }
        assertEquals(1, SmileMapper().parseAllToNodes(nestedBytes).size)
    }

    @Test
    fun maxItemsIsIndependentOfDepth() {
        val bytes = listOf(NumberNode.of(1), NumberNode.of(2)).toSmileSequenceBytes()

        // 标量根值的 maxDepth=1 可以通过，maxItems=1 在 2 个消息时触发。
        assertFailsWith<SmileParseException> {
            SmileSequenceReader(bytes, maxDepth = 1, maxItems = 1).readAll()
        }
        assertEquals(2, SmileSequenceReader(bytes, maxDepth = 1, maxItems = 2).readAll().size)
    }

    private class SequenceBean {
        var name: String? = null
        var age: Int = 0
    }
}
