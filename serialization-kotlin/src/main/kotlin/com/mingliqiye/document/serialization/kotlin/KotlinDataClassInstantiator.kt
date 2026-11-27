package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import com.mingliqiye.document.serialization.DocumentValueReader
import com.mingliqiye.document.serialization.InstantiationContext
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.StreamingInstantiationContext
import com.mingliqiye.document.serialization.TokenInstantiationContext
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
    private val dataClassTypes = ConcurrentHashMap<Class<*>, Boolean>()

    override fun canInstantiate(type: Class<*>): Boolean = dataClassTypes.computeIfAbsent(type) { it.kotlin.isData }

    override fun instantiate(
        reader: DocumentValueReader,
        type: Class<*>,
        context: StreamingInstantiationContext,
    ): Any? {
        if (!type.kotlin.isData) {
            return null
        }
        val metadata = cache.computeIfAbsent(type, ::createMetadata)
        return try {
            instantiateStreaming(reader, metadata, context, type)
        } catch (e: DocumentSerializationException) {
            throw e
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot instantiate data class ${type.name}", e)
        }
    }

    override fun instantiate(
        reader: DocumentTokenReader,
        type: Class<*>,
        context: TokenInstantiationContext,
    ): Any? {
        if (!type.kotlin.isData) {
            return null
        }
        val metadata = cache.computeIfAbsent(type, ::createMetadata)
        return try {
            instantiateTokens(reader, metadata, context, type)
        } catch (e: DocumentSerializationException) {
            throw e
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot instantiate data class ${type.name}", e)
        }
    }

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

    private fun instantiateStreaming(
        reader: DocumentValueReader,
        metadata: DataClassMetadata,
        context: StreamingInstantiationContext,
        type: Class<*>,
    ): Any {
        val arguments = LinkedHashMap<KParameter, Any?>()
        val argumentArray = arrayOfNulls<Any?>(metadata.parameters.size)
        val seen = BooleanArray(metadata.parameters.size)

        reader.beginObject()
        while (reader.hasNextField()) {
            val fieldName = reader.readFieldName()
            val entry = metadata.parametersByField[fieldName]
            if (entry == null) {
                reader.skipValue()
                continue
            }
            val (index, parameterMetadata) = entry
            if (parameterMetadata.ignored) {
                reader.skipValue()
                continue
            }
            val value = context.readValue(reader, parameterMetadata.javaType, parameterMetadata.property)
            arguments[parameterMetadata.parameter] = value
            argumentArray[index] = value
            seen[index] = true
        }
        reader.endObject()

        if (metadata.directConstruction) {
            for (index in seen.indices) {
                if (!seen[index]) {
                    throw DocumentSerializationException(
                        "Missing required data class parameter '${metadata.parameters[index].parameter.name}' at ${context.path}",
                    )
                }
            }
            val constructor = requireNotNull(metadata.javaConstructor) {
                "Data class ${type.name} has no Java constructor"
            }
            return constructor.newInstance(*argumentArray)
        }

        for ((index, parameterMetadata) in metadata.parameters.withIndex()) {
            if (!seen[index] && !parameterMetadata.ignored && !parameterMetadata.parameter.isOptional) {
                throw DocumentSerializationException(
                    "Missing required data class parameter '${parameterMetadata.parameter.name}' at ${context.path}",
                )
            }
        }
        return metadata.constructor.callBy(arguments)
    }

    private fun instantiateTokens(
        reader: DocumentTokenReader,
        metadata: DataClassMetadata,
        context: TokenInstantiationContext,
        type: Class<*>,
    ): Any {
        if (reader.currentToken != DocumentTokenType.START_OBJECT) {
            throw DocumentSerializationException("Expected object at ${context.path}")
        }
        val arguments = LinkedHashMap<KParameter, Any?>()
        val argumentArray = arrayOfNulls<Any?>(metadata.parameters.size)
        val seen = BooleanArray(metadata.parameters.size)

        while (true) {
            when (reader.nextToken()) {
                null -> throw DocumentSerializationException("Unexpected end of object at ${context.path}")
                DocumentTokenType.END_OBJECT -> break
                DocumentTokenType.FIELD_NAME -> {
                    val fieldName = reader.textValue
                        ?: throw DocumentSerializationException("Missing field name at ${context.path}")
                    val entry = metadata.parametersByField[fieldName]
                    reader.nextToken() ?: throw DocumentSerializationException(
                        "Unexpected end of object at ${context.path}",
                    )
                    if (entry == null) {
                        context.skipValue(reader)
                        continue
                    }
                    val (index, parameterMetadata) = entry
                    if (parameterMetadata.ignored) {
                        context.skipValue(reader)
                        continue
                    }
                    val value = context.readValue(reader, parameterMetadata.javaType, parameterMetadata.property)
                    arguments[parameterMetadata.parameter] = value
                    argumentArray[index] = value
                    seen[index] = true
                }

                else -> throw DocumentSerializationException("Expected field name at ${context.path}")
            }
        }

        if (metadata.directConstruction) {
            for (index in seen.indices) {
                if (!seen[index]) {
                    throw DocumentSerializationException(
                        "Missing required data class parameter '${metadata.parameters[index].parameter.name}' at ${context.path}",
                    )
                }
            }
            val constructor = requireNotNull(metadata.javaConstructor) {
                "Data class ${type.name} has no Java constructor"
            }
            return constructor.newInstance(*argumentArray)
        }

        for ((index, parameterMetadata) in metadata.parameters.withIndex()) {
            if (!seen[index] && !parameterMetadata.ignored && !parameterMetadata.parameter.isOptional) {
                throw DocumentSerializationException(
                    "Missing required data class parameter '${parameterMetadata.parameter.name}' at ${context.path}",
                )
            }
        }
        return metadata.constructor.callBy(arguments)
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
                val annotations = collectAnnotations(type, propertyName, property, parameter)
                val dataProperty = KotlinDataProperty(
                    type,
                    propertyName,
                    parameter.type.javaType,
                    annotations,
                )
                DataClassParameterMetadata(
                    parameter = parameter,
                    fieldName = dataProperty.documentName,
                    ignored = dataProperty.isIgnored(),
                    javaType = parameter.type.javaType,
                    property = dataProperty,
                )
            }
        val directConstruction = javaConstructor != null &&
            parameters.none {
                it.ignored || it.parameter.isOptional
            }
        return DataClassMetadata(constructor, javaConstructor, directConstruction, parameters)
    }

    private fun collectAnnotations(
        type: Class<*>,
        propertyName: String,
        property: KProperty1<*, *>?,
        parameter: KParameter,
    ): List<Annotation> {
        val annotations = LinkedHashSet<Annotation>()
        property?.annotations?.let(annotations::addAll)
        property?.getter?.annotations?.let(annotations::addAll)
        parameter.annotations.let(annotations::addAll)
        field(type, propertyName)?.annotations?.let(annotations::addAll)
        return annotations.toList()
    }

    private fun field(type: Class<*>, name: String): Field? = try {
        type.getDeclaredField(name)
    } catch (_: NoSuchFieldException) {
        null
    }
}
