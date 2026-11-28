package com.mingliqiye.document.format.xml

import com.mingliqiye.document.annotation.DocumentRootName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [XmlMapper] 测试。
 */
class XmlMapperTest {

    @Test
    fun mapperMapsBeanToXmlAndBack() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).build())
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
            XmlConfig.builder().typeAttributes(true)
                .rootName("person")
                .build(),
        )
        val xml = mapper.formatPretty(Person().apply { name = "小于" })

        assertTrue(xml.startsWith("<person") && xml.contains("type=\"object\""))
        assertTrue(xml.contains("\n"))
    }

    @Test
    fun documentRootNameAnnotationSetsRootElement() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).build())

        assertTrue(mapper.format(NamedRootPerson().apply { name = "小于" }).startsWith("<person") && mapper.format(NamedRootPerson()).contains("type=\"object\""))
        assertTrue(mapper.formatPretty(NamedRootPerson()).startsWith("<person") && mapper.formatPretty(NamedRootPerson()).contains("type=\"object\""))
    }

    @Test
    fun documentRootNameAnnotationOverridesConfiguredRootName() {
        val mapper = XmlMapper(XmlConfig.builder().typeAttributes(true).rootName("configured").build())

        assertTrue(mapper.format(NamedRootPerson()).startsWith("<person") && mapper.format(NamedRootPerson()).contains("type=\"object\""))
    }

    @Test
    fun builderAcceptsXmlConfig() {
        val mapper = XmlMapper.builder()
            .config(XmlConfig.builder().typeAttributes(true).rootName("bean").build())
            .build()

        assertTrue(mapper.format(Person()).startsWith("<bean") && mapper.format(Person()).contains("type=\"object\""))
    }

    @DocumentRootName("person")
    private class NamedRootPerson {
        var name: String? = null
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
