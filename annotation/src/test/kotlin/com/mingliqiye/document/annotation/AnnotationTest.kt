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

    @Test
    fun namingAndIncludeAnnotationsAreReadableAtRuntime() {
        val classNaming = NamingModel::class.java.getAnnotation(DocumentNaming::class.java)
        assertNotNull(classNaming)
        assertEquals(DocumentNamingStrategy.SnakeCaseStrategy, classNaming.value)

        val userNameField = NamingModel::class.java.getDeclaredField("userName")
        val fieldNaming = userNameField.getAnnotation(DocumentNaming::class.java)
        assertNotNull(fieldNaming)
        assertEquals(DocumentNamingStrategy.KebabCaseStrategy, fieldNaming.value)

        val nicknameField = NamingModel::class.java.getDeclaredField("nickname")
        val include = nicknameField.getAnnotation(DocumentInclude::class.java)
        assertNotNull(include)
        assertEquals(DocumentIncludePolicy.NON_EMPTY, include.value)
    }

    @Test
    fun namingStrategyAndIncludePolicyEnumerationsAreStable() {
        assertEquals(
            listOf(
                "AsIsStrategy",
                "SnakeCaseStrategy",
                "KebabCaseStrategy",
                "UpperCamelStrategy",
                "LowerCamelStrategy",
            ),
            DocumentNamingStrategy.entries.map { it.name },
        )
        assertEquals(
            listOf("ALWAYS", "NON_NULL", "NON_EMPTY", "USE_DEFAULTS"),
            DocumentIncludePolicy.entries.map { it.name },
        )
    }
}
