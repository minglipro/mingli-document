package com.mingliqiye.document.annotation

/**
 * 指定数字字段的解析与格式化模式。
 *
 * 使用 [java.text.DecimalFormat] 的 pattern 语法，例如 `#,##0.00`、`0.00%`。
 * 数字字段会以字符串形式写入文档，读取时再按目标数字类型还原。
 *
 * @property value 数字格式，例如 `#,##0.00`
 * @property locale 可选 Locale，例如 `zh-CN`；为空时使用系统默认 Locale
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class DocumentNumberFormat(
    val value: String,
    val locale: String = "",
)
