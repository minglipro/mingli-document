package com.mingliqiye.document.jpa.internal

import jakarta.persistence.DiscriminatorType
import jakarta.persistence.InheritanceType

/**
 * 一个类在 JPA 继承层次中的 discriminator 元数据。
 *
 * 只有当类层次中存在 `@Inheritance` 或 `@DiscriminatorColumn` 时才会生成；此时文档
 * 对象会额外包含一个 discriminator 字段（默认列名 `DTYPE`），值为该类的
 * `@DiscriminatorValue`（缺省时 STRING/CHAR 使用实体名）。
 *
 * 继承策略 [strategy] 只作为元数据记录：文档树的表示与 JPA 的 `SINGLE_TABLE`/
 * `JOINED`/`TABLE_PER_CLASS` 无关，三种策略的写出/读入语义一致。
 */
internal class JpaInheritanceMetadata(
    /** 解析该元数据的类（写出时的实际类，或读入时的声明类型）。 */
    val declaredClass: Class<*>,
    /** 继承层次根类（声明 `@Inheritance`/`@DiscriminatorColumn` 的类）。 */
    val rootClass: Class<*>,
    /** `@Inheritance` 策略。 */
    val strategy: InheritanceType,
    /** 数据库 discriminator 列名。 */
    val discriminatorColumnName: String,
    /** 文档节点字段名（形如列名经命名策略转换）。 */
    val discriminatorNodeName: String,
    /** discriminator 类型（STRING / CHAR / INTEGER）。 */
    val discriminatorType: DiscriminatorType,
    /**
     * [declaredClass] 的 discriminator 值文本。
     *
     * `null` 表示无法推导：`DiscriminatorType.INTEGER` 且未标注 `@DiscriminatorValue`。
     * 写出该类的实例时抛配置异常；读入时该值不参与候选匹配。
     */
    val discriminatorValue: String?,
)
