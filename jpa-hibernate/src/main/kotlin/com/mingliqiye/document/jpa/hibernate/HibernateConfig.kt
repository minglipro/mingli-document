package com.mingliqiye.document.jpa.hibernate

/**
 * Hibernate 运行期集成配置。
 *
 * 只包含 Hibernate 特有的运行期策略；命名策略、transient、关联深度、null 策略、
 * 未知属性策略与访问策略统一由 jpa 模块的 [com.mingliqiye.document.jpa.JpaConfig] 控制，
 * 本模块不重复定义元数据配置。
 *
 * @property lazyLoadPolicy 未初始化懒加载数据的处理策略，默认
 * [HibernateLazyLoadPolicy.INITIALIZE]
 * @property cyclePolicy 循环引用处理策略，默认 [HibernateCyclePolicy.FAIL]
 */
public class HibernateConfig internal constructor(
    public val lazyLoadPolicy: HibernateLazyLoadPolicy,
    public val cyclePolicy: HibernateCyclePolicy,
) {

    public companion object {

        /** 创建配置构建器。 */
        @JvmStatic
        public fun builder(): HibernateConfigBuilder = HibernateConfigBuilder()
    }
}
