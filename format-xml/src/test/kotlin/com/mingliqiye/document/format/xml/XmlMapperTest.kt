package com.mingliqiye.document.format.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [XmlMapper] 测试。
 */
class XmlMapperTest {

    @Test
    fun mapperMapsBeanToXmlAndBack() {
        val mapper = XmlMapper()
        val person = Person().apply {
            name = "小于"
            age = 18
            tags = listOf("a", "b")
            address = Address().apply { city = "杭州" }
        }

        val xml = mapper.format(person)
        assertTrue(xml.startsWith("<root type=\"object\">"))
        assertTrue(xml.contains("<name type=\"string\">小于</name>"))

        val restored = mapper.parse(xml, Person::class.java)
        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals("杭州", restored.address?.city)
    }

    @Test
    fun rootNameAndPrettyAreConfigurable() {
        val mapper = XmlMapper(
            XmlConfig.builder()
                .rootName("person")
                .build(),
        )
        val xml = mapper.formatPretty(Person().apply { name = "小于" })

        assertTrue(xml.startsWith("<person type=\"object\">"))
        assertTrue(xml.contains("\n"))
    }

    @Test
    fun builderAcceptsXmlConfig() {
        val mapper = XmlMapper.builder()
            .config(XmlConfig.builder().rootName("bean").build())
            .build()

        assertTrue(mapper.format(Person()).startsWith("<bean type=\"object\">"))
    }

    private class Person {
        var name: String? = null
        var age: Int = 0
        var tags: List<String> = emptyList()
        var address: Address? = null
    }

    private class Address {
        var city: String? = null
    }
}
