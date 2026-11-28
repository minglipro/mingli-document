package com.mingliqiye.document.codegen.test

import com.mingliqiye.document.annotation.DocumentEnumCodec

/**
 * 使用默认代码生成配置的枚举。
 *
 * 编译期应生成 `com.mingliqiye.document.generated.PetDocumentConverter`，并由
 * `GeneratedDocumentModule` 注册。
 */
@DocumentEnumCodec
public enum class Pet {
    CAT,
    DOG,
}
