package com.mingliqiye.document.jpa.internal

/**
 * 单个实体/嵌入类的映射元数据。
 */
internal class JpaEntityMetadata(
    /** 实际用于映射的类（Hibernate 代理类会解析到其 `@Entity` 父类）。 */
    val entityClass: Class<*>,
    /** 实体名：`@Entity(name)` 非空时使用，否则使用简单类名。 */
    val entityName: String,
    /** 表名：`@Table(name)` 非空时使用，否则为 `null`。 */
    val tableName: String?,
    /** 映射属性，保持“父类在前、子类在后”的声明顺序。 */
    val properties: List<JpaProperty>,
    /** 别名索引：节点字段名、列名、属性名都指向同一个属性。 */
    val aliases: Map<String, JpaProperty>,
) {

    /** 按节点键查找属性。 */
    fun propertyFor(key: String): JpaProperty? = aliases[key]

    /** 可读取的属性。 */
    fun readableProperties(): List<JpaProperty> = properties.filter { it.canRead }

    /** 是否为嵌入类（`@Embeddable`）。 */
    val embeddable: Boolean
        get() = entityClass.isAnnotationPresent(jakarta.persistence.Embeddable::class.java)
}
