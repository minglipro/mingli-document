package com.mingliqiye.document.jpa

import com.mingliqiye.document.serialization.PropertyNamingStrategy

/**
 * [JpaConfig] 构建器。
 *
 * 除常规映射策略外，还用于注册本模块无法通过 classpath 扫描发现的两类类型：
 * - autoApply `AttributeConverter`：按属性的 Java 类型自动应用（JPA 默认规则）；
 * - 继承层次子类：按 discriminator 值反序列化为具体子类。
 */
public class JpaConfigBuilder {

    private var namingStrategy: PropertyNamingStrategy = PropertyNamingStrategy.AS_IS
    private var includeTransient: Boolean = false
    private var associationDepth: Int = DEFAULT_ASSOCIATION_DEPTH
    private var nullPolicy: JpaNullPolicy = JpaNullPolicy.INCLUDE
    private var unknownPropertyPolicy: JpaUnknownPropertyPolicy = JpaUnknownPropertyPolicy.FAIL
    private var accessType: JpaAccessType = JpaAccessType.AUTO
    private val autoApplyConverters: LinkedHashSet<Class<*>> = LinkedHashSet()
    private val entityTypes: LinkedHashSet<Class<*>> = LinkedHashSet()

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
     * 注册 autoApply `AttributeConverter`（JPA 默认规则：按属性的 Java 类型匹配）。
     *
     * 本模块不做 classpath 扫描，因此 `@Converter(autoApply = true)` 的转换器也必须
     * 通过本方法（或 [autoApplyConverters]）注册才会生效；注册后：
     * - 属性类型与 `AttributeConverter<X, Y>` 的 X（基本类型/包装类型等价）相同时自动应用；
     * - `@Convert` 显式指定的转换器优先于 autoApply；
     * - `@Convert(disableConversion = true)` 可对单个属性关闭自动转换；
     * - `@Id`/`@EmbeddedId`、关联属性、显式 `@Enumerated`/`@Temporal`/`@Version` 属性不应用；
     * - 同一属性类型注册多个 autoApply 转换器会在映射时抛
     *   [com.mingliqiye.document.jpa.exception.JpaConfigException]。
     *
     * @param converterClass 实现 [jakarta.persistence.AttributeConverter] 的类（不得为接口/抽象类，
     * 且必须有可访问的无参构造器）
     * @return 当前构建器
     */
    public fun autoApplyConverter(converterClass: Class<*>): JpaConfigBuilder {
        autoApplyConverters.add(converterClass)
        return this
    }

    /**
     * 批量注册 autoApply `AttributeConverter`，语义见 [autoApplyConverter]。
     *
     * @param converterClasses 转换器类
     * @return 当前构建器
     */
    public fun autoApplyConverters(vararg converterClasses: Class<*>): JpaConfigBuilder {
        converterClasses.forEach { autoApplyConverters.add(it) }
        return this
    }

    /**
     * 注册实体类，用于多态反序列化时按 discriminator 选择子类。
     *
     * 读入的声明类型是父类（通常是抽象类）时，只有已注册且可赋值给声明类型的子类
     * 会成为 discriminator 的候选；未知 discriminator 值抛
     * [com.mingliqiye.document.jpa.exception.JpaMappingException]。
     *
     * 写出方向不需要注册：映射器按实际对象类型写出对应的 discriminator 值。
     *
     * @param entityType 实体类（或其 Hibernate 代理对应的实体类）
     * @return 当前构建器
     */
    public fun entityType(entityType: Class<*>): JpaConfigBuilder {
        entityTypes.add(entityType)
        return this
    }

    /**
     * 批量注册实体类，语义见 [entityType]。
     *
     * @param types 实体类
     * @return 当前构建器
     */
    public fun entityTypes(vararg types: Class<*>): JpaConfigBuilder {
        types.forEach { entityTypes.add(it) }
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
        autoApplyConverters.toList(),
        entityTypes.toList(),
    )
}

/** 默认关联递归层数。 */
internal const val DEFAULT_ASSOCIATION_DEPTH: Int = 1
