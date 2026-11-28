package com.mingliqiye.document.codegen

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * [DocumentEnumCodecProcessor] 的 KSP 注册入口。
 *
 * 通过 `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
 * 暴露给 KSP，使用方只需在 Gradle 中声明 `ksp(project(":codegen"))`。
 */
public class DocumentEnumCodecProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = DocumentEnumCodecProcessor(environment.codeGenerator, environment.logger)
}
