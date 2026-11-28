package com.mingliqiye.document.jpa.hibernate

/**
 * 循环引用处理策略。
 *
 * 只在传入 `EntityManager` / `Session` 的 Hibernate 感知映射中生效。
 * 按对象身份（代理已还原为实体实现）检测递归路径上的重复实体。
 */
public enum class HibernateCyclePolicy {

    /**
     * 检测到循环引用时抛
     * [com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaMappingException]，
     * 消息包含首次出现路径。
     */
    FAIL,

    /**
     * 检测到循环引用时省略该引用：单值关联字段不写出、集合中的循环元素跳过。
     *
     * 适合直接序列化双向关联对象图，不会写 `$ref` 之类的引用语法。
     */
    OMIT,
}
