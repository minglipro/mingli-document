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

    private class Person {
        var name: String = ""
    }
}
