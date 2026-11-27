package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.json.JsonReader
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReferenceResolverTest {

    private val document = JsonReader.parse(
        """
        {
          "definitions": {
            "user": {"name": "小于", "age": 18}
          },
          "user": {"${'$'}ref": "#/definitions/user"},
          "users": [{"${'$'}ref": "#/definitions/user"}]
        }
        """.trimIndent(),
    )

    @Test
    fun resolvesLocalRefsWhenEnabled() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().resolveReferences(true).build())
        val result = mapper.fromNode(document, Map::class.java)

        val user = result["user"] as ObjectNode
        assertEquals("小于", user["name"]?.asString())
        val users = result["users"] as com.mingliqiye.document.core.ArrayNode
        assertEquals("小于", (users[0] as ObjectNode)["name"]?.asString())
    }

    @Test
    fun keepsRefsWhenDisabled() {
        val mapper = JsonMapper()
        val result = mapper.fromNode(document, Map::class.java)

        val user = result["user"] as ObjectNode
        assertEquals("#/definitions/user", user[REF]?.asString())
    }

    @Test
    fun rejectsUnknownAndCircularRefs() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().resolveReferences(true).build())

        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(JsonReader.parse("""{"${'$'}ref": "#/missing"}"""), Map::class.java)
        }
        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(JsonReader.parse("""{"a": {"${'$'}ref": "#/a"}}"""), Map::class.java)
        }
    }

    private companion object {
        private const val REF = "\$ref"
    }
}
