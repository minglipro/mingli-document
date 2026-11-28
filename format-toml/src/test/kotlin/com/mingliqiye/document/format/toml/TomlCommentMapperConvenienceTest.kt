package com.mingliqiye.document.format.toml

import com.mingliqiye.document.format.toml.exception.TomlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [TomlMapper] 上注释保留便捷入口（`parseCommented` / `formatCommented`）的集成测试。
 */
class TomlCommentMapperConvenienceTest {

    private val source = """
        # 文件头注释
        title = "demo" # 行尾注释

        # db 表
        [db]
        # 主机
        host = "localhost"
        port = 5432
    """.trimIndent() + "\n"

    @Test
    fun mapperParseCommentedMatchesDedicatedReader() {
        val mapper = TomlMapper()
        val document = mapper.parseCommented(source)
        val dedicated = TomlCommentPreservingReader.parse(source)

        assertEquals(TomlReader(source).read(), document.root)
        assertEquals(dedicated.root, document.root)
        assertEquals(dedicated.comments, document.comments)
        assertEquals(dedicated.blankLines, document.blankLines)
    }

    @Test
    fun mapperFormatCommentedMatchesDedicatedWriter() {
        val mapper = TomlMapper()
        val document = mapper.parseCommented(source)

        assertEquals(TomlCommentPreservingWriter().write(document), mapper.formatCommented(document))
        assertEquals(document.toTomlString(), mapper.formatCommented(document))
        // 示例文本可逐字节 round-trip（写出器统一以换行结尾）。
        assertEquals(source, mapper.formatCommented(mapper.parseCommented(source)))
    }

    @Test
    fun formatCommentedHonorsConfig() {
        val mapper = TomlMapper()
        val document = mapper.parseCommented("a = 1\n\n# 注释\nb = 2\n")
        val config = TomlCommentConfig(
            orphanCommentPolicy = TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_TABLE,
            preserveBlankLines = false,
            trailingNewline = false,
        )

        val written = mapper.formatCommented(document, config)
        assertEquals("a = 1\n# 注释\nb = 2", written)
        assertTrue(document.toTomlString(config) == written)
    }

    @Test
    fun commentConvenienceRespectsMapperMaxDepth() {
        val limited = TomlMapper.builder().maxDepth(3).build()
        assertFailsWith<TomlParseException> {
            limited.parseCommented("[a.b.c]\n")
        }
        val allowed = TomlMapper.builder().maxDepth(4).build()
        allowed.parseCommented("[a.b.c]\n")
    }

    @Test
    fun commentedDocumentParseAcceptsMaxDepth() {
        assertFailsWith<TomlParseException> {
            TomlCommentedDocument.parse("[a.b.c]\n", maxDepth = 3)
        }
        TomlCommentedDocument.parse("[a.b.c]\n", maxDepth = 4)
        assertFailsWith<TomlParseException> {
            "[a.b.c]\n".toTomlCommentedDocument(maxDepth = 3)
        }
        val document = "[a.b.c]\n".toTomlCommentedDocument(maxDepth = 4)
        assertEquals(1, document.root.size)
        assertTrue(document.root["a"] != null)
    }
}
