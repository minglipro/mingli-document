package com.mingliqiye.document.jdbc

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DocumentRowMapperTest {

    @Test
    fun nodesMapperReturnsObjectNode() {
        val row = ObjectNode.of("id" to NumberNode.of(1))

        assertEquals(row, DocumentRowMapper.nodes().mapRow(row))
    }

    @Test
    fun mapsMapperConvertsNestedNodes() {
        val row = ObjectNode.from(
            linkedMapOf(
                "name" to StringNode.of("alice"),
                "active" to BooleanNode.of(true),
                "score" to NumberNode.of(BigDecimal("1.25")),
                "payload" to BinaryNode.of(byteArrayOf(1, 2)),
                "missing" to NullNode.INSTANCE,
                "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
                "address" to ObjectNode.of("city" to StringNode.of("hz")),
            ),
        )

        val map = DocumentRowMapper.maps().mapRow(row)

        assertEquals("alice", map["name"])
        assertEquals(true, map["active"])
        assertEquals(BigDecimal("1.25"), map["score"])
        assertContentEquals(byteArrayOf(1, 2), map["payload"] as ByteArray)
        assertNull(map["missing"])
        assertEquals(listOf("a", "b"), map["tags"])
        assertEquals(mapOf("city" to "hz"), map["address"])
    }

    @Test
    fun beanMapperUsesDocumentMapper() {
        val row = ObjectNode.from(
            linkedMapOf(
                "id" to NumberNode.of(4),
                "name" to StringNode.of("dave"),
            ),
        )

        val user = DocumentRowMapper.of(UserBean::class.java, TestDocumentMapper()).mapRow(row)

        assertEquals(4, user.id)
        assertEquals("dave", user.name)
    }

    @Test
    fun beanMapperSupportsTypeReference() {
        val row = ObjectNode.from(linkedMapOf("id" to NumberNode.of(6)))
        val mapper = DocumentRowMapper.of(
            DocumentTypeReference.of(UserBean::class.java),
            TestDocumentMapper(),
        )

        assertEquals(6, mapper.mapRow(row).id)
    }

    @Test
    fun customMapperCanMapAnyNodeShape() {
        val row: ObjectNode = ObjectNode.of(
            "items" to ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
        )
        val mapper = DocumentRowMapper<Int> { current ->
            val items: Node = assertNotNull(current["items"])
            items.asArray().sumOf { element -> element.asNumber().toInt() }
        }

        assertEquals(3, mapper.mapRow(row))
    }

    @Test
    fun mapsMapperKeepsInsertionOrder() {
        val row = ObjectNode.from(
            linkedMapOf(
                "second" to NumberNode.of(2),
                "first" to NumberNode.of(1),
            ),
        )

        val keys = DocumentRowMapper.maps().mapRow(row).keys.toList()

        assertEquals(listOf("second", "first"), keys)
        assertEquals(2, keys.size)
    }
}
