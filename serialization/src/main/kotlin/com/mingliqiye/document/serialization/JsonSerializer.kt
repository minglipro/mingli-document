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
public interface JsonSerializer<T> {

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

/**
 * 文档反序列化器。
 *
 * 转换器通过 [DeserializationContext.property] 读取当前字段的名称、类型和注解。
 *
 * @param T 需要反序列化的类型
 */
public interface JsonDeserializer<T> {

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

/**
 * 双向转换器：同时具备序列化和反序列化能力。
 *
 * 字段注解、字段名等上下文通过 [SerializationContext.property] /
 * [DeserializationContext.property] 获取。
 *
 * @param T 转换类型
 */
public interface JsonConverter<T> :
    JsonSerializer<T>,
    JsonDeserializer<T>
