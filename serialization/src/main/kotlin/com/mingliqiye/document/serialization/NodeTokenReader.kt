package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode

/**
 * 从 [Node] 树生成 Token 流的读取器。
 *
 * 用于暂时没有原生流式 Token 实现的文本格式：格式先解析成 Node，再通过
 * 本适配器走统一的 Token Bean 解码路径。
 *
 * @param node 根节点
 */
public class NodeTokenReader(
    node: Node,
) : DocumentTokenReader {

    private val tokens: List<DocumentToken> = buildTokens(node)
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

    private companion object {

        private fun buildTokens(node: Node): List<DocumentToken> {
            val tokens = ArrayList<DocumentToken>()
            appendNode(node, tokens)
            return tokens
        }

        private fun appendNode(node: Node, tokens: MutableList<DocumentToken>) {
            when (node) {
                is NullNode -> tokens.add(DocumentToken.nullValue())
                is BooleanNode -> tokens.add(DocumentToken.boolean(node.value))
                is StringNode -> tokens.add(DocumentToken.string(node.value))
                is NumberNode -> tokens.add(DocumentToken.number(node.raw))
                is BinaryNode -> tokens.add(DocumentToken.binary(node.value))
                is ObjectNode -> {
                    tokens.add(DocumentToken.startObject())
                    for ((name, value) in node.entries()) {
                        tokens.add(DocumentToken.fieldName(name))
                        appendNode(value, tokens)
                    }
                    tokens.add(DocumentToken.endObject())
                }

                is ArrayNode -> {
                    tokens.add(DocumentToken.startArray())
                    for (element in node) {
                        appendNode(element, tokens)
                    }
                    tokens.add(DocumentToken.endArray())
                }
            }
        }
    }
}
