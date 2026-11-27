package com.mingliqiye.document.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [BinaryNode] 契约测试。
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
}
