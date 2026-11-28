package com.mingliqiye.document.format.xml

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

class XmlStreamingOutputTest {

    @Test
    fun outputStreamMatchesFormat() {
        val mapper = XmlMapper()
        val value = linkedMapOf("name" to "a", "numbers" to listOf(1, 2, 3))

        val output = ByteArrayOutputStream()
        mapper.format(value, output)
        assertContentEquals(mapper.format(value).toByteArray(Charsets.UTF_8), output.toByteArray())
    }

    @Test
    fun prettyOutputStreamMatchesFormatPretty() {
        val mapper = XmlMapper()
        val value = linkedMapOf("name" to "a", "nested" to mapOf("x" to true))

        val output = ByteArrayOutputStream()
        mapper.formatPretty(value, output)
        assertContentEquals(mapper.formatPretty(value).toByteArray(Charsets.UTF_8), output.toByteArray())
    }
}
