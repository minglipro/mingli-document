package com.mingliqiye.document.format.properties

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.properties.exception.PropertiesParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Properties 读写测试。
 */
class PropertiesCodecTest {

    @Test
    fun documentTreeRoundTrip() {
        val node = ObjectNode.from(
            linkedMapOf(
                "name" to StringNode.of("小于"),
                "age" to NumberNode.of(18),
                "active" to BooleanNode.of(true),
                "tags" to ArrayNode.from(listOf(StringNode.of("a"), StringNode.of("b"))),
                "address" to ObjectNode.from(mapOf("city" to StringNode.of("杭州"))),
            ),
        )

        val text = node.toPropertiesString()
        assertTrue(text.contains("name=小于"))
        assertTrue(text.contains("tags[0]=a"))
        assertTrue(text.contains("address.city=杭州"))

        val restored = PropertiesReader(text).read()
        assertEquals("小于", restored.asObject()["name"]?.asString())
        assertEquals(18, restored.asObject()["age"]?.asNumber()?.toInt())
        assertEquals("a", restored.asObject()["tags"]?.asArray()?.get(0)?.asString())
        assertEquals("杭州", restored.asObject()["address"]?.asObject()?.get("city")?.asString())
    }

    @Test
    fun dotInFieldNameIsEscaped() {
        val node = ObjectNode.from(mapOf("user.name" to StringNode.of("小于")))
        val text = node.toPropertiesString()
        val restored = PropertiesReader(text).read()

        assertEquals("小于", restored.asObject()["user.name"]?.asString())
    }

    @Test
    fun rootArrayRoundTrip() {
        val node = ArrayNode.from(listOf(StringNode.of("a"), StringNode.of("b")))
        val restored = PropertiesReader(node.toPropertiesString()).read()

        assertEquals("a", restored.asArray().get(0).asString())
        assertEquals("b", restored.asArray().get(1).asString())
    }

    @Test
    fun emptyDocumentBecomesEmptyObject() {
        val restored = PropertiesReader("").read()

        assertEquals(0, restored.asObject().size)
    }

    @Test
    fun conflictingPathsThrow() {
        assertFailsWith<PropertiesParseException> {
            PropertiesReader("a=1\na.b=2").read()
        }
    }

    @Test
    fun beanRoundTrip() {
        val mapper = PropertiesMapper()
        val user = User().apply {
            name = "小于"
            age = 18
            tags = listOf("a", "b")
            address = Address().apply { city = "杭州" }
        }

        val text = mapper.format(user)
        val restored = mapper.parse(text, User::class.java)

        assertEquals(user.name, restored.name)
        assertEquals(user.age, restored.age)
        assertEquals(user.tags, restored.tags)
        assertEquals(user.address?.city, restored.address?.city)
    }

    private class User {
        var name: String? = null
        var age: Int = 0
        var tags: List<String> = emptyList()
        var address: Address? = null
    }

    private class Address {
        var city: String? = null
    }
}
