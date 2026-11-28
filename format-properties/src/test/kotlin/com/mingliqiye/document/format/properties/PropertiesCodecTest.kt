package com.mingliqiye.document.format.properties

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Properties 读写测试（默认配置，对齐 上游实现 `JavaPropsSchema` 默认行为）。
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
        // 默认对齐 上游实现：简单下标、起始编号 1。
        assertTrue(text.contains("tags.1=a"))
        assertTrue(text.contains("tags.2=b"))
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

        // 本模块扩展：字段名中的 `.` 默认百分号编码，保证可往返（上游实现 无此扩展）。
        assertTrue(text.contains("user%2Ename=小于"))
        val restored = PropertiesReader(text).read()
        assertEquals("小于", restored.asObject()["user.name"]?.asString())
    }

    @Test
    fun rootArrayRoundTrip() {
        val node = ArrayNode.from(listOf(StringNode.of("a"), StringNode.of("b")))

        val text = node.toPropertiesString()

        assertEquals("1=a\n2=b\n", text)
        val restored = PropertiesReader(text).read()
        assertEquals("a", restored.asArray().get(0).asString())
        assertEquals("b", restored.asArray().get(1).asString())
    }

    @Test
    fun emptyDocumentBecomesEmptyObject() {
        val restored = PropertiesReader("").read()

        assertEquals(0, restored.asObject().size)
        assertEquals("", ObjectNode.EMPTY.toPropertiesString())
    }

    @Test
    fun valueAndChildrenUseJacksonCoercion() {
        // 上游实现 语义：同一路径既有值又有子节点时，值写成空字符串键字段。
        val restored = PropertiesReader("a=1\na.b=2").read()

        val a = restored.asObject()["a"]?.asObject()
        assertEquals(1, a?.get("")?.asNumber()?.toInt())
        assertEquals(2, a?.get("b")?.asNumber()?.toInt())
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
