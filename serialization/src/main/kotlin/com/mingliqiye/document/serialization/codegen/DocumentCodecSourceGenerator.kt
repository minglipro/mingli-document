package com.mingliqiye.document.serialization.codegen

/**
 * 编译期源码生成器。
 *
 * 在构建阶段为枚举生成 [com.mingliqiye.document.serialization.DocumentBaseConverter]
 * 的 Kotlin 源码，用户把返回的源码写入源码目录后由编译器编译。
 *
 * 生成器本身不依赖编译器插件，适合在 Gradle/Maven 的自定义 task 中调用。
 */
public class DocumentCodecSourceGenerator {

    /**
     * 为枚举生成转换器源码。
     *
     * @param enumType 枚举类型
     * @param packageName 生成源码的包名
     * @param converterName 生成的转换器类名；为空时使用 `<枚举名>DocumentConverter`
     * @return Kotlin 源码
     */
    public fun enumConverter(
        enumType: Class<out Enum<*>>,
        packageName: String,
        converterName: String = "${enumType.simpleName}DocumentConverter",
    ): String {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(converterName.isNotBlank()) { "converterName must not be blank" }
        val typeName = enumType.name.replace('$', '.')
        return buildString {
            appendLine("@file:Suppress(\"unused\")")
            appendLine()
            appendLine("package $packageName")
            appendLine()
            appendLine("import com.mingliqiye.document.core.DocumentTypeReference")
            appendLine("import com.mingliqiye.document.core.Node")
            appendLine("import com.mingliqiye.document.core.StringNode")
            appendLine("import com.mingliqiye.document.serialization.DeserializationContext")
            appendLine("import com.mingliqiye.document.serialization.DocumentBaseConverter")
            appendLine("import com.mingliqiye.document.serialization.SerializationContext")
            appendLine()
            appendLine("public class $converterName : DocumentBaseConverter<$typeName> {")
            appendLine()
            appendLine("    override fun getType(): DocumentTypeReference<$typeName> =")
            appendLine("        DocumentTypeReference.of($typeName::class.java)")
            appendLine()
            appendLine("    override fun serialize(value: $typeName, context: SerializationContext): Node =")
            appendLine("        StringNode.of(value.name)")
            appendLine()
            appendLine("    override fun deserialize(node: Node, context: DeserializationContext): $typeName? =")
            appendLine("        try {")
            appendLine("            $typeName.valueOf(node.asString())")
            appendLine("        } catch (_: Exception) {")
            appendLine("            null")
            appendLine("        }")
            appendLine("}")
        }
    }

    /**
     * 生成注册全部转换器的 [com.mingliqiye.document.serialization.DocumentModule] 源码。
     *
     * @param packageName 生成源码的包名
     * @param moduleName 模块名
     * @param converterTypes 转换器类全限定名
     * @return Kotlin 源码
     */
    public fun module(
        packageName: String,
        moduleName: String,
        converterTypes: List<String>,
    ): String {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(moduleName.isNotBlank()) { "moduleName must not be blank" }
        return buildString {
            appendLine("@file:Suppress(\"unused\")")
            appendLine()
            appendLine("package $packageName")
            appendLine()
            appendLine("import com.mingliqiye.document.serialization.DocumentModule")
            appendLine("import com.mingliqiye.document.serialization.ModuleSetupContext")
            appendLine()
            appendLine("public class $moduleName : DocumentModule {")
            appendLine()
            appendLine("    override val moduleName: String = \"$moduleName\"")
            appendLine()
            appendLine("    override fun setupModule(context: ModuleSetupContext) {")
            for (converter in converterTypes) {
                appendLine("        context.addConverter($converter())")
            }
            appendLine("    }")
            appendLine("}")
        }
    }
}
