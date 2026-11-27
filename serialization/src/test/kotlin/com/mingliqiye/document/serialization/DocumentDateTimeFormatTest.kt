package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentDateTimeFormat
import com.mingliqiye.document.format.json.JsonMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DocumentDateTimeFormat] 测试。
 */
class DocumentDateTimeFormatTest {

    @Test
    fun javaUtilDateUsesFieldPattern() {
        val mapper = JsonMapper()
        val event = DateEvent().apply {
            date = Date(0)
        }

        val json = mapper.format(event)
        assertTrue(json.contains("""date":"1970-01-01 08:00:00"""))

        val restored = mapper.parse(json, DateEvent::class.java)
        assertEquals(0L, restored.date?.time)
    }

    @Test
    fun javaTimeTypesUseFieldPattern() {
        val mapper = JsonMapper()
        val event = TimeEvent().apply {
            localDate = LocalDate.of(2024, 5, 1)
            localDateTime = LocalDateTime.of(2024, 5, 1, 12, 30, 1)
        }

        val json = mapper.format(event)
        assertTrue(json.contains("""localDate":"2024-05-01"""))
        assertTrue(json.contains("""localDateTime":"2024-05-01 12:30:01"""))

        val restored = mapper.parse(json, TimeEvent::class.java)
        assertEquals(event.localDate, restored.localDate)
        assertEquals(event.localDateTime, restored.localDateTime)
    }

    private class DateEvent {
        @field:DocumentDateTimeFormat("yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
        var date: Date? = null
    }

    private class TimeEvent {
        @field:DocumentDateTimeFormat("yyyy-MM-dd")
        var localDate: LocalDate? = null

        @field:DocumentDateTimeFormat("yyyy-MM-dd HH:mm:ss")
        var localDateTime: LocalDateTime? = null
    }
}
