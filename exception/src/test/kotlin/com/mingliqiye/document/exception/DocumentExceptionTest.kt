package com.mingliqiye.document.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DocumentExceptionTest {

    @Test
    fun baseExceptionsKeepCause() {
        val cause = IllegalStateException("root")

        assertSame(cause, DocumentException("document", cause).cause)
        assertEquals("root", DocumentException("document", cause).cause?.message)
        assertSame(cause, DocumentTypeException("type", cause).cause)
    }
}
