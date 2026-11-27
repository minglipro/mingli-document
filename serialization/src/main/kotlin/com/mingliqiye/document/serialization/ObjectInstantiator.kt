package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ObjectNode

/**
 * 自定义对象实例化策略。
 *
 * 用于支持没有 Java 无参构造器的实体，例如 Kotlin data class。
 * 返回 `null` 表示不处理该类型，Mapper 会回退到默认无参构造器。
 */
public fun interface ObjectInstantiator {

    /**
     * 创建目标类型的实例。
     *
     * @param type 目标类型
     * @param node 对象节点
     * @param context 实例化上下文，可递归反序列化构造参数
     * @return 实例；不处理时返回 `null`
     */
    public fun instantiate(
        type: Class<*>,
        node: ObjectNode,
        context: InstantiationContext,
    ): Any?

    /**
     * 是否处理指定类型。
     *
     * 用于流式读取时判断是否需要回退到 Node 路径。
     *
     * @param type 目标类型
     */
    public fun canInstantiate(type: Class<*>): Boolean = true

    /**
     * 流式创建目标类型实例。
     *
     * 返回 `null` 表示不处理；默认实现不读取流。
     *
     * @param reader 文档读取器
     * @param type 目标类型
     * @param context 流式实例化上下文
     * @return 实例；不处理时返回 `null`
     */
    public fun instantiate(
        reader: DocumentValueReader,
        type: Class<*>,
        context: StreamingInstantiationContext,
    ): Any? = null
}
