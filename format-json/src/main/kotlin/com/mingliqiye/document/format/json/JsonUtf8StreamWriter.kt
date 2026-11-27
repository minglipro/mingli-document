package com.mingliqiye.document.format.json

import com.fasterxml.jackson.core.io.schubfach.DoubleToDecimal
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DocumentTokenWriter
import java.io.ByteArrayOutputStream
import java.util.Base64

private val TRUE_BYTES = "true".toByteArray(Charsets.US_ASCII)
private val FALSE_BYTES = "false".toByteArray(Charsets.US_ASCII)
private val NULL_BYTES = "null".toByteArray(Charsets.US_ASCII)

/**
 * 直接输出 UTF-8 JSON 字节的流式写出器（紧凑格式）。
 *
 * 与 [JsonStreamWriter] 的 `pretty = false` 输出逐字节一致，但直接从 Token
 * 流写出 UTF-8 字节，不构造中间 String。
 */
internal class JsonUtf8StreamWriter : DocumentTokenWriter {

    private val output = ByteArrayOutputStream(32 * 1024)
    private var firstElements = BooleanArray(8)
    private var depth = 0
    private var expectingValue = false

    fun toByteArray(): ByteArray = output.toByteArray()

    override fun writeBinary(value: ByteArray) {
        writeString(Base64.getEncoder().encodeToString(value))
    }

    override fun writeNull() {
        beforeValue()
        output.write(NULL_BYTES)
    }

    override fun writeString(value: String) {
        beforeValue()
        appendString(value)
    }

    override fun writeNumber(raw: String) {
        beforeValue()
        output.write(raw.toByteArray(Charsets.US_ASCII))
    }

    override fun writeInt(value: Int) {
        beforeValue()
        output.write(value.toString().toByteArray(Charsets.US_ASCII))
    }

    override fun writeLong(value: Long) {
        beforeValue()
        output.write(value.toString().toByteArray(Charsets.US_ASCII))
    }

    override fun writeDouble(value: Double) {
        require(value.isFinite()) { "NaN and Infinity cannot be serialized" }
        beforeValue()
        output.write(DoubleToDecimal.toString(value).toByteArray(Charsets.US_ASCII))
    }

    override fun writeBoolean(value: Boolean) {
        beforeValue()
        output.write(if (value) TRUE_BYTES else FALSE_BYTES)
    }

    override fun startObject() {
        beforeValue()
        output.write('{'.code)
        push()
    }

    override fun endObject() {
        pop()
        output.write('}'.code)
        expectingValue = false
    }

    override fun startArray() {
        beforeValue()
        output.write('['.code)
        push()
    }

    override fun endArray() {
        pop()
        output.write(']'.code)
        expectingValue = false
    }

    override fun fieldName(name: String) {
        val index = depth - 1
        if (!firstElements[index]) {
            output.write(','.code)
        }
        firstElements[index] = false
        appendString(name)
        output.write(':'.code)
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
            output.write(','.code)
        }
        firstElements[index] = false
    }

    private fun push() {
        if (depth == firstElements.size) {
            firstElements = firstElements.copyOf(firstElements.size * 2)
        }
        firstElements[depth++] = true
    }

    private fun pop() {
        depth--
    }

    private fun appendString(value: String) {
        var needsEscape = false
        for (ch in value) {
            if (ch == '"' || ch == '\\' || ch < ' ') {
                needsEscape = true
                break
            }
        }
        if (!needsEscape) {
            output.write('"'.code)
            output.write(value.toByteArray(Charsets.UTF_8))
            output.write('"'.code)
            return
        }
        output.write('"'.code)
        val escaped = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '"' -> escaped.append("\\\"")
                '\\' -> escaped.append("\\\\")
                '\b' -> escaped.append("\\b")
                '\u000C' -> escaped.append("\\f")
                '\n' -> escaped.append("\\n")
                '\r' -> escaped.append("\\r")
                '\t' -> escaped.append("\\t")
                else -> {
                    if (ch < ' ') {
                        escaped.append("\\u")
                        val code = ch.code
                        for (shift in 12 downTo 0 step 4) {
                            escaped.append("0123456789abcdef"[(code shr shift) and 0xF])
                        }
                    } else {
                        escaped.append(ch)
                    }
                }
            }
        }
        output.write(escaped.toString().toByteArray(Charsets.UTF_8))
        output.write('"'.code)
    }
}
