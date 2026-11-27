package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.serialization.PropertyMetadataResolver
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.kotlin
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Kotlin 属性元数据解析器。
 *
 * 读取 Kotlin 反射可见、但 Java 反射不可见的属性注解；
 * 同时合并主构造参数上的注解（例如 `@DocumentName` 未写 use-site target 时）。
 */
internal object KotlinPropertyMetadataResolver : PropertyMetadataResolver {

    private val cache = ConcurrentHashMap<Class<*>, Map<String, List<Annotation>>>()

    override fun resolve(type: Class<*>, propertyName: String): List<Annotation>? = cache.computeIfAbsent(type, ::resolveType)[propertyName]

    private fun resolveType(type: Class<*>): Map<String, List<Annotation>> {
        val parameterAnnotations = type.kotlin.primaryConstructor
            ?.parameters
            ?.mapNotNull { parameter -> parameter.name?.let { it to parameter.annotations } }
            ?.toMap()
            .orEmpty()
        val result = LinkedHashMap<String, List<Annotation>>()
        for (property in type.kotlin.memberProperties) {
            val annotations = LinkedHashSet<Annotation>()
            annotations.addAll(property.annotations)
            annotations.addAll(property.getter.annotations)
            annotations.addAll(parameterAnnotations[property.name].orEmpty())
            if (annotations.isNotEmpty()) {
                result[property.name] = annotations.toList()
            }
        }
        return result
    }
}
