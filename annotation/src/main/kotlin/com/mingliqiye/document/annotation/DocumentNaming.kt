package com.mingliqiye.document.annotation

/**
 * 字段命名策略，配合 [DocumentNaming] 使用。
 *
 * 枚举项采用 Java/Kotlin 通用写法，Kotlin 中可导入枚举项后直接写
 * `@DocumentNaming(SnakeCaseStrategy)`：
 *
 * ```kotlin
 * import com.mingliqiye.document.annotation.DocumentNaming
 * import com.mingliqiye.document.annotation.DocumentNamingStrategy.SnakeCaseStrategy
 *
 * @DocumentNaming(SnakeCaseStrategy)
 * class User {
 *     var userName: String = ""
 * }
 * ```
 */
public enum class DocumentNamingStrategy {

    /** 保持属性名原样。 */
    AsIsStrategy,

    /** 转换为 snake_case，例如 `userName` 转为 `user_name`。 */
    SnakeCaseStrategy,

    /** 转换为 kebab-case，例如 `userName` 转为 `user-name`。 */
    KebabCaseStrategy,

    /** 转换为 UpperCamel/PascalCase，例如 `user_name` 转为 `UserName`。 */
    UpperCamelStrategy,

    /** 转换为 lowerCamelCase，例如 `user_name` 转为 `userName`。 */
    LowerCamelStrategy,
}

/**
 * 类级/字段级字段命名策略覆盖，对应 Jackson 的
 * `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)`。
 *
 * 同时支持类级与字段级：
 * - 类级标注作用于该类型所有**未显式命名**的属性/构造参数/record 组件；
 * - 字段级标注覆盖类级；
 * - [DocumentName] 显式指定的字段名优先级最高，不受本注解影响；
 * - 都没有时回退到全局 `DocumentMapperConfig.namingStrategy`。
 *
 * 优先级（从高到低）：
 *
 * ```text
 * @DocumentName
 * 字段级 @DocumentNaming
 * 类级 @DocumentNaming
 * 全局 DocumentMapperConfig.namingStrategy
 * ```
 *
 * @property value 命名策略
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
public annotation class DocumentNaming(
    val value: DocumentNamingStrategy,
)
