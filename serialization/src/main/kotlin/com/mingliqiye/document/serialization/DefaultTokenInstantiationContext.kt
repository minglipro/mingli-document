package com.mingliqiye.document.serialization

import java.lang.reflect.Type

/**
 * [TokenInstantiationContext] 默认实现。
 */
internal class DefaultTokenInstantiationContext(
    private val mapper: DocumentMapper,
    override val path: String,
) : TokenInstantiationContext {

    override fun readValue(
        reader: DocumentTokenReader,
        targetType: Type,
        property: DocumentProperty?,
    ): Any? = mapper.readTokenValueForInstantiator(reader, targetType, property, path)

    override fun skipValue(reader: DocumentTokenReader) {
        TokenTreeReader.skipValue(reader)
    }
}
