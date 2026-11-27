package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentName
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
 */
internal class DefaultDocumentProperty(
    override val declaringClass: Class<*>,
    override val name: String,
    override val type: Type,
    private val elements: List<AnnotatedElement>,
    private val additionalAnnotations: List<Annotation> = emptyList(),
) : DocumentProperty {

    override val annotations: List<Annotation> =
        (elements.flatMap { it.annotations.toList() } + additionalAnnotations).distinct()

    override val documentName: String =
        getAnnotation(DocumentName::class.java)?.value ?: name

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
