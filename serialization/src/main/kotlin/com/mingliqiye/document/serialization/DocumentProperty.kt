package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.utils.aliasfor.AnnotationGetter
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type

/**
 * Java 实体字段/属性的元数据，提供给转换器读取字段信息与注解。
 */
interface DocumentProperty {

    /** 声明该属性的 Java 类。 */
    val declaringClass: Class<*>

    /** Java 属性名（不受注解影响）。 */
    val name: String

    /** 泛型类型。 */
    val type: Type

    /** 文档字段名，已应用 [DocumentName]。 */
    val documentName: String

    /** 字段上合并后的注解。 */
    val annotations: List<Annotation>

    /**
     * 获取指定注解，支持 `@AliasFor` 别名解析。
     *
     * @param annotationClass 注解类型
     * @return 注解实例；不存在时返回 `null`
     */
    fun <A : Annotation> getAnnotation(annotationClass: Class<A>): A?

    /**
     * 是否包含指定注解。
     *
     * @param annotationClass 注解类型
     */
    fun hasAnnotation(annotationClass: Class<out Annotation>): Boolean

    /**
     * 是否被 [DocumentIgnore] 标记。
     */
    fun isIgnored(): Boolean = hasAnnotation(DocumentIgnore::class.java)

    /**
     * 获取属性的原始类型。
     */
    fun getRawType(): Class<*>
}

/**
 * [DocumentProperty] 默认实现。
 */
internal class DefaultDocumentProperty(
    override val declaringClass: Class<*>,
    override val name: String,
    override val type: Type,
    private val elements: List<AnnotatedElement>,
) : DocumentProperty {

    override val annotations: List<Annotation> =
        elements.flatMap { it.annotations.toList() }.distinct()

    override val documentName: String =
        getAnnotation(DocumentName::class.java)?.value ?: name

    override fun <A : Annotation> getAnnotation(annotationClass: Class<A>): A? = elements.firstNotNullOfOrNull { AnnotationGetter.getAnnotation(it, annotationClass) }

    override fun hasAnnotation(annotationClass: Class<out Annotation>): Boolean = elements.any { AnnotationGetter.getAnnotation(it, annotationClass) != null }

    override fun getRawType(): Class<*> {
        val rawType = if (type is java.lang.reflect.ParameterizedType) type.rawType else type
        require(rawType is Class<*>) { "Cannot resolve raw type from $type" }
        return rawType
    }
}
