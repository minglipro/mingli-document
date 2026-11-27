package com.mingliqiye.document.serialization

/**
 * 序列化上下文。
 */
public interface SerializationContext {

    /** 当前 Mapper。 */
    public val mapper: DocumentMapperApi

    /** 当前字段/属性；根对象或集合元素为 `null`。 */
    public val property: DocumentProperty?

    /** 当前路径，用于错误定位。 */
    public val path: String
}
