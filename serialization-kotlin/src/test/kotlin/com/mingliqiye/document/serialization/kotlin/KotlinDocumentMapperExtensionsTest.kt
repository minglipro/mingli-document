package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.format.json.JsonMapper
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinDocumentMapperExtensionsTest {

    private val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .build()

    data class User(
        val name: String = "",
        val age: Int = 0,
    )

    @Test
    fun reifiedParseSources() {
        val source = """{"name":"小于","age":18}"""
        val expected = User("小于", 18)

        assertEquals(expected, mapper.parse<User>(source))
        assertEquals(expected, mapper.parse<User>(source.toByteArray()))
        assertEquals(expected, mapper.parse<User>(ByteArrayInputStream(source.toByteArray())))
        assertEquals(expected, mapper.parse<User>(StringReader(source)))

        val file = Files.createTempFile("mingli-kotlin-ext", ".json").toFile()
        try {
            file.writeText(source)
            assertEquals(expected, mapper.parse<User>(file))
            assertEquals(expected, mapper.parse<User>(file.toPath()))
        } finally {
            file.delete()
        }
    }

    @Test
    fun reifiedTreeConvertAndReader() {
        val node = mapper.readTree("""{"name":"小于","age":18}""")

        assertEquals(User("小于", 18), mapper.fromNode<User>(node))
        assertEquals(User("小于", 18), mapper.convert<User>(node))

        val reader = mapper.readerFor<User>()
        assertEquals(User("小于", 18), reader.readValue("""{"name":"小于","age":18}"""))
        assertEquals(User("小于", 18), reader.readValue("""{"name":"小于","age":18}""".toByteArray()))
    }
}
