package com.mingliqiye.document.format.xml

import com.mingliqiye.document.format.xml.exception.XmlParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XmlDepthTest {

    private val text = "<root><a><b><c>value</c></b></a></root>"

    @Test
    fun maxDepthRejectsNestedElements() {
        assertFailsWith<XmlParseException> {
            XmlReader(text, maxDepth = 3).read()
        }
        XmlReader(text, maxDepth = 4).read()
    }

    @Test
    fun mapperUsesMaxNodeDepth() {
        val mapper = XmlMapper(DocumentMapperConfig.builder().maxNodeDepth(3).build())
        assertFailsWith<XmlParseException> {
            mapper.parse(text, Map::class.java)
        }
    }

    @Test
    fun defaultDepthIsSafe() {
        assertEquals(512, XmlConfig.DEFAULT_MAX_DEPTH)
        XmlReader(nestedXml(XmlConfig.DEFAULT_MAX_DEPTH)).read()

        val error = assertFailsWith<XmlParseException> {
            XmlReader(nestedXml(XmlConfig.DEFAULT_MAX_DEPTH + 1)).read()
        }
        assertTrue(error.message.orEmpty().contains("maxDepth ${XmlConfig.DEFAULT_MAX_DEPTH}"))
    }

    @Test
    fun mapperDefaultDepthIsSafeAndDoesNotStackOverflow() {
        // 3000 层远超默认上限，必须在迭代深度校验处受控失败，而不是 StackOverflowError。
        val deep = nestedXml(3000)
        val error = assertFailsWith<XmlParseException> {
            XmlMapper().parse(deep, Map::class.java)
        }
        assertTrue(error.message.orEmpty().contains("maxDepth ${XmlConfig.DEFAULT_MAX_DEPTH}"))
    }

    @Test
    fun configBuilderSetsMaxDepth() {
        val mapper = XmlMapper(XmlConfig.builder().maxDepth(3).build())
        assertFailsWith<XmlParseException> { mapper.parse(text, Map::class.java) }

        val okMapper = XmlMapper(XmlConfig.builder().maxDepth(4).build())
        okMapper.parse(text, Map::class.java)
    }

    @Test
    fun nonDefaultReadConfigNodePathHonorsMaxDepth() {
        val mapper = XmlMapper(XmlConfig.builder().attributePrefix("@").maxDepth(2).build())
        assertFailsWith<XmlParseException> {
            mapper.parse("<root><a><b>1</b></a></root>", Map::class.java)
        }
    }

    @Test
    fun tokenPathHonorsMaxDepth() {
        assertFailsWith<XmlParseException> {
            XmlTokenReader(nestedXml(4), maxDepth = 3)
        }
        XmlTokenReader(nestedXml(3), maxDepth = 3)
    }

    @Test
    fun configDefaultAndValidation() {
        assertEquals(XmlConfig.DEFAULT_MAX_DEPTH, XmlConfig.builder().build().maxDepth)
        assertFailsWith<IllegalArgumentException> { XmlConfig.builder().maxDepth(0) }
        assertFailsWith<IllegalArgumentException> { XmlConfig.builder().maxDepth(-1) }
        assertEquals(8, XmlConfig.builder().maxDepth(8).build().maxDepth)
    }

    @Test
    fun explicitZeroDisablesLimit() {
        // 显式传 0 表示关闭保护；600 层在默认 JVM 栈下可安全递归。
        XmlReader(nestedXml(600), maxDepth = 0).read()
        assertFailsWith<XmlParseException> {
            XmlReader(nestedXml(600)).read()
        }
    }

    private fun nestedXml(depth: Int): String = "<r>".repeat(depth) + "value" + "</r>".repeat(depth)
}
