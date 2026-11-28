package com.mingliqiye.document.codegen

/**
 * 单个枚举的代码生成参数。
 *
 * @property enumQualifiedName 枚举的 Kotlin 全限定名，例如 `com.example.Pet`
 * @property converterPackage 生成转换器的包名
 * @property converterName 生成转换器的类名
 * @property moduleName 生成文档模块的类名；空字符串表示使用默认模块名
 */
public data class DocumentEnumCodecSpec(
    public val enumQualifiedName: String,
    public val converterPackage: String = DocumentCodegenDefaults.PACKAGE,
    public val converterName: String,
    public val moduleName: String = "",
)
