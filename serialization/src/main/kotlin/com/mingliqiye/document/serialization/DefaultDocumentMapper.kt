package com.mingliqiye.document.serialization

import com.mingliqiye.document.serialization.exception.DocumentSerializationException

/**
 * 不带格式实现的内部 Mapper，用于只做树映射的场景与测试。
 */
internal class DefaultDocumentMapper :
    DocumentMapper(
        "none",
        { throw DocumentSerializationException("This DocumentMapper has no text parser") },
        { _, _ -> throw DocumentSerializationException("This DocumentMapper has no text writer") },
    )
