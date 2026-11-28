package com.mingliqiye.document.format.smile

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.smile.exception.SmileParseException
import java.io.IOException

/**
 * Smile 二进制读取入口。
 *
 * 基于 Jackson Smile 解析器，把 Smile 字节流还原为通用 [Node] 树。支持单文档
 * 单根：输入必须且只能包含一个顶层 Smile 值。
 *
 * 类型映射：
 * - `null` → [NullNode]
 * - `true` / `false` → [BooleanNode]
 * - 整数 / 浮点数 / 大数 → [NumberNode]（保留 [NumberNode.raw]）
 * - 文本 → [StringNode]
 * - Smile 二进制值 → [BinaryNode]（见 [SmileWriter] 中关于二进制扩展的说明）
 * - 数组 → [ArrayNode]
 * - 对象 → [ObjectNode]（重复字段名以后出现的值为准）
 *
 * @param source Smile 二进制内容
 */
public class SmileReader @JvmOverloads constructor(
    private val source: ByteArray,
    private val maxDepth: Int = 0,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws SmileParseException Smile 格式非法、根值缺失或存在多个根值时抛出
     */
    public fun read(): Node {
        val parser = try {
            SMILE_FACTORY.createParser(source)
        } catch (e: IOException) {
            throw SmileParseException("Invalid Smile: ${e.message}", e)
        }
        try {
            return readDocument(parser)
        } catch (e: IOException) {
            throw SmileParseException("Invalid Smile: ${e.message}", e)
        } finally {
            closeQuietly(parser)
        }
    }

    /**
     * 读取 Smile 多消息流（连续多个根值）中的全部文档树。
     *
     * 仅供 [SmileSequenceReader] 使用；单值 [read] 语义保持不变。每个根值独立
     * 解析，深度从 1 重新开始计数。空输入返回空列表。
     *
     * @param maxItems 最大消息数量；`<= 0` 表示不限制
     * @return 按出现顺序排列的文档树列表
     * @throws SmileParseException 任一消息格式非法、数据不完整、消息数超过
     *   maxItems，或包含 NaN/Infinity 等无法表示的浮点值时抛出
     */
    internal fun readAll(maxItems: Int): List<Node> {
        val parser = try {
            SMILE_FACTORY.createParser(source)
        } catch (e: IOException) {
            throw SmileParseException("Invalid Smile: ${e.message}", e)
        }
        try {
            val nodes = ArrayList<Node>()
            while (true) {
                val token = parser.nextToken() ?: break
                if (maxItems > 0 && nodes.size >= maxItems) {
                    throw SmileParseException("Smile stream contains more than maxItems $maxItems values")
                }
                nodes.add(readValue(parser, token, 1))
            }
            return nodes
        } catch (e: IOException) {
            throw SmileParseException("Invalid Smile: ${e.message}", e)
        } finally {
            closeQuietly(parser)
        }
    }

    private fun readDocument(parser: JsonParser): Node {
        val firstToken = parser.nextToken()
            ?: throw SmileParseException("Smile document has no top-level value")
        val node = readValue(parser, firstToken, 1)
        if (parser.nextToken() != null) {
            throw SmileParseException("Smile document must contain exactly one top-level value")
        }
        return node
    }

    private fun readValue(parser: JsonParser, token: JsonToken, depth: Int): Node = when (token) {
        JsonToken.START_OBJECT -> readObject(parser, depth)
        JsonToken.START_ARRAY -> readArray(parser, depth)
        JsonToken.VALUE_STRING -> StringNode.of(parser.text)
        JsonToken.VALUE_NUMBER_INT -> NumberNode.ofRaw(parser.text)
        JsonToken.VALUE_NUMBER_FLOAT -> readFloatNode(parser)
        JsonToken.VALUE_TRUE -> BooleanNode.of(true)
        JsonToken.VALUE_FALSE -> BooleanNode.of(false)
        JsonToken.VALUE_NULL -> NullNode.INSTANCE
        JsonToken.VALUE_EMBEDDED_OBJECT -> readBinaryNode(parser)
        else -> throw SmileParseException("Unsupported Smile token: $token")
    }

    private fun readFloatNode(parser: JsonParser): NumberNode {
        val numberType = parser.numberType
        if (numberType == JsonParser.NumberType.FLOAT || numberType == JsonParser.NumberType.DOUBLE) {
            if (!parser.doubleValue.isFinite()) {
                throw SmileParseException("Smile floating-point NaN and Infinity are not supported")
            }
        }
        return NumberNode.ofRaw(parser.text)
    }

    private fun readBinaryNode(parser: JsonParser): BinaryNode {
        val value = parser.binaryValue
            ?: throw SmileParseException("Smile binary value is missing")
        return BinaryNode.of(value)
    }

    private fun readArray(parser: JsonParser, depth: Int): ArrayNode {
        checkDepth(depth)
        val elements = ArrayList<Node>()
        while (true) {
            val token = parser.nextToken()
                ?: throw SmileParseException("Smile document ended inside an array")
            if (token == JsonToken.END_ARRAY) {
                return ArrayNode.from(elements)
            }
            elements.add(readValue(parser, token, depth + 1))
        }
    }

    private fun readObject(parser: JsonParser, depth: Int): ObjectNode {
        checkDepth(depth)
        val fields = LinkedHashMap<String, Node>()
        while (true) {
            val token = parser.nextToken()
                ?: throw SmileParseException("Smile document ended inside an object")
            if (token == JsonToken.END_OBJECT) {
                return ObjectNode.from(fields)
            }
            if (token != JsonToken.FIELD_NAME) {
                throw SmileParseException("Expected Smile object field name but was $token")
            }
            val name = parser.currentName
            val valueToken = parser.nextToken()
                ?: throw SmileParseException("Smile document ended after field '$name'")
            fields[name] = readValue(parser, valueToken, depth + 1)
        }
    }

    private fun checkDepth(depth: Int) {
        if (maxDepth > 0 && depth > maxDepth) {
            throw SmileParseException("Smile nesting depth $depth exceeds maxDepth $maxDepth")
        }
    }

    private fun closeQuietly(parser: JsonParser) {
        try {
            parser.close()
        } catch (_: IOException) {
            // 文档已经读取完成，关闭失败不影响解析结果。
        }
    }

    public companion object {

        private val SMILE_FACTORY: SmileFactory = SmileFactory()

        /**
         * 解析 Smile 字节数组。
         *
         * @param source Smile 二进制内容
         * @return 文档树
         * @throws SmileParseException Smile 格式非法、根值缺失或存在多个根值时抛出
         */
        @JvmStatic
        public fun parse(source: ByteArray): Node = SmileReader(source).read()
    }
}

/**
 * 将 Smile 字节数组解析为文档树。
 *
 * @return 文档树
 * @throws SmileParseException Smile 格式非法、根值缺失或存在多个根值时抛出
 */
public fun ByteArray.toSmileNode(): Node = SmileReader(this).read()
