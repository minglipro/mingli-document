package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.format.yaml.exception.YamlParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import kotlin.test.Test
import kotlin.test.assertFailsWith

class YamlDepthTest {

    private val text = """
        a:
          b:
            c: 1
    """.trimIndent()

    @Test
    fun maxDepthRejectsNestedMapping() {
        assertFailsWith<YamlParseException> {
            YamlReader(text, maxDepth = 2).read()
        }
        YamlReader(text, maxDepth = 3).read()
    }

    @Test
    fun mapperUsesMaxNodeDepth() {
        val mapper = YamlMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build())
        assertFailsWith<YamlParseException> {
            mapper.parse(text, Map::class.java)
        }
    }
}
