package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.serialization.DocumentProperty
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

/**
 * Kotlin data class 主构造参数对应的属性元数据。
 */
internal class KotlinDataProperty(
    override val declaringClass: Class<*>,
    override val name: String,
    override val type: Type,
    override val annotations: List<Annotation>,
) : DocumentProperty {

    override val documentName: String =
        annotations.firstOrNull { it is DocumentName }
            ?.let { (it as DocumentName).value }
            ?: name

    override fun <A : Annotation> getAnnotation(annotationClass: Class<A>): A? = annotations.firstOrNull { annotationClass.isInstance(it) }?.let(annotationClass::cast)

    override fun hasAnnotation(annotationClass: Class<out Annotation>): Boolean = annotations.any { annotationClass.isInstance(it) }

    override fun isIgnored(): Boolean = annotations.any { it is DocumentIgnore }

    override fun getRawType(): Class<*> = when (val current = type) {
        is Class<*> -> current
        is ParameterizedType -> (current.rawType as? Class<*>) ?: Any::class.java
        is WildcardType -> (current.upperBounds.firstOrNull() as? Class<*>) ?: Any::class.java
        is TypeVariable<*> -> (current.bounds.firstOrNull() as? Class<*>) ?: Any::class.java
        is GenericArrayType -> Any::class.java
        else -> Any::class.java
    }
}
