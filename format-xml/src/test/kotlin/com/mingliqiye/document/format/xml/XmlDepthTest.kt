package com.mingliqiye.document.format.xml

import com.mingliqiye.document.format.xml.exception.XmlParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
