package com.mingliqiye.document.format.json

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class JsonStreamingOutputTest {

    @Test
    fun outputStreamMatchesFormatBytes() {
        val mapper = JsonMapper()
        val value = linkedMapOf("name" to "a", "numbers" to listOf(1, 2, 3))

        val output = TrackingOutputStream()
        mapper.format(value, output)
        assertContentEquals(mapper.formatBytes(value), output.toByteArray())
        assertFalse(output.closed)
    }

    @Test
    fun prettyOutputStreamMatchesFormatPretty() {
        val mapper = JsonMapper()
        val value = linkedMapOf("name" to "a", "nested" to mapOf("x" to true))

        val output = ByteArrayOutputStream()
        mapper.formatPretty(value, output)
        assertContentEquals(mapper.formatPretty(value).toByteArray(Charsets.UTF_8), output.toByteArray())
    }

    private class TrackingOutputStream : OutputStream() {
        private val delegate = ByteArrayOutputStream()
        var closed = false
            private set

        override fun write(b: Int) {
            delegate.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
        }

        fun toByteArray(): ByteArray = delegate.toByteArray()

        override fun close() {
            closed = true
        }
    }
}
