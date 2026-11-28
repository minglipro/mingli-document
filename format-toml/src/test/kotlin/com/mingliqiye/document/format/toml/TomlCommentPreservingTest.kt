package com.mingliqiye.document.format.toml

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.toml.exception.TomlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [TomlCommentPreservingReader] / [TomlCommentPreservingWriter] 的 round-trip 测试。
 *
 * 覆盖：文件头/尾注释、表头注释、键前注释、行尾注释、嵌套表、数组表、空行、
 * 多行值内部注释、特殊字符注释、CRLF 归一化以及无注释文档。
 */
class TomlCommentPreservingTest {

    @Test
    fun fileHeaderTableAndInlineCommentsRoundTrip() {
        val toml = toml(
            """
            # 文件头注释
            #
            # 第二行

            title = "demo" # 行尾注释

            # user 表
            [user]
            # 用户名
            name = "小于"
            age = 18 # 年龄

            # database 表
            [database]
            host = "localhost"
            port = 5432
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(TomlReader(toml).read(), document.root)
        assertEquals(toml, document.toTomlString())
        assertEquals(toml, TomlCommentPreservingWriter().write(document))
        assertEquals(document.root, TomlCommentPreservingReader.parse(document.toTomlString()).root)
    }

    @Test
    fun nestedTablesAndArrayTablesRoundTrip() {
        val toml = toml(
            """
            # 服务器列表

            [[servers]]
            # 名称
            name = "alpha"
            port = 80 # 端口

            # 第二个服务器
            [[servers]]
            name = "beta"

            # 限制
            [servers.limits]
            max = 10
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(TomlReader(toml).read(), document.root)
        assertEquals(toml, document.toTomlString())
    }

    @Test
    fun nestedTableWithoutParentHeaderRoundTrip() {
        val toml = toml(
            """
            title = "t"

            # a.b 表
            [a.b]
            x = 1
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(TomlReader(toml).read(), document.root)
        assertEquals(toml, document.toTomlString())
    }

    @Test
    fun quotedTableKeyRoundTrip() {
        val toml = toml(
            """
            title = "t"

            ["a.b"]
            # 键前注释
            x = 1
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(TomlReader(toml).read(), document.root)
        assertEquals(toml, document.toTomlString())
    }

    @Test
    fun arrayTableCommentsStayWithElement() {
        val toml = toml(
            """
            [[items]]
            # 第一个
            name = "a" # inline

            [[items]]
            # 第二个
            name = "b"
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(TomlReader(toml).read(), document.root)
        assertEquals(toml, document.toTomlString())
    }

    @Test
    fun tableHeaderInlineCommentsRoundTrip() {
        val toml = toml(
            """
            [a] # a 表头行尾
            x = 1
            [[items]] # items 表头行尾
            name = "n"
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(TomlReader(toml).read(), document.root)
        assertEquals(
            listOf(TomlCommentKind.INLINE, TomlCommentKind.INLINE),
            document.comments.map { it.kind },
        )
        assertEquals(toml, document.toTomlString())
    }

    @Test
    fun escapedTableKeyCommentIsAnchoredAfterUnescape() {
        val toml = toml(
            """
            title = "t"

            # 转义 key
            ["a\u002Eb"]
            x = 1
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(TomlReader(toml).read(), document.root)
        assertEquals(1, document.root["a.b"]?.asObject()?.get("x")?.asNumber()?.toInt())
        assertEquals(
            toml(
                """
                title = "t"

                # 转义 key
                ["a.b"]
                x = 1
                """,
            ),
            document.toTomlString(),
        )
    }

    @Test
    fun rootOrphanCommentPreservedBeforeTables() {
        val toml = toml(
            """
            a = 1

            # root 游离

            [b]
            x = 2
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        val orphan = document.comments.single { it.kind == TomlCommentKind.ORPHAN }
        assertTrue(orphan.path.isEmpty())
        assertEquals(
            toml(
                """
                a = 1
                # root 游离


                [b]
                x = 2
                """,
            ),
            TomlCommentPreservingWriter().write(document),
        )
    }

    @Test
    fun endOfFileBlankLinesAndTrailerComments() {
        assertEquals("a = 1\n\n", TomlCommentPreservingReader.parse("a = 1\n\n").toTomlString())
        assertEquals(
            "a = 1\n\n# tail\n",
            TomlCommentPreservingReader.parse("a = 1\n\n# tail\n").toTomlString(),
        )
    }

    @Test
    fun reorderMovesCommentsWithTheirAnchors() {
        val toml = toml(
            """
            # 文档

            # a 表
            [a]
            x = 1

            # b 表
            [b]
            y = 2
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(toml, document.toTomlString())

        val root = document.root
        val reordered = ObjectNode.of(
            "b" to root["b"]!!,
            "a" to root["a"]!!,
        )
        assertEquals(
            toml(
                """
                # 文档

                # b 表
                [b]
                y = 2

                # a 表
                [a]
                x = 1
                """,
            ),
            document.withRoot(reordered).toTomlString(),
        )
    }

    @Test
    fun blankLinesRoundTripAndCanBeDisabled() {
        val toml = toml(
            """
            a = 1

            b = 2


            [c]
            x = 3
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(toml, document.toTomlString())
        assertEquals(
            toml(
                """
                a = 1
                b = 2
                [c]
                x = 3
                """,
            ),
            document.toTomlString(TomlCommentConfig(preserveBlankLines = false)),
        )
    }

    @Test
    fun trailingNewlineCanBeDisabled() {
        val document = TomlCommentPreservingReader.parse("a = 1")
        assertEquals("a = 1\n", document.toTomlString())
        assertEquals(
            "a = 1",
            document.toTomlString(TomlCommentConfig(trailingNewline = false)),
        )
    }

    @Test
    fun multiLineArrayCommentsBecomeLeadingComments() {
        val toml = toml(
            """
            values = [
              1, # 第一个
              2, # 第二个
            ]
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(TomlReader(toml).read(), document.root)
        assertEquals(
            listOf(TomlCommentKind.INTERIOR, TomlCommentKind.INTERIOR),
            document.comments.map { it.kind },
        )
        assertEquals(
            toml(
                """
                # 第一个
                # 第二个
                values = [ 1, 2 ]
                """,
            ),
            document.toTomlString(),
        )
        assertEquals(document.root, TomlCommentPreservingReader.parse(document.toTomlString()).root)
    }

    @Test
    fun multiLineStringContentIsNotParsedAsComment() {
        val tripleQuote = "\"\"\""
        val toml = "text = $tripleQuote\n# 不是注释\n$tripleQuote\n"

        val document = TomlCommentPreservingReader.parse(toml)
        assertTrue(document.comments.isEmpty())
        assertEquals(TomlReader(toml).read(), document.root)
        assertTrue(document.root["text"]!!.asString().contains("# 不是注释"))
        assertEquals(document.root, TomlCommentPreservingReader.parse(document.toTomlString()).root)
    }

    @Test
    fun specialCharacterCommentsArePreservedVerbatim() {
        val toml = toml(
            """
            # [not-a-table] # "引号" 中文 = 等号
            value = "x" # inline # hash [bracket]
            # 文件尾 [still-comment]
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(toml, document.toTomlString())
        assertEquals(
            listOf(
                " [not-a-table] # \"引号\" 中文 = 等号",
                " inline # hash [bracket]",
                " 文件尾 [still-comment]",
            ),
            document.comments.map { it.text },
        )
    }

    @Test
    fun commentModelExposesKindPathAndLine() {
        val toml = toml(
            """
            # header
            [a]
            # lead
            x = 1 # inline
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(3, document.comments.size)
        assertTrue(document.blankLines.isEmpty())

        val header = document.comments[0]
        assertEquals(TomlCommentKind.FILE_HEADER, header.kind)
        assertEquals(" header", header.text)
        assertEquals("header", header.content)
        assertEquals(1, header.line)
        assertTrue(header.path.isEmpty())

        val leading = document.comments[1]
        assertEquals(TomlCommentKind.LEADING, leading.kind)
        assertEquals(listOf("a"), leading.tablePath)
        assertEquals(listOf("x"), leading.keyPath)
        assertEquals("x", leading.key)
        assertEquals(listOf("a", "x"), leading.path)
        assertEquals(3, leading.line)

        val inline = document.comments[2]
        assertEquals(TomlCommentKind.INLINE, inline.kind)
        assertEquals(" inline", inline.text)
        assertEquals(4, inline.line)
    }

    @Test
    fun crlfInputIsNormalizedToLf() {
        val document = TomlCommentPreservingReader.parse("# header\r\nx = 1\r\n")
        assertEquals("# header\nx = 1\n", document.toTomlString())
    }

    @Test
    fun documentWithoutCommentsRoundTrip() {
        val toml = toml(
            """
            title = "x"

            [a]
            x = 1
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertTrue(document.comments.isEmpty())
        assertEquals(toml, document.toTomlString())
    }

    @Test
    fun emptyDocument() {
        val document = TomlCommentPreservingReader.parse("")
        assertEquals(0, document.root.size)
        assertTrue(document.comments.isEmpty())
        assertEquals("", document.toTomlString())
    }

    @Test
    fun commentsOnlyDocument() {
        val document = TomlCommentPreservingReader.parse("# a\n# b\n")
        assertEquals(2, document.comments.size)
        assertTrue(document.comments.all { it.kind == TomlCommentKind.FILE_HEADER })
        assertEquals("# a\n# b\n", document.toTomlString())
    }

    @Test
    fun invalidTomlThrowsParseException() {
        val invalid = listOf("42", "a =", "= 1", "[table", "a = [1,", "a = 1\na = 2")
        for (source in invalid) {
            assertFailsWith<TomlParseException>("should fail: <$source>") {
                TomlCommentPreservingReader.parse(source)
            }
        }
    }

    private fun toml(text: String): String = text.trimIndent() + "\n"
}
