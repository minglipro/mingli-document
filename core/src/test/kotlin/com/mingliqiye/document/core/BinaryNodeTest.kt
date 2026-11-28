package com.mingliqiye.document.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [BinaryNode] 契约测试：复制语义、零拷贝语义与不可变约定。
 */
class BinaryNodeTest {

    @Test
    fun binaryNodeCopiesAndComparesContent() {
        val source = byteArrayOf(1, 2, 3)
        val node = BinaryNode.of(source)

        source[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), node.value)
        assertContentEquals(byteArrayOf(1, 2, 3), node.asBinary())

        val copy = node.value
        copy[1] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), node.value)

        assertEquals(NodeType.BINARY, node.type)
        assertTrue(node.isBinary)
        assertFalse(node.isString)
        assertEquals(BinaryNode.of(byteArrayOf(1, 2, 3)), node)
    }

    @Test
    fun ofCopiesInputArray() {
        val source = byteArrayOf(1, 2, 3)
        val node = BinaryNode.of(source)

        assertNotSame(source, node.value)
        assertNotSame(node.value, node.value)
    }

    @Test
    fun ofSharedDoesNotCopy() {
        val source = byteArrayOf(1, 2, 3)
        val node = BinaryNode.ofShared(source)

        assertSame(source, node.value)
        assertSame(source, node.asBinary())
        assertContentEquals(byteArrayOf(1, 2, 3), node.asBinary())
        assertEquals(BinaryNode.of(byteArrayOf(1, 2, 3)), node)
    }

    @Test
    fun sharedNodeReflectsArrayMutation() {
        // 文档化的“不安全”语义：调用方违反约定修改数组时，节点内容随之变化。
        val source = byteArrayOf(1, 2, 3)
        val node = BinaryNode.ofShared(source)

        source[0] = 9
        assertContentEquals(byteArrayOf(9, 2, 3), node.value)
    }

    @Test
    fun ofUnsafeUsesSharedSemantics() {
        val source = byteArrayOf(4, 5)
        val node = BinaryNode.ofUnsafe(source)

        assertSame(source, node.value)
        assertEquals(node, BinaryNode.ofShared(source))
    }

    @Test
    fun sharedAndCopiedNodesAreEqualByContent() {
        val source = byteArrayOf(1, 2, 3)
        val shared = BinaryNode.ofShared(source)
        val copied = BinaryNode.of(source)

        assertNotSame(shared.value, copied.value)
        assertEquals(shared, copied)
        assertEquals(shared.hashCode(), copied.hashCode())
    }
}
