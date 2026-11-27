package com.mingliqiye.document.annotation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AnnotationTest {

    @Test
    fun fieldAnnotations() {
        val valueField = AnnotationModel::class.java.getDeclaredField("value")
        assertEquals("display_name", valueField.getAnnotation(DocumentName::class.java).value)
        assertNotNull(valueField.getAnnotation(DocumentIgnore::class.java))

        val convertedField = AnnotationModel::class.java.getDeclaredField("converted")
        val converter = convertedField.getAnnotation(DocumentConverter::class.java)
        assertNotNull(converter)
        assertEquals(SampleConverter::class, converter.value)
    }
}
