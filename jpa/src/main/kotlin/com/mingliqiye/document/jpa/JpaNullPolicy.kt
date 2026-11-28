package com.mingliqiye.document.jpa

/**
 * null 属性处理策略。
 */
public enum class JpaNullPolicy {

    /**
     * 包含 null（默认）：写出时 null 属性使用 `NullNode`，读取时 `NullNode` 写入 null。
     */
    INCLUDE,

    /**
     * 忽略 null：写出时跳过 null 属性，读取时文档中缺失的属性保持对象默认值。
     */
    OMIT,
}
