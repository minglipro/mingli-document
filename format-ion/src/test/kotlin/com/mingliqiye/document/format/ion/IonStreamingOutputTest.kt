package com.mingliqiye.document.format.ion

import com.mingliqiye.document.core.Node
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class IonStreamingOutputTest {

    @Test
    fun textOutputStreamMatchesFormat() {
        val mapper = IonMapper()
        val value = linkedMapOf("name" to "a", "numbers" to listOf(1, 2, 3))

        val output = ByteArrayOutputStream()
        mapper.format(value, output)
        assertContentEquals(mapper.format(value).toByteArray(Charsets.UTF_8), output.toByteArray())
        assertEquals(
            mapper.parse(mapper.format(value), Node::class.java),
            mapper.parse(output.toByteArray(), Node::class.java),
        )
    }

    @Test
    fun prettyOutputStreamMatchesFormatPretty() {
        val mapper = IonMapper()
        val value = linkedMapOf("name" to "a", "nested" to mapOf("x" to true))

        val output = ByteArrayOutputStream()
        mapper.formatPretty(value, output)
        assertContentEquals(
            mapper.formatPretty(value).toByteArray(Charsets.UTF_8),
            output.toByteArray(),
        )
    }
}
