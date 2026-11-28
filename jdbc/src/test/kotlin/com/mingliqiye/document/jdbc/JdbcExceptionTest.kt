package com.mingliqiye.document.jdbc

import com.mingliqiye.document.exception.DocumentException
import com.mingliqiye.document.jdbc.exception.JdbcException
import com.mingliqiye.document.jdbc.exception.JdbcReadException
import com.mingliqiye.document.jdbc.exception.JdbcWriteException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JdbcExceptionTest {

    @Test
    fun exceptionHierarchyDerivesFromDocumentException() {
        assertTrue(DocumentException::class.java.isAssignableFrom(JdbcException::class.java))
        assertTrue(JdbcException::class.java.isAssignableFrom(JdbcReadException::class.java))
        assertTrue(JdbcException::class.java.isAssignableFrom(JdbcWriteException::class.java))
    }

    @Test
    fun keepsMessageAndCause() {
        val cause = IllegalStateException("root cause")

        val read = JdbcReadException("read failed", cause)
        assertEquals("read failed", read.message)
        assertSame(cause, read.cause)

        val write = JdbcWriteException("write failed", cause)
        assertEquals("write failed", write.message)
        assertSame(cause, write.cause)

        val base = JdbcException(throwable = cause)
        assertSame(cause, base.cause)
        assertTrue(base.suppressedExceptions.isEmpty())
        assertTrue(base.stackTrace.isNotEmpty())
    }

    @Test
    fun supportsDisabledWritableStackTrace() {
        val exception = JdbcWriteException("no stack", null, writableStackTrace = false)

        assertEquals("no stack", exception.message)
        assertTrue(exception.stackTrace.isEmpty())
    }
}
