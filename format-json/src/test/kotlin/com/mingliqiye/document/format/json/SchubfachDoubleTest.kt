package com.mingliqiye.document.format.json

import com.fasterxml.jackson.core.io.schubfach.DoubleToDecimal
import com.mingliqiye.document.core.NumberNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchubfachDoubleTest {

    @Test
    fun doublesUseShortestRepresentation() {
        val mapper = JsonMapper()
        for (value in listOf(0.1, 1.0E23, 1.0E-7, Math.PI)) {
            val json = mapper.format(mapOf("value" to value))
            assertTrue(json.contains(DoubleToDecimal.toString(value)))
            val restored = mapper.parse(json, Map::class.java)["value"] as NumberNode
            assertEquals(value, restored.toDouble())
        }
    }
}
