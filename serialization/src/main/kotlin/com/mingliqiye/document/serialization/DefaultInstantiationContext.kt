package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.Node
import java.lang.reflect.Type

/**
 * [InstantiationContext] 默认实现。
 */
internal class DefaultInstantiationContext(
    private val mapper: DocumentMapper,
    override val path: String,
) : InstantiationContext {

    override fun deserialize(node: Node, targetType: Type): Any? = mapper.deserializeForInstantiator(node, targetType, path)
}
