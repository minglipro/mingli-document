package com.mingliqiye.document.format.cbor

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.cbor.exception.CborParseException
import java.io.IOException
import java.math.BigDecimal
import java.util.LinkedHashMap
import com.fasterxml.jackson.databind.node.ArrayNode as JacksonArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode as JacksonObjectNode

/**
 * CBOR 读取入口。
 *
 * 基于 Jackson 的 CBOR 解析器（[CBORFactory] + `ObjectMapper.readTree`）把二进制
 * 文档还原为通用 [Node] 树。输入必须且只能包含一个顶层 CBOR 数据项。
 *
 * 类型映射：
 * - `null` / `undefined` → [NullNode]
 * - `false` / `true` → [BooleanNode]
 * - 整数（含 bignum）→ [NumberNode]，保留十进制文本 [NumberNode.raw]
 * - 浮点数（半精度/单精度/双精度）→ [NumberNode]，保留 Jackson 解码后的文本
 * - 十进制分数（tag 4）→ [NumberNode]，精确值
 * - 文本字符串（含 tag 0 日期文本）→ [StringNode]
 * - 字节字符串 → [BinaryNode]（CBOR byte string 不会被当成文本）
 * - 数组（含不定长数组）→ [ArrayNode]
 * - map（含不定长 map）→ [ObjectNode]
 *
 * 说明：
 * - CBOR tag 不单独保留；tag 2/3（bignum）、tag 4（十进制分数）由 Jackson 解码为
 *   整数或小数，日期等字符串 tag 解码为文本。
 * - Jackson 能解码半精度/单精度/双精度浮点；但 [NumberNode] 只支持有限
 *   [BigDecimal]，因此 NaN 与 Infinity 会抛 [CborParseException]，不会静默降级。
 * - CBOR map 的 key 由 Jackson 转换：文本 key 原样使用，整数 key 转为十进制字符串；
 *   其他类型 key 不在本模块支持范围内，Jackson 解析失败时同样抛 [CborParseException]。
 *
 * @param source CBOR 二进制内容
 */
public class CborReader(
    private val source: ByteArray,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws CborParseException CBOR 格式非法、根值缺失、存在多个根值，或包含
     *   NaN/Infinity 等无法表示的浮点值时抛出
     */
    public fun read(): Node {
        val parser = try {
            CBOR_FACTORY.createParser(source)
        } catch (e: IOException) {
            throw CborParseException("Cannot create CBOR parser", e)
        }
        try {
            val tree: JsonNode = OBJECT_MAPPER.readTree<JsonNode>(parser)
                ?: throw CborParseException("CBOR document has no top-level value")
            if (parser.nextToken() != null) {
                throw CborParseException("CBOR document must contain exactly one top-level value")
            }
            return toNode(tree)
        } catch (e: JsonProcessingException) {
            throw CborParseException("Invalid CBOR: ${e.originalMessage}", e)
        } catch (e: IOException) {
            throw CborParseException("Invalid CBOR: ${e.message}", e)
        } finally {
            closeQuietly(parser)
        }
    }

    private fun toNode(node: JsonNode): Node = when {
        node.isObject -> readObject(node as JacksonObjectNode)
        node.isArray -> readArray(node as JacksonArrayNode)
        node.isTextual -> StringNode.of(node.textValue())
        node.isNumber -> readNumber(node as NumericNode)
        node.isBoolean -> BooleanNode.of(node.booleanValue())
        node.isBinary -> BinaryNode.of(node.binaryValue())
        node.isNull -> NullNode.INSTANCE
        else -> throw CborParseException("Unsupported CBOR value: ${node.nodeType}")
    }

    private fun readObject(node: JacksonObjectNode): ObjectNode {
        val fields = LinkedHashMap<String, Node>()
        val iterator = node.fields()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            fields[entry.key] = toNode(entry.value)
        }
        return ObjectNode.from(fields)
    }

    private fun readArray(node: JacksonArrayNode): ArrayNode {
        val elements = ArrayList<Node>(node.size())
        for (element in node) {
            elements.add(toNode(element))
        }
        return ArrayNode.from(elements)
    }

    private fun readNumber(node: NumericNode): NumberNode {
        val raw = node.asText()
        try {
            BigDecimal(raw)
        } catch (e: NumberFormatException) {
            throw CborParseException(
                "CBOR non-finite floating point value '$raw' is not supported",
                e,
            )
        }
        return NumberNode.ofRaw(raw)
    }

    private fun closeQuietly(parser: JsonParser) {
        try {
            parser.close()
        } catch (_: IOException) {
            // 文档已经读取完成，关闭失败不影响解析结果。
        }
    }

    public companion object {

        private val CBOR_FACTORY: CBORFactory = CBORFactory()

        private val OBJECT_MAPPER: ObjectMapper = ObjectMapper(CBOR_FACTORY)

        /**
         * 解析 CBOR 字节数组。
         *
         * @param source CBOR 二进制内容
         * @return 文档树
         * @throws CborParseException CBOR 格式非法、根值缺失、存在多个根值，或包含
         *   NaN/Infinity 等无法表示的浮点值时抛出
         */
        @JvmStatic
        public fun parse(source: ByteArray): Node = CborReader(source).read()
    }
}

/**
 * 将 CBOR 字节数组解析为文档树。
 *
 * @return 文档树
 * @throws CborParseException CBOR 格式非法、根值缺失、存在多个根值，或包含
 *   NaN/Infinity 等无法表示的浮点值时抛出
 */
public fun ByteArray.toCborNode(): Node = CborReader(this).read()
