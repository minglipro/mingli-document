package com.mingliqiye.document.jpa.internal

/**
 * 一条 `@Convert(attributeName = "...")` 覆盖规则。
 *
 * [converter] 为 `null` 表示 `disableConversion = true`：目标属性不应用任何转换器
 * （包括 autoApply 与属性自身的 `@Convert`）。
 */
internal class JpaConvertOverride(
    /** 覆盖使用的转换器；`null` 表示禁用转换。 */
    val converter: JpaAttributeConverter?,
)
