package com.mingliqiye.document.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DocumentModuleSpecResolutionTest {

    @Test
    fun defaultsToGeneratedPackageAndModuleName() {
        val resolution = resolveDocumentModuleSpec(
            listOf(
                DocumentEnumCodecSpec(enumQualifiedName = "com.example.Pet", converterName = "PetDocumentConverter"),
                DocumentEnumCodecSpec(enumQualifiedName = "com.example.Priority", converterName = "PriorityDocumentConverter"),
            ),
        )

        val spec = assertIs<DocumentModuleSpecResolution.Resolved>(resolution).spec
        assertEquals(DocumentCodegenDefaults.PACKAGE, spec.packageName)
        assertEquals(DocumentCodegenDefaults.MODULE_NAME, spec.moduleName)
        assertEquals(
            listOf(
                "com.mingliqiye.document.generated.PetDocumentConverter",
                "com.mingliqiye.document.generated.PriorityDocumentConverter",
            ),
            spec.converters,
        )
    }

    @Test
    fun configuredModuleNameAndPackageAreUsed() {
        val resolution = resolveDocumentModuleSpec(
            listOf(
                DocumentEnumCodecSpec(
                    enumQualifiedName = "com.example.Pet",
                    converterName = "PetDocumentConverter",
                ),
                DocumentEnumCodecSpec(
                    enumQualifiedName = "com.example.Review",
                    converterPackage = "com.example.generated.custom",
                    converterName = "ReviewCodec",
                    moduleName = "CustomDocumentModule",
                ),
            ),
        )

        val spec = assertIs<DocumentModuleSpecResolution.Resolved>(resolution).spec
        assertEquals("com.example.generated.custom", spec.packageName)
        assertEquals("CustomDocumentModule", spec.moduleName)
        assertEquals(
            listOf(
                "com.mingliqiye.document.generated.PetDocumentConverter",
                "com.example.generated.custom.ReviewCodec",
            ),
            spec.converters,
        )
    }

    @Test
    fun conflictingModuleNamesAreReported() {
        val resolution = resolveDocumentModuleSpec(
            listOf(
                DocumentEnumCodecSpec(
                    enumQualifiedName = "com.example.Pet",
                    converterName = "PetDocumentConverter",
                    moduleName = "FirstDocumentModule",
                ),
                DocumentEnumCodecSpec(
                    enumQualifiedName = "com.example.Review",
                    converterName = "ReviewDocumentConverter",
                    moduleName = "SecondDocumentModule",
                ),
            ),
        )

        assertIs<DocumentModuleSpecResolution.Conflict>(resolution)
    }
}
