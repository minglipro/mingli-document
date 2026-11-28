package com.mingliqiye.document.format.toml

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

class TomlStreamingOutputTest {

    @Test
    fun outputStreamMatchesFormat() {
        val mapper = TomlMapper()
        val value = linkedMapOf("name" to "a", "nested" to mapOf("age" to 1))

        val output = ByteArrayOutputStream()
        mapper.format(value, output)
        assertContentEquals(mapper.format(value).toByteArray(Charsets.UTF_8), output.toByteArray())
    }
}
