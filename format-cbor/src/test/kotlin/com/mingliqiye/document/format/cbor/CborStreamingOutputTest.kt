package com.mingliqiye.document.format.cbor

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

class CborStreamingOutputTest {

    @Test
    fun outputStreamMatchesFormatBytes() {
        val mapper = CborMapper()
        val value = linkedMapOf("name" to "a", "numbers" to listOf(1, 2, 3))

        val output = ByteArrayOutputStream()
        mapper.format(value, output)
        assertContentEquals(mapper.formatBytes(value), output.toByteArray())
    }
}
