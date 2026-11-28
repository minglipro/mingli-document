package com.mingliqiye.document.serialization.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentCodegenTest {

    class CodegenBean(
        var name: String,
    ) {
        val age: Int = 1
    }

    enum class Color {
        RED,
        GREEN,
    }

    @Test
    fun runtimeAccessorCodegenReadsAndWrites() {
        val codegen = DocumentAccessorCodegen()
        val bean = CodegenBean("a")
        val getter = codegen.getter(CodegenBean::class.java, "name")
        assertNotNull(getter)
        assertEquals("a", getter.apply(bean))

        val setter = codegen.setter(CodegenBean::class.java, "name")
        assertNotNull(setter)
        setter.accept(bean, "b")
        assertEquals("b", bean.name)

        assertNull(codegen.setter(CodegenBean::class.java, "age"))
    }

    @Test
    fun compileTimeEnumConverterSource() {
        val source = DocumentCodecSourceGenerator().enumConverter(
            Color::class.java,
            "com.example.generated",
        )
        assertTrue(source.contains("public class ColorDocumentConverter : DocumentBaseConverter<"))
        assertTrue(source.contains("StringNode.of(value.name)"))
        assertTrue(source.contains("Color.valueOf(node.asString())"))
    }

    @Test
    fun compileTimeModuleSource() {
        val source = DocumentCodecSourceGenerator().module(
            "com.example.generated",
            "GeneratedDocumentModule",
            listOf("com.example.generated.ColorDocumentConverter"),
        )
        assertTrue(source.contains("public class GeneratedDocumentModule : DocumentModule"))
        assertTrue(source.contains("context.addConverter(com.example.generated.ColorDocumentConverter())"))
    }
}

class CodegenBean(
    var name: String,
) {
    val age: Int = 1
}
