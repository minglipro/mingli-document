package com.mingliqiye.document.exception

import com.mingliqiye.document.format.json.exception.JsonException
import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.format.yaml.exception.YamlException
import com.mingliqiye.document.format.yaml.exception.YamlParseException
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExceptionTest {

    @Test
    fun allDocumentExceptionsShareBaseClass() {
        assertTrue(DocumentTypeException("type") is DocumentException)
        assertTrue(DocumentSerializationException("serialization") is DocumentException)
        assertTrue(JsonException("json") is DocumentException)
        assertTrue(JsonParseException("json parse") is DocumentException)
        assertTrue(YamlException("yaml") is DocumentException)
        assertTrue(YamlParseException("yaml parse") is DocumentException)
    }

    @Test
    fun specializedExceptionsKeepHierarchy() {
        assertTrue(JsonParseException("json parse") is JsonException)
        assertTrue(YamlParseException("yaml parse") is YamlException)
    }

    @Test
    fun causesArePreserved() {
        val cause = IllegalStateException("root")

        assertSame(cause, DocumentSerializationException("serialization", cause).cause)
        assertSame(cause, JsonParseException("json", cause).cause)
        assertSame(cause, YamlParseException("yaml", cause).cause)
        assertEquals("root", DocumentException("document", cause).cause?.message)
    }
}
