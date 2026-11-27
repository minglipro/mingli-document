package com.mingliqiye.document.format.toml

import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TomlDepthTest {

    @Test
    fun maxNodeDepthRejectsNestedTable() {
        val mapper = TomlMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("[a.b]\nc = 1", Map::class.java)
        }
    }
}
