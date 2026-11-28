package com.mingliqiye.document.format.ion

import com.amazon.ion.Timestamp
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.ion.exception.IonException

/**
 * Ion 类型保真包装对象。
 *
 * 开启 [IonConfig.preserveTypes] 后，Node 路径使用仅含两个字段的包装对象
 * 表示 [Node] 模型无法原生区分的 Ion 值：
 * - `{"$ionType":"timestamp","value":"2024-01-02T03:04:05Z"}`
 * - `{"$ionType":"symbol","value":"text"}`
 * - `{"$ionType":"clob","value":<BinaryNode>}`
 * - `{"$ionType":"blob","value":<BinaryNode>}`
 *
 * 另在 [IonReadFeature.USE_NATIVE_TYPE_ID] / [IonWriteFeature.USE_NATIVE_TYPE_ID]
 * 开启时（保真模式下读取）用注解包装对象表示 Ion annotation：
 * - `{"$ionAnnotations":["ann1","ann2"],"value":<node>}`
 *
 * 写出时只识别「恰好两个字段、标记字段类型/取值匹配」的对象；其余对象按普通
 * 对象处理。
 */
internal object IonTypeFidelity {

    /** 包装对象的类型字段名。 */
    const val TYPE_FIELD: String = "\$ionType"

    /** 包装对象的值字段名。 */
    const val VALUE_FIELD: String = "value"

    /** 注解包装对象的注解数组字段名。 */
    const val ANNOTATIONS_FIELD: String = "\$ionAnnotations"

    /** timestamp 类型标记。 */
    const val TIMESTAMP: String = "timestamp"

    /** symbol 类型标记。 */
    const val SYMBOL: String = "symbol"

    /** clob 类型标记。 */
    const val CLOB: String = "clob"

    /** blob 类型标记。 */
    const val BLOB: String = "blob"

    /** 创建 timestamp 包装对象。 */
    fun timestamp(value: String): ObjectNode = wrap(TIMESTAMP, StringNode.of(value))

    /** 创建 symbol 包装对象。 */
    fun symbol(value: String): ObjectNode = wrap(SYMBOL, StringNode.of(value))

    /** 创建 clob 包装对象。 */
    fun clob(value: ByteArray): ObjectNode = wrap(CLOB, BinaryNode.of(value))

    /** 创建 blob 包装对象。 */
    fun blob(value: ByteArray): ObjectNode = wrap(BLOB, BinaryNode.of(value))

    /**
     * 创建注解包装对象。
     *
     * @param annotations Ion annotation 文本列表（保持顺序）
     * @param value 被注解的值节点
     */
    fun annotated(annotations: List<String>, value: Node): ObjectNode = ObjectNode.of(
        ANNOTATIONS_FIELD to ArrayNode.from(annotations.map { StringNode.of(it) }),
        VALUE_FIELD to value,
    )

    /**
     * 识别注解包装对象。
     *
     * 只识别「恰好两个字段、`$ionAnnotations` 为字符串数组、包含 `value` 字段」
     * 的对象，其余返回 `null`。
     *
     * @return 注解文本列表与内部值节点；不是注解包装对象时返回 `null`
     */
    fun annotationWrapper(node: Node): Pair<List<String>, Node>? {
        if (node !is ObjectNode || node.size != 2) {
            return null
        }
        val annotationArray = node[ANNOTATIONS_FIELD] as? ArrayNode ?: return null
        val value = node[VALUE_FIELD] ?: return null
        val annotations = ArrayList<String>(annotationArray.size)
        for (element in annotationArray) {
            annotations.add(element.asStringOrNull() ?: return null)
        }
        return annotations to value
    }

    /**
     * 尝试把类型保真包装对象写成对应的 Ion 类型。
     *
     * @param writer ion-java 写出器（文本或二进制）
     * @param node 待写出的对象节点
     * @return 是否识别并写出；`false` 表示应按普通对象处理
     * @throws IonException 包装对象声明为 timestamp 但 `value` 不是合法 Ion 时间戳文本时抛出
     */
    fun writeWrapped(writer: com.amazon.ion.IonWriter, node: ObjectNode): Boolean {
        if (node.size != 2) {
            return false
        }
        val type = node[TYPE_FIELD]?.asStringOrNull() ?: return false
        return when (type) {
            TIMESTAMP -> {
                val value = node[VALUE_FIELD]?.asStringOrNull() ?: return false
                writer.writeTimestamp(parseTimestamp(value))
                true
            }

            SYMBOL -> {
                val value = node[VALUE_FIELD]?.asStringOrNull() ?: return false
                writer.writeSymbol(value)
                true
            }

            CLOB -> {
                val value = node[VALUE_FIELD]?.asBinaryOrNull() ?: return false
                writer.writeClob(value)
                true
            }

            BLOB -> {
                val value = node[VALUE_FIELD]?.asBinaryOrNull() ?: return false
                writer.writeBlob(value)
                true
            }

            else -> false
        }
    }

    private fun wrap(type: String, value: Node): ObjectNode = ObjectNode.of(
        TYPE_FIELD to StringNode.of(type),
        VALUE_FIELD to value,
    )

    private fun parseTimestamp(value: String): Timestamp = try {
        Timestamp.valueOf(value)
    } catch (e: RuntimeException) {
        throw IonException("Invalid Ion timestamp text '$value'", e)
    }
}
