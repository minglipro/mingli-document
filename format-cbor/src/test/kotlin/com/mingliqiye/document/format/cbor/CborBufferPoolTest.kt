package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.ByteArrayPool
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.cbor.exception.CborException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * CBOR 输出缓冲池与零拷贝输入（[BinaryNode.ofShared]）测试。
 *
 * 覆盖：池化写出与默认写出的字节一致性、缓冲区复用与归还、异常路径归还、
 * [CborConfig.bufferPool] 配置入口、CBOR sequence，以及共享二进制节点的零拷贝语义。
 */
class CborBufferPoolTest {

    @Test
    fun pooledWriterProducesIdenticalBytesAndReleasesBuffer() {
        val pool = ByteArrayPool()
        try {
            val node = sampleNode()
            val expected = node.toCborBytes()
            val pooled = node.toCborBytes(bufferPool = pool)

            assertContentEquals(expected, pooled)
            assertEquals(1L, pool.totalBorrowed)
            assertEquals(1L, pool.totalReleased)
            assertEquals(0, pool.borrowedBufferCount)
            assertEquals(1, pool.idleBufferCount)
            assertEquals(1L, pool.totalAllocated)
        } finally {
            pool.close()
        }
    }

    @Test
    fun repeatedPooledWritesReuseBuffers() {
        val pool = ByteArrayPool(maxIdleBuffers = 4)
        try {
            val node = sampleNode()
            val expected = node.toCborBytes()
            repeat(6) {
                assertContentEquals(expected, node.toCborBytes(bufferPool = pool))
            }

            assertEquals(1L, pool.totalAllocated)
            assertTrue(pool.totalReused >= 5, "expected buffers to be reused, but reused=${pool.totalReused}")
            assertEquals(0, pool.borrowedBufferCount)
        } finally {
            pool.close()
        }
    }

    @Test
    fun pooledWriterSupportsCanonicalMode() {
        val pool = ByteArrayPool()
        try {
            val node = sampleNode()
            assertContentEquals(
                node.toCanonicalCborBytes(),
                node.toCanonicalCborBytes(bufferPool = pool),
            )
            assertEquals(0, pool.borrowedBufferCount)
        } finally {
            pool.close()
        }
    }

    @Test
    fun failedWriteReleasesPooledBuffer() {
        val pool = ByteArrayPool()
        try {
            assertFailsWith<CborException> {
                NumberNode.ofRaw("not-a-number").toCborBytes(bufferPool = pool)
            }

            assertEquals(0, pool.borrowedBufferCount)
            assertEquals(pool.totalBorrowed, pool.totalReleased)
            // 池仍可用
            assertContentEquals(
                NumberNode.of(1).toCborBytes(),
                NumberNode.of(1).toCborBytes(bufferPool = pool),
            )
            assertEquals(0, pool.borrowedBufferCount)
        } finally {
            pool.close()
        }
    }

    @Test
    fun pooledTokenWriterProducesSameBytes() {
        val pool = ByteArrayPool()
        try {
            val node = sampleNode()
            assertContentEquals(writeTokens(node), writeTokens(node, pool))
            assertEquals(1, pool.idleBufferCount)
            assertEquals(0, pool.borrowedBufferCount)
        } finally {
            pool.close()
        }
    }

    @Test
    fun configCarriesBufferPool() {
        val pool = ByteArrayPool()
        try {
            assertNull(CborConfig.builder().build().bufferPool)

            val config = CborConfig.builder().bufferPool(pool).build()
            assertSame(pool, config.bufferPool)
            assertFalse(config.preserveTags)
        } finally {
            pool.close()
        }
    }

    @Test
    fun mapperWithBufferPoolMatchesDefaultOutput() {
        val pool = ByteArrayPool()
        try {
            val node = sampleNode()
            val defaultMapper = CborMapper()
            val pooledMapper = CborMapper(CborConfig.builder().bufferPool(pool).build())

            assertContentEquals(defaultMapper.formatBytes(node), pooledMapper.formatBytes(node))
            assertContentEquals(
                defaultMapper.formatCanonicalBytes(node),
                pooledMapper.formatCanonicalBytes(node),
            )

            val items: List<Any?> = listOf(node, StringNode.of("x"), NumberNode.of(42))
            assertContentEquals(defaultMapper.formatAll(items), pooledMapper.formatAll(items))

            assertEquals(node, CborReader(pooledMapper.formatBytes(node)).read())
            assertEquals(0, pool.borrowedBufferCount)
            assertTrue(pool.totalReused > 0, "expected the mapper to reuse pooled buffers")
        } finally {
            pool.close()
        }
    }

    @Test
    fun preserveTagsMapperUsesPooledWriter() {
        val pool = ByteArrayPool()
        try {
            val wrapper = ObjectNode.of(
                CborConfig.TAG_FIELD to NumberNode.of(100),
                CborConfig.VALUE_FIELD to StringNode.of("abc"),
            )
            val defaultMapper = CborMapper(CborConfig.builder().preserveTags(true).build())
            val pooledMapper = CborMapper(
                CborConfig.builder()
                    .preserveTags(true)
                    .bufferPool(pool)
                    .build(),
            )

            val pooledBytes = pooledMapper.formatBytes(wrapper)
            assertContentEquals(defaultMapper.formatBytes(wrapper), pooledBytes)
            assertEquals("d86463616263", pooledBytes.toHex())
            assertContentEquals(
                defaultMapper.formatAll(listOf(wrapper, wrapper)),
                pooledMapper.formatAll(listOf(wrapper, wrapper)),
            )
            assertEquals(0, pool.borrowedBufferCount)
        } finally {
            pool.close()
        }
    }

    @Test
    fun pooledSequenceWriterMatchesDefault() {
        val pool = ByteArrayPool()
        try {
            val nodes = listOf(sampleNode(), StringNode.of("x"), NumberNode.of(42))

            assertContentEquals(
                CborSequenceWriter().writeAll(nodes),
                CborSequenceWriter(bufferPool = pool).writeAll(nodes),
            )
            assertContentEquals(
                CborSequenceWriter.formatAll(nodes),
                CborSequenceWriter.formatAll(nodes, bufferPool = pool),
            )
            assertContentEquals(
                nodes.toCborSequenceBytes(),
                nodes.toCborSequenceBytes(bufferPool = pool),
            )
            assertEquals(0, pool.borrowedBufferCount)
        } finally {
            pool.close()
        }
    }

    @Test
    fun sharedBinaryNodeIsWrittenWithoutCopying() {
        val payload = byteArrayOf(1, 2, 3)
        val node = BinaryNode.ofShared(payload)
        assertSame(payload, node.value)

        val pool = ByteArrayPool()
        try {
            val bytes = node.toCborBytes(bufferPool = pool)
            assertEquals("43010203", bytes.toHex())
            // 写出过程不会修改共享数组
            assertContentEquals(byteArrayOf(1, 2, 3), payload)
            assertSame(payload, node.value)
            assertEquals(node, CborReader(bytes).read())
            assertEquals(0, pool.borrowedBufferCount)
            assertContentEquals(bytes, writeTokens(node, pool))
        } finally {
            pool.close()
        }
    }

    private fun writeTokens(node: Node, pool: ByteArrayPool? = null): ByteArray {
        val writer = if (pool != null) CborTokenWriter(pool) else CborTokenWriter()
        return try {
            writer.writeNode(node)
            writer.toByteArray()
        } finally {
            writer.close()
        }
    }

    private fun sampleNode(): Node = ObjectNode.of(
        "name" to StringNode.of("小于 & <cbor>"),
        "age" to NumberNode.of(18),
        "pi" to NumberNode.of(BigDecimal("3.14")),
        "active" to BooleanNode.of(true),
        "missing" to NullNode.INSTANCE,
        "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
        "data" to BinaryNode.of(byteArrayOf(0, 1, 2, -1)),
    )

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}
