package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node

/**
 * 文档序列化器。
 *
 * 转换器通过 [SerializationContext.property] 读取当前字段的名称、类型和注解。
 *
 * @param T 需要序列化的类型
 */
public interface DocumentBaseSerializer<T> : com.mingliqiye.document.annotation.DocumentBaseSerializer<T> {

    /**
     * 获取序列化器负责的类型。
     */
    public fun getType(): DocumentTypeReference<T>

    /**
     * 将对象转换为文档树。
     *
     * @param value 待序列化对象
     * @param context 序列化上下文
     * @return 文档节点
     */
    public fun serialize(value: T, context: SerializationContext): Node
}
