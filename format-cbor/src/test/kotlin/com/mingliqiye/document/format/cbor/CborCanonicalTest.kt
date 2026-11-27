package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CborCanonicalTest {

    @Test
    fun canonicalMapKeysHaveStableOrder() {
        val first = ObjectNode.of(
            "bb" to StringNode.of("2"),
            "a" to StringNode.of("1"),
            "ccc" to StringNode.of("3"),
        )
        val second = ObjectNode.of(
            "ccc" to StringNode.of("3"),
            "a" to StringNode.of("1"),
            "bb" to StringNode.of("2"),
        )

        assertContentEquals(first.toCanonicalCborBytes(), second.toCanonicalCborBytes())
        assertEquals(
            "a,bb,ccc",
            (CborReader(first.toCanonicalCborBytes()).read().asObject().keys).joinToString(","),
        )
    }

    @Test
    fun mapperCanonicalBytesAreStable() {
        val mapper = CborMapper()
        val first = linkedMapOf("b" to 2, "aa" to 1)
        val second = linkedMapOf("aa" to 1, "b" to 2)

        assertContentEquals(mapper.formatCanonicalBytes(first), mapper.formatCanonicalBytes(second))
        assertEquals(
            mapper.parse(mapper.formatCanonicalBytes(first), Map::class.java).size,
            mapper.parse(mapper.formatCanonicalBytes(second), Map::class.java).size,
        )
    }
}
