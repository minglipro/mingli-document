package com.mingliqiye.document.jpa

import com.mingliqiye.document.serialization.PropertyNamingStrategy

/**
 * [JpaConfig] 构建器。
 */
public class JpaConfigBuilder {

    private var namingStrategy: PropertyNamingStrategy = PropertyNamingStrategy.AS_IS
    private var includeTransient: Boolean = false
    private var associationDepth: Int = DEFAULT_ASSOCIATION_DEPTH
    private var nullPolicy: JpaNullPolicy = JpaNullPolicy.INCLUDE
    private var unknownPropertyPolicy: JpaUnknownPropertyPolicy = JpaUnknownPropertyPolicy.FAIL
    private var accessType: JpaAccessType = JpaAccessType.AUTO

    /**
     * 设置节点字段命名策略。
     *
     * 只影响从列名/属性名派生节点字段名的过程；读取始终接受节点字段名、列名与
     * 属性名三种别名。
     *
     * @param strategy 命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): JpaConfigBuilder {
        namingStrategy = strategy
        return this
    }

    /**
     * 设置是否包含 `@Transient`/`transient` 属性。
     *
     * 开启后这些属性按普通值（标量、枚举、日期时间、集合、Map、数组）写出/读入；
     * 其类型必须受支持，否则映射时抛
     * [com.mingliqiye.document.jpa.exception.JpaMappingException]。
     *
     * @param include 是否包含 transient 属性
     * @return 当前构建器
     */
    public fun includeTransient(include: Boolean): JpaConfigBuilder {
        includeTransient = include
        return this
    }

    /**
     * 设置关联递归最大层数。
     *
     * 根实体的关联为第 1 层；超过层数的关联属性在写出时被省略、读取时跳过。
     *
     * @param depth 最大层数，必须大于等于 0；`0` 表示不展开关联
     * @return 当前构建器
     */
    public fun associationDepth(depth: Int): JpaConfigBuilder {
        require(depth >= 0) { "associationDepth must be >= 0" }
        associationDepth = depth
        return this
    }

    /**
     * 设置 null 属性处理策略。
     *
     * @param policy null 策略
     * @return 当前构建器
     */
    public fun nullPolicy(policy: JpaNullPolicy): JpaConfigBuilder {
        nullPolicy = policy
        return this
    }

    /**
     * 设置未知属性处理策略。
     *
     * @param policy 未知属性策略
     * @return 当前构建器
     */
    public fun unknownPropertyPolicy(policy: JpaUnknownPropertyPolicy): JpaConfigBuilder {
        unknownPropertyPolicy = policy
        return this
    }

    /**
     * 设置属性访问策略。
     *
     * @param type 访问策略
     * @return 当前构建器
     */
    public fun accessType(type: JpaAccessType): JpaConfigBuilder {
        accessType = type
        return this
    }

    /**
     * 构建不可变配置。
     */
    public fun build(): JpaConfig = JpaConfig(
        namingStrategy,
        includeTransient,
        associationDepth,
        nullPolicy,
        unknownPropertyPolicy,
        accessType,
    )
}

/** 默认关联递归层数。 */
internal const val DEFAULT_ASSOCIATION_DEPTH: Int = 1
