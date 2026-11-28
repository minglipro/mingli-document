package com.mingliqiye.document.annotation

/**
 * 属性写出策略，配合 [DocumentInclude] 使用。
 */
public enum class DocumentIncludePolicy {

    /** 总是写出该属性，即使值为 `null`。 */
    ALWAYS,

    /** `null` 不写出，其余值照常写出。 */
    NON_NULL,

    /**
     * `null`、空字符串、空集合、空 `Map`、空数组不写出，其余值照常写出。
     */
    NON_EMPTY,

    /** 回退到下一优先级；都没有显式策略时使用全局 `writeNulls` 配置。 */
    USE_DEFAULTS,
}

/**
 * 类级/字段级属性写出策略覆盖，对应 上游实现 的
 * `@JsonInclude(JsonInclude.Include.NON_NULL)`。
 *
 * 同时支持类级与字段级：
 * - 字段级标注覆盖类级；
 * - 类级标注作用于该类型所有属性；
 * - 都没有显式策略（或值为 [DocumentIncludePolicy.USE_DEFAULTS]）时回退到全局
 *   `DocumentMapperConfig.writeNulls` 配置；
 * - 嵌套对象与集合元素中的属性分别按各自类型的类级/字段级策略处理。
 *
 * 只影响序列化；反序列化与字段匹配不受影响。
 *
 * 优先级（从高到低）：
 *
 * ```text
 * 字段级 @DocumentInclude
 * 类级 @DocumentInclude
 * 全局 DocumentMapperConfig.writeNulls
 * ```
 *
 * @property value 写出策略
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class DocumentInclude(
    val value: DocumentIncludePolicy,
)
