package com.mingliqiye.document.jpa

/**
 * JPA 属性访问策略。
 *
 * 对应 `jakarta.persistence.AccessType`，用于决定读取映射注解的位置：
 * 字段访问读取 `Field` 上的注解，属性访问读取 getter 上的注解。
 */
public enum class JpaAccessType {

    /**
     * 自动判断（默认）。
     *
     * 依次尝试：类上的 `@Access` → 第一个 `@Id` 所在成员（字段或 getter）→ 字段访问。
     */
    AUTO,

    /** 强制字段访问。 */
    FIELD,

    /** 强制属性访问（getter/setter）。 */
    PROPERTY,
}
