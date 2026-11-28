package com.mingliqiye.document.format.smile

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.smile.exception.SmileException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger

/**
 * 将文档树写成 Smile 二进制。
 *
 * 使用 Jackson Smile 写出器（默认写出 Smile 头部，共享字段名开启），因此输出
 * 是自描述的 Smile 文档，可直接用 [SmileReader] 读回。
 *
 * 数字按 [NumberNode.raw] 分类：纯整数写为 Smile 整数（int / long / big integer），
 * 含小数点或指数的写为 big decimal；读取后按数值比较与原始树等价。
 *
 * Smile 格式规范本身没有独立的 binary 类型，Jackson 的 Smile 实现通过扩展令牌
 * （7-bit 编码的 `0xE8` / 原始 `0xFD`）承载二进制内容，解析时给出
 * `VALUE_EMBEDDED_OBJECT`。本模块直接使用该扩展完成 [BinaryNode] 往返，
 * 不额外做 Base64 字符串降级。
 */
public class SmileWriter {

    /**
     * 写出节点并返回 Smile 字节。
     *
     * @throws SmileException 节点包含无法编码的内容时抛出
     */
    public fun write(node: Node): ByteArray = writeAll(listOf(node))

    /**
     * 按顺序写出多个根值，返回 Smile 多消息流字节。
     *
     * 仅供 [SmileSequenceWriter] 使用；空集合返回空字节数组。
     *
     * @param nodes 要写出的文档树
     * @return Smile 二进制内容
     * @throws SmileException 节点包含无法编码的内容时抛出
     */
    internal fun writeAll(nodes: Iterable<Node>): ByteArray {
        val iterator = nodes.iterator()
        if (!iterator.hasNext()) {
            return ByteArray(0)
        }
        val output = ByteArrayOutputStream()
        val generator = try {
            SMILE_FACTORY.createGenerator(output)
        } catch (e: IOException) {
            throw SmileException("Cannot create Smile generator", e)
        }
        try {
            while (iterator.hasNext()) {
                writeValue(generator, iterator.next())
            }
            generator.flush()
        } catch (e: IOException) {
            throw SmileException("Failed to write Smile content: ${e.message}", e)
        } finally {
            closeQuietly(generator)
        }
        return output.toByteArray()
    }

    private fun writeValue(generator: JsonGenerator, node: Node) {
        when (node) {
            is ObjectNode -> writeObject(generator, node)
            is ArrayNode -> writeArray(generator, node)
            is StringNode -> generator.writeString(node.value)
            is NumberNode -> writeSmileNumber(generator, node.raw)
            is BooleanNode -> generator.writeBoolean(node.value)
            is NullNode -> generator.writeNull()
            is BinaryNode -> generator.writeBinary(node.value)
        }
    }

    private fun writeObject(generator: JsonGenerator, node: ObjectNode) {
        generator.writeStartObject()
        for ((name, child) in node.asMap()) {
            generator.writeFieldName(name)
            writeValue(generator, child)
        }
        generator.writeEndObject()
    }

    private fun writeArray(generator: JsonGenerator, node: ArrayNode) {
        generator.writeStartArray()
        for (child in node) {
            writeValue(generator, child)
        }
        generator.writeEndArray()
    }

    private fun closeQuietly(generator: JsonGenerator) {
        try {
            generator.close()
        } catch (_: IOException) {
            // 内容已经写出，关闭失败不影响结果。
        }
    }

    public companion object {

        private val SMILE_FACTORY: SmileFactory = SmileFactory()
    }
}

/**
 * 按数字原始文本写出 Smile 数字。
 *
 * 纯整数（无小数点/指数）写为 Smile 整数：能放进 Int/Long 时写原生整数，否则写
 * big integer；其余写为 big decimal。Node 写出与 Token 写出共用该分类，保证两条
 * 路径的输出一致。
 *
 * @param generator Jackson Smile 生成器
 * @param raw 数字原始文本
 * @throws java.io.IOException Jackson 写出失败
 * @throws SmileException 数字文本非法
 */
internal fun writeSmileNumber(generator: JsonGenerator, raw: String) {
    try {
        if (isIntegerText(raw)) {
            val intValue = raw.toIntOrNull()
            if (intValue != null) {
                generator.writeNumber(intValue)
                return
            }
            val longValue = raw.toLongOrNull()
            if (longValue != null) {
                generator.writeNumber(longValue)
                return
            }
            generator.writeNumber(BigInteger(raw))
        } else {
            generator.writeNumber(BigDecimal(raw))
        }
    } catch (e: NumberFormatException) {
        throw SmileException("Invalid number '$raw'", e)
    }
}

private fun isIntegerText(raw: String): Boolean = raw.none { it == '.' || it == 'e' || it == 'E' || it == 'd' || it == 'D' }

/**
 * 将文档树写成 Smile 字节数组。
 *
 * @return Smile 二进制内容
 * @throws SmileException 节点包含无法编码的内容时抛出
 */
public fun Node.toSmileBytes(): ByteArray = SmileWriter().write(this)
