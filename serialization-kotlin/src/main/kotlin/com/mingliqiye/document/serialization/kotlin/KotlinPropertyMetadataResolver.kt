package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.serialization.PropertyMetadataResolver
import java.beans.IntrospectionException
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.kotlin
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

/**
 * Kotlin 属性元数据解析器。
 *
 * 读取 Kotlin 反射可见、但 Java 反射不可见的属性注解，并合并主构造参数上的注解
 * （例如 `@DocumentName` 未写 use-site target 时）。
 *
 * 额外处理 value class 属性导致的 JVM 访问器名字改写（mangling）：
 * `val id: UserId` 编译出的 getter 是 `getId-ejSxcig`，JavaBeans 会把它当作
 * 属性 `id-ejSxcig`。这里把这类属性映射回 Kotlin 属性名 `id`，并补充一个
 * 合成 `@DocumentName("id")`，让序列化写出的字段名与 data class 构造参数名一致。
 * 合成的 [DocumentName] 也是 value class 属性注解（`@DocumentIgnore`、
 * `@DocumentDateTimeFormat` 等）在序列化路径上的入口。
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
        val properties: Collection<KProperty1<out Any, *>> = try {
            type.kotlin.memberProperties
        } catch (_: Throwable) {
            emptyList()
        }
        val result = LinkedHashMap<String, List<Annotation>>()
        for (property in properties) {
            val annotations = LinkedHashSet<Annotation>()
            annotations.addAll(property.annotations)
            annotations.addAll(property.getter.annotations)
            annotations.addAll(parameterAnnotations[property.name].orEmpty())
            property.javaField?.annotations?.let(annotations::addAll)
            if (annotations.isNotEmpty()) {
                result[property.name] = annotations.toList()
            }
        }
        for ((beanPropertyName, kotlinPropertyName) in mangledPropertyNames(type, properties)) {
            val annotations = LinkedHashSet<Annotation>(result[kotlinPropertyName].orEmpty())
            if (annotations.none { it is DocumentName }) {
                annotations.add(DocumentName(kotlinPropertyName))
            }
            result[beanPropertyName] = annotations.toList()
        }
        return result
    }

    /**
     * 计算被 value class 访问器改写（mangled）的 JavaBeans 属性名到 Kotlin 属性名的映射。
     */
    private fun mangledPropertyNames(
        type: Class<*>,
        properties: Collection<KProperty1<out Any, *>>,
    ): Map<String, String> {
        if (properties.isEmpty()) {
            return emptyMap()
        }
        val javaPropertyNames = javaBeanPropertyNames(type)
        if (javaPropertyNames.isEmpty()) {
            return emptyMap()
        }
        val kotlinPropertyNames = properties.map { it.name }.sortedByDescending { it.length }
        val result = LinkedHashMap<String, String>()
        for (javaPropertyName in javaPropertyNames) {
            if (javaPropertyName in kotlinPropertyNames) {
                continue
            }
            val kotlinName = kotlinPropertyNames.firstOrNull { candidate ->
                javaPropertyName.length > candidate.length + 1 &&
                    javaPropertyName.startsWith(candidate) &&
                    javaPropertyName[candidate.length] == '-'
            } ?: continue
            result[javaPropertyName] = kotlinName
        }
        return result
    }

    private fun javaBeanPropertyNames(type: Class<*>): List<String> {
        val descriptors: Array<PropertyDescriptor> = try {
            Introspector.getBeanInfo(type, Any::class.java).propertyDescriptors
        } catch (_: IntrospectionException) {
            return emptyList()
        }
        return descriptors.map { it.name }
    }
}
