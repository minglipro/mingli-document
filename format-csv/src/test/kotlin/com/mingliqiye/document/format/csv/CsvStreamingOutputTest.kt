package com.mingliqiye.document.format.csv

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

class CsvStreamingOutputTest {

    @Test
    fun outputStreamMatchesFormat() {
        val mapper = CsvMapper()
        val value = listOf(
            linkedMapOf("name" to "a", "age" to 1),
            linkedMapOf("name" to "b", "age" to 2),
        )

        val output = ByteArrayOutputStream()
        mapper.format(value, output)
        assertContentEquals(mapper.format(value).toByteArray(Charsets.UTF_8), output.toByteArray())
    }
}
