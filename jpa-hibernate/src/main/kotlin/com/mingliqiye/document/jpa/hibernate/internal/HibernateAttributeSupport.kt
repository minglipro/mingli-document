package com.mingliqiye.document.jpa.hibernate.internal

import jakarta.persistence.Convert
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.metamodel.Attribute
import java.lang.reflect.AnnotatedElement

/**
 * 判断关系属性是否带显式 `@Convert`（jpa 模块会把整个关联值按基本属性写/读）。
 *
 * 这类属性由 jpa 元数据映射处理，Hibernate 运行期映射**不得**再按关联展开
 * （否则会覆盖转换后的标量值）。`@ElementCollection` 属性上的 `@Convert` 作用于
 * 元素值（语义不同），不在此列。
 *
 * @param attribute JPA/Hibernate 元模型属性
 */
internal fun hasExplicitConvertedAssociation(attribute: Attribute<*, *>): Boolean {
    if (!attribute.isAssociation) {
        return false
    }
    val member = attribute.javaMember as? AnnotatedElement ?: return false
    val isRelationship = member.getAnnotation(OneToOne::class.java) != null ||
        member.getAnnotation(OneToMany::class.java) != null ||
        member.getAnnotation(ManyToOne::class.java) != null ||
        member.getAnnotation(ManyToMany::class.java) != null
    if (!isRelationship) {
        return false
    }
    val converts = member.getAnnotationsByType(Convert::class.java)
        .filter { it.attributeName.isEmpty() }
    return converts.any { !it.disableConversion && it.converter.java != java.lang.Void.TYPE }
}
