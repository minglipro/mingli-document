package com.mingliqiye.document.format.properties

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

class PropertiesStreamingOutputTest {

    @Test
    fun outputStreamMatchesFormat() {
        val mapper = PropertiesMapper()
        val value = linkedMapOf("name" to "a", "nested" to mapOf("age" to 1))

        val output = ByteArrayOutputStream()
        mapper.format(value, output)
        assertContentEquals(mapper.format(value).toByteArray(Charsets.UTF_8), output.toByteArray())
    }
}
