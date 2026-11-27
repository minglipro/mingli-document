package com.mingliqiye.document.serialization

/**
 * 一个流式文档 Token。
 *
 * 不同 Token 只使用对应字段：
 * - `FIELD_NAME` / `STRING`：`text`
 * - `NUMBER`：`text`（原始数字文本）
 * - `BOOLEAN`：`booleanValue`
 * - `BINARY`：`binaryValue`
 */
public class DocumentToken private constructor(
    public val type: DocumentTokenType,
    public val text: String? = null,
    public val booleanValue: Boolean? = null,
    public val binaryValue: ByteArray? = null,
) {

    public companion object {

        @JvmStatic
        public fun startObject(): DocumentToken = DocumentToken(DocumentTokenType.START_OBJECT)

        @JvmStatic
        public fun endObject(): DocumentToken = DocumentToken(DocumentTokenType.END_OBJECT)

        @JvmStatic
        public fun startArray(): DocumentToken = DocumentToken(DocumentTokenType.START_ARRAY)

        @JvmStatic
        public fun endArray(): DocumentToken = DocumentToken(DocumentTokenType.END_ARRAY)

        @JvmStatic
        public fun fieldName(name: String): DocumentToken = DocumentToken(DocumentTokenType.FIELD_NAME, text = name)

        @JvmStatic
        public fun string(value: String): DocumentToken = DocumentToken(DocumentTokenType.STRING, text = value)

        @JvmStatic
        public fun number(raw: String): DocumentToken = DocumentToken(DocumentTokenType.NUMBER, text = raw)

        @JvmStatic
        public fun boolean(value: Boolean): DocumentToken = DocumentToken(DocumentTokenType.BOOLEAN, booleanValue = value)

        @JvmStatic
        public fun nullValue(): DocumentToken = DocumentToken(DocumentTokenType.NULL)

        @JvmStatic
        public fun binary(value: ByteArray): DocumentToken = DocumentToken(DocumentTokenType.BINARY, binaryValue = value.copyOf())
    }
}
