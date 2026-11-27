package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonCodec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultDocumentMapperTest {

    private val mapper = DefaultDocumentMapper()

    @Test
    fun mapperApi() {
        val mapper: DocumentMapperApi = DefaultDocumentMapper(JsonCodec)
        val source = User().apply {
            name = "api"
            age = 9
            address = Address("北京", 100000)
            tags = listOf("a")
            scores = mapOf("math" to 100)
            status = Status.ACTIVE
        }

        val json = mapper.format(source)
        assertNotNull(json)
        val restored = mapper.parse(json, User::class.java)
        assertEquals("api", restored.name)
        assertEquals(9, restored.age)

        val converted: User = mapper.convert(source, DocumentTypeReference.of<User>())
        assertEquals("api", converted.name)

        val users: List<User> = mapper.convert(
            ArrayNode.of(mapper.toNode(source)),
            DocumentTypeReference.listType(User::class.java),
        )
        assertEquals(1, users.size)
        assertEquals("api", users[0].name)
    }

    @Test
    fun annotationDrivenNamesAndIgnore() {
        val source = AnnotatedUser().apply {
            name = "小于"
            age = 18
            city = "杭州"
        }

        val node = mapper.toNode(source).asObject()
        assertEquals("小于", node["display_name"]?.asString())
        assertEquals("杭州", node["city_name"]?.asString())
        assertFalse(node.containsKey("name"))
        assertFalse(node.containsKey("age"))

        val restored = mapper.fromNode(
            ObjectNode.of(
                "display_name" to StringNode.of("小于"),
                "city_name" to StringNode.of("杭州"),
                "age" to NumberNode.of(18),
            ),
            AnnotatedUser::class.java,
        )
        assertEquals("小于", restored.name)
        assertEquals("杭州", restored.city)
        assertEquals(0, restored.age)
    }

    @Test
    fun serializeJavaBean() {
        val user = User().apply {
            name = "小于"
            age = 18
            address = Address("杭州", 310000)
            tags = listOf("a", "b")
            scores = mapOf("math" to 95, "english" to 90)
            status = Status.ACTIVE
        }

        val node = mapper.toNode(user).asObject()
        assertEquals("小于", node["name"]?.asString())
        assertEquals(18, node["age"]?.asNumber()?.toInt())
        assertEquals("杭州", node["address"]?.asObject()?.get("city")?.asString())
        assertEquals(2, node["tags"]?.asArray()?.size)
        assertEquals(95, node["scores"]?.asObject()?.get("math")?.asNumber()?.toInt())
        assertEquals("ACTIVE", node["status"]?.asString())
    }

    @Test
    fun deserializeJavaBean() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "age" to NumberNode.of(18),
            "address" to ObjectNode.of(
                "city" to StringNode.of("杭州"),
                "zipCode" to NumberNode.of(310000),
            ),
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "scores" to ObjectNode.of("math" to NumberNode.of(95)),
            "status" to StringNode.of("ACTIVE"),
        )

        val user = mapper.fromNode(node, User::class.java)
        assertEquals("小于", user.name)
        assertEquals(18, user.age)
        assertNotNull(user.address)
        assertEquals("杭州", user.address.city)
        assertEquals(310000, user.address.zipCode)
        assertEquals(listOf("a", "b"), user.tags)
        assertEquals(mapOf("math" to 95), user.scores)
        assertEquals(Status.ACTIVE, user.status)
    }

    @Test
    fun roundTrip() {
        val source = User().apply {
            name = "mingli"
            age = 20
            address = Address("上海", 200000)
            tags = listOf("x")
            scores = mapOf("java" to 100)
            status = Status.DISABLED
        }
        val restored = mapper.fromNode(mapper.toNode(source), User::class.java)
        assertEquals(source.name, restored.name)
        assertEquals(source.age, restored.age)
        assertEquals(source.address.city, restored.address.city)
        assertEquals(source.tags, restored.tags)
        assertEquals(source.scores, restored.scores)
        assertEquals(source.status, restored.status)
    }

    @Test
    fun primitiveArrayAndNull() {
        val node = mapper.toNode(intArrayOf(1, 2, 3))
        assertEquals(3, node.asArray().size)
        assertContentEquals(intArrayOf(1, 2, 3), mapper.fromNode(node, IntArray::class.java) as IntArray)

        val nullNode = mapper.toNode(null)
        assertNull(mapper.fromNode(nullNode, Any::class.java))
    }

    @Test
    fun circularReferenceIsRejected() {
        val first = mutableListOf<Any?>()
        val second = mutableListOf<Any?>()
        first.add(second)
        second.add(first)

        assertFailsWith<DocumentSerializationException> {
            mapper.toNode(first)
        }
    }
}
