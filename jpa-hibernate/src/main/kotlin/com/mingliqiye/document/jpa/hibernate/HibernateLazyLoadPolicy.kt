package com.mingliqiye.document.jpa.hibernate

/**
 * 未初始化懒加载数据的处理策略。
 *
 * 只在传入 `EntityManager` / `Session` 的 Hibernate 感知映射中生效；
 * 不传 `EntityManager` 时映射完全委托给 jpa 模块，本策略不参与。
 */
public enum class HibernateLazyLoadPolicy {

    /**
     * 需要时通过 `Hibernate.initialize` 初始化懒加载集合/代理。
     *
     * 要求传入的 `EntityManager`/`Session` 处于打开状态且与实体关联；
     * 无法初始化时抛
     * [com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaLazyLoadException]。
     */
    INITIALIZE,

    /**
     * 遇到未初始化的懒加载数据直接抛异常，不触发任何额外数据库查询。
     *
     * 适合需要严格控制 SQL 数量、要求调用方预先初始化的场景。
     */
    FAIL,
}
