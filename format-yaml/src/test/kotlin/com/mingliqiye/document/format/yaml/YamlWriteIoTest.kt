package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.Node
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class YamlWriteIoTest {

    private val mapper = YamlMapper()
    private val value = linkedMapOf("name" to "a", "age" to 1)

    @Test
    fun textFormatSupportsByteArray() {
        val expected = mapper.format(value)
        assertEquals(expected, String(mapper.formatBytes(value), Charsets.UTF_8))
        assertEquals(
            mapper.parse(expected, Node::class.java),
            mapper.parse(mapper.formatBytes(value), Node::class.java),
        )
    }

    @Test
    fun filePathStreamAndWriter() {
        val expected = mapper.format(value)
        val directory = Files.createTempDirectory("mingli-yaml-io")
        try {
            val file = directory.resolve("value.yaml").toFile()
            mapper.format(value, file)
            assertEquals(expected, file.readText())

            val path = directory.resolve("value-path.yaml")
            mapper.format(value, path)
            assertEquals(expected, String(Files.readAllBytes(path), Charsets.UTF_8))

            val output = ByteArrayOutputStream()
            mapper.format(value, output)
            assertEquals(expected, output.toString(Charsets.UTF_8.name()))

            val writer = StringWriter()
            mapper.format(value, writer)
            assertEquals(expected, writer.toString())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun streamingOutputStreamMatchesFormat() {
        val output = ByteArrayOutputStream()
        mapper.format(value, output)
        assertEquals(mapper.format(value), output.toString(Charsets.UTF_8.name()))

        val pretty = ByteArrayOutputStream()
        mapper.formatPretty(value, pretty)
        assertEquals(mapper.formatPretty(value), pretty.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun prettyOverloads() {
        val expected = mapper.formatPretty(value)
        val directory = Files.createTempDirectory("mingli-yaml-pretty")
        try {
            val file = directory.resolve("pretty.yaml").toFile()
            mapper.formatPretty(value, file)
            assertEquals(expected, file.readText())

            val path = directory.resolve("pretty-path.yaml")
            mapper.formatPretty(value, path)
            assertEquals(expected, String(Files.readAllBytes(path), Charsets.UTF_8))

            val output = ByteArrayOutputStream()
            mapper.formatPretty(value, output)
            assertEquals(expected, output.toString(Charsets.UTF_8.name()))

            val writer = StringWriter()
            mapper.formatPretty(value, writer)
            assertEquals(expected, writer.toString())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
