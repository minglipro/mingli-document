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
import java.lang.reflect.Constructor
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

/**
 * Kotlin data class / object 实例化策略。
 *
 * - data class：使用主构造器反序列化，构造参数名对应文档字段名，支持
 *   `@DocumentName` 重命名、默认参数、嵌套 data class、完整泛型集合与
 *   value class 参数；
 * - 无 `var` 属性的 Kotlin object（含 `data object`）：复用 `INSTANCE` 单例，
 *   用于 sealed class / sealed interface 多态反序列化；
 * - 其它类型返回 `null`，由 Mapper 回退到默认实现。
 *
 * value class 参数会让编译器生成带 `DefaultConstructorMarker` 的 synthetic
 * 构造器，`kotlin-reflect` 的 `javaConstructor` 会指向它。这里按"构造参数 JVM
 * 类型"重新定位真正的完整构造器与默认参数 synthetic 构造器，避免直接调用
 * synthetic 构造器导致参数个数错误。
 */
internal object KotlinDataClassInstantiator : ObjectInstantiator {

    private enum class Kind {
        DATA_CLASS,
        SINGLETON,
        NONE,
    }

    private val cache = ConcurrentHashMap<Class<*>, DataClassMetadata>()
    private val kinds = ConcurrentHashMap<Class<*>, Kind>()

    override fun canInstantiate(type: Class<*>): Boolean = kindOf(type) != Kind.NONE

    override fun instantiate(
        reader: DocumentValueReader,
        type: Class<*>,
        context: StreamingInstantiationContext,
    ): Any? {
        when (kindOf(type)) {
            Kind.NONE -> return null
            Kind.SINGLETON -> {
                val instance = singletonOf(type, context.path)
                consumeObject(reader)
                return instance
            }

            Kind.DATA_CLASS -> Unit
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
        when (kindOf(type)) {
            Kind.NONE -> return null
            Kind.SINGLETON -> {
                val instance = singletonOf(type, context.path)
                if (reader.currentToken != DocumentTokenType.START_OBJECT) {
                    throw DocumentSerializationException(
                        "Expected object for Kotlin object ${type.name} at ${context.path} " +
                            "but was ${reader.currentToken}",
                    )
                }
                context.skipValue(reader)
                return instance
            }

            Kind.DATA_CLASS -> Unit
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
        when (kindOf(type)) {
            Kind.NONE -> return null
            Kind.SINGLETON -> return singletonOf(type, context.path)
            Kind.DATA_CLASS -> Unit
        }
        val metadata = cache.computeIfAbsent(type, ::createMetadata)
        return try {
            instantiateNode(metadata, node, context, type)
        } catch (e: DocumentSerializationException) {
            throw e
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot instantiate data class ${type.name}", e)
        }
    }

    private fun kindOf(type: Class<*>): Kind = kinds.computeIfAbsent(type) {
        when {
            KotlinObjects.instanceOf(type) != null ->
                if (KotlinObjects.statelessInstanceOf(type) != null) Kind.SINGLETON else Kind.NONE

            type.kotlin.isData -> Kind.DATA_CLASS
            else -> Kind.NONE
        }
    }

    private fun singletonOf(type: Class<*>, path: String): Any = KotlinObjects.statelessInstanceOf(type)
        ?: throw DocumentSerializationException("Kotlin object ${type.name} has no singleton instance at $path")

    private fun consumeObject(reader: DocumentValueReader) {
        reader.beginObject()
        while (reader.hasNextField()) {
            reader.readFieldName()
            reader.skipValue()
        }
        reader.endObject()
    }

    private fun instantiateStreaming(
        reader: DocumentValueReader,
        metadata: DataClassMetadata,
        context: StreamingInstantiationContext,
        type: Class<*>,
    ): Any {
        val values = arrayOfNulls<Any?>(metadata.parameterCount)
        val present = BooleanArray(metadata.parameterCount)

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
            values[index] = context.readValue(reader, parameterMetadata.javaType, parameterMetadata.property)
            present[index] = true
        }
        reader.endObject()

        failOnMissingParameters(metadata, present, context.path)
        return construct(metadata, values, present, type, context.path)
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
        val values = arrayOfNulls<Any?>(metadata.parameterCount)
        val present = BooleanArray(metadata.parameterCount)

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
                    values[index] = context.readValue(reader, parameterMetadata.javaType, parameterMetadata.property)
                    present[index] = true
                }

                else -> throw DocumentSerializationException("Expected field name at ${context.path}")
            }
        }

        failOnMissingParameters(metadata, present, context.path)
        return construct(metadata, values, present, type, context.path)
    }

    private fun instantiateNode(
        metadata: DataClassMetadata,
        node: ObjectNode,
        context: InstantiationContext,
        type: Class<*>,
    ): Any {
        val values = arrayOfNulls<Any?>(metadata.parameterCount)
        val present = BooleanArray(metadata.parameterCount)
        for ((index, parameterMetadata) in metadata.parameters.withIndex()) {
            if (parameterMetadata.ignored) {
                continue
            }
            val valueNode = node[parameterMetadata.fieldName]
            if (valueNode == null) {
                if (parameterMetadata.parameter.isOptional) {
                    continue
                }
                throw DocumentSerializationException(
                    "Missing required data class parameter '${parameterMetadata.parameter.name}' at ${context.path}",
                )
            }
            values[index] = context.deserialize(valueNode, parameterMetadata.javaType)
            present[index] = true
        }
        return construct(metadata, values, present, type, context.path)
    }

    private fun failOnMissingParameters(
        metadata: DataClassMetadata,
        present: BooleanArray,
        path: String,
    ) {
        for ((index, parameterMetadata) in metadata.parameters.withIndex()) {
            if (!present[index] && !parameterMetadata.ignored && !parameterMetadata.parameter.isOptional) {
                throw DocumentSerializationException(
                    "Missing required data class parameter '${parameterMetadata.parameter.name}' at $path",
                )
            }
        }
    }

    /**
     * 按"完整构造器 → 默认参数 synthetic 构造器 → 完整构造器补默认值"的顺序实例化。
     *
     * 默认参数通过 Kotlin 生成的 `(参数..., mask: Int..., DefaultConstructorMarker)`
     * 构造器传入 mask 位，避免 `kotlin-reflect` 在 value class 参数场景下调用失败。
     */
    private fun construct(
        metadata: DataClassMetadata,
        values: Array<Any?>,
        present: BooleanArray,
        type: Class<*>,
        path: String,
    ): Any {
        val allPresent = metadata.parameters.indices.all { present[it] } &&
            metadata.parameters.none { it.ignored }
        val fullConstructor = metadata.javaConstructor
        if (fullConstructor != null && allPresent) {
            return invokeConstructor(fullConstructor, values, type, path)
        }
        val defaultsConstructor = metadata.defaultsConstructor
        if (defaultsConstructor != null) {
            val total = metadata.parameterCount + metadata.maskCount + 1
            val arguments = arrayOfNulls<Any?>(total)
            val masks = IntArray(metadata.maskCount)
            for (index in 0 until metadata.parameterCount) {
                val parameterMetadata = metadata.parameters[index]
                if (present[index] && !parameterMetadata.ignored) {
                    arguments[index] = values[index]
                    continue
                }
                if (parameterMetadata.parameter.isOptional) {
                    masks[index / 32] = masks[index / 32] or (1 shl (index % 32))
                }
                arguments[index] = defaultValueOf(parameterMetadata.jvmType)
            }
            for (maskIndex in 0 until metadata.maskCount) {
                arguments[metadata.parameterCount + maskIndex] = masks[maskIndex]
            }
            arguments[total - 1] = null
            return invokeConstructor(defaultsConstructor, arguments, type, path)
        }
        if (fullConstructor != null) {
            val arguments = values.copyOf()
            for (index in 0 until metadata.parameterCount) {
                if (!present[index]) {
                    arguments[index] = defaultValueOf(metadata.parameters[index].jvmType)
                }
            }
            return invokeConstructor(fullConstructor, arguments, type, path)
        }
        throw DocumentSerializationException(
            "Cannot instantiate data class ${type.name}: no usable constructor at $path",
        )
    }

    private fun invokeConstructor(
        constructor: Constructor<*>,
        arguments: Array<Any?>,
        type: Class<*>,
        path: String,
    ): Any = try {
        constructor.newInstance(*arguments)
    } catch (e: DocumentSerializationException) {
        throw e
    } catch (e: Exception) {
        throw DocumentSerializationException(
            "Cannot invoke constructor of data class ${type.name} at $path",
            e,
        )
    }

    private fun defaultValueOf(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Character.TYPE -> '\u0000'
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0.0f
        java.lang.Double.TYPE -> 0.0
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun createMetadata(type: Class<*>): DataClassMetadata {
        val kClass = type.kotlin
        val constructor = kClass.primaryConstructor as KFunction<Any>?
            ?: throw DocumentSerializationException("Data class ${type.name} has no primary constructor")
        constructor.isAccessible = true
        val properties = kClass.memberProperties.associateBy { it.name }
        val parameters = constructor.parameters
            .filter { it.kind == KParameter.Kind.VALUE }
            .map { parameter ->
                val propertyName = parameter.name
                    ?: throw DocumentSerializationException("Data class ${type.name} has unnamed parameter")
                val property = properties[propertyName]
                val annotations = collectAnnotations(type, propertyName, property, parameter)
                val javaType = try {
                    parameter.type.toConstructionJavaType()
                } catch (e: DocumentSerializationException) {
                    throw DocumentSerializationException(
                        "Unsupported parameter '$propertyName' of data class ${type.name}: ${e.message}",
                        e,
                    )
                }
                val dataProperty = KotlinDataProperty(
                    type,
                    propertyName,
                    javaType,
                    annotations,
                )
                DataClassParameterMetadata(
                    parameter = parameter,
                    fieldName = dataProperty.documentName,
                    ignored = dataProperty.isIgnored(),
                    javaType = javaType,
                    jvmType = rawJavaClass(javaType),
                    property = dataProperty,
                )
            }
        val expectedTypes = parameters.map { it.jvmType }.toTypedArray()
        val javaConstructor = findValueConstructor(type, expectedTypes)
            ?: constructor.javaConstructor?.takeIf { it.parameterCount == parameters.size }?.apply { isAccessible = true }
        val hasDefaultParameters = parameters.any { it.parameter.isOptional }
        val maskCount = (parameters.size + 31) / 32
        val defaultsConstructor = if (hasDefaultParameters) {
            findDefaultsConstructor(type, expectedTypes, maskCount)
        } else {
            null
        }
        val directConstruction = javaConstructor != null &&
            parameters.none {
                it.ignored || it.parameter.isOptional
            }
        return DataClassMetadata(javaConstructor, defaultsConstructor, directConstruction, parameters)
    }

    private fun findValueConstructor(type: Class<*>, expectedTypes: Array<Class<*>>): Constructor<*>? = type.declaredConstructors.firstOrNull { constructor ->
        constructor.parameterCount == expectedTypes.size &&
            constructor.parameterTypes.indices.all { constructor.parameterTypes[it] == expectedTypes[it] }
    }?.apply { isAccessible = true }

    private fun findDefaultsConstructor(
        type: Class<*>,
        expectedTypes: Array<Class<*>>,
        maskCount: Int,
    ): Constructor<*>? = type.declaredConstructors.firstOrNull { constructor ->
        if (constructor.parameterCount != expectedTypes.size + maskCount + 1) {
            return@firstOrNull false
        }
        val parameterTypes = constructor.parameterTypes
        val markerIndex = parameterTypes.size - 1
        if (parameterTypes[markerIndex].name != DEFAULT_CONSTRUCTOR_MARKER) {
            return@firstOrNull false
        }
        expectedTypes.indices.all { parameterTypes[it] == expectedTypes[it] } &&
            (0 until maskCount).all {
                parameterTypes[expectedTypes.size + it] == java.lang.Integer.TYPE
            }
    }?.apply { isAccessible = true }

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

    private const val DEFAULT_CONSTRUCTOR_MARKER = "kotlin.jvm.internal.DefaultConstructorMarker"
}
