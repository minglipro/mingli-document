package com.mingliqiye.document.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import java.io.IOException

/**
 * `@DocumentEnumCodec` 的 KSP 处理器。
 *
 * 扫描源码中被 `@DocumentEnumCodec` 标注的枚举，为每个枚举生成一个
 * `DocumentBaseConverter`，并在处理结束时生成一个注册全部转换器的 `DocumentModule`。
 * 生成的源码由 KSP 加入当前编译单元，与手写源码一起参与编译。
 */
public class DocumentEnumCodecProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private val sourceGenerator: DocumentEnumCodecSourceGenerator = DefaultDocumentEnumCodecSourceGenerator()
    private val specs = LinkedHashMap<String, DocumentEnumCodecSpec>()
    private val sources = LinkedHashMap<String, KSFile>()
    private val generatedConverters = HashSet<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        for (symbol in resolver.getSymbolsWithAnnotation(ANNOTATION_QUALIFIED_NAME)) {
            if (!symbol.validate()) {
                deferred += symbol
                continue
            }
            val declaration = symbol as? KSClassDeclaration
            if (declaration == null || declaration.classKind != ClassKind.ENUM_CLASS) {
                logger.error("@DocumentEnumCodec can only be applied to enum classes.", symbol)
                continue
            }
            val enumQualifiedName = declaration.qualifiedName?.asString()
            if (enumQualifiedName == null) {
                logger.error("Cannot resolve the qualified name of the annotated enum.", declaration)
                continue
            }
            if (specs.containsKey(enumQualifiedName)) {
                continue
            }
            if (!isAccessible(declaration)) {
                logger.error(
                    "@DocumentEnumCodec requires a public enum: $enumQualifiedName.",
                    declaration,
                )
                continue
            }
            val spec = parseSpec(declaration, enumQualifiedName) ?: continue
            specs[enumQualifiedName] = spec
            declaration.containingFile?.let { sources[enumQualifiedName] = it }
            generateConverter(declaration, spec)
        }
        return deferred
    }

    override fun finish() {
        if (specs.isEmpty()) {
            return
        }
        when (val resolution = resolveDocumentModuleSpec(specs.values)) {
            is DocumentModuleSpecResolution.Resolved -> generateModule(resolution.spec)
            is DocumentModuleSpecResolution.Conflict -> logger.error(resolution.message)
        }
    }

    private fun parseSpec(
        declaration: KSClassDeclaration,
        enumQualifiedName: String,
    ): DocumentEnumCodecSpec? {
        val annotation = declaration.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_QUALIFIED_NAME
        } ?: return null
        val converterPackage = annotation.stringArgument("converterPackage")
            .ifBlank { DocumentCodegenDefaults.PACKAGE }
        if (!DocumentCodegenNames.isValidPackage(converterPackage)) {
            logger.error(
                "Invalid converterPackage '$converterPackage' on @DocumentEnumCodec for $enumQualifiedName.",
                declaration,
            )
            return null
        }
        val defaultConverterName = "${declaration.simpleName.asString()}DocumentConverter"
        val converterName = annotation.stringArgument("converterName").ifBlank { defaultConverterName }
        if (!DocumentCodegenNames.isValidIdentifier(converterName)) {
            logger.error(
                "Invalid converterName '$converterName' on @DocumentEnumCodec for $enumQualifiedName.",
                declaration,
            )
            return null
        }
        val moduleName = annotation.stringArgument("moduleName")
        if (moduleName.isNotBlank() && !DocumentCodegenNames.isValidIdentifier(moduleName)) {
            logger.error(
                "Invalid moduleName '$moduleName' on @DocumentEnumCodec for $enumQualifiedName.",
                declaration,
            )
            return null
        }
        return DocumentEnumCodecSpec(
            enumQualifiedName = enumQualifiedName,
            converterPackage = converterPackage,
            converterName = converterName,
            moduleName = moduleName,
        )
    }

    private fun generateConverter(declaration: KSClassDeclaration, spec: DocumentEnumCodecSpec) {
        val converterQualifiedName = "${spec.converterPackage}.${spec.converterName}"
        if (!generatedConverters.add(converterQualifiedName)) {
            logger.error(
                "Duplicate generated converter '$converterQualifiedName' for ${spec.enumQualifiedName}. " +
                    "Configure a unique converterName or converterPackage on @DocumentEnumCodec.",
                declaration,
            )
            return
        }
        val sourceFile = declaration.containingFile
        val dependencies = if (sourceFile == null) {
            Dependencies(true)
        } else {
            Dependencies(false, sourceFile)
        }
        writeSource(
            packageName = spec.converterPackage,
            fileName = spec.converterName,
            dependencies = dependencies,
            source = sourceGenerator.converterSource(spec),
            symbol = declaration,
        )
    }

    private fun generateModule(spec: DocumentModuleSpec) {
        val dependencies = Dependencies(true, *sources.values.toTypedArray())
        writeSource(
            packageName = spec.packageName,
            fileName = spec.moduleName,
            dependencies = dependencies,
            source = sourceGenerator.moduleSource(spec),
            symbol = null,
        )
    }

    private fun writeSource(
        packageName: String,
        fileName: String,
        dependencies: Dependencies,
        source: String,
        symbol: KSNode?,
    ) {
        try {
            codeGenerator.createNewFile(dependencies, packageName, fileName).use { output ->
                output.write(source.toByteArray(Charsets.UTF_8))
            }
        } catch (e: IOException) {
            logger.error("Failed to generate $packageName.$fileName: ${e.message}", symbol)
        }
    }

    /**
     * 判断枚举及其外层声明是否都可以从生成代码所在的包访问。
     *
     * 生成的转换器是 public 类，枚举若为 private/internal/protected 会导致生成代码无法编译，
     * 因此在处理阶段提前报错并给出明确提示。
     */
    private fun isAccessible(declaration: KSClassDeclaration): Boolean {
        var current: KSDeclaration? = declaration
        while (current != null) {
            val restricted = current.modifiers.any {
                it == Modifier.PRIVATE || it == Modifier.INTERNAL || it == Modifier.PROTECTED
            }
            if (restricted) {
                return false
            }
            current = current.parentDeclaration
        }
        return true
    }

    private fun KSAnnotation.stringArgument(name: String): String = arguments.firstOrNull { it.name?.asString() == name }?.value as? String ?: ""

    private companion object {

        private const val ANNOTATION_QUALIFIED_NAME: String =
            "com.mingliqiye.document.annotation.DocumentEnumCodec"
    }
}
