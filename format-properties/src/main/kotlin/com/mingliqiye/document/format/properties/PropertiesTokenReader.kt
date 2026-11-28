package com.mingliqiye.document.format.properties

import com.mingliqiye.document.serialization.DocumentToken
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.math.BigDecimal

/**
 * Properties 原生 Token 读取器。
 *
 * 直接把 Properties 平铺 key/value 转换成 [DocumentTokenReader] Token 流，
 * 不构建 `com.mingliqiye.document.core.Node` 树；使用与 [PropertiesReader] 相同的
 * 轻量路径树（[PropertiesTreeReader]），按 [PropertiesConfig] 的 上游实现
 * `JavaPropsSchema` 语义生成 Token：
 * - 对象：START_OBJECT、若干 FIELD_NAME + 值 Token、END_OBJECT；
 * - 数组：START_ARRAY、元素 Token、END_ARRAY（稠密）；
 * - 标量：STRING / NUMBER / BOOLEAN；
 * - 空输入：空对象（START_OBJECT、END_OBJECT）。
 *
 * 与 `NodeTokenReader(PropertiesReader(source, config).read())` 的 Token 序列一致。
 *
 * Token 流同样受 maxNodeDepth/maxNodeCount/maxStringLength 限制：构造时在生成
 * Token 的过程中执行与 Node 路径 `NodeLimits` 等价的校验，超限抛
 * [DocumentSerializationException]。无法在 Token 流校验的项只有 Node 树循环检测：
 * Properties 平铺 key 还原出的结构本身是一棵树，不可能出现环；maxInputSize 仍由
 * `DocumentMapper.parse` 在创建本读取器之前校验。
 *
 * @param source Properties 文本
 * @param config Properties 配置
 * @param maxNodeDepth Node 树最大深度；0 表示不限制
 * @param maxNodeCount Node 树最大节点数；0 表示不限制
 * @param maxStringLength 字段名与字符串值最大长度；0 表示不限制
 * @param maxDepth 解析最大嵌套深度；默认取 [PropertiesConfig.maxDepth]，
 *   `0` 或负数表示不限制
 */
internal class PropertiesTokenReader(
    source: String,
    private val config: PropertiesConfig = PropertiesConfig.empty(),
    maxNodeDepth: Int = 0,
    maxNodeCount: Long = 0,
    maxStringLength: Int = 0,
    maxDepth: Int = config.maxDepth,
) : DocumentTokenReader {

    private val limits = TokenLimits(maxNodeDepth, maxNodeCount, maxStringLength)
    private val tokens: List<DocumentToken> = buildTokens(source, maxDepth)
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

    private fun buildTokens(source: String, maxDepth: Int): List<DocumentToken> {
        val root = PropertiesTreeReader(config, maxDepth).read(source)
        val tokens = ArrayList<DocumentToken>()
        appendNode(root, 1, tokens)
        return tokens
    }

    private fun appendNode(node: PropertiesPathNode, depth: Int, tokens: MutableList<DocumentToken>) {
        when {
            node.isArray -> {
                limits.enterNode(depth)
                tokens.add(DocumentToken.startArray())
                node.nullableValue?.let { appendScalar(it, depth + 1, tokens) }
                for (child in node.arrayContents()) {
                    appendNode(child, depth + 1, tokens)
                }
                tokens.add(DocumentToken.endArray())
            }

            node.hasNameChildren -> {
                limits.enterNode(depth)
                tokens.add(DocumentToken.startObject())
                node.nullableValue?.let {
                    limits.checkString("")
                    tokens.add(DocumentToken.fieldName(""))
                    appendScalar(it, depth + 1, tokens)
                }
                for ((name, child) in node.nameContents()) {
                    limits.checkString(name)
                    tokens.add(DocumentToken.fieldName(name))
                    appendNode(child, depth + 1, tokens)
                }
                tokens.add(DocumentToken.endObject())
            }

            else -> {
                val value = node.nullableValue
                if (value == null) {
                    // 空输入/无内容：与 PropertiesReader 一致返回空对象。
                    limits.enterNode(depth)
                    tokens.add(DocumentToken.startObject())
                    tokens.add(DocumentToken.endObject())
                } else {
                    appendScalar(value, depth, tokens)
                }
            }
        }
    }

    private fun appendScalar(text: String, depth: Int, tokens: MutableList<DocumentToken>) {
        limits.enterNode(depth)
        when {
            text == "true" -> tokens.add(DocumentToken.boolean(true))
            text == "false" -> tokens.add(DocumentToken.boolean(false))
            isNumber(text) -> tokens.add(DocumentToken.number(text))
            else -> {
                limits.checkString(text)
                tokens.add(DocumentToken.string(text))
            }
        }
    }

    private fun isNumber(text: String): Boolean = try {
        BigDecimal(text)
        true
    } catch (_: NumberFormatException) {
        false
    }
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
