package com.mingliqiye.document.format.toml

import com.mingliqiye.document.format.toml.exception.TomlParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TomlDepthTest {

    @Test
    fun maxNodeDepthRejectsNestedTable() {
        val mapper = TomlMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("[a.b]\nc = 1", Map::class.java)
        }
    }

    @Test
    fun defaultDepthRejectsDeepArrays() {
        assertEquals(512, TomlMapper.DEFAULT_MAX_DEPTH)
        TomlReader(nestedArrays(TomlMapper.DEFAULT_MAX_DEPTH - 2)).read()

        val error = assertFailsWith<TomlParseException> {
            TomlReader(nestedArrays(TomlMapper.DEFAULT_MAX_DEPTH - 1)).read()
        }
        assertTrue(error.message.orEmpty().contains("maxDepth ${TomlMapper.DEFAULT_MAX_DEPTH}"))
    }

    @Test
    fun defaultDepthRejectsDeepTablePaths() {
        // [t1...t510] + x 标量：最深节点深度 512。
        TomlReader(tableHeader(TomlMapper.DEFAULT_MAX_DEPTH - 2)).read()
        assertFailsWith<TomlParseException> {
            TomlReader(tableHeader(TomlMapper.DEFAULT_MAX_DEPTH - 1)).read()
        }
        TomlReader(dottedKey(TomlMapper.DEFAULT_MAX_DEPTH - 1)).read()
        assertFailsWith<TomlParseException> {
            TomlReader(dottedKey(TomlMapper.DEFAULT_MAX_DEPTH)).read()
        }
    }

    @Test
    fun veryDeepInputFailsWithoutStackOverflow() {
        // 5000 层数组/内联 table 在交给 tomlj 前就会被词法预扫描拒绝。
        assertFailsWith<TomlParseException> { TomlReader(nestedArrays(5000)).read() }
        assertFailsWith<TomlParseException> { TomlReader(nestedInlineTables(5000)).read() }
        assertFailsWith<TomlParseException> { TomlMapper().parse(nestedArrays(5000), Map::class.java) }
        assertFailsWith<TomlParseException> { TomlMapper.builder().build().parse(nestedArrays(5000), Map::class.java) }
    }

    @Test
    fun configuredMaxDepthBoundaries() {
        assertFailsWith<TomlParseException> {
            TomlReader(nestedArrays(2), maxDepth = 3).read()
        }
        TomlReader(nestedArrays(2), maxDepth = 4).read()
        assertFailsWith<TomlParseException> {
            TomlCommentPreservingReader("[a.b.c]\n", maxDepth = 3).read()
        }
        TomlCommentPreservingReader("[a.b.c]\n", maxDepth = 4).read()
    }

    @Test
    fun mapperAndBuilderHonorConfiguredMaxDepth() {
        val builderMapper = TomlMapper.builder().maxDepth(3).build()
        builderMapper.parse("[a]\nb = 1", Map::class.java)
        assertFailsWith<TomlParseException> {
            builderMapper.parse("[a.b]\nc = 1", Map::class.java)
        }

        val explicitMapper = TomlMapper(DocumentMapperConfig.empty(), 3)
        explicitMapper.parse("[a]\nb = 1", Map::class.java)
        assertFailsWith<TomlParseException> {
            explicitMapper.parse("[a.b]\nc = 1", Map::class.java)
        }
    }

    @Test
    fun depthLimitAppliesToNativeAndNodeTokenPaths() {
        // 默认无 Node 限制 -> 原生 TomlTokenReader 路径。
        assertFailsWith<TomlParseException> {
            TomlMapper().parse(nestedArrays(5000), Map::class.java)
        }
        // 配置 Node 限制 -> 回退 NodeTokenReader 路径，解析深度仍然生效。
        val limited = TomlMapper(DocumentMapperConfig.builder().maxNodeDepth(4).build())
        assertFailsWith<DocumentSerializationException> {
            limited.parse("[a.b.c]\nd = 1", Map::class.java)
        }
        assertFailsWith<TomlParseException> {
            limited.parse(nestedArrays(600), Map::class.java)
        }
    }

    @Test
    fun explicitZeroDisablesLimit() {
        TomlReader(nestedArrays(600), maxDepth = 0).read()
        assertFailsWith<TomlParseException> { TomlReader(nestedArrays(600)).read() }
    }

    @Test
    fun maxDepthValidation() {
        assertFailsWith<IllegalArgumentException> { TomlMapper.builder().maxDepth(0) }
        assertFailsWith<IllegalArgumentException> { TomlMapper.builder().maxDepth(-1) }
        assertFailsWith<IllegalArgumentException> { TomlMapper(DocumentMapperConfig.empty(), 0) }
    }

    @Test
    fun lexicalPreScanIgnoresBracketsInStringsCommentsAndTableHeaders() {
        // 字符串/注释里的括号、表头方括号都不应计入嵌套深度。
        val source = buildString {
            append("s1 = \"[[[[[[[[[[\"\n")
            append("s2 = '{{{{{{{{{{'\n")
            append("# 注释里的 [[[[[[[[[[\n")
            append("b = [1, 2]\n")
            append("[[items]]\n")
            append("x = 1\n")
        }
        // 实际最深：items 元素对象中的 x = 4 层。
        TomlReader(source, maxDepth = 4).read()
        assertFailsWith<TomlParseException> {
            TomlReader(source, maxDepth = 3).read()
        }

        // 多行字符串与多行注释/字符串中的括号同样跳过。
        val multiline = "s = \"\"\"\n[[[[[[[[[[\n\"\"\"\na = { b = 1 }\n"
        TomlReader(multiline, maxDepth = 3).read()
        assertFailsWith<TomlParseException> {
            TomlReader(multiline, maxDepth = 2).read()
        }
    }

    private fun nestedArrays(depth: Int): String = "a = " + "[".repeat(depth) + "1" + "]".repeat(depth) + "\n"

    private fun nestedInlineTables(depth: Int): String = "a = " + "{ b = ".repeat(depth) + "1" + " }".repeat(depth) + "\n"

    private fun tableHeader(segments: Int): String = "[" + (1..segments).joinToString(".") { "t$it" } + "]\nx = 1\n"

    private fun dottedKey(segments: Int): String = (1..segments).joinToString(".") { "k$it" } + " = 1\n"
}
