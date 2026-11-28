package com.mingliqiye.document.format.smile

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

class SmileStreamingOutputTest {

    @Test
    fun outputStreamMatchesFormatBytes() {
        val mapper = SmileMapper()
        val value = linkedMapOf("name" to "a", "numbers" to listOf(1, 2, 3))

        val output = ByteArrayOutputStream()
        mapper.format(value, output)
        assertContentEquals(mapper.formatBytes(value), output.toByteArray())
    }
}
