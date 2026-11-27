package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.Node
import java.lang.reflect.Type

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
