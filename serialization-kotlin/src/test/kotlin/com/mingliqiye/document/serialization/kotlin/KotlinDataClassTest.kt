package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.annotation.DocumentDateTimeFormat
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KotlinDataClassTest {

    private val jsonMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .build()

    @Test
    fun jsonRoundTrip() {
        val user = User("小于", 18)

        val node = jsonMapper.toNode(user)

        val restored = jsonMapper.fromNode(node, User::class.java)
        assertEquals(user, restored)
    }

    @Test
    fun jsonParse() {
        val user = jsonMapper.parse("""{"name":"小于","age":18}""", User::class.java)
        assertEquals(User("小于", 18), user)
    }

    @Test
    fun dateTimeFormatAnnotationIsRead() {
        val value = Timed(LocalDate.of(2024, 5, 1))
        val node = jsonMapper.toNode(value)
        assertEquals("2024/05/01", node.asObject()["date"]?.asString())

        val restored = jsonMapper.parse("""{"date":"2024/05/01"}""", Timed::class.java)
        assertEquals(value, restored)
    }

    @Test
    fun nestedDataClassesAndCollections() {
        val profile = Profile(
            user = User("小于", 18),
            address = Address("杭州"),
            tags = listOf("a", "b"),
        )

        val node = jsonMapper.toNode(profile)
        val restored = jsonMapper.fromNode(node, Profile::class.java)
        assertEquals(profile, restored)
    }

    @Test
    fun defaultParametersAreUsedWhenMissing() {
        val settings = jsonMapper.fromNode(jsonMapper.toNode(emptyMap<String, Any>()), Settings::class.java)
        assertEquals(Settings(), settings)
    }

    @Test
    fun documentNameIsRead() {
        val value = Named("小于", 18)
        val node = jsonMapper.toNode(value)
        assertEquals("小于", node.asObject()["display_name"]?.asString())
        assertNull(node.asObject()["name"])

        val restored = jsonMapper.fromNode(node, Named::class.java)
        assertEquals(value, restored)
    }

    @Test
    fun missingRequiredParameterThrows() {
        assertFailsWith<DocumentSerializationException> {
            jsonMapper.parse("{}", Required::class.java)
        }
    }

    @Test
    fun nonDataClassFallsBackToJavaBean() {
        val restored = jsonMapper.parse("""{"name":"bean"}""", Plain::class.java)
        assertEquals("bean", restored.name)
    }

    @Test
    fun yamlMapperUsesKotlinInstantiator() {
        val mapper = YamlMapper.builder()
            .addModule(kotlinModule())
            .build()

        val user = mapper.parse("name: 小于\nage: 18\n", User::class.java)
        assertEquals(User("小于", 18), user)
    }

    private data class User(val name: String, val age: Int)

    private data class Address(val city: String)

    private data class Profile(
        val user: User,
        val address: Address,
        val tags: List<String>,
    )

    private data class Settings(val name: String = "guest", val age: Int = 18)

    private data class Named(
        @DocumentName("display_name") val name: String,
        val age: Int,
    )

    private data class Required(val name: String)

    private data class Timed(
        @field:DocumentDateTimeFormat("yyyy/MM/dd")
        val date: LocalDate,
    )

    private class Plain {
        var name: String = ""
    }
}
