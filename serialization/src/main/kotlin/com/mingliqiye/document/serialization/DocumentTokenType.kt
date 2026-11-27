package com.mingliqiye.document.serialization

/**
 * 流式文档 Token 类型。
 */
public enum class DocumentTokenType {
    START_OBJECT,
    END_OBJECT,
    START_ARRAY,
    END_ARRAY,
    FIELD_NAME,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    BINARY,
}
