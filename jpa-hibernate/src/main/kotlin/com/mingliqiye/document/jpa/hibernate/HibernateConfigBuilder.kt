package com.mingliqiye.document.jpa.hibernate

/**
 * [HibernateConfig] 构建器。
 */
public class HibernateConfigBuilder {

    private var lazyLoadPolicy: HibernateLazyLoadPolicy = HibernateLazyLoadPolicy.INITIALIZE
    private var cyclePolicy: HibernateCyclePolicy = HibernateCyclePolicy.FAIL

    /**
     * 设置未初始化懒加载数据的处理策略。
     *
     * @param policy 懒加载策略
     * @return 当前构建器
     */
    public fun lazyLoadPolicy(policy: HibernateLazyLoadPolicy): HibernateConfigBuilder {
        lazyLoadPolicy = policy
        return this
    }

    /**
     * 设置循环引用处理策略。
     *
     * @param policy 循环策略
     * @return 当前构建器
     */
    public fun cyclePolicy(policy: HibernateCyclePolicy): HibernateConfigBuilder {
        cyclePolicy = policy
        return this
    }

    /** 构建不可变配置。 */
    public fun build(): HibernateConfig = HibernateConfig(lazyLoadPolicy, cyclePolicy)
}
