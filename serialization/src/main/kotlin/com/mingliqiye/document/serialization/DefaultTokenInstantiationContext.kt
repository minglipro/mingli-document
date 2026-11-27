package com.mingliqiye.document.serialization

import java.lang.reflect.Type

/**
 * [TokenInstantiationContext] 默认实现。
 */
internal class DefaultTokenInstantiationContext(
    private val mapper: DocumentMapper,
    private val decodePath: DecodePath,
) : TokenInstantiationContext {

    override val path: String
        get() = decodePath.toString()

    override fun readValue(
        reader: DocumentTokenReader,
        targetType: Type,
        property: DocumentProperty?,
    ): Any? = mapper.readTokenValueForInstantiator(reader, targetType, property, decodePath)

    override fun skipValue(reader: DocumentTokenReader) {
        TokenTreeReader.skipValue(reader)
    }
}
