package com.mingliqiye.document.codegen.test

import com.mingliqiye.document.annotation.DocumentEnumCodec

/**
 * 自定义生成包名与转换器类名的枚举。
 *
 * 编译期应生成 `com.mingliqiye.document.generated.custom.ReviewCodec`；
 * 由于没有指定模块名，它同样会被默认的 `GeneratedDocumentModule` 注册。
 */
@DocumentEnumCodec(
    converterPackage = "com.mingliqiye.document.generated.custom",
    converterName = "ReviewCodec",
)
public enum class Review {
    APPROVED,
    REJECTED,
}
