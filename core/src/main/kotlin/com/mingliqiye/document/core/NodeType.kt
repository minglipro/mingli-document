package com.mingliqiye.document.core

/**
 * 节点类型。
 */
public enum class NodeType(public val value: String) {
    OBJECT("object"),
    ARRAY("array"),
    NUMBER("number"),
    STRING("string"),
    BOOLEAN("boolean"),
    NULL("null"),
}
