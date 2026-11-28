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
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Base64

private val TRUE_BYTES = "true".toByteArray(Charsets.US_ASCII)
private val FALSE_BYTES = "false".toByteArray(Charsets.US_ASCII)
private val NULL_BYTES = "null".toByteArray(Charsets.US_ASCII)

/**
 * 直接输出 UTF-8 JSON 字节的流式写出器。
 *
 * 与 [JsonStreamWriter] 的输出逐字节一致（在相同特性下），但直接从 Token 流写出
 * UTF-8 字节，不构造中间 String（字符串需要转义时除外）。输出行为由
 * [JsonWriteOptions] 控制。
 *
 * @param output 目标输出流；默认使用内存缓冲
 * @param pretty 是否使用两空格缩进
 * @param options 写出特性
 */
internal class JsonUtf8StreamWriter(
    private val output: OutputStream = ByteArrayOutputStream(32 * 1024),
    private val pretty: Boolean = false,
    private val options: JsonWriteOptions = JsonWriteOptions.DEFAULT,
) : DocumentTokenWriter {

    private val buffer = output as? ByteArrayOutputStream
    private var firstElements = BooleanArray(8)
    private var depth = 0
    private var expectingValue = false

    fun toByteArray(): ByteArray = buffer?.toByteArray()
        ?: throw IllegalStateException("toByteArray is only available for the in-memory constructor")

    override fun flush() {
        output.flush()
    }

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
        output.write(if (value) TRUE_BYTES else FALSE_BYTES)
    }

    override fun startObject() {
        beforeValue()
        output.write('{'.code)
        push()
    }

    override fun endObject() {
        val index = depth - 1
        val empty = firstElements[index]
        depth--
        if (pretty && !empty) {
            newlineAndIndent()
        }
        output.write('}'.code)
        expectingValue = false
    }

    override fun startArray() {
        beforeValue()
        output.write('['.code)
        push()
    }

    override fun endArray() {
        val index = depth - 1
        val empty = firstElements[index]
        depth--
        if (pretty && !empty) {
            newlineAndIndent()
        }
        output.write(']'.code)
        expectingValue = false
    }

    override fun fieldName(name: String) {
        val index = depth - 1
        if (!firstElements[index]) {
            output.write(','.code)
        }
        if (pretty) {
            newlineAndIndent()
        }
        firstElements[index] = false
        appendFieldName(name)
        output.write(':'.code)
        if (pretty) {
            output.write(' '.code)
        }
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
        if (pretty) {
            newlineAndIndent()
        }
        firstElements[index] = false
    }

    private fun newlineAndIndent() {
        output.write('\n'.code)
        var count = depth * 2
        while (count-- > 0) {
            output.write(' '.code)
        }
    }

    private fun push() {
        if (depth == firstElements.size) {
            firstElements = firstElements.copyOf(firstElements.size * 2)
        }
        firstElements[depth++] = true
    }

    private fun appendFieldName(name: String) {
        if (options.quoteFieldNames) {
            appendString(name)
        } else {
            output.write(name.toByteArray(Charsets.UTF_8))
        }
    }

    private fun appendString(value: String) {
        if (!options.needsEscape(value)) {
            output.write('"'.code)
            output.write(value.toByteArray(Charsets.UTF_8))
            output.write('"'.code)
            return
        }
        val escaped = options.escape(value)
        output.write('"'.code)
        output.write(escaped.toByteArray(Charsets.UTF_8))
        output.write('"'.code)
    }

    private fun appendNumber(raw: String) {
        val text = options.numberLiteral(raw)
        if (options.shouldQuoteNumber(text)) {
            appendString(text)
        } else {
            output.write(text.toByteArray(Charsets.US_ASCII))
        }
    }
}
