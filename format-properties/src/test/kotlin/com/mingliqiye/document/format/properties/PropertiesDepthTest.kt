package com.mingliqiye.document.format.properties

import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PropertiesDepthTest {

    @Test
    fun maxNodeDepthRejectsNestedPath() {
        val mapper = PropertiesMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("a.b.c=1", Map::class.java)
        }
    }
}
