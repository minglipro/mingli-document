package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.serialization.exception.DocumentSerializationException

/**
 * 二进制格式 Mapper 基类。
 *
 * 文本 `parse(String)` / `format` 会抛出不支持异常，字节 API 由
 * [parseBytes] / [writeBytes] 提供。
 */
public abstract class BinaryDocumentMapper protected constructor(
    formatName: String,
    parseBytes: (ByteArray) -> Node,
    writeBytes: (Node, Boolean) -> ByteArray,
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    formatName,
    { throw DocumentSerializationException("$formatName mapper only supports binary input") },
    { _, _ -> throw DocumentSerializationException("$formatName mapper only supports binary output") },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
    parseBytes,
    writeBytes,
) {

    protected override val usesTextFormat: Boolean = false
}
