package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName
import java.lang.reflect.Type

/**
 * Java 实体字段/属性的元数据，提供给转换器读取字段信息与注解。
 */
public interface DocumentProperty {

    /** 声明该属性的 Java 类。 */
    public val declaringClass: Class<*>

    /** Java 属性名（不受注解影响）。 */
    public val name: String

    /** 泛型类型。 */
    public val type: Type

    /** 文档字段名，已应用 [DocumentName]。 */
    public val documentName: String

    /** 字段上合并后的注解。 */
    public val annotations: List<Annotation>

    /**
     * 获取指定注解，支持 `@AliasFor` 别名解析。
     *
     * @param annotationClass 注解类型
     * @return 注解实例；不存在时返回 `null`
     */
    public fun <A : Annotation> getAnnotation(annotationClass: Class<A>): A?

    /**
     * 是否包含指定注解。
     *
     * @param annotationClass 注解类型
     */
    public fun hasAnnotation(annotationClass: Class<out Annotation>): Boolean

    /**
     * 是否被 [DocumentIgnore] 标记。
     */
    public fun isIgnored(): Boolean = hasAnnotation(DocumentIgnore::class.java)

    /**
     * 获取属性的原始类型。
     */
    public fun getRawType(): Class<*>
}
