package com.mingliqiye.document.jpa

import com.mingliqiye.document.serialization.PropertyNamingStrategy

/**
 * JPA 实体 <-> [com.mingliqiye.document.core.Node] 映射配置。
 *
 * 配置只影响映射过程，不改变实体本身，也不与 `EntityManager`/`Session` 交互。
 *
 * 字段名规则（[namingStrategy]）：
 * - 标量属性使用列名（`@Column(name)`；未标注时用 Java/Kotlin 属性名）作为基础名；
 * - 关联属性与 `@Embedded` 属性使用属性名作为基础名（关联不是列）；
 * - 基础名再经 [namingStrategy] 转换，默认 [PropertyNamingStrategy.AS_IS] 表示原样使用。
 *
 * 读取时同时接受节点字段名、列名、属性名三种别名，因此命名策略只需保证写出稳定。
 *
 * @property namingStrategy 节点字段命名策略，默认 [PropertyNamingStrategy.AS_IS]
 * @property includeTransient 是否包含 `@Transient`/`transient` 属性，默认 `false`
 * @property associationDepth 关联递归最大层数（根实体的关联为第 1 层），默认 `1`；
 * `0` 表示不展开任何关联
 * @property nullPolicy null 处理策略，默认 [JpaNullPolicy.INCLUDE]
 * @property unknownPropertyPolicy 未知属性策略，默认 [JpaUnknownPropertyPolicy.FAIL]
 * @property accessType 属性访问策略，默认 [JpaAccessType.AUTO]
 */
public class JpaConfig internal constructor(
    public val namingStrategy: PropertyNamingStrategy,
    public val includeTransient: Boolean,
    public val associationDepth: Int,
    public val nullPolicy: JpaNullPolicy,
    public val unknownPropertyPolicy: JpaUnknownPropertyPolicy,
    public val accessType: JpaAccessType,
) {

    public companion object {

        /**
         * 创建配置构建器。
         */
        @JvmStatic
        public fun builder(): JpaConfigBuilder = JpaConfigBuilder()
    }
}
