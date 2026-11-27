package com.mingliqiye.document.serialization

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * [DocumentToken] 基础契约测试。
 */
class DocumentTokenTest {

    @Test
    fun factoryMethodsPopulateCorrectFields() {
        assertEquals(DocumentTokenType.START_OBJECT, DocumentToken.startObject().type)
        assertEquals("name", DocumentToken.fieldName("name").text)
        assertEquals("value", DocumentToken.string("value").text)
        assertEquals("12.5", DocumentToken.number("12.5").text)
        assertEquals(true, DocumentToken.boolean(true).booleanValue)
        assertEquals(DocumentTokenType.NULL, DocumentToken.nullValue().type)

        val source = byteArrayOf(1, 2, 3)
        val binary = DocumentToken.binary(source)
        source[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), binary.binaryValue)
        assertEquals(DocumentTokenType.BINARY, binary.type)
    }
}
