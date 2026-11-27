package com.mingliqiye.document.format.ion

import com.amazon.ion.IonSystem
import com.amazon.ion.IonType
import com.amazon.ion.system.IonSystemBuilder
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.ion.exception.IonParseException
import java.io.StringReader
import java.util.Base64

/**
 * Ion 读取入口。
 *
 * 基于 ion-java 的流式读取器，把 Ion 文本还原为通用 [Node] 树。当前只支持
 * 单文档单根：输入必须且只能包含一个顶层 Ion 值；Ion 注解不会保留。
 *
 * 类型映射：
 * - `null`（含 `null.int` 等类型化 null）→ [NullNode]
 * - `bool` → [BooleanNode]
 * - `int` / `float` / `decimal` → [NumberNode]（保留 [NumberNode.raw]）
 * - `string` / `symbol` → [StringNode]
 * - `timestamp` → [StringNode]（Ion 时间戳文本，ISO 8601 风格）
 * - `blob` / `clob` → [StringNode]（Base64 编码，Node 模型暂无二进制节点）
 * - `list` / `sexp` → [ArrayNode]
 * - `struct` → [ObjectNode]
 *
 * @param source Ion 文本
 */
public class IonReader(
    private val source: String,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws IonParseException Ion 格式非法、根值缺失或存在多个根值时抛出
     */
    public fun read(): Node {
        val reader = ION_SYSTEM.newReader(StringReader(source))
        try {
            return readDocument(reader)
        } catch (e: com.amazon.ion.IonException) {
            throw IonParseException("Invalid Ion: ${e.message}", e)
        } finally {
            closeQuietly(reader)
        }
    }

    private fun readDocument(reader: com.amazon.ion.IonReader): Node {
        val firstType = reader.next()
            ?: throw IonParseException("Ion document has no top-level value")
        val node = readValue(reader, firstType)
        if (reader.next() != null) {
            throw IonParseException("Ion document must contain exactly one top-level value")
        }
        return node
    }

    private fun readValue(reader: com.amazon.ion.IonReader, type: IonType): Node {
        if (reader.isNullValue()) {
            return NullNode.INSTANCE
        }
        return when (type) {
            IonType.NULL -> NullNode.INSTANCE
            IonType.BOOL -> BooleanNode.of(reader.booleanValue())
            IonType.INT -> NumberNode.ofRaw(reader.bigIntegerValue().toString())
            IonType.FLOAT -> readFloatNode(reader)
            IonType.DECIMAL -> NumberNode.ofRaw(reader.bigDecimalValue().toString())
            IonType.TIMESTAMP -> StringNode.of(requireTimestamp(reader))
            IonType.SYMBOL, IonType.STRING -> StringNode.of(reader.stringValue())
            IonType.CLOB, IonType.BLOB -> StringNode.of(Base64.getEncoder().encodeToString(reader.newBytes()))
            IonType.LIST, IonType.SEXP -> readArrayNode(reader)
            IonType.STRUCT -> readObjectNode(reader)
            IonType.DATAGRAM -> throw IonParseException("Unexpected Ion datagram value")
        }
    }

    private fun readFloatNode(reader: com.amazon.ion.IonReader): NumberNode {
        val value = reader.doubleValue()
        if (!value.isFinite()) {
            throw IonParseException("Ion float NaN and Infinity are not supported")
        }
        return NumberNode.ofRaw(value.toString())
    }

    private fun requireTimestamp(reader: com.amazon.ion.IonReader): String {
        val timestamp = reader.timestampValue()
            ?: throw IonParseException("Ion timestamp value is missing")
        return timestamp.toString()
    }

    private fun readArrayNode(reader: com.amazon.ion.IonReader): ArrayNode {
        reader.stepIn()
        val elements = ArrayList<Node>()
        while (true) {
            val childType = reader.next() ?: break
            elements.add(readValue(reader, childType))
        }
        reader.stepOut()
        return ArrayNode.from(elements)
    }

    private fun readObjectNode(reader: com.amazon.ion.IonReader): ObjectNode {
        reader.stepIn()
        val fields = LinkedHashMap<String, Node>()
        while (true) {
            val childType = reader.next() ?: break
            val fieldName = reader.getFieldName()
                ?: throw IonParseException("Ion struct field is missing a name")
            fields[fieldName] = readValue(reader, childType)
        }
        reader.stepOut()
        return ObjectNode.from(fields)
    }

    private fun closeQuietly(reader: com.amazon.ion.IonReader) {
        try {
            reader.close()
        } catch (_: Exception) {
            // 文档已经读取完成，关闭失败不影响解析结果。
        }
    }

    public companion object {

        private val ION_SYSTEM: IonSystem = IonSystemBuilder.standard().build()

        /**
         * 解析 Ion 字符串。
         *
         * @param source Ion 文本
         * @return 文档树
         * @throws IonParseException Ion 格式非法、根值缺失或存在多个根值时抛出
         */
        @JvmStatic
        public fun parse(source: String): Node = IonReader(source).read()
    }
}

/**
 * 将字符串解析为文档树。
 *
 * @return 文档树
 * @throws IonParseException Ion 格式非法、根值缺失或存在多个根值时抛出
 */
public fun String.toIonNode(): Node = IonReader(this).read()
