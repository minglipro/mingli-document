package com.mingliqiye.document.format.cbor

import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.math.BigDecimal
import java.math.BigInteger

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
 *
 * [preserveTags] 为 `true` 时识别 CBOR tag 包装对象
 * `{"$cborTag": <tag 整数>, "value": <Node>}`：先写 `CBORGenerator.writeTag`，
 * 再递归写出 `value`，从而支持对象/数组/标量上的 tag 以及嵌套 tag。
 * 只有恰好两个字段、tag 为 `>= 0` 整数且包含 `value` 字段的对象才算包装；
 * 其余对象（tag 非整数、value 缺失或多字段）按普通对象写出。
 *
 * [bufferPool] 非空时使用池化中间缓冲区写出：缓冲区从池借出、随容量增长换用更大的
 * 池缓冲区、写出完成后归还，返回的 `ByteArray` 仍是一次复制（所有权归调用方）。
 * 默认 `null` 使用普通 `ByteArrayOutputStream`，行为与不加池时完全一致。
 *
 * [writeFeatures] 直接落到 上游实现 generator 特性上：
 * - [CborWriteFeature.WRITE_MINIMAL_INTS]：整数最短编码（默认开启）；
 * - [CborWriteFeature.WRITE_TYPE_HEADER]：文档开头写自描述 tag（55799）；
 * - [CborWriteFeature.LENIENT_UTF_ENCODING]：非法代理对替换为 `U+FFFD`；
 * - [CborWriteFeature.STRINGREF]：生成 stringref 共享字符串引用；
 * - [CborWriteFeature.ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING]：按规范编码
 *   负数 bignum。
 * [CborWriteFeature.WRITE_MINIMAL_DOUBLES] 只影响 token 直写路径，见 [CborTokenWriter]。
 *
 * @param canonical 是否按 canonical map key 顺序写出
 * @param preserveTags 是否识别并写出 CBOR tag 包装对象，默认 `false`
 * @param bufferPool 可选的中间缓冲区池，默认 `null`（不池化）
 * @param writeFeatures CBOR 写出特性集合，默认 [CborConfig.DEFAULT_WRITE_FEATURES]
 */
public class CborWriter @JvmOverloads constructor(
    private val canonical: Boolean = false,
    private val preserveTags: Boolean = false,
    private val bufferPool: ByteArrayPool? = null,
    private val writeFeatures: Set<CborWriteFeature> = CborConfig.DEFAULT_WRITE_FEATURES,
) {

    /**
     * 写出节点并返回 CBOR 字节。
     *
     * @throws CborException 生成 CBOR 失败，或 [NumberNode.raw] 不是合法数字时抛出
     */
    public fun write(node: Node): ByteArray = writeAll(listOf(node))

    /**
     * 按顺序写出多个节点，返回 CBOR sequence 字节（各数据项直接首尾相接）。
     *
     * 仅供 [CborSequenceWriter] 使用；空集合返回空字节数组。
     *
     * @param nodes 要写出的文档树
     * @return CBOR 二进制内容
     * @throws CborException 生成 CBOR 失败，或 [NumberNode.raw] 不是合法数字时抛出
     */
    internal fun writeAll(nodes: Iterable<Node>): ByteArray {
        val pooledOutput = bufferPool?.let { PooledByteArrayOutputStream(it) }
        val output: OutputStream = pooledOutput ?: ByteArrayOutputStream()
        var generator: CBORGenerator? = null
        try {
            val factory = CborFeatureSupport.factory(CborConfig.DEFAULT_READ_FEATURES, writeFeatures)
            val created = factory.createGenerator(output)
            generator = created
            for (node in nodes) {
                writeValue(created, node)
            }
            created.flush()
            return if (pooledOutput != null) {
                pooledOutput.toByteArray()
            } else {
                (output as ByteArrayOutputStream).toByteArray()
            }
        } catch (e: IOException) {
            throw CborException("Failed to write CBOR: ${e.message}", e)
        } finally {
            closeQuietly(generator)
            pooledOutput?.close()
        }
    }

    private fun writeValue(generator: CBORGenerator, node: Node) {
        if (preserveTags && node is ObjectNode) {
            val tagged = CborTagWrapper.unwrap(node)
            if (tagged != null) {
                writeTag(generator, tagged.tag)
                writeValue(generator, tagged.value)
                return
            }
        }
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

    private fun writeTag(generator: CBORGenerator, tag: Int) {
        try {
            generator.writeTag(tag)
        } catch (e: IllegalArgumentException) {
            throw CborException("Invalid CBOR tag $tag", e)
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
     *   [BigDecimal] 精度；读取端由 上游实现 还原为小数 [NumberNode]。
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

    private fun closeQuietly(generator: CBORGenerator?) {
        try {
            generator?.close()
        } catch (_: IOException) {
            // 写出结果已经确定（返回或已抛出），关闭失败不覆盖原结果。
        }
    }
}

/**
 * 将文档树写成 CBOR 字节。
 *
 * @param preserveTags 是否识别并写出 CBOR tag 包装对象，默认 `false`
 * @param bufferPool 可选的中间缓冲区池，默认 `null`（不池化）
 * @param writeFeatures CBOR 写出特性集合，默认 [CborConfig.DEFAULT_WRITE_FEATURES]
 * @return CBOR 二进制内容
 * @throws CborException 生成 CBOR 失败，或数字 raw 非法时抛出
 */
public fun Node.toCborBytes(
    preserveTags: Boolean = false,
    bufferPool: ByteArrayPool? = null,
    writeFeatures: Set<CborWriteFeature> = CborConfig.DEFAULT_WRITE_FEATURES,
): ByteArray = CborWriter(preserveTags = preserveTags, bufferPool = bufferPool, writeFeatures = writeFeatures).write(this)

/**
 * 将文档树按 canonical map key 顺序写成 CBOR 字节。
 *
 * 对象 key 按 UTF-8 编码长度升序、同长度按字节序排序；数组保持原顺序。
 * 整数已使用最短编码形式。
 *
 * @param preserveTags 是否识别并写出 CBOR tag 包装对象，默认 `false`
 * @param bufferPool 可选的中间缓冲区池，默认 `null`（不池化）
 * @param writeFeatures CBOR 写出特性集合，默认 [CborConfig.DEFAULT_WRITE_FEATURES]
 * @return CBOR 二进制内容
 * @throws CborException 生成 CBOR 失败，或数字 raw 非法时抛出
 */
public fun Node.toCanonicalCborBytes(
    preserveTags: Boolean = false,
    bufferPool: ByteArrayPool? = null,
    writeFeatures: Set<CborWriteFeature> = CborConfig.DEFAULT_WRITE_FEATURES,
): ByteArray = CborWriter(
    canonical = true,
    preserveTags = preserveTags,
    bufferPool = bufferPool,
    writeFeatures = writeFeatures,
).write(this)

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
