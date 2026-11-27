package com.mingliqiye.document.serialization

import java.lang.reflect.Type

/**
 * 流式对象实例化上下文。
 */
public interface StreamingInstantiationContext {

    /** 当前节点路径。 */
    public val path: String

    /**
     * 从流中读取指定属性值。
     *
     * @param reader 文档读取器
     * @param targetType 目标类型
     * @param property 当前属性元数据；根对象为 `null`
     * @return 读取结果
     */
    public fun readValue(
        reader: DocumentValueReader,
        targetType: Type,
        property: DocumentProperty?,
    ): Any?
}
