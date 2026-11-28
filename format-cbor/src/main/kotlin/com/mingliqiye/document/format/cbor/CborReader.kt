package com.mingliqiye.document.format.cbor

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.cbor.CBORParser
import com.fasterxml.jackson.dataformat.cbor.CBORSimpleValue
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

/**
 * CBOR 读取入口。
 *
 * 基于 上游实现 的 [CBORParser] 流式递归读取，把二进制文档还原为通用 [Node] 树。
 * 输入必须且只能包含一个顶层 CBOR 数据项（CBOR sequence 请使用
 * [CborSequenceReader]）。
 *
 * 类型映射：
 * - `null` / `undefined` → [NullNode]（开启
 *   [CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT] 时 `undefined` 映射为
 *   `{"$cborUndefined": true}` 标记对象）
 * - `false` / `true` → [BooleanNode]
 * - 整数（含 bignum）→ [NumberNode]，保留十进制文本 [NumberNode.raw]
 * - 浮点数（半精度/单精度/双精度）→ [NumberNode]，保留 上游实现 解码后的文本
 * - 十进制分数（tag 4）→ [NumberNode]，精确值
 * - 文本字符串（含 tag 0 日期文本）→ [StringNode]
 * - 字节字符串 → [BinaryNode]（CBOR byte string 不会被当成文本）
 * - CBOR simple value → [NumberNode]（开启
 *   [CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT] 时映射为
 *   `{"$cborSimpleValue": <整数>}` 标记对象）
 * - 数组（含不定长数组）→ [ArrayNode]
 * - map（含不定长 map）→ [ObjectNode]
 *
 * 说明：
 * - 默认不保留 CBOR tag；tag 2/3（bignum）、tag 4（十进制分数）由 上游实现 语义解码，
 *   日期等字符串 tag 解码为文本。
 * - 上游实现 能解码半精度/单精度/双精度浮点；但 [NumberNode] 只支持有限
 *   [BigDecimal]，因此 NaN 与 Infinity 会抛 [CborParseException]，不会静默降级。
 * - CBOR map 的 key 由 上游实现 转换：文本 key 原样使用，整数 key 转为十进制字符串；
 *   其他类型 key 不在本模块支持范围内，上游实现 解析失败时同样抛 [CborParseException]。
 *
 * [preserveTags] 为 `true` 时保留每个值的 `getCurrentTag()` 为包装对象
 * `{"$cborTag": <tag 整数>, "value": <Node>}`，支持对象/数组/标量上的 tag 以及嵌套
 * 值上的 tag。实验行为（上游实现 2.20）：
 * - tag 0/1/24 等普通 tag 在对应值 Token 上可见，会被包装保留；
 * - tag 2/3（bignum）、tag 4（十进制分数）已被 上游实现 语义解码，`getCurrentTag()`
 *   返回 -1，因此不会包装；写出时由数字策略重新编码等价 tag；
 * - 同一值上叠加的多个 tag 只能看到最外层第一个（上游实现 公有 API 限制），
 *   其余 tag 在保真模式下丢失；CBOR map key 上的 tag 不保留。
 *
 * [readFeatures] 直接落到 上游实现 parser 特性上：负数 bignum 解码、`undefined` /
 * simple value 的 embedded object 暴露、重复 key 检测（通用
 * `STRICT_DUPLICATE_DETECTION`），默认值对齐 上游实现 2.x。
 *
 * @param source CBOR 二进制内容
 * @param maxDepth Node 树最大深度；0 表示不限制
 * @param preserveTags 是否保留 CBOR tag（包装对象协议），默认 `false`
 * @param readFeatures CBOR 读取特性集合，默认 [CborConfig.DEFAULT_READ_FEATURES]
 */
public class CborReader @JvmOverloads constructor(
    private val source: ByteArray,
    private val maxDepth: Int = 0,
    private val preserveTags: Boolean = false,
    private val readFeatures: Set<CborReadFeature> = CborConfig.DEFAULT_READ_FEATURES,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws CborParseException CBOR 格式非法、根值缺失、存在多个根值，或包含
     *   NaN/Infinity 等无法表示的浮点值时抛出
     */
    public fun read(): Node {
        val parser = createParser()
        try {
            parser.nextToken() ?: throw CborParseException("CBOR document has no top-level value")
            val node = readValue(parser, 1)
            if (parser.nextToken() != null) {
                throw CborParseException("CBOR document must contain exactly one top-level value")
            }
            return node
        } catch (e: JsonProcessingException) {
            throw invalidCbor(e)
        } catch (e: IOException) {
            throw CborParseException("Invalid CBOR: ${e.message}", e)
        } finally {
            closeQuietly(parser)
        }
    }

    /**
     * 读取 CBOR sequence（连续多个自描述数据项）中的全部文档树。
     *
     * 仅供 [CborSequenceReader] 使用；单值 [read] 语义保持不变。每个数据项独立
     * 解析，深度从 1 重新开始计数。空输入返回空列表。
     *
     * @param maxItems 最大数据项数量；`<= 0` 表示不限制
     * @return 按出现顺序排列的文档树列表
     * @throws CborParseException 任一项格式非法、项数超过 maxItems，或包含
     *   NaN/Infinity 等无法表示的浮点值时抛出
     */
    internal fun readAll(maxItems: Int): List<Node> {
        val parser = createParser()
        try {
            val nodes = ArrayList<Node>()
            while (true) {
                parser.nextToken() ?: break
                if (maxItems > 0 && nodes.size >= maxItems) {
                    throw CborParseException("CBOR sequence contains more than maxItems $maxItems items")
                }
                nodes.add(readValue(parser, 1))
            }
            return nodes
        } catch (e: JsonProcessingException) {
            throw invalidCbor(e)
        } catch (e: IOException) {
            throw CborParseException("Invalid CBOR: ${e.message}", e)
        } finally {
            closeQuietly(parser)
        }
    }

    private fun createParser(): CBORParser = try {
        CborFeatureSupport.factory(readFeatures, CborConfig.DEFAULT_WRITE_FEATURES).createParser(source)
    } catch (e: IOException) {
        throw CborParseException("Cannot create CBOR parser", e)
    }

    private fun readValue(parser: CBORParser, depth: Int): Node {
        val tag = if (preserveTags) parser.currentTag else -1
        val token = parser.currentToken()
        val value = when (token) {
            JsonToken.START_OBJECT -> readObject(parser, depth)
            JsonToken.START_ARRAY -> readArray(parser, depth)
            JsonToken.VALUE_STRING -> StringNode.of(parser.text)
            JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> numberNode(parser.text)
            JsonToken.VALUE_TRUE -> BooleanNode.of(true)
            JsonToken.VALUE_FALSE -> BooleanNode.of(false)
            JsonToken.VALUE_NULL -> NullNode.INSTANCE
            JsonToken.VALUE_EMBEDDED_OBJECT -> readEmbedded(parser)
            else -> throw CborParseException("Unsupported CBOR token: $token")
        }
        return if (tag >= 0) CborTagWrapper.wrap(tag, value) else value
    }

    private fun readObject(parser: CBORParser, depth: Int): ObjectNode {
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
                    fields[name] = readValue(parser, depth + 1)
                }

                else -> throw CborParseException("Expected CBOR field name but was ${parser.currentToken()}")
            }
        }
    }

    private fun readArray(parser: CBORParser, depth: Int): ArrayNode {
        checkDepth(depth)
        val elements = ArrayList<Node>()
        while (true) {
            when (parser.nextToken()) {
                null -> throw CborParseException("Unexpected end of CBOR array")
                JsonToken.END_ARRAY -> return ArrayNode.from(elements)
                else -> elements.add(readValue(parser, depth + 1))
            }
        }
    }

    private fun readEmbedded(parser: CBORParser): Node {
        val embedded = parser.embeddedObject
        if (embedded is ByteArray) {
            // 上游实现 的 CBORParser 对 byte string 会分配新数组；BinaryNode.of 仍会
            // 防御性复制一次，保证节点不可变。
            return BinaryNode.of(embedded)
        }
        if (embedded == null) {
            return if (CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT in readFeatures) {
                CborFeatureSupport.undefinedNode()
            } else {
                NullNode.INSTANCE
            }
        }
        if (embedded is CBORSimpleValue) {
            return if (CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT in readFeatures) {
                CborFeatureSupport.simpleValueNode(embedded.value)
            } else {
                numberNode(embedded.value.toString())
            }
        }
        throw CborParseException("Unsupported CBOR embedded object: ${embedded.javaClass.name}")
    }

    private fun checkDepth(depth: Int) {
        if (maxDepth > 0 && depth > maxDepth) {
            throw CborParseException("CBOR nesting depth $depth exceeds maxDepth $maxDepth")
        }
    }

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

    private fun invalidCbor(e: JsonProcessingException): CborParseException = CborParseException("Invalid CBOR: ${e.originalMessage}", e)

    private fun closeQuietly(parser: CBORParser) {
        try {
            parser.close()
        } catch (_: IOException) {
            // 文档已经读取完成，关闭失败不影响解析结果。
        }
    }

    public companion object {

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
 * @param readFeatures CBOR 读取特性集合，默认 [CborConfig.DEFAULT_READ_FEATURES]
 * @return 文档树
 * @throws CborParseException CBOR 格式非法、根值缺失、存在多个根值，或包含
 *   NaN/Infinity 等无法表示的浮点值时抛出
 */
public fun ByteArray.toCborNode(
    preserveTags: Boolean = false,
    readFeatures: Set<CborReadFeature> = CborConfig.DEFAULT_READ_FEATURES,
): Node = CborReader(this, preserveTags = preserveTags, readFeatures = readFeatures).read()
