package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.json.JsonReader
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteReferenceResolverTest {

    private val external = JsonReader.parse(
        """{"definitions":{"user":{"name":"小于","age":18}}}""",
    )

    @Test
    fun resolvesRemoteReference() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .resolveReferences(true)
                .remoteReferenceResolver { uri ->
                    assertEquals("memory://schema", uri)
                    external
                }
                .build(),
        )
        val result = mapper.fromNode(
            JsonReader.parse("""{"${'$'}ref":"memory://schema#/definitions/user"}"""),
            Map::class.java,
        ) as Map<*, *>
        assertEquals("小于", (result["name"] as StringNode).asString())
    }

    @Test
    fun remoteReferencesDisabledByDefault() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().resolveReferences(true).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(JsonReader.parse("""{"${'$'}ref":"memory://schema#/definitions/user"}"""), Map::class.java)
        }
    }
}
