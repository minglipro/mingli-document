package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import java.lang.reflect.Type

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
}

/**
 * 对象实例化上下文。
 */
public interface InstantiationContext {

    /** 当前节点路径。 */
    public val path: String

    /**
     * 将节点反序列化为指定 Java 类型。
     *
     * @param node 文档节点
     * @param targetType 目标类型
     * @return 反序列化结果
     */
    public fun deserialize(node: Node, targetType: Type): Any?
}

/**
 * [InstantiationContext] 默认实现。
 */
internal class DefaultInstantiationContext(
    private val mapper: DocumentMapper,
    override val path: String,
) : InstantiationContext {

    override fun deserialize(node: Node, targetType: Type): Any? = mapper.deserializeForInstantiator(node, targetType, path)
}
