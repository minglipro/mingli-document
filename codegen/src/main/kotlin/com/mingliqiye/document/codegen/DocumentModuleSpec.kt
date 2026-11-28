package com.mingliqiye.document.codegen

/**
 * 文档模块的代码生成参数。
 *
 * @property packageName 生成模块的包名
 * @property moduleName 生成模块的类名，同时作为 [com.mingliqiye.document.serialization.DocumentModule.moduleName]
 * @property converters 需要注册的转换器全限定类名，按枚举全限定名排序
 */
public data class DocumentModuleSpec(
    public val packageName: String,
    public val moduleName: String,
    public val converters: List<String>,
)
