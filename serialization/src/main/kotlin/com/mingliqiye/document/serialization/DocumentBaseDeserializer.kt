package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node

/**
 * 文档反序列化器。
 *
 * 转换器通过 [DeserializationContext.property] 读取当前字段的名称、类型和注解。
 *
 * @param T 需要反序列化的类型
 */
public interface DocumentBaseDeserializer<T> {

    /**
     * 获取反序列化器负责的类型。
     */
    public fun getType(): DocumentTypeReference<T>

    /**
     * 将文档树还原为对象。
     *
     * @param node 文档节点
     * @param context 反序列化上下文
     * @return 还原后的对象；无法还原时返回 `null`
     */
    public fun deserialize(node: Node, context: DeserializationContext): T?
}
