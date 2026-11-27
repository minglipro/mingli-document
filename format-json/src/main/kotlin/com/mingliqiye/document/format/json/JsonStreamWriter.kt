package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DocumentValueWriter

private const val HEX_DIGITS = "0123456789abcdef"

/**
 * 直接输出 JSON 的流式写出器。
 *
 * 与 [JsonWriter] 输出格式一致，但由 Mapper 直接驱动，不构建完整 Node 树。
 */
internal class JsonStreamWriter(
    private val pretty: Boolean,
) : DocumentValueWriter {

    private val builder = StringBuilder()
    private val firstElements = ArrayList<Boolean>()
    private var expectingValue = false

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
        builder.append(raw)
    }

    override fun writeBoolean(value: Boolean) {
        beforeValue()
        builder.append(if (value) "true" else "false")
    }

    override fun startObject() {
        beforeValue()
        builder.append('{')
        firstElements.add(true)
    }

    override fun endObject() {
        val first = firstElements.removeAt(firstElements.lastIndex)
        if (!first && pretty) {
            appendNewline(firstElements.size)
        }
        builder.append('}')
        expectingValue = false
    }

    override fun startArray() {
        beforeValue()
        builder.append('[')
        firstElements.add(true)
    }

    override fun endArray() {
        val first = firstElements.removeAt(firstElements.lastIndex)
        if (!first && pretty) {
            appendNewline(firstElements.size)
        }
        builder.append(']')
        expectingValue = false
    }

    override fun fieldName(name: String) {
        val first = firstElements.removeAt(firstElements.lastIndex)
        if (!first) {
            builder.append(',')
        }
        firstElements.add(false)
        if (pretty) {
            appendNewline(firstElements.size)
        }
        appendString(name)
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
            is NullNode -> writeNull()
        }
    }

    override fun toString(): String = builder.toString()

    private fun beforeValue() {
        if (expectingValue) {
            expectingValue = false
            return
        }
        if (firstElements.isEmpty()) {
            return
        }
        val first = firstElements.removeAt(firstElements.lastIndex)
        if (!first) {
            builder.append(',')
        }
        firstElements.add(false)
        if (pretty) {
            appendNewline(firstElements.size)
        }
    }

    private fun appendString(value: String) {
        builder.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (ch < ' ') {
                        appendUnicodeEscape(ch)
                    } else {
                        builder.append(ch)
                    }
                }
            }
        }
        builder.append('"')
    }

    private fun appendUnicodeEscape(ch: Char) {
        builder.append("\\u")
        val code = ch.code
        for (shift in 12 downTo 0 step 4) {
            builder.append(HEX_DIGITS[(code shr shift) and 0xF])
        }
    }

    private fun appendNewline(indent: Int) {
        builder.append('\n')
        repeat(indent) {
            builder.append("  ")
        }
    }
}
