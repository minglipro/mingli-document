package com.mingliqiye.document.format.properties

import com.mingliqiye.document.format.properties.exception.PropertiesParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PropertiesDepthTest {

    @Test
    fun maxNodeDepthRejectsNestedPath() {
        val mapper = PropertiesMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("a.b.c=1", Map::class.java)
        }
    }

    @Test
    fun defaultMaxDepthBoundsPathDepth() {
        assertEquals(512, PropertiesMapper.DEFAULT_MAX_DEPTH)
        // 根对象深度 1，511 段 key 的最深层级为 512。
        PropertiesReader(pathKey(511)).read()
        val error = assertFailsWith<PropertiesParseException> {
            PropertiesReader(pathKey(512)).read()
        }
        assertTrue(error.message.orEmpty().contains("maxDepth ${PropertiesMapper.DEFAULT_MAX_DEPTH}"))
    }

    @Test
    fun configuredMaxDepthRejectsDeeperPaths() {
        assertFailsWith<PropertiesParseException> {
            PropertiesReader("a.b=1", maxDepth = 2).read()
        }
        PropertiesReader("a.b=1", maxDepth = 3).read()
        assertFailsWith<PropertiesParseException> {
            PropertiesReader("a[0]=1", maxDepth = 2).read()
        }
        PropertiesReader("a[0]=1", maxDepth = 3).read()
    }

    @Test
    fun veryDeepInputFailsWithoutStackOverflow() {
        // 5000 层路径在生成中间结构前就必须受控失败（不能递归到 StackOverflow）。
        val deep = pathKey(5000)
        assertFailsWith<PropertiesParseException> { PropertiesReader(deep).read() }
        assertFailsWith<PropertiesParseException> { PropertiesMapper().parse(deep, Map::class.java) }
        assertFailsWith<PropertiesParseException> { PropertiesMapper.builder().build().parse(deep, Map::class.java) }
    }

    @Test
    fun mapperAndBuilderHonorMaxDepthOnTokenPath() {
        val builderMapper = PropertiesMapper.builder().maxDepth(3).build()
        builderMapper.parse("a.b=1", Map::class.java)
        assertFailsWith<PropertiesParseException> {
            builderMapper.parse("a.b.c=1", Map::class.java)
        }

        val explicitMapper = PropertiesMapper(DocumentMapperConfig.empty(), 2)
        explicitMapper.parse("a=1", Map::class.java)
        assertFailsWith<PropertiesParseException> {
            explicitMapper.parse("a.b=1", Map::class.java)
        }
    }

    @Test
    fun explicitZeroDisablesLimit() {
        PropertiesReader(pathKey(600), maxDepth = 0).read()
    }

    @Test
    fun maxDepthValidation() {
        assertFailsWith<IllegalArgumentException> { PropertiesMapper.builder().maxDepth(0) }
        assertFailsWith<IllegalArgumentException> { PropertiesMapper.builder().maxDepth(-1) }
        assertFailsWith<IllegalArgumentException> {
            PropertiesMapper(DocumentMapperConfig.empty(), 0)
        }
    }

    private fun pathKey(segments: Int): String = (1..segments).joinToString(".") { "k$it" } + "=1\n"
}
