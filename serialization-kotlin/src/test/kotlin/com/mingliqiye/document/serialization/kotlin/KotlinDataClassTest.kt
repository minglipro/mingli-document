package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KotlinDataClassTest {

    private val jsonMapper = JsonMapper.builder()
        .addModule(KotlinModule)
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
            .addModule(KotlinModule)
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
        @field:DocumentName("display_name") val name: String,
        val age: Int,
    )

    private data class Required(val name: String)

    private class Plain {
        var name: String = ""
    }
}
