package com.mingliqiye.document.serialization.exception

import com.mingliqiye.document.exception.DocumentException
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DocumentSerializationExceptionTest {

    @Test
    fun extendsDocumentException() {
        assertTrue(DocumentSerializationException("serialization") is DocumentException)
    }

    @Test
    fun keepsCause() {
        val cause = IllegalStateException("root")
        assertSame(cause, DocumentSerializationException("serialization", cause).cause)
    }
}
