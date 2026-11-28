package com.mingliqiye.document.jpa

/**
 * 反序列化时文档中出现未知属性的处理策略。
 *
 * “未知”指既不是节点字段名、也不是列名/属性名别名的键；映射深度限制而跳过的
 * 关联属性仍属于已知属性，不受该策略影响。
 */
public enum class JpaUnknownPropertyPolicy {

    /** 抛出 [com.mingliqiye.document.jpa.exception.JpaMappingException]（默认）。 */
    FAIL,

    /** 忽略未知属性。 */
    IGNORE,
}
