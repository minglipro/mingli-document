package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NodeLimitsTest {

    private val nested = ArrayNode.of(
        ArrayNode.of(
            ArrayNode.of(StringNode.of("deep")),
        ),
    )

    @Test
    fun maxNodeDepthRejectsDeepTree() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(nested, Object::class.java)
        }
    }

    @Test
    fun maxNodeCountRejectsLargeTree() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().maxNodeCount(3).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(nested, Object::class.java)
        }
    }

    @Test
    fun limitsApplyToSerialization() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().maxNodeCount(1).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.toNode(listOf("a", "b"))
        }
    }

    @Test
    fun unlimitedByDefaultAndRejectsNegativeConfig() {
        JsonMapper().fromNode(nested, Object::class.java)
        assertFailsWith<IllegalArgumentException> {
            DocumentMapperConfig.builder().maxNodeDepth(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentMapperConfig.builder().maxNodeCount(-1)
        }
    }
}
