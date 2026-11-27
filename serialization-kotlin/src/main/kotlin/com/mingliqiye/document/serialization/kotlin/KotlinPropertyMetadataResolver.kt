package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.serialization.PropertyMetadataResolver
import kotlin.jvm.kotlin
import kotlin.reflect.full.memberProperties

/**
 * Kotlin 属性元数据解析器。
 *
 * 读取 Kotlin 反射可见、但 Java 反射不可见的属性注解。
 */
internal object KotlinPropertyMetadataResolver : PropertyMetadataResolver {

    override fun resolve(type: Class<*>, propertyName: String): List<Annotation>? {
        val property = type.kotlin.memberProperties.firstOrNull { it.name == propertyName } ?: return null
        val annotations = LinkedHashSet<Annotation>()
        annotations.addAll(property.annotations)
        annotations.addAll(property.getter.annotations)
        return annotations.toList().ifEmpty { null }
    }
}
