package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.DocumentMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YamlMapperTest {

    @Test
    fun mapperInheritsDocumentMapperAndUsesYaml() {
        val mapper: DocumentMapper = YamlMapper()

        assertEquals("yaml", mapper.formatName)
        val node = mapper.toNode(mapOf("name" to "小于"))
        assertTrue(mapper.format(node).contains("name: 小于"))
        assertEquals("小于", mapper.parse("name: 小于", Person::class.java).name)
    }

    @Test
    fun yamlConfigControlsPrettyOutput() {
        val mapper = YamlMapper(YamlConfig.builder().pretty(true).build())
        val text = mapper.format(linkedMapOf("name" to "小于", "age" to 18))

        assertTrue(text.contains("name: 小于"))
        assertTrue(text.contains("\n"))
    }

    @Test
    fun numericScalarIsCoercedToStringProperty() {
        val mapper = YamlMapper()

        assertEquals("666", mapper.parse("name: 666", Person::class.java).name)
    }

    private class Person {
        var name: String = ""
    }
}
