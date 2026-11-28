package com.mingliqiye.document.format.toml

import com.mingliqiye.document.format.toml.exception.TomlException
import com.mingliqiye.document.format.toml.exception.TomlParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [TomlMapper] 测试。
 */
class TomlMapperTest {

    @Test
    fun mapperMapsBeanToTomlAndBack() {
        val mapper = TomlMapper()
        val person = Person().apply {
            name = "小于"
            age = 18
            active = true
            tags = listOf("a", "b")
            address = Address().apply { city = "杭州" }
        }

        val toml = mapper.format(person)
        assertTrue(toml.contains("name = \"小于\""))
        assertTrue(toml.contains("tags = [ \"a\", \"b\" ]"))
        assertTrue(toml.contains("address = { city = \"杭州\" }"))

        val restored = mapper.parse(toml, Person::class.java)
        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertEquals(true, restored.active)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals("杭州", restored.address?.city)
    }

    @Test
    fun nestedBeansRoundTrip() {
        val mapper = TomlMapper()
        val order = Order().apply {
            id = "o-1"
            customer = Customer().apply {
                name = "小于"
                address = Address().apply { city = "杭州" }
            }
        }

        val toml = mapper.format(order)
        assertTrue(toml.contains("customer = {"))
        assertTrue(toml.contains("address = { city = \"杭州\" }"))
        assertTrue(toml.contains("name = \"小于\""))

        val restored = mapper.parse(toml, Order::class.java)
        assertEquals("o-1", restored.id)
        assertEquals("小于", restored.customer?.name)
        assertEquals("杭州", restored.customer?.address?.city)
    }

    @Test
    fun nullBeanFieldsAreWrittenAsEmptyString() {
        val mapper = TomlMapper()
        val person = Person().apply { name = "小于" }

        // 对齐 上游实现 TOML 默认行为：null 写成空字符串，而不是省略字段。
        val toml = mapper.format(person)
        assertTrue(toml.contains("address = \"\""))
        assertTrue(toml.contains("tags = []"))
    }

    @Test
    fun nullBeanFieldsAreOmittedWhenWriteNullsDisabled() {
        val mapper = TomlMapper(DocumentMapperConfig.builder().writeNulls(false).build())
        val person = Person().apply { name = "小于" }

        val toml = mapper.format(person)
        assertFalse(toml.contains("address"))

        val restored = mapper.parse(toml, Person::class.java)
        assertEquals("小于", restored.name)
        assertNull(restored.address)
        assertEquals(emptyList(), restored.tags)
    }

    @Test
    fun builderAndConfigConstructors() {
        val mapper = TomlMapper.builder()
            .config(DocumentMapperConfig.builder().writeNulls(false).build())
            .build()
        assertEquals("toml", mapper.formatName)

        val plain = TomlMapper()
        val withConfig = TomlMapper(DocumentMapperConfig.empty())
        assertEquals(plain.formatName, withConfig.formatName)

        assertEquals(plain.format(plain.toNode(mapOf("a" to 1))), plain.formatPretty(plain.toNode(mapOf("a" to 1))))
    }

    @Test
    fun parseErrorThrowsTomlParseException() {
        val mapper = TomlMapper()

        assertFailsWith<TomlParseException> {
            mapper.parse("name = ", Person::class.java)
        }
        assertFailsWith<TomlParseException> {
            mapper.parse("42", Person::class.java)
        }
    }

    @Test
    fun formatRootMustBeObject() {
        val mapper = TomlMapper()

        assertFailsWith<TomlException> { mapper.format("plain string") }
        assertFailsWith<TomlException> { mapper.format(listOf(1, 2)) }
    }

    private class Person {
        var name: String? = null
        var age: Int = 0
        var active: Boolean = false
        var tags: List<String> = emptyList()
        var address: Address? = null
    }

    private class Address {
        var city: String? = null
    }

    private class Customer {
        var name: String? = null
        var address: Address? = null
    }

    private class Order {
        var id: String? = null
        var customer: Customer? = null
    }
}
