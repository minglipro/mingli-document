package com.mingliqiye.document.annotation

/**
 * 指定日期/时间字段的解析与格式化模式。
 *
 * 支持 `java.util.Date`、`Calendar` 以及 `java.time` 下的
 * `LocalDate`、`LocalDateTime`、`LocalTime`、`OffsetDateTime`、
 * `ZonedDateTime`、`Instant`。
 *
 * @property value 日期时间格式，例如 `yyyy-MM-dd HH:mm:ss`
 * @property timezone 可选时区，例如 `Asia/Shanghai`；为空时使用系统默认时区
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class DocumentDateTimeFormat(
    val value: String,
    val timezone: String = "",
)
