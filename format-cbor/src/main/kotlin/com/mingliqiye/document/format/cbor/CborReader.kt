package com.mingliqiye.document.format.cbor

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.CBORParser
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
 * 默认基于 Jackson 的 CBOR 解析器（[CBORFactory] + `ObjectMapper.readTree`）把二进制
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
 * - 默认不保留 CBOR tag；tag 2/3（bignum）、tag 4（十进制分数）由 Jackson 语义解码，
 *   日期等字符串 tag 解码为文本。
 * - Jackson 能解码半精度/单精度/双精度浮点；但 [NumberNode] 只支持有限
 *   [BigDecimal]，因此 NaN 与 Infinity 会抛 [CborParseException]，不会静默降级。
 * - CBOR map 的 key 由 Jackson 转换：文本 key 原样使用，整数 key 转为十进制字符串；
 *   其他类型 key 不在本模块支持范围内，Jackson 解析失败时同样抛 [CborParseException]。
 *
 * [preserveTags] 为 `true` 时改用 [CBORParser] 流式递归读取，并把每个值的
 * `getCurrentTag()` 保留为包装对象 `{"$cborTag": <tag 整数>, "value": <Node>}`，
 * 支持对象/数组/标量上的 tag 以及嵌套值上的 tag。实验行为（Jackson 2.15）：
 * - tag 0/1/24 等普通 tag 在对应值 Token 上可见，会被包装保留；
 * - tag 2/3（bignum）、tag 4（十进制分数）已被 Jackson 语义解码，`getCurrentTag()`
 *   返回 -1，因此不会包装；写出时由数字策略重新编码等价 tag；
 * - 同一值上叠加的多个 tag 只能看到最外层第一个（Jackson 公有 API 限制），
 *   其余 tag 在保真模式下丢失；CBOR map key 上的 tag 不保留。
 *
 * @param source CBOR 二进制内容
 * @param maxDepth Node 树最大深度；0 表示不限制
 * @param preserveTags 是否保留 CBOR tag（包装对象协议），默认 `false`
 */
public class CborReader @JvmOverloads constructor(
    private val source: ByteArray,
    private val maxDepth: Int = 0,
    private val preserveTags: Boolean = false,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws CborParseException CBOR 格式非法、根值缺失、存在多个根值，或包含
     *   NaN/Infinity 等无法表示的浮点值时抛出
     */
    public fun read(): Node = if (preserveTags) readPreservingTags() else readWithoutTags()

    private fun readWithoutTags(): Node {
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

    private fun readPreservingTags(): Node {
        val parser = try {
            CBOR_FACTORY.createParser(source)
        } catch (e: IOException) {
            throw CborParseException("Cannot create CBOR parser", e)
        }
        try {
            parser.nextToken() ?: throw CborParseException("CBOR document has no top-level value")
            val node = readTaggedValue(parser, 1)
            if (parser.nextToken() != null) {
                throw CborParseException("CBOR document must contain exactly one top-level value")
            }
            return node
        } catch (e: JsonProcessingException) {
            throw CborParseException("Invalid CBOR: ${e.originalMessage}", e)
        } catch (e: IOException) {
            throw CborParseException("Invalid CBOR: ${e.message}", e)
        } finally {
            closeQuietly(parser)
        }
    }

    private fun readTaggedValue(parser: CBORParser, depth: Int): Node {
        val tag = parser.currentTag
        val value = when (parser.currentToken()) {
            JsonToken.START_OBJECT -> readTaggedObject(parser, depth)
            JsonToken.START_ARRAY -> readTaggedArray(parser, depth)
            JsonToken.VALUE_STRING -> StringNode.of(parser.text)
            JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> numberNode(parser.text)
            JsonToken.VALUE_TRUE -> BooleanNode.of(true)
            JsonToken.VALUE_FALSE -> BooleanNode.of(false)
            JsonToken.VALUE_NULL -> NullNode.INSTANCE
            JsonToken.VALUE_EMBEDDED_OBJECT -> BinaryNode.of(embeddedBytes(parser))
            else -> throw CborParseException("Unsupported CBOR token: ${parser.currentToken()}")
        }
        return if (tag >= 0) CborTagWrapper.wrap(tag, value) else value
    }

    private fun readTaggedObject(parser: CBORParser, depth: Int): ObjectNode {
        checkDepth(depth)
        val fields = LinkedHashMap<String, Node>()
        while (true) {
            when (parser.nextToken()) {
                null -> throw CborParseException("Unexpected end of CBOR object")
                JsonToken.END_OBJECT -> return ObjectNode.from(fields)
                JsonToken.FIELD_NAME -> {
                    val name = parser.currentName()
                        ?: throw CborParseException("CBOR map field name is missing")
                    parser.nextToken() ?: throw CborParseException("Unexpected end of CBOR object")
                    fields[name] = readTaggedValue(parser, depth + 1)
                }

                else -> throw CborParseException("Expected CBOR field name but was ${parser.currentToken()}")
            }
        }
    }

    private fun readTaggedArray(parser: CBORParser, depth: Int): ArrayNode {
        checkDepth(depth)
        val elements = ArrayList<Node>()
        while (true) {
            when (parser.nextToken()) {
                null -> throw CborParseException("Unexpected end of CBOR array")
                JsonToken.END_ARRAY -> return ArrayNode.from(elements)
                else -> elements.add(readTaggedValue(parser, depth + 1))
            }
        }
    }

    private fun embeddedBytes(parser: CBORParser): ByteArray {
        val embedded = parser.embeddedObject
        if (embedded is ByteArray) {
            return embedded.copyOf()
        }
        throw CborParseException("Unsupported CBOR embedded object: ${embedded?.javaClass?.name}")
    }

    private fun toNode(node: JsonNode, depth: Int = 1): Node = when {
        node.isObject -> readObject(node as JacksonObjectNode, depth)
        node.isArray -> readArray(node as JacksonArrayNode, depth)
        node.isTextual -> StringNode.of(node.textValue())
        node.isNumber -> readNumber(node as NumericNode)
        node.isBoolean -> BooleanNode.of(node.booleanValue())
        node.isBinary -> BinaryNode.of(node.binaryValue())
        node.isNull -> NullNode.INSTANCE
        else -> throw CborParseException("Unsupported CBOR value: ${node.nodeType}")
    }

    private fun readObject(node: JacksonObjectNode, depth: Int): ObjectNode {
        checkDepth(depth)
        val fields = LinkedHashMap<String, Node>()
        val iterator = node.fields()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            fields[entry.key] = toNode(entry.value, depth + 1)
        }
        return ObjectNode.from(fields)
    }

    private fun readArray(node: JacksonArrayNode, depth: Int): ArrayNode {
        checkDepth(depth)
        val elements = ArrayList<Node>(node.size())
        for (element in node) {
            elements.add(toNode(element, depth + 1))
        }
        return ArrayNode.from(elements)
    }

    private fun checkDepth(depth: Int) {
        if (maxDepth > 0 && depth > maxDepth) {
            throw CborParseException("CBOR nesting depth $depth exceeds maxDepth $maxDepth")
        }
    }

    private fun readNumber(node: NumericNode): NumberNode = numberNode(node.asText())

    private fun numberNode(raw: String): NumberNode {
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
 * @param preserveTags 是否保留 CBOR tag（包装对象协议），默认 `false`
 * @return 文档树
 * @throws CborParseException CBOR 格式非法、根值缺失、存在多个根值，或包含
 *   NaN/Infinity 等无法表示的浮点值时抛出
 */
public fun ByteArray.toCborNode(preserveTags: Boolean = false): Node = CborReader(this, preserveTags = preserveTags).read()
