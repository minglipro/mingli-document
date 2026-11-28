package com.mingliqiye.document.format.toml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [TomlCommentedDocument]、[TomlCommentConfig] 与孤立注释策略测试。
 */
class TomlCommentedDocumentApiTest {

    @Test
    fun ofBuildsDocumentWithoutComments() {
        val node = ObjectNode.of(
            "name" to StringNode.of("x"),
            "nested" to ObjectNode.of("a" to NumberNode.of(1)),
        )

        val document = TomlCommentedDocument.of(node)
        assertTrue(document.comments.isEmpty())
        assertTrue(document.blankLines.isEmpty())
        assertEquals(node, document.root)
        assertEquals(node.toTomlString(), document.toTomlString())
        assertEquals(node, TomlReader(document.toTomlString()).read())
    }

    @Test
    fun parsedDocumentExposesRootAndComments() {
        val document = TomlCommentPreservingReader.parse("# h\na = 1 # i\n")
        assertEquals(1, document.root["a"]?.asNumber()?.toInt())
        assertEquals(2, document.comments.size)
        assertTrue(document.blankLines.isEmpty())
        assertEquals(listOf(TomlCommentKind.FILE_HEADER, TomlCommentKind.INLINE), document.comments.map { it.kind })
    }

    @Test
    fun withRootReordersKeysAndKeepsInlineComments() {
        val document = TomlCommentPreservingReader.parse("a = 1 # 注释\nb = 2\n")
        val reordered = ObjectNode.of(
            "b" to NumberNode.of(2),
            "a" to NumberNode.of(1),
        )

        assertEquals("b = 2\na = 1 # 注释\n", document.withRoot(reordered).toTomlString())
    }

    @Test
    fun deletedKeyCommentPreservedAtEndOfTableByDefault() {
        val document = TomlCommentPreservingReader.parse(modifiedSource())
        val modified = deleteYFromA(document)

        assertEquals(
            "[a]\nx = 1\n# y 注释\n\n[b]\nz = 3\n",
            modified.toTomlString(),
        )
        assertEquals(modified.root, TomlReader(modified.toTomlString()).read())
    }

    @Test
    fun deletedKeyCommentCanBeDroppedOrMovedToEndOfFile() {
        val document = TomlCommentPreservingReader.parse(modifiedSource())
        val modified = deleteYFromA(document)

        assertEquals(
            "[a]\nx = 1\n\n[b]\nz = 3\n",
            modified.toTomlString(TomlCommentConfig(orphanCommentPolicy = TomlOrphanCommentPolicy.DROP)),
        )
        assertEquals(
            "[a]\nx = 1\n\n[b]\nz = 3\n# y 注释\n",
            modified.toTomlString(
                TomlCommentConfig(orphanCommentPolicy = TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_FILE),
            ),
        )
    }

    @Test
    fun detachedOrphanCommentPreservedAtEndOfTable() {
        val toml = toml(
            """
            [a]
            x = 1

            # 游离注释

            y = 2
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(
            toml(
                """
                [a]
                x = 1


                y = 2
                # 游离注释
                """,
            ),
            document.toTomlString(),
        )
        assertEquals(document.root, TomlReader(document.toTomlString()).read())
    }

    @Test
    fun configBuilderAppliesOptions() {
        val config = TomlCommentConfig.builder()
            .orphanCommentPolicy(TomlOrphanCommentPolicy.DROP)
            .preserveBlankLines(false)
            .trailingNewline(false)
            .build()

        assertEquals(TomlOrphanCommentPolicy.DROP, config.orphanCommentPolicy)
        assertEquals(false, config.preserveBlankLines)
        assertEquals(false, config.trailingNewline)

        val document = TomlCommentPreservingReader.parse("\n# 注释\na = 1\n")
        assertEquals("# 注释\na = 1", document.toTomlString(config))
    }

    @Test
    fun withRootAddsNewKeysAfterExistingOnes() {
        val document = TomlCommentPreservingReader.parse("a = 1 # 注释\n")
        val modified = document.withRoot(
            ObjectNode.of(
                "a" to NumberNode.of(1),
                "b" to StringNode.of("new"),
            ),
        )

        assertEquals("a = 1 # 注释\nb = \"new\"\n", modified.toTomlString())
        assertEquals(modified.root, TomlReader(modified.toTomlString()).read())
    }

    @Test
    fun deletedTableCommentsGoToEndOfFile() {
        val toml = toml(
            """
            [a]
            # x 注释
            x = 1

            [b]
            y = 2
            """,
        )
        val document = TomlCommentPreservingReader.parse(toml)
        val modified = document.withRoot(
            ObjectNode.of("b" to document.root["b"]!!),
        )

        assertEquals("[b]\ny = 2\n# x 注释\n", modified.toTomlString())
    }

    @Test
    fun reorderingArrayTableElementsKeepsCommentsByIndex() {
        val toml = toml(
            """
            [[items]]
            # 第一个
            name = "a"

            [[items]]
            # 第二个
            name = "b"
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        val items = document.root["items"]!!.asArray()
        val swapped = ObjectNode.of(
            "items" to ArrayNode.of(items[1], items[0]),
        )

        assertEquals(
            toml(
                """
                [[items]]
                # 第一个
                name = "b"

                [[items]]
                # 第二个
                name = "a"
                """,
            ),
            document.withRoot(swapped).toTomlString(),
        )
    }

    @Test
    fun dottedKeyCommentFallsBackToInlineTableLine() {
        val toml = toml(
            """
            point = { x = 1, y = 2 } # 坐标
            # 点分键注释
            a.b = 3
            """,
        )

        val document = TomlCommentPreservingReader.parse(toml)
        assertEquals(document.root, TomlReader(toml).read())
        assertEquals(
            toml(
                """
                point = { x = 1, y = 2 } # 坐标
                # 点分键注释
                a = { b = 3 }
                """,
            ),
            document.toTomlString(),
        )
        assertEquals(document.root, TomlCommentPreservingReader.parse(document.toTomlString()).root)
    }

    private fun modifiedSource(): String = toml(
        """
        [a]
        x = 1
        # y 注释
        y = 2

        [b]
        z = 3
        """,
    )

    private fun deleteYFromA(document: TomlCommentedDocument): TomlCommentedDocument {
        val root = document.root
        val a = root["a"]!!.asObject()
        val newRoot = ObjectNode.of(
            "a" to ObjectNode.of("x" to a["x"]!!),
            "b" to root["b"]!!,
        )
        return document.withRoot(newRoot)
    }

    private fun toml(text: String): String = text.trimIndent() + "\n"
}
