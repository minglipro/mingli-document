package com.mingliqiye.document.format.json.exception

import com.mingliqiye.document.exception.DocumentException
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JsonExceptionTest {

    @Test
    fun jsonParseExceptionExtendsJsonException() {
        assertTrue(JsonParseException("parse") is JsonException)
        assertTrue(JsonException("json") is DocumentException)
    }

    @Test
    fun keepsCause() {
        val cause = IllegalStateException("root")
        assertSame(cause, JsonParseException("json", cause).cause)
    }
}
