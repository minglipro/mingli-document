package com.mingliqiye.document.serialization

/**
 * [SerializationContext] 默认实现。
 */
internal class DefaultSerializationContext(
    override val mapper: DocumentMapperApi,
    override val property: DocumentProperty?,
    override val path: String,
) : SerializationContext
