package com.mingliqiye.document.serialization

import java.lang.reflect.Type

/**
 * [StreamingInstantiationContext] 默认实现。
 */
internal class DefaultStreamingInstantiationContext(
    private val mapper: DocumentMapper,
    override val path: String,
) : StreamingInstantiationContext {

    override fun readValue(
        reader: DocumentValueReader,
        targetType: Type,
        property: DocumentProperty?,
    ): Any? = mapper.readValueForInstantiator(reader, targetType, property, path)
}
