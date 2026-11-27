package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.util.LinkedHashMap

/**
 * 把 Token 流读取成局部 Node 树。
 *
 * 只用于转换器、data class 等仍依赖 Node 的局部兼容路径；
 * 普通 Bean 映射可以直接消费 Token。
 */
internal object TokenTreeReader {

    fun readNode(reader: DocumentTokenReader): Node {
        val token = reader.currentToken
            ?: throw DocumentSerializationException("No current token")
        return when (token) {
            DocumentTokenType.START_OBJECT -> readObject(reader)
            DocumentTokenType.START_ARRAY -> readArray(reader)
            DocumentTokenType.STRING, DocumentTokenType.FIELD_NAME ->
                StringNode.of(reader.textValue ?: throw missing("string"))
            DocumentTokenType.NUMBER ->
                NumberNode.ofRaw(reader.textValue ?: throw missing("number"))
            DocumentTokenType.BOOLEAN ->
                BooleanNode.of(reader.booleanValue ?: throw missing("boolean"))
            DocumentTokenType.BINARY ->
                BinaryNode.of(reader.binaryValue ?: throw missing("binary"))
            DocumentTokenType.NULL -> NullNode.INSTANCE
            DocumentTokenType.END_OBJECT,
            DocumentTokenType.END_ARRAY,
            -> throw DocumentSerializationException("Unexpected token $token")
        }
    }

    fun skipValue(reader: DocumentTokenReader) {
        when (reader.currentToken) {
            DocumentTokenType.START_OBJECT -> {
                while (true) {
                    when (reader.nextToken()) {
                        null -> throw DocumentSerializationException("Unexpected end of object")
                        DocumentTokenType.END_OBJECT -> return
                        DocumentTokenType.FIELD_NAME -> {
                            reader.nextToken() ?: throw DocumentSerializationException("Unexpected end of object")
                            skipValue(reader)
                        }

                        else -> throw DocumentSerializationException("Expected field name")
                    }
                }
            }

            DocumentTokenType.START_ARRAY -> {
                while (true) {
                    when (reader.nextToken()) {
                        null -> throw DocumentSerializationException("Unexpected end of array")
                        DocumentTokenType.END_ARRAY -> return
                        else -> skipValue(reader)
                    }
                }
            }

            null -> throw DocumentSerializationException("No current token")
            else -> Unit
        }
    }

    private fun readObject(reader: DocumentTokenReader): ObjectNode {
        val fields = LinkedHashMap<String, Node>()
        while (true) {
            when (reader.nextToken()) {
                null -> throw DocumentSerializationException("Unexpected end of object")
                DocumentTokenType.END_OBJECT -> return ObjectNode.from(fields)
                DocumentTokenType.FIELD_NAME -> {
                    val name = reader.textValue ?: throw missing("field name")
                    reader.nextToken() ?: throw DocumentSerializationException("Unexpected end of object")
                    fields[name] = readNode(reader)
                }

                else -> throw DocumentSerializationException("Expected field name")
            }
        }
    }

    private fun readArray(reader: DocumentTokenReader): ArrayNode {
        val elements = ArrayList<Node>()
        while (true) {
            when (reader.nextToken()) {
                null -> throw DocumentSerializationException("Unexpected end of array")
                DocumentTokenType.END_ARRAY -> return ArrayNode.from(elements)
                else -> elements.add(readNode(reader))
            }
        }
    }

    private fun missing(kind: String): DocumentSerializationException = DocumentSerializationException("Missing $kind token value")
}
