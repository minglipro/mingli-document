package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DocumentTokenWriter
import java.util.Base64

/**
 * 直接输出 JSON 的流式写出器。
 *
 * 与 [JsonWriter] 输出格式一致，但由 Mapper 直接驱动，不构建完整 Node 树。
 * 输出行为由 [JsonWriteOptions] 控制。
 *
 * @param pretty 是否使用两空格缩进
 * @param options 写出特性
 */
internal class JsonStreamWriter(
    private val pretty: Boolean,
    private val options: JsonWriteOptions = JsonWriteOptions.DEFAULT,
) : DocumentTokenWriter {

    private val builder = StringBuilder(32 * 1024)
    private val quotedNames = HashMap<String, String>()
    private var firstElements = BooleanArray(8)
    private var depth = 0
    private var expectingValue = false

    override fun writeBinary(value: ByteArray) {
        writeString(Base64.getEncoder().encodeToString(value))
    }

    override fun writeNull() {
        beforeValue()
        builder.append("null")
    }

    override fun writeString(value: String) {
        beforeValue()
        appendString(value)
    }

    override fun writeNumber(raw: String) {
        beforeValue()
        appendNumber(raw)
    }

    override fun writeInt(value: Int) {
        beforeValue()
        appendNumber(value.toString())
    }

    override fun writeLong(value: Long) {
        beforeValue()
        appendNumber(value.toString())
    }

    override fun writeDouble(value: Double) {
        beforeValue()
        appendNumber(if (value.isFinite()) JsonDoubleFormatter.format(value) else nonFiniteDoubleText(value))
    }

    override fun writeBoolean(value: Boolean) {
        beforeValue()
        builder.append(if (value) "true" else "false")
    }

    override fun startObject() {
        beforeValue()
        builder.append('{')
        push(true)
    }

    override fun endObject() {
        val first = pop()
        if (!first && pretty) {
            appendNewline(depth)
        }
        builder.append('}')
        expectingValue = false
    }

    override fun startArray() {
        beforeValue()
        builder.append('[')
        push(true)
    }

    override fun endArray() {
        val first = pop()
        if (!first && pretty) {
            appendNewline(depth)
        }
        builder.append(']')
        expectingValue = false
    }

    override fun fieldName(name: String) {
        val index = depth - 1
        if (!firstElements[index]) {
            builder.append(',')
        }
        firstElements[index] = false
        if (pretty) {
            appendNewline(depth)
        }
        appendFieldName(name)
        builder.append(if (pretty) ": " else ":")
        expectingValue = true
    }

    override fun writeNode(node: Node) {
        when (node) {
            is ObjectNode -> {
                startObject()
                for ((key, value) in node.asMap()) {
                    fieldName(key)
                    writeNode(value)
                }
                endObject()
            }

            is ArrayNode -> {
                startArray()
                for (element in node) {
                    writeNode(element)
                }
                endArray()
            }

            is NumberNode -> writeNumber(node.raw)
            is StringNode -> writeString(node.value)
            is BooleanNode -> writeBoolean(node.value)
            is BinaryNode -> writeString(Base64.getEncoder().encodeToString(node.value))
            is NullNode -> writeNull()
        }
    }

    override fun toString(): String = builder.toString()

    private fun beforeValue() {
        if (expectingValue) {
            expectingValue = false
            return
        }
        if (depth == 0) {
            return
        }
        val index = depth - 1
        if (!firstElements[index]) {
            builder.append(',')
        }
        firstElements[index] = false
        if (pretty) {
            appendNewline(depth)
        }
    }

    private fun push(first: Boolean) {
        if (depth == firstElements.size) {
            firstElements = firstElements.copyOf(firstElements.size * 2)
        }
        firstElements[depth++] = first
    }

    private fun pop(): Boolean = firstElements[--depth]

    private fun appendFieldName(name: String) {
        val cached = quotedNames[name]
        if (cached != null) {
            builder.append(cached)
            return
        }
        val start = builder.length
        if (options.quoteFieldNames) {
            appendString(name)
        } else {
            builder.append(name)
        }
        quotedNames[name] = builder.substring(start)
    }

    private fun appendString(value: String) {
        builder.append('"')
        if (options.needsEscape(value)) {
            builder.append(options.escape(value))
        } else {
            builder.append(value)
        }
        builder.append('"')
    }

    private fun appendNumber(raw: String) {
        val text = options.numberLiteral(raw)
        if (options.shouldQuoteNumber(text)) {
            appendString(text)
        } else {
            builder.append(text)
        }
    }

    private fun appendNewline(indent: Int) {
        builder.append('\n')
        repeat(indent) {
            builder.append("  ")
        }
    }
}
