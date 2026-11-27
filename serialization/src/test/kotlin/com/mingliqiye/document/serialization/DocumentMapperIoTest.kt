package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * File / Path / Stream 快速入口测试。
 */
class DocumentMapperIoTest {

    @Test
    fun jsonFileAndPathRoundTrip() {
        val mapper = JsonMapper()
        val file = Files.createTempFile("mingli-document", ".json").toFile()
        try {
            mapper.format(
                User().apply {
                    name = "小于"
                    age = 18
                },
                file,
            )
            assertEquals("小于", mapper.parse(file, User::class.java).name)

            mapper.format(
                User().apply {
                    name = "小于"
                    age = 18
                },
                file.toPath(),
            )
            assertEquals(18, mapper.parse(file.toPath(), User::class.java).age)

            assertEquals(
                listOf("a", "b"),
                mapper.parse(
                    file.toPath().toFile().apply { writeText("""["a","b"]""") }.toPath(),
                    DocumentTypeReference.listType(String::class.java),
                ),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun jsonStreamAndReaderWriterRoundTrip() {
        val mapper = JsonMapper()
        val user = User().apply {
            name = "小于"
            age = 18
        }

        val output = ByteArrayOutputStream()
        mapper.format(user, output)
        assertEquals("小于", mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java).name)

        val writer = StringWriter()
        mapper.format(user, writer)
        assertEquals(18, mapper.parse(StringReader(writer.toString()), User::class.java).age)
    }

    @Test
    fun binaryMapperFileAndStreamRoundTrip() {
        val mapper = BinaryIoMapper()
        val file = Files.createTempFile("mingli-document", ".bin").toFile()
        val bytes = byteArrayOf(1, 2, 3, 4)
        try {
            mapper.format(bytes, file)
            assertContentEquals(bytes, mapper.parse(file, ByteArray::class.java))

            val output = ByteArrayOutputStream()
            mapper.format(bytes, output)
            assertContentEquals(bytes, mapper.parse(ByteArrayInputStream(output.toByteArray()), ByteArray::class.java))
        } finally {
            file.delete()
        }
    }

    @Test
    fun binaryMapperRejectsCharacterStreams() {
        val mapper = BinaryIoMapper()

        assertFailsWith<DocumentSerializationException> {
            mapper.parse(StringReader("value"), ByteArray::class.java)
        }
        assertFailsWith<DocumentSerializationException> {
            mapper.format(byteArrayOf(1), StringWriter())
        }
    }

    private class BinaryIoMapper :
        BinaryDocumentMapper(
            "binary-io-test",
            { source -> BinaryNode.of(source) },
            { node, _ -> node.asBinary() },
            emptyMap(),
            emptyMap(),
            null,
            null,
            DocumentMapperConfig.empty(),
        )
}
