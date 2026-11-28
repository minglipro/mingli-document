package com.mingliqiye.document.format.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonDoubleFormattingTest {

    private val mapper = JsonMapper()

    @Test
    fun doublesRoundTripAsJsonNumbers() {
        val values = listOf(
            0.1,
            1.0,
            1.0E23,
            1.0E-7,
            Math.PI,
            -0.0,
            Double.MIN_VALUE,
            Double.MAX_VALUE,
        )

        for (value in values) {
            val json = mapper.format(mapOf("value" to value))
            val node = JsonReader(json).read().asObject()
            assertEquals(value, node["value"]?.asNumber()?.toDouble(), "failed for $value: $json")
        }
    }

    @Test
    fun doubleOutputMatchesJdkRoundTripText() {
        for (value in listOf(0.1, 1.0E23, 1.0E-7, Math.PI)) {
            val json = mapper.format(mapOf("value" to value))
            assertTrue(json.contains(JsonDoubleFormatter.format(value)), json)
            assertFalse(json.contains("NaN"), json)
            assertFalse(json.contains("Infinity"), json)
        }
    }
}
