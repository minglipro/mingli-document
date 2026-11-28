package com.mingliqiye.document.codegen

import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultDocumentEnumCodecSourceGeneratorTest {

    private val generator = DefaultDocumentEnumCodecSourceGenerator()

    @Test
    fun converterSourceUsesNameAndValueOf() {
        val source = generator.converterSource(
            DocumentEnumCodecSpec(
                enumQualifiedName = "com.example.Pet",
                converterName = "PetDocumentConverter",
            ),
        )

        assertTrue(source.contains("package com.mingliqiye.document.generated"))
        assertTrue(source.contains("public class PetDocumentConverter : DocumentBaseConverter<com.example.Pet>"))
        assertTrue(source.contains("DocumentTypeReference.of(com.example.Pet::class.java)"))
        assertTrue(source.contains("StringNode.of(value.name)"))
        assertTrue(source.contains("com.example.Pet.valueOf(node.asString())"))
        assertTrue(source.contains("catch (_: Exception)"))
    }

    @Test
    fun converterSourceHonoursCustomPackageAndName() {
        val source = generator.converterSource(
            DocumentEnumCodecSpec(
                enumQualifiedName = "com.example.Review",
                converterPackage = "com.example.generated.custom",
                converterName = "ReviewCodec",
            ),
        )

        assertTrue(source.contains("package com.example.generated.custom"))
        assertTrue(source.contains("public class ReviewCodec : DocumentBaseConverter<com.example.Review>"))
    }

    @Test
    fun moduleSourceRegistersEveryConverter() {
        val source = generator.moduleSource(
            DocumentModuleSpec(
                packageName = "com.example.generated",
                moduleName = "CustomDocumentModule",
                converters = listOf(
                    "com.example.generated.PetDocumentConverter",
                    "com.example.generated.custom.ReviewCodec",
                ),
            ),
        )

        assertTrue(source.contains("package com.example.generated"))
        assertTrue(source.contains("public class CustomDocumentModule : DocumentModule"))
        assertTrue(source.contains("override val moduleName: String = \"CustomDocumentModule\""))
        assertTrue(source.contains("context.addConverter(com.example.generated.PetDocumentConverter())"))
        assertTrue(source.contains("context.addConverter(com.example.generated.custom.ReviewCodec())"))
    }
}
