package com.mingliqiye.document.codegen.test

import com.mingliqiye.document.annotation.DocumentEnumCodec

/**
 * 仅存在于测试源码集的枚举，用于验证 `moduleName` 与模块包名可配置。
 *
 * 测试源码集同样应用 KSP 处理器，编译期应生成
 * `com.mingliqiye.document.generated.CustomDocumentModule` 与
 * `com.mingliqiye.document.generated.MarkerDocumentConverter`。
 */
@DocumentEnumCodec(moduleName = "CustomDocumentModule")
public enum class Marker {
    ALPHA,
    BETA,
}
