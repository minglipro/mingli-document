package com.mingliqiye.document.jpa.internal

import java.lang.reflect.AnnotatedElement

/**
 * 属性注解来源：优先读取主成员（字段或 getter），缺失时回退到次成员。
 *
 * 属性访问策略下主成员是 getter、次成员是字段；字段访问策略下相反。这样既符合
 * JPA 的访问策略语义，也兼容 Kotlin 中注解落在字段上的常见写法。
 */
internal class JpaAnnotationSource(
    private val primary: AnnotatedElement?,
    private val secondary: AnnotatedElement?,
) {

    /** 读取注解，主成员优先。 */
    fun <A : Annotation> annotation(type: Class<A>): A? = primary?.getAnnotation(type) ?: secondary?.getAnnotation(type)

    /**
     * 读取可重复注解的全部实例（如 `@Convert`/`@Converts`），主成员优先。
     *
     * 主成员上存在该注解（或容器注解）时只返回主成员的结果，不再合并次成员，
     * 避免同一属性在字段与 getter 同时标注时重复应用。
     */
    fun <A : Annotation> annotations(type: Class<A>): List<A> {
        val primaryAnnotations = primary?.getAnnotationsByType(type)?.toList().orEmpty()
        if (primaryAnnotations.isNotEmpty()) {
            return primaryAnnotations
        }
        return secondary?.getAnnotationsByType(type)?.toList().orEmpty()
    }

    /** 是否存在指定注解。 */
    fun isPresent(type: Class<out Annotation>): Boolean = annotation(type) != null
}
