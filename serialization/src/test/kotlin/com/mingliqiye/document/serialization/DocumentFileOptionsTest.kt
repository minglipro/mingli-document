package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 文件读写选项测试。
 */
class DocumentFileOptionsTest {

    @Test
    fun fileCharsetParentDirectoriesAndAtomicWrite() {
        val directory = Files.createTempDirectory("mingli-document-file-options")
        val file = directory.resolve("nested/user.json")
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .fileCharset(Charset.forName("ISO-8859-1"))
                .createParentDirectories(true)
                .atomicFileWrite(true)
                .build(),
        )
        try {
            mapper.format(mapOf("name" to "héllo"), file)
            assertTrue(Files.readAllBytes(file).any { it == 0xE9.toByte() })
            assertEquals("héllo", (mapper.parse(file, Map::class.java)["name"] as StringNode).asString())

            mapper.format(mapOf("name" to "héllo"), file)
            assertEquals("héllo", (mapper.parse(file, Map::class.java)["name"] as StringNode).asString())
            assertTrue(Files.list(file.parent).use { entries -> entries.noneMatch { it.fileName.toString().endsWith(".tmp") } })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun maxInputSizeRejectsTextAndFile() {
        val directory = Files.createTempDirectory("mingli-document-max-input")
        val file = directory.resolve("input.json")
        val mapper = JsonMapper(DocumentMapperConfig.builder().maxInputSize(4).build())
        try {
            Files.write(file, """{"a":1}""".toByteArray())
            assertFailsWith<DocumentSerializationException> {
                mapper.parse("""{"a":1}""", Map::class.java)
            }
            assertFailsWith<DocumentSerializationException> {
                mapper.parse(file, Map::class.java)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
