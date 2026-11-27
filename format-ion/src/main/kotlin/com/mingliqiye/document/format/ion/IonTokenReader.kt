package com.mingliqiye.document.format.ion

import com.amazon.ion.IonSystem
import com.amazon.ion.IonType
import com.amazon.ion.system.IonSystemBuilder
import com.mingliqiye.document.format.ion.exception.IonParseException
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import java.io.ByteArrayInputStream

/**
 * 基于 ion-java 的流式 Token 读取器。
 *
 * 把 ion-java 的 `IonReader` 事件流转换为 [DocumentTokenReader] Token 流，
 * 供 [com.mingliqiye.document.serialization.DocumentMapper.fromTokens] 直接
 * 反序列化 Bean，避免为映射构建完整 Node 树。
 *
 * Token 语义与 Mapper 解码约定一致：
 * - STRUCT → START_OBJECT，随后 FIELD_NAME 与值 Token，最后 END_OBJECT
 * - LIST / SEXP → START_ARRAY，随后值 Token，最后 END_ARRAY
 * - 标量 → STRING / NUMBER / BOOLEAN / NULL / BINARY
 * - 顶层结束后返回 `null`
 *
 * 与 [IonBinaryReader] 一致，使用 `IonSystem.newReader(ByteArrayInputStream)`
 * 读取，自动识别二进制/文本 Ion。timestamp 映射为 STRING。
 *
 * @param source 二进制（或文本）Ion 内容
 */
internal class IonTokenReader(
    source: ByteArray,
) : DocumentTokenReader {

    private val reader: com.amazon.ion.IonReader = ION_SYSTEM.newReader(ByteArrayInputStream(source))
    private val containers = ArrayDeque<IonType>()

    private var current: DocumentTokenType? = null
    private var currentText: String? = null
    private var currentBoolean: Boolean? = null
    private var currentBinary: ByteArray? = null
    private var pendingValue = false
    private var finished = false

    override val currentToken: DocumentTokenType?
        get() = current

    override val textValue: String?
        get() = currentText

    override val booleanValue: Boolean?
        get() = currentBoolean

    override val binaryValue: ByteArray?
        get() = currentBinary

    override fun nextToken(): DocumentTokenType? {
        if (finished) {
            return null
        }
        return try {
            advance()
        } catch (e: com.amazon.ion.IonException) {
            finished = true
            throw IonParseException("Invalid Ion: ${e.message}", e)
        }
    }

    private fun advance(): DocumentTokenType? {
        // 已定位在 struct 字段值上：先发出 FIELD_NAME，下一次调用再发出值 Token。
        if (pendingValue) {
            pendingValue = false
            val type = reader.getType() ?: throw IonParseException("Missing Ion value")
            return emitValue(type)
        }
        val type = reader.next()
        if (type == null) {
            if (containers.isEmpty()) {
                finished = true
                clearCurrent()
                closeQuietly()
                return null
            }
            reader.stepOut()
            val container = containers.removeLast()
            val end = if (container == IonType.STRUCT) {
                DocumentTokenType.END_OBJECT
            } else {
                DocumentTokenType.END_ARRAY
            }
            return setCurrent(end)
        }
        if (reader.isInStruct) {
            pendingValue = true
            val fieldName = reader.getFieldName()
                ?: throw IonParseException("Ion struct field is missing a name")
            return setCurrent(DocumentTokenType.FIELD_NAME, text = fieldName)
        }
        return emitValue(type)
    }

    private fun emitValue(type: IonType): DocumentTokenType {
        if (reader.isNullValue) {
            return setCurrent(DocumentTokenType.NULL)
        }
        return when (type) {
            IonType.NULL -> setCurrent(DocumentTokenType.NULL)
            IonType.BOOL -> setCurrent(DocumentTokenType.BOOLEAN, boolean = reader.booleanValue())
            IonType.INT -> setCurrent(DocumentTokenType.NUMBER, text = reader.bigIntegerValue().toString())
            IonType.FLOAT -> setCurrent(DocumentTokenType.NUMBER, text = reader.doubleValue().toString())
            IonType.DECIMAL -> setCurrent(DocumentTokenType.NUMBER, text = reader.bigDecimalValue().toString())
            IonType.TIMESTAMP -> setCurrent(DocumentTokenType.STRING, text = reader.timestampValue()?.toString())
            IonType.SYMBOL, IonType.STRING -> setCurrent(DocumentTokenType.STRING, text = reader.stringValue())
            IonType.BLOB, IonType.CLOB -> setCurrent(DocumentTokenType.BINARY, binary = reader.newBytes())
            IonType.STRUCT -> {
                containers.addLast(IonType.STRUCT)
                reader.stepIn()
                setCurrent(DocumentTokenType.START_OBJECT)
            }

            IonType.LIST, IonType.SEXP -> {
                containers.addLast(type)
                reader.stepIn()
                setCurrent(DocumentTokenType.START_ARRAY)
            }

            IonType.DATAGRAM -> throw IonParseException("Unexpected Ion datagram value")
        }
    }

    private fun setCurrent(
        token: DocumentTokenType,
        text: String? = null,
        boolean: Boolean? = null,
        binary: ByteArray? = null,
    ): DocumentTokenType {
        current = token
        currentText = text
        currentBoolean = boolean
        currentBinary = binary
        return token
    }

    private fun clearCurrent() {
        current = null
        currentText = null
        currentBoolean = null
        currentBinary = null
    }

    private fun closeQuietly() {
        try {
            reader.close()
        } catch (_: Exception) {
            // Token 流已经结束，关闭失败不影响解析结果。
        }
    }

    private companion object {

        private val ION_SYSTEM: IonSystem = IonSystemBuilder.standard().build()
    }
}
