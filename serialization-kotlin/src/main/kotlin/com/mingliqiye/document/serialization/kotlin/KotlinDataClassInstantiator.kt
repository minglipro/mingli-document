package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.serialization.InstantiationContext
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.kotlin
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaConstructor
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

    private val cache = ConcurrentHashMap<Class<*>, DataClassMetadata>()

    override fun canInstantiate(type: Class<*>): Boolean = type.kotlin.isData

    override fun instantiate(
        type: Class<*>,
        node: ObjectNode,
        context: InstantiationContext,
    ): Any? {
        if (!type.kotlin.isData) {
            return null
        }
        val metadata = cache.computeIfAbsent(type, ::createMetadata)
        return if (metadata.directConstruction) {
            instantiateDirect(metadata, node, context, type)
        } else {
            instantiateWithDefaults(metadata, node, context, type)
        }
    }

    private fun instantiateDirect(
        metadata: DataClassMetadata,
        node: ObjectNode,
        context: InstantiationContext,
        type: Class<*>,
    ): Any {
        val arguments = arrayOfNulls<Any?>(metadata.parameters.size)
        for ((index, parameterMetadata) in metadata.parameters.withIndex()) {
            val valueNode = node[parameterMetadata.fieldName]
                ?: throw DocumentSerializationException(
                    "Missing required data class parameter '${parameterMetadata.parameter.name}' at ${context.path}",
                )
            arguments[index] = context.deserialize(valueNode, parameterMetadata.javaType)
        }
        return try {
            val constructor = requireNotNull(metadata.javaConstructor) {
                "Data class ${type.name} has no Java constructor"
            }
            constructor.newInstance(*arguments)
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot instantiate data class ${type.name}", e)
        }
    }

    private fun instantiateWithDefaults(
        metadata: DataClassMetadata,
        node: ObjectNode,
        context: InstantiationContext,
        type: Class<*>,
    ): Any {
        val arguments = LinkedHashMap<KParameter, Any?>()
        for (parameterMetadata in metadata.parameters) {
            val parameter = parameterMetadata.parameter
            if (parameterMetadata.ignored) {
                continue
            }
            val valueNode = node[parameterMetadata.fieldName]
            if (valueNode == null) {
                if (parameter.isOptional) {
                    continue
                }
                throw DocumentSerializationException(
                    "Missing required data class parameter '${parameter.name}' at ${context.path}",
                )
            }
            arguments[parameter] = context.deserialize(valueNode, parameterMetadata.javaType)
        }
        return try {
            metadata.constructor.callBy(arguments)
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot instantiate data class ${type.name}", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createMetadata(type: Class<*>): DataClassMetadata {
        val kClass = type.kotlin
        val constructor = kClass.primaryConstructor as KFunction<Any>?
            ?: throw DocumentSerializationException("Data class ${type.name} has no primary constructor")
        constructor.isAccessible = true
        val javaConstructor = constructor.javaConstructor?.apply { isAccessible = true }
        val properties = kClass.memberProperties.associateBy { it.name }
        val parameters = constructor.parameters
            .filter { it.kind == KParameter.Kind.VALUE }
            .map { parameter ->
                val propertyName = parameter.name
                    ?: throw DocumentSerializationException("Data class ${type.name} has unnamed parameter")
                val property = properties[propertyName]
                DataClassParameterMetadata(
                    parameter = parameter,
                    fieldName = documentName(type, property, parameter, propertyName),
                    ignored = isIgnored(type, property, propertyName),
                    javaType = parameter.type.javaType,
                )
            }
        val directConstruction = javaConstructor != null &&
            parameters.none {
                it.ignored || it.parameter.isOptional
            }
        return DataClassMetadata(constructor, javaConstructor, directConstruction, parameters)
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
