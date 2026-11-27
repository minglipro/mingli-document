package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.serialization.InstantiationContext
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.lang.reflect.Field
import kotlin.jvm.kotlin
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

/**
 * Kotlin data class 实例化策略。
 *
 * 对 `data class` 使用主构造器反序列化；构造参数名对应文档字段名，
 * 支持 `@DocumentName` 重命名、默认参数、嵌套 data class 与泛型集合。
 *
 * 非 data class 返回 `null`，由 Mapper 回退到默认无参构造器。
 */
internal object KotlinDataClassInstantiator : ObjectInstantiator {

    override fun instantiate(
        type: Class<*>,
        node: ObjectNode,
        context: InstantiationContext,
    ): Any? {
        val kClass = type.kotlin
        if (!kClass.isData) {
            return null
        }
        val constructor = kClass.primaryConstructor ?: return null
        constructor.isAccessible = true
        val arguments = LinkedHashMap<KParameter, Any?>()

        for (parameter in constructor.parameters) {
            if (parameter.kind != KParameter.Kind.VALUE) {
                continue
            }
            val propertyName = parameter.name ?: continue
            val property = kClass.memberProperties.firstOrNull { it.name == propertyName }
            if (isIgnored(type, property, propertyName)) {
                continue
            }
            val fieldName = documentName(type, property, parameter, propertyName)
            val valueNode = node[fieldName]
            if (valueNode == null) {
                if (parameter.isOptional) {
                    continue
                }
                throw DocumentSerializationException(
                    "Missing required data class parameter '$propertyName' at ${context.path}",
                )
            }
            arguments[parameter] = context.deserialize(valueNode, parameter.type.javaType)
        }

        return try {
            constructor.callBy(arguments)
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot instantiate data class ${type.name}", e)
        }
    }

    private fun documentName(
        type: Class<*>,
        property: KProperty1<*, *>?,
        parameter: KParameter,
        fallback: String,
    ): String {
        property?.annotations?.filterIsInstance<DocumentName>()?.firstOrNull()?.let { return it.value }
        property?.getter?.annotations?.filterIsInstance<DocumentName>()?.firstOrNull()?.let { return it.value }
        parameter.annotations.filterIsInstance<DocumentName>().firstOrNull()?.let { return it.value }
        return field(type, fallback)?.getAnnotation(DocumentName::class.java)?.value ?: fallback
    }

    private fun isIgnored(
        type: Class<*>,
        property: KProperty1<*, *>?,
        fallback: String,
    ): Boolean {
        if (property?.annotations?.any { it is DocumentIgnore } == true) {
            return true
        }
        if (property?.getter?.annotations?.any { it is DocumentIgnore } == true) {
            return true
        }
        return field(type, fallback)?.isAnnotationPresent(DocumentIgnore::class.java) == true
    }

    private fun field(type: Class<*>, name: String): Field? = try {
        type.getDeclaredField(name)
    } catch (_: NoSuchFieldException) {
        null
    }
}
