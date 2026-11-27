package com.mingliqiye.document.format.cbor

import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.cbor.exception.CborException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger

private val CBOR_FACTORY: CBORFactory = CBORFactory()

/**
 * 将文档树写成 CBOR 二进制。
 *
 * 类型映射：
 * - [ObjectNode] → CBOR map（定长），[ArrayNode] → CBOR array（定长）
 * - [StringNode] → 文本字符串
 * - [BinaryNode] → 字节字符串（CBOR byte string）
 * - [BooleanNode] → `true` / `false`，[NullNode] → `null`
 * - [NumberNode] → 整数或十进制分数，见 [writeNumber]
 *
 * 每个对象/数组都按实际元素数量写成定长 CBOR。
 */
public class CborWriter(
    private val canonical: Boolean = false,
) {

    /**
     * 写出节点并返回 CBOR 字节。
     *
     * @throws CborException 生成 CBOR 失败，或 [NumberNode.raw] 不是合法数字时抛出
     */
    public fun write(node: Node): ByteArray {
        val buffer = ByteArrayOutputStream()
        try {
            CBOR_FACTORY.createGenerator(buffer).use { generator ->
                writeValue(generator, node)
            }
        } catch (e: IOException) {
            throw CborException("Failed to write CBOR: ${e.message}", e)
        }
        return buffer.toByteArray()
    }

    private fun writeValue(generator: CBORGenerator, node: Node) {
        when (node) {
            is ObjectNode -> writeObject(generator, node)
            is ArrayNode -> writeArray(generator, node)
            is StringNode -> generator.writeString(node.value)
            is NumberNode -> writeNumber(generator, node)
            is BooleanNode -> generator.writeBoolean(node.value)
            is NullNode -> generator.writeNull()
            is BinaryNode -> generator.writeBinary(node.value)
        }
    }

    private fun writeObject(generator: CBORGenerator, node: ObjectNode) {
        generator.writeStartObject(node.size)
        val entries = if (canonical) {
            node.asMap().entries.sortedWith(CANONICAL_KEY_ORDER)
        } else {
            node.asMap().entries.toList()
        }
        for ((name, value) in entries) {
            generator.writeFieldName(name)
            writeValue(generator, value)
        }
        generator.writeEndObject()
    }

    private fun writeArray(generator: CBORGenerator, node: ArrayNode) {
        generator.writeStartArray(null, node.size)
        for (element in node) {
            writeValue(generator, element)
        }
        generator.writeEndArray()
    }

    /**
     * 按 [NumberNode.raw] 写出数字。
     *
     * - 不含小数点/指数标记的 raw 写成 CBOR 整数：能放进 Int/Long 时写最短整数，
     *   更大的整数写成 bignum（tag 2/3）。
     * - 其余 raw 用 [BigDecimal] 写成 CBOR 十进制分数（tag 4），保留 Node 模型的
     *   [BigDecimal] 精度；读取端由 Jackson 还原为小数 [NumberNode]。
     */
    private fun writeNumber(generator: CBORGenerator, node: NumberNode) {
        val raw = node.raw
        try {
            if (raw.any { it == '.' || it == 'e' || it == 'E' }) {
                generator.writeNumber(BigDecimal(raw))
            } else {
                writeInteger(generator, BigInteger(raw))
            }
        } catch (e: NumberFormatException) {
            throw CborException("Invalid number '$raw'", e)
        }
    }

    private fun writeInteger(generator: CBORGenerator, value: BigInteger) {
        when {
            value.bitLength() < 32 -> generator.writeNumber(value.toInt())
            value.bitLength() < 64 -> generator.writeNumber(value.toLong())
            else -> generator.writeNumber(value)
        }
    }
}

/**
 * 将文档树写成 CBOR 字节。
 *
 * @return CBOR 二进制内容
 * @throws CborException 生成 CBOR 失败，或数字 raw 非法时抛出
 */
public fun Node.toCborBytes(): ByteArray = CborWriter().write(this)

/**
 * 将文档树按 canonical map key 顺序写成 CBOR 字节。
 *
 * 对象 key 按 UTF-8 编码长度升序、同长度按字节序排序；数组保持原顺序。
 * 整数已使用最短编码形式。
 *
 * @return CBOR 二进制内容
 * @throws CborException 生成 CBOR 失败，或数字 raw 非法时抛出
 */
public fun Node.toCanonicalCborBytes(): ByteArray = CborWriter(canonical = true).write(this)

private val CANONICAL_KEY_ORDER: Comparator<Map.Entry<String, Node>> =
    Comparator { left, right ->
        val leftBytes = left.key.toByteArray(Charsets.UTF_8)
        val rightBytes = right.key.toByteArray(Charsets.UTF_8)
        val lengthCompare = leftBytes.size.compareTo(rightBytes.size)
        if (lengthCompare != 0) {
            lengthCompare
        } else {
            compareBytes(leftBytes, rightBytes)
        }
    }

private fun compareBytes(left: ByteArray, right: ByteArray): Int {
    for (index in left.indices) {
        val leftValue = left[index].toInt() and 0xFF
        val rightValue = right[index].toInt() and 0xFF
        val comparison = leftValue.compareTo(rightValue)
        if (comparison != 0) {
            return comparison
        }
    }
    return 0
}
