package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.annotation.DocumentNaming
import com.mingliqiye.document.annotation.DocumentNamingStrategy
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import com.mingliqiye.utils.aliasfor.AnnotationGetter
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

/**
 * [DocumentProperty] 默认实现。
 *
 * [documentName] 的解析优先级（从高到低）：
 * 1. 字段/getter 上的 [DocumentName]；
 * 2. 字段/getter 上的 [DocumentNaming]（字段级命名策略）；
 * 3. 声明类及其父类上的 [DocumentNaming]（类级命名策略；Builder 模式还会回退到
 *    封闭类上的注解）；
 * 4. 构造时传入的全局 [namingStrategy]。
 */
internal class DefaultDocumentProperty(
    override val declaringClass: Class<*>,
    override val name: String,
    override val type: Type,
    private val elements: List<AnnotatedElement>,
    private val additionalAnnotations: List<Annotation> = emptyList(),
    private val namingStrategy: PropertyNamingStrategy = PropertyNamingStrategy.AS_IS,
) : DocumentProperty {

    override val annotations: List<Annotation> =
        (elements.flatMap { it.annotations.toList() } + additionalAnnotations).distinct()

    override val documentName: String = resolveDocumentName()

    private fun resolveDocumentName(): String {
        getAnnotation(DocumentName::class.java)?.let { return it.value }
        getAnnotation(DocumentNaming::class.java)?.let {
            return it.value.toPropertyNamingStrategy().translate(name)
        }
        classNamingStrategy(declaringClass)?.let { return it.translate(name) }
        return namingStrategy.translate(name)
    }

    override fun <A : Annotation> getAnnotation(annotationClass: Class<A>): A? = additionalAnnotations.firstOrNull { annotationClass.isInstance(it) }?.let(annotationClass::cast)
        ?: elements.firstNotNullOfOrNull { AnnotationGetter.getAnnotation(it, annotationClass) }

    override fun hasAnnotation(annotationClass: Class<out Annotation>): Boolean = getAnnotation(annotationClass) != null

    override fun getRawType(): Class<*> = resolveRawType(type)

    private fun resolveRawType(type: Type): Class<*> = when (type) {
        is Class<*> -> type
        is ParameterizedType -> resolveRawType(type.rawType)
        is WildcardType -> resolveRawType(type.upperBounds.firstOrNull() ?: Any::class.java)
        is TypeVariable<*> -> resolveRawType(type.bounds.firstOrNull() ?: Any::class.java)
        is GenericArrayType ->
            java.lang.reflect.Array
                .newInstance(resolveRawType(type.genericComponentType), 0)
                .javaClass

        else -> throw DocumentSerializationException("Cannot resolve raw type from $type")
    }
}

/**
 * 把注解模块的 [DocumentNamingStrategy] 转换为 Mapper 内部的 [PropertyNamingStrategy]。
 */
internal fun DocumentNamingStrategy.toPropertyNamingStrategy(): PropertyNamingStrategy = when (this) {
    DocumentNamingStrategy.AsIsStrategy -> PropertyNamingStrategy.AS_IS
    DocumentNamingStrategy.SnakeCaseStrategy -> PropertyNamingStrategy.SNAKE_CASE
    DocumentNamingStrategy.KebabCaseStrategy -> PropertyNamingStrategy.KEBAB_CASE
    DocumentNamingStrategy.UpperCamelStrategy -> PropertyNamingStrategy.UPPER_CAMEL
    DocumentNamingStrategy.LowerCamelStrategy -> PropertyNamingStrategy.LOWER_CAMEL
}

/**
 * 解析类级 [DocumentNaming] 命名策略。
 *
 * 先查类型自身与父类链；再回退到封闭类链，覆盖 `@DocumentBuilder` 的 Builder
 * 通常声明为目标类内部类的常见写法。
 *
 * @param type 声明属性的类型
 * @return 类级命名策略；没有标注时返回 `null`
 */
internal fun classNamingStrategy(type: Class<*>): PropertyNamingStrategy? {
    namingStrategyOnHierarchy(type)?.let { return it }
    var enclosing: Class<*>? = type.enclosingClass
    while (enclosing != null) {
        namingStrategyOnHierarchy(enclosing)?.let { return it }
        enclosing = enclosing.enclosingClass
    }
    return null
}

private fun namingStrategyOnHierarchy(type: Class<*>): PropertyNamingStrategy? {
    var current: Class<*>? = type
    while (current != null && current != Any::class.java) {
        current.getAnnotation(DocumentNaming::class.java)
            ?.let { return it.value.toPropertyNamingStrategy() }
        current = current.superclass
    }
    return null
}
