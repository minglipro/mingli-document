package com.mingliqiye.document.codegen.test

import com.mingliqiye.document.annotation.DocumentEnumCodec

/**
 * 使用默认代码生成配置的第二个枚举，验证同一模块内多个枚举都会被注册。
 */
@DocumentEnumCodec
public enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
}
