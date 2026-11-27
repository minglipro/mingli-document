package com.mingliqiye.document.format.csv

import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CsvDepthTest {

    @Test
    fun maxNodeDepthRejectsRows() {
        val mapper = CsvMapper(DocumentMapperConfig.builder().maxNodeDepth(1).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("name\nvalue", Map::class.java)
        }
    }
}
