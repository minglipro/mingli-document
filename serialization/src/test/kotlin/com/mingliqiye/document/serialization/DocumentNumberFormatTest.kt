package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentNumberFormat
import com.mingliqiye.document.format.json.JsonMapper
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DocumentNumberFormat] 测试。
 */
class DocumentNumberFormatTest {

    @Test
    fun numbersAreFormattedAndParsedBack() {
        val mapper = JsonMapper()
        val value = AmountEvent().apply {
            amount = BigDecimal("1234.5")
            count = 1234
        }

        val json = mapper.format(value)
        assertTrue(json.contains("""amount":"1,234.50"""))
        assertTrue(json.contains("""count":"1,234"""))

        val restored = mapper.parse(json, AmountEvent::class.java)
        assertEquals(BigDecimal("1234.50"), restored.amount)
        assertEquals(1234, restored.count)
    }

    @Test
    fun localeCanBeSpecified() {
        val mapper = JsonMapper()
        val value = LocaleEvent().apply {
            amount = BigDecimal("1234.5")
        }

        val json = mapper.format(value)
        assertTrue(json.contains("""amount":"1.234,50"""))

        val restored = mapper.parse(json, LocaleEvent::class.java)
        assertEquals(BigDecimal("1234.50"), restored.amount)
    }

    private class AmountEvent {
        @field:DocumentNumberFormat("#,##0.00")
        var amount: BigDecimal? = null

        @field:DocumentNumberFormat("#,##0")
        var count: Int = 0
    }

    private class LocaleEvent {
        @field:DocumentNumberFormat("#,##0.00", locale = "de-DE")
        var amount: BigDecimal? = null
    }
}
