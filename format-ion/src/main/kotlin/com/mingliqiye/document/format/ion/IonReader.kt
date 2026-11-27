package com.mingliqiye.document.format.ion

import com.amazon.ion.IonSystem
import com.amazon.ion.IonType
import com.amazon.ion.system.IonSystemBuilder
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.ion.exception.IonParseException
import java.io.StringReader

/**
 * 文本 Ion 读取入口。
 *
 * 基于 ion-java 的流式读取器，把 Ion 文本还原为通用 [Node] 树。当前只支持
 * 单文档单根：输入必须且只能包含一个顶层 Ion 值；Ion 注解不会保留。
 *
 * 默认类型映射（[preserveTypes] = false）：
 * - `null`（含 `null.int` 等类型化 null）→ [NullNode]
 * - `bool` → [BooleanNode]
 * - `int` / `float` / `decimal` → [NumberNode]（保留 [NumberNode.raw]）
 * - `string` / `symbol` → [StringNode]
 * - `timestamp` → [StringNode]（Ion 时间戳文本，ISO 8601 风格）
 * - `blob` / `clob` → [BinaryNode]
 * - `list` / `sexp` → [ArrayNode]
 * - `struct` → [ObjectNode]
 *
 * [preserveTypes] = true 时 timestamp / symbol / blob / clob 使用类型保真包装
 * 对象（见 [IonConfig.preserveTypes]），普通 `string` 仍为 [StringNode]。
 *
 * @param source Ion 文本
 * @param maxDepth Node 树最大深度，0 表示不限制
 * @param preserveTypes 是否保留 Ion 专属类型，默认 `false`
 */
public class IonReader @JvmOverloads constructor(
    private val source: String,
    private val maxDepth: Int = 0,
    private val preserveTypes: Boolean = false,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws IonParseException Ion 格式非法、根值缺失或存在多个根值时抛出
     */
    public fun read(): Node = readIonNode(ION_SYSTEM.newReader(StringReader(source)), maxDepth, preserveTypes)

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
 * 从任意 ion-java 数据源（文本或二进制）读取单文档单根。
 *
 * 读取完成后关闭底层读取器；ion-java 的 `IonException` 统一转换为
 * [IonParseException]。文本 [IonReader] 与 [IonBinaryReader] 共用该实现。
 *
 * @param preserveTypes 是否把 Ion 专属类型读成类型保真包装对象
 */
internal fun readIonNode(
    reader: com.amazon.ion.IonReader,
    maxDepth: Int = 0,
    preserveTypes: Boolean = false,
): Node {
    try {
        return readDocument(reader, maxDepth, preserveTypes)
    } catch (e: com.amazon.ion.IonException) {
        throw IonParseException("Invalid Ion: ${e.message}", e)
    } finally {
        closeQuietly(reader)
    }
}

private fun readDocument(reader: com.amazon.ion.IonReader, maxDepth: Int, preserveTypes: Boolean): Node {
    val firstType = reader.next()
        ?: throw IonParseException("Ion document has no top-level value")
    val node = readValue(reader, firstType, 1, maxDepth, preserveTypes)
    if (reader.next() != null) {
        throw IonParseException("Ion document must contain exactly one top-level value")
    }
    return node
}

private fun readValue(
    reader: com.amazon.ion.IonReader,
    type: IonType,
    depth: Int,
    maxDepth: Int,
    preserveTypes: Boolean,
): Node {
    if (reader.isNullValue()) {
        return NullNode.INSTANCE
    }
    return when (type) {
        IonType.NULL -> NullNode.INSTANCE
        IonType.BOOL -> BooleanNode.of(reader.booleanValue())
        IonType.INT -> NumberNode.ofRaw(reader.bigIntegerValue().toString())
        IonType.FLOAT -> readFloatNode(reader)
        IonType.DECIMAL -> NumberNode.ofRaw(reader.bigDecimalValue().toString())
        IonType.TIMESTAMP -> readTimestampNode(reader, preserveTypes)
        IonType.SYMBOL -> readSymbolNode(reader, preserveTypes)
        IonType.STRING -> StringNode.of(reader.stringValue())
        IonType.CLOB -> readClobNode(reader, preserveTypes)
        IonType.BLOB -> readBlobNode(reader, preserveTypes)
        IonType.LIST, IonType.SEXP -> readArrayNode(reader, depth, maxDepth, preserveTypes)
        IonType.STRUCT -> readObjectNode(reader, depth, maxDepth, preserveTypes)
        IonType.DATAGRAM -> throw IonParseException("Unexpected Ion datagram value")
    }
}

private fun readTimestampNode(reader: com.amazon.ion.IonReader, preserveTypes: Boolean): Node {
    val text = requireTimestamp(reader)
    return if (preserveTypes) IonTypeFidelity.timestamp(text) else StringNode.of(text)
}

private fun readSymbolNode(reader: com.amazon.ion.IonReader, preserveTypes: Boolean): Node {
    val text = reader.stringValue()
    return if (preserveTypes) IonTypeFidelity.symbol(text) else StringNode.of(text)
}

private fun readClobNode(reader: com.amazon.ion.IonReader, preserveTypes: Boolean): Node {
    val bytes = reader.newBytes()
    return if (preserveTypes) IonTypeFidelity.clob(bytes) else BinaryNode.of(bytes)
}

private fun readBlobNode(reader: com.amazon.ion.IonReader, preserveTypes: Boolean): Node {
    val bytes = reader.newBytes()
    return if (preserveTypes) IonTypeFidelity.blob(bytes) else BinaryNode.of(bytes)
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

private fun readArrayNode(
    reader: com.amazon.ion.IonReader,
    depth: Int,
    maxDepth: Int,
    preserveTypes: Boolean,
): ArrayNode {
    checkDepth(depth, maxDepth)
    reader.stepIn()
    val elements = ArrayList<Node>()
    while (true) {
        val childType = reader.next() ?: break
        elements.add(readValue(reader, childType, depth + 1, maxDepth, preserveTypes))
    }
    reader.stepOut()
    return ArrayNode.from(elements)
}

private fun readObjectNode(
    reader: com.amazon.ion.IonReader,
    depth: Int,
    maxDepth: Int,
    preserveTypes: Boolean,
): ObjectNode {
    checkDepth(depth, maxDepth)
    reader.stepIn()
    val fields = LinkedHashMap<String, Node>()
    while (true) {
        val childType = reader.next() ?: break
        val fieldName = reader.getFieldName()
            ?: throw IonParseException("Ion struct field is missing a name")
        fields[fieldName] = readValue(reader, childType, depth + 1, maxDepth, preserveTypes)
    }
    reader.stepOut()
    return ObjectNode.from(fields)
}

private fun checkDepth(depth: Int, maxDepth: Int) {
    if (maxDepth > 0 && depth > maxDepth) {
        throw IonParseException("Ion nesting depth $depth exceeds maxDepth $maxDepth")
    }
}

private fun closeQuietly(reader: com.amazon.ion.IonReader) {
    try {
        reader.close()
    } catch (_: Exception) {
        // 文档已经读取完成，关闭失败不影响解析结果。
    }
}

/**
 * 将字符串解析为文档树。
 *
 * @return 文档树
 * @throws IonParseException Ion 格式非法、根值缺失或存在多个根值时抛出
 */
public fun String.toIonNode(): Node = IonReader(this).read()
