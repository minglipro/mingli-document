package com.mingliqiye.document.serialization

/**
 * 流式文档 Token 写出器。
 */
public interface DocumentTokenWriter {

    public fun startObject()

    public fun endObject()

    public fun startArray()

    public fun endArray()

    public fun fieldName(name: String)

    public fun writeString(value: String)

    public fun writeNumber(raw: String)

    public fun writeBoolean(value: Boolean)

    public fun writeNull()

    public fun writeBinary(value: ByteArray)
}
