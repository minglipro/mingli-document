package com.mingliqiye.document.serialization

/**
 * [DeserializationContext] 默认实现。
 */
internal class DefaultDeserializationContext(
    override val mapper: DocumentMapperApi,
    override val property: DocumentProperty?,
    override val path: String,
) : DeserializationContext
