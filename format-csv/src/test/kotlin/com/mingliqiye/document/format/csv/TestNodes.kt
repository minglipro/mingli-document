package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import kotlin.test.assertEquals

/** 构造对象节点。 */
internal fun obj(vararg entries: Pair<String, Node>): ObjectNode = ObjectNode.of(*entries)

/** 构造数组节点。 */
internal fun arr(vararg elements: Node): ArrayNode = ArrayNode.of(*elements)

/** 构造字符串节点。 */
internal fun str(value: String): StringNode = StringNode.of(value)

/** 构造数字节点。 */
internal fun num(value: Int): NumberNode = NumberNode.of(value)

/** 构造小数节点。 */
internal fun dnum(value: Double): NumberNode = NumberNode.of(value)

/** 递归比较两棵文档树。 */
internal fun assertNodesEqual(expected: Node, actual: Node, path: String = "$") {
    assertEquals(expected.type, actual.type, "node type at $path")
    when (expected) {
        is NullNode -> Unit
        is StringNode -> assertEquals(expected.value, actual.asString(), "string at $path")
        is NumberNode -> assertEquals(expected.raw, actual.asNumber().raw, "number at $path")
        is BooleanNode -> assertEquals(expected.value, actual.asBoolean(), "boolean at $path")
        is BinaryNode -> assertEquals(expected.value.toList(), actual.asBinary().toList(), "binary at $path")
        is ArrayNode -> {
            val actualArray = actual.asArray()
            assertEquals(expected.size, actualArray.size, "array size at $path")
            for (index in 0 until expected.size) {
                assertNodesEqual(expected[index], actualArray[index], "$path[$index]")
            }
        }

        is ObjectNode -> {
            val actualObject = actual.asObject()
            assertEquals(expected.keys, actualObject.keys, "object keys at $path")
            for (key in expected.keys) {
                assertNodesEqual(expected[key]!!, actualObject[key]!!, "$path.$key")
            }
        }
    }
}
