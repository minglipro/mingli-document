package com.mingliqiye.document.serialization

import java.lang.reflect.Type

/**
 * Token 流实例化上下文。
 */
public interface TokenInstantiationContext {

    /** 当前节点路径。 */
    public val path: String

    /**
     * 从 Token 流读取指定字段值。
     */
    public fun readValue(
        reader: DocumentTokenReader,
        targetType: Type,
        property: DocumentProperty?,
    ): Any?

    /**
     * 跳过当前值。
     */
    public fun skipValue(reader: DocumentTokenReader)
}
