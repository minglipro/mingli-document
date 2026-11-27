package com.mingliqiye.document.format.properties

import com.mingliqiye.document.format.properties.exception.PropertiesParseException
import com.mingliqiye.document.serialization.DocumentToken
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.io.StringReader
import java.math.BigDecimal
import java.util.Properties

/**
 * Properties 原生 Token 读取器。
 *
 * 直接把 Properties 平铺 key/value 转换成 [DocumentTokenReader] Token 流，
 * 不构建 `com.mingliqiye.document.core.Node` 树；为了还原对象/数组层级，只使用
 * `Map` / `List` / [DocumentToken] 组成的轻量中间结构，最终输出 Token 列表。
 *
 * 生成的 Token 序列与 `NodeTokenReader(PropertiesReader(source).read())` 等价：
 * - 对象：START_OBJECT、若干 FIELD_NAME + 值 Token、END_OBJECT
 * - 数组：START_ARRAY、元素 Token、END_ARRAY
 * - 标量：STRING / NUMBER / BOOLEAN
 * - 空输入：空对象（START_OBJECT、END_OBJECT）
 *
 * 解析语义与 [PropertiesReader] 保持一致：key 先排序，再由 [PropertiesPath] 还原
 * `.` 对象路径和 `[index]` 数组下标（包含百分号转义）；值按 true/false/数字/字符串
 * 推断；冲突路径抛 [PropertiesParseException]。
 *
 * Token 流同样受 maxNodeDepth/maxNodeCount/maxStringLength 限制：构造时在生成
 * Token 的过程中执行与 Node 路径 `NodeLimits` 等价的校验，超限抛
 * [DocumentSerializationException]。无法在 Token 流校验的项只有 Node 树循环检测：
 * Properties 平铺 key 还原出的结构本身是一棵树，不可能出现环；maxInputSize 仍由
 * `DocumentMapper.parse` 在创建本读取器之前校验。
 *
 * @param source Properties 文本
 * @param maxNodeDepth Node 树最大深度；0 表示不限制
 * @param maxNodeCount Node 树最大节点数；0 表示不限制
 * @param maxStringLength 字段名与字符串值最大长度；0 表示不限制
 */
internal class PropertiesTokenReader(
    source: String,
    maxNodeDepth: Int = 0,
    maxNodeCount: Long = 0,
    maxStringLength: Int = 0,
) : DocumentTokenReader {

    private val limits = TokenLimits(maxNodeDepth, maxNodeCount, maxStringLength)
    private val tokens: List<DocumentToken> = buildTokens(source)
    private var index = -1
    private var current: DocumentToken? = null

    override val currentToken: DocumentTokenType?
        get() = current?.type

    override val textValue: String?
        get() = current?.text

    override val booleanValue: Boolean?
        get() = current?.booleanValue

    override val binaryValue: ByteArray?
        get() = current?.binaryValue

    override fun nextToken(): DocumentTokenType? {
        index++
        if (index >= tokens.size) {
            current = null
            return null
        }
        val token = tokens[index]
        current = token
        return token.type
    }

    private fun buildTokens(source: String): List<DocumentToken> {
        val properties = Properties()
        try {
            properties.load(StringReader(source))
        } catch (e: Exception) {
            throw PropertiesParseException("Invalid Properties: ${e.message}", e)
        }
        var root: Any? = null
        for (name in properties.stringPropertyNames().sorted()) {
            val segments = PropertiesPath.parse(name)
            val value = inferScalar(properties.getProperty(name))
            root = insert(root, segments, 0, value, name)
        }
        val tokens = ArrayList<DocumentToken>()
        appendNode(root ?: LinkedHashMap<String, Any?>(), 1, tokens)
        return tokens
    }

    @Suppress("UNCHECKED_CAST")
    private fun insert(
        current: Any?,
        segments: List<PropertiesPathSegment>,
        depth: Int,
        value: DocumentToken,
        path: String,
    ): Any? {
        if (depth == segments.size) {
            return value
        }
        val segment = segments[depth]
        return if (segment.isIndex) {
            val list = current as? MutableList<Any?> ?: if (current == null) {
                ArrayList<Any?>()
            } else {
                throw conflict(path)
            }
            while (list.size <= segment.index) {
                list.add(null)
            }
            list[segment.index] = insert(list[segment.index], segments, depth + 1, value, path)
            list
        } else {
            val map = current as? MutableMap<String, Any?> ?: if (current == null) {
                LinkedHashMap<String, Any?>()
            } else {
                throw conflict(path)
            }
            val key = segment.key ?: throw conflict(path)
            map[key] = insert(map[key], segments, depth + 1, value, path)
            map
        }
    }

    private fun appendNode(value: Any?, depth: Int, tokens: MutableList<DocumentToken>) {
        limits.enterNode(depth)
        when (value) {
            null -> {
                // [PropertiesReader] 把数组空洞还原成空对象节点，Token 流保持一致。
                tokens.add(DocumentToken.startObject())
                tokens.add(DocumentToken.endObject())
            }

            is DocumentToken -> {
                if (value.type == DocumentTokenType.STRING) {
                    value.text?.let(limits::checkString)
                }
                tokens.add(value)
            }

            is Map<*, *> -> {
                tokens.add(DocumentToken.startObject())
                for ((key, child) in value) {
                    val name = key.toString()
                    limits.checkString(name)
                    tokens.add(DocumentToken.fieldName(name))
                    appendNode(child, depth + 1, tokens)
                }
                tokens.add(DocumentToken.endObject())
            }

            is List<*> -> {
                tokens.add(DocumentToken.startArray())
                for (element in value) {
                    appendNode(element, depth + 1, tokens)
                }
                tokens.add(DocumentToken.endArray())
            }

            else -> throw PropertiesParseException("Unsupported Properties tree value: ${value.javaClass.name}")
        }
    }

    private fun inferScalar(text: String): DocumentToken = when {
        text == "true" -> DocumentToken.boolean(true)
        text == "false" -> DocumentToken.boolean(false)
        isNumber(text) -> DocumentToken.number(text)
        else -> DocumentToken.string(text)
    }

    private fun isNumber(text: String): Boolean = try {
        BigDecimal(text)
        true
    } catch (_: NumberFormatException) {
        false
    }

    private fun conflict(path: String): PropertiesParseException = PropertiesParseException("Conflicting Properties key path '$path'")
}

/**
 * Token 生成过程中的 Node 限制校验，语义与序列化模块的 `NodeLimits` 对齐。
 */
private class TokenLimits(
    private val maxNodeDepth: Int,
    private val maxNodeCount: Long,
    private val maxStringLength: Int,
) {

    private var nodeCount = 0L

    fun enterNode(depth: Int) {
        if (maxNodeDepth > 0 && depth > maxNodeDepth) {
            throw DocumentSerializationException("Node depth $depth exceeds maxNodeDepth $maxNodeDepth")
        }
        nodeCount++
        if (maxNodeCount > 0 && nodeCount > maxNodeCount) {
            throw DocumentSerializationException("Node count exceeds maxNodeCount $maxNodeCount")
        }
    }

    fun checkString(value: String) {
        if (maxStringLength > 0 && value.length > maxStringLength) {
            throw DocumentSerializationException("String length ${value.length} exceeds maxStringLength $maxStringLength")
        }
    }
}
