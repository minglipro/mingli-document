package com.mingliqiye.document.codegen

/**
 * 文档模块参数的解析结果。
 */
internal sealed interface DocumentModuleSpecResolution {

    /**
     * 解析成功。
     *
     * @property spec 生成的模块参数
     */
    data class Resolved(val spec: DocumentModuleSpec) : DocumentModuleSpecResolution

    /**
     * 多个枚举配置的模块名/包名不一致。
     *
     * @property message 冲突描述，供 KSP 报错
     */
    data class Conflict(val message: String) : DocumentModuleSpecResolution
}

/**
 * 按枚举参数解析唯一的文档模块参数。
 *
 * 默认模块位于 [DocumentCodegenDefaults.PACKAGE]，类名为
 * [DocumentCodegenDefaults.MODULE_NAME]；任一枚举通过 `moduleName` 指定了模块名时，
 * 模块包名取该枚举的转换器包名，所有显式指定的模块名与包名必须一致。
 *
 * @param specs 全部枚举的代码生成参数
 * @return 模块参数或冲突描述
 */
internal fun resolveDocumentModuleSpec(specs: Collection<DocumentEnumCodecSpec>): DocumentModuleSpecResolution {
    val ordered = specs.sortedBy { it.enumQualifiedName }
    val explicitModules = ordered.filter { it.moduleName.isNotBlank() }
    val modulePackage: String
    val moduleName: String
    if (explicitModules.isEmpty()) {
        modulePackage = DocumentCodegenDefaults.PACKAGE
        moduleName = DocumentCodegenDefaults.MODULE_NAME
    } else {
        val targets = explicitModules.map { it.converterPackage to it.moduleName }.distinct()
        if (targets.size > 1) {
            val rendered = targets.joinToString { (packageName, name) -> "$packageName.$name" }
            return DocumentModuleSpecResolution.Conflict(
                "Conflicting @DocumentEnumCodec moduleName values: $rendered. " +
                    "Every annotated enum that configures moduleName must use the same package and class name.",
            )
        }
        val target = targets.single()
        modulePackage = target.first
        moduleName = target.second
    }
    return DocumentModuleSpecResolution.Resolved(
        DocumentModuleSpec(
            packageName = modulePackage,
            moduleName = moduleName,
            converters = ordered.map { "${it.converterPackage}.${it.converterName}" },
        ),
    )
}
