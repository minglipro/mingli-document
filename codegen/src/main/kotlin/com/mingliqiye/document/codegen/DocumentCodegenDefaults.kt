package com.mingliqiye.document.codegen

/**
 * 编译期代码生成默认值。
 *
 * 这里的默认值与 `@DocumentEnumCodec` 注解参数的默认值保持一致；注解模块不能反向依赖
 * 代码生成模块，因此注解里使用字面量，修改时需同步两处。
 */
public object DocumentCodegenDefaults {

    /** 生成转换器与文档模块的默认包名。 */
    public const val PACKAGE: String = "com.mingliqiye.document.generated"

    /** 生成文档模块的默认类名。 */
    public const val MODULE_NAME: String = "GeneratedDocumentModule"
}
