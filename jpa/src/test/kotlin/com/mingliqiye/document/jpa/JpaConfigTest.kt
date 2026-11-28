package com.mingliqiye.document.jpa

import com.mingliqiye.document.serialization.PropertyNamingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JpaConfigTest {

    @Test
    fun hasExpectedDefaults() {
        val config = JpaConfig.builder().build()

        assertEquals(PropertyNamingStrategy.AS_IS, config.namingStrategy)
        assertFalse(config.includeTransient)
        assertEquals(1, config.associationDepth)
        assertEquals(JpaNullPolicy.INCLUDE, config.nullPolicy)
        assertEquals(JpaUnknownPropertyPolicy.FAIL, config.unknownPropertyPolicy)
        assertEquals(JpaAccessType.AUTO, config.accessType)
    }

    @Test
    fun builderOverridesEveryOption() {
        val config = JpaConfig.builder()
            .namingStrategy(PropertyNamingStrategy.LOWER_CAMEL)
            .includeTransient(true)
            .associationDepth(3)
            .nullPolicy(JpaNullPolicy.OMIT)
            .unknownPropertyPolicy(JpaUnknownPropertyPolicy.IGNORE)
            .accessType(JpaAccessType.PROPERTY)
            .build()

        assertEquals(PropertyNamingStrategy.LOWER_CAMEL, config.namingStrategy)
        assertTrue(config.includeTransient)
        assertEquals(3, config.associationDepth)
        assertEquals(JpaNullPolicy.OMIT, config.nullPolicy)
        assertEquals(JpaUnknownPropertyPolicy.IGNORE, config.unknownPropertyPolicy)
        assertEquals(JpaAccessType.PROPERTY, config.accessType)
    }

    @Test
    fun builderRejectsNegativeAssociationDepth() {
        assertFailsWith<IllegalArgumentException> {
            JpaConfig.builder().associationDepth(-1)
        }
    }

    @Test
    fun zeroAssociationDepthIsAllowed() {
        assertEquals(0, JpaConfig.builder().associationDepth(0).build().associationDepth)
    }

    @Test
    fun mapperExposesConfig() {
        val config = JpaConfig.builder().associationDepth(5).build()

        assertEquals(5, JpaDocumentMapper(config).config.associationDepth)
        assertEquals(1, JpaDocumentMapper().config.associationDepth)
    }
}
