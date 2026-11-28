package com.mingliqiye.document.jpa.internal

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.exception.JpaMappingException
import jakarta.persistence.EnumType
import jakarta.persistence.TemporalType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.LinkedHashMap
import java.util.UUID
import java.lang.reflect.Array as ReflectArray

/**
 * 标量属性值 <-> [Node] 转换。
 *
 * 覆盖 JPA 常用属性类型：字符串/字符、布尔、数字、`byte[]`、枚举（ORDINAL/STRING）、
 * `@Temporal` 的 `Date`/`Calendar`、`java.time` 类型、UUID，以及这些类型的集合/Map/数组
 * （用于 transient 属性、非关联集合属性与 `@ElementCollection` 元素）。
 */
internal class JpaValueConverter {

    /** 写出实体标量属性（JPA 默认枚举为 ORDINAL）。 */
    fun writeProperty(value: Any?, property: JpaProperty, path: String): Node = writeValue(
        value,
        property.declaredType,
        property.enumType,
        property.temporalType,
        EnumType.ORDINAL,
        path,
    )

    /** 读入实体标量属性（JPA 默认枚举为 ORDINAL）。 */
    fun readProperty(node: Node, property: JpaProperty, path: String): Any? = readValue(
        node,
        property.declaredType,
        property.enumType,
        property.temporalType,
        EnumType.ORDINAL,
        path,
        property.genericType,
    )

    /** 写出 transient 属性；没有 `@Enumerated` 时枚举使用名称。 */
    fun writeTransient(value: Any?, property: JpaProperty, path: String): Node = writeValue(
        value,
        property.declaredType,
        property.enumType,
        property.temporalType,
        EnumType.STRING,
        path,
    )

    /** 读入 transient 属性。 */
    fun readTransient(node: Node, property: JpaProperty, path: String): Any? = readValue(
        node,
        property.declaredType,
        property.enumType,
        property.temporalType,
        EnumType.STRING,
        path,
        property.genericType,
    )

    /** 写出 `@ElementCollection` 元素。 */
    fun writeElement(value: Any?, elementType: Class<*>, enumType: EnumType?, path: String): Node = writeValue(
        value,
        elementType,
        enumType,
        null,
        EnumType.STRING,
        path,
    )

    /** 读入 `@ElementCollection` 元素。 */
    fun readElement(node: Node, elementType: Class<*>, enumType: EnumType?, path: String): Any? = readValue(
        node,
        elementType,
        enumType,
        null,
        EnumType.STRING,
        path,
    )

    private fun writeValue(
        value: Any?,
        declaredType: Class<*>,
        enumType: EnumType?,
        temporalType: TemporalType?,
        enumFallback: EnumType,
        path: String,
    ): Node {
        if (value == null) {
            return NullNode.INSTANCE
        }
        return when (value) {
            is String -> StringNode.of(value)
            is CharSequence -> StringNode.of(value.toString())
            is Char -> StringNode.of(value.toString())
            is Boolean -> BooleanNode.of(value)
            is Byte -> NumberNode.of(value.toLong())
            is Short -> NumberNode.of(value.toLong())
            is Int -> NumberNode.of(value)
            is Long -> NumberNode.of(value)
            is Float -> NumberNode.of(value)
            is Double -> NumberNode.of(value)
            is BigDecimal -> NumberNode.of(value)
            is BigInteger -> NumberNode.of(value)
            is ByteArray -> BinaryNode.of(value)
            is UUID -> StringNode.of(value.toString())
            is Enum<*> -> writeEnum(value, enumType ?: enumFallback)
            is Date -> StringNode.of(formatDate(value, temporalType, path))
            is Calendar -> StringNode.of(formatDate(value.time, temporalType, path))
            is LocalDate, is LocalTime, is LocalDateTime, is Instant,
            is OffsetDateTime, is OffsetTime, is ZonedDateTime,
            -> StringNode.of(value.toString())
            is Collection<*> -> writeCollection(value, enumType, temporalType, enumFallback, path)
            is Map<*, *> -> writeMap(value, enumType, temporalType, enumFallback, path)
            else -> if (value.javaClass.isArray) {
                writeArray(value, enumType, temporalType, enumFallback, path)
            } else {
                throw JpaMappingException(
                    "Unsupported value type ${value.javaClass.name} (declared as ${declaredType.name}) at $path",
                )
            }
        }
    }

    private fun writeEnum(value: Enum<*>, enumType: EnumType): Node = when (enumType) {
        EnumType.STRING -> StringNode.of(value.name)
        EnumType.ORDINAL -> NumberNode.of(value.ordinal)
        else -> throw JpaMappingException("Unsupported enum strategy $enumType")
    }

    private fun formatDate(value: Date, temporalType: TemporalType?, path: String): String {
        val zone = ZoneId.systemDefault()
        // java.sql.Date/Time.toInstant() 会抛 UnsupportedOperationException，统一按毫秒构造。
        val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(value.time), zone)
        val effective = temporalType ?: naturalTemporalType(value)
        return when (effective) {
            TemporalType.DATE -> localDateTime.toLocalDate().toString()
            TemporalType.TIME -> localDateTime.toLocalTime().toString()
            TemporalType.TIMESTAMP -> localDateTime.toString()
            else -> throw JpaMappingException("Unsupported temporal type $effective at $path")
        }
    }

    private fun naturalTemporalType(value: Date): TemporalType = when (value) {
        is java.sql.Date -> TemporalType.DATE
        is java.sql.Time -> TemporalType.TIME
        else -> TemporalType.TIMESTAMP
    }

    private fun writeCollection(
        value: Collection<*>,
        enumType: EnumType?,
        temporalType: TemporalType?,
        enumFallback: EnumType,
        path: String,
    ): Node {
        val elements = ArrayList<Node>(value.size)
        var index = 0
        for (element in value) {
            elements.add(writeValue(element, Any::class.java, enumType, temporalType, enumFallback, "$path[$index]"))
            index++
        }
        return ArrayNode.from(elements)
    }

    private fun writeMap(
        value: Map<*, *>,
        enumType: EnumType?,
        temporalType: TemporalType?,
        enumFallback: EnumType,
        path: String,
    ): Node {
        val fields = LinkedHashMap<String, Node>(value.size)
        for ((key, entryValue) in value) {
            val keyText = key?.toString()
                ?: throw JpaMappingException("Map key must not be null at $path")
            fields[keyText] = writeValue(entryValue, Any::class.java, enumType, temporalType, enumFallback, "$path.$keyText")
        }
        return ObjectNode.from(fields)
    }

    private fun writeArray(
        value: Any,
        enumType: EnumType?,
        temporalType: TemporalType?,
        enumFallback: EnumType,
        path: String,
    ): Node {
        val length = ReflectArray.getLength(value)
        val elements = ArrayList<Node>(length)
        for (index in 0 until length) {
            elements.add(
                writeValue(ReflectArray.get(value, index), Any::class.java, enumType, temporalType, enumFallback, "$path[$index]"),
            )
        }
        return ArrayNode.from(elements)
    }

    private fun readValue(
        node: Node,
        declaredType: Class<*>,
        enumType: EnumType?,
        temporalType: TemporalType?,
        enumFallback: EnumType,
        path: String,
        genericType: Type? = null,
    ): Any? {
        if (node.isNull) {
            return null
        }
        val targetType = boxed(declaredType)
        if (targetType == Any::class.java) {
            return readUntyped(node, path)
        }
        if (targetType == String::class.java || targetType == CharSequence::class.java) {
            return requireString(node, path)
        }
        if (targetType == Char::class.javaObjectType) {
            val text = requireString(node, path)
            if (text.length != 1) {
                throw JpaMappingException("Expected single character at $path but was '$text'")
            }
            return text[0]
        }
        if (targetType == Boolean::class.javaObjectType) {
            return requireBoolean(node, path)
        }
        if (isNumberType(targetType)) {
            return numberTo(requireNumber(node, path), targetType, path)
        }
        if (targetType == ByteArray::class.java) {
            return requireBinary(node, path)
        }
        if (targetType == UUID::class.java) {
            val text = requireString(node, path)
            return try {
                UUID.fromString(text)
            } catch (e: IllegalArgumentException) {
                throw JpaMappingException("Invalid UUID '$text' at $path", e)
            }
        }
        if (targetType.isEnum) {
            return readEnum(node, targetType, enumType ?: enumFallback, path)
        }
        if (Date::class.java.isAssignableFrom(targetType)) {
            return readDate(node, targetType, temporalType, path)
        }
        if (Calendar::class.java.isAssignableFrom(targetType)) {
            return readCalendar(node, temporalType, path)
        }
        if (isJavaTimeType(targetType)) {
            return readJavaTime(node, targetType, path)
        }
        if (Collection::class.java.isAssignableFrom(targetType) || targetType == Iterable::class.java) {
            return readCollection(node, targetType, enumType, temporalType, enumFallback, path, genericType)
        }
        if (Map::class.java.isAssignableFrom(targetType)) {
            return readMap(node, targetType, enumType, temporalType, enumFallback, path, genericType)
        }
        if (targetType.isArray) {
            return readArray(node, targetType, enumType, temporalType, enumFallback, path)
        }
        throw JpaMappingException("Unsupported target type ${targetType.name} at $path")
    }

    private fun isNumberType(type: Class<*>): Boolean = type == Byte::class.javaObjectType ||
        type == Short::class.javaObjectType ||
        type == Int::class.javaObjectType ||
        type == Long::class.javaObjectType ||
        type == Float::class.javaObjectType ||
        type == Double::class.javaObjectType ||
        type == BigDecimal::class.java ||
        type == BigInteger::class.java ||
        type == Number::class.java

    private fun numberTo(node: NumberNode, targetType: Class<*>, path: String): Any = try {
        when (targetType) {
            Byte::class.javaObjectType -> node.toByte()
            Short::class.javaObjectType -> node.toShort()
            Int::class.javaObjectType -> node.toInt()
            Long::class.javaObjectType -> node.toLong()
            Float::class.javaObjectType -> node.toFloat()
            Double::class.javaObjectType -> node.toDouble()
            BigInteger::class.java -> node.toBigInteger()
            else -> node.toBigDecimal()
        }
    } catch (e: ArithmeticException) {
        throw JpaMappingException(
            "Number '${node.raw}' cannot be converted to ${targetType.simpleName} at $path",
            e,
        )
    }

    private fun readEnum(
        node: Node,
        targetType: Class<*>,
        enumType: EnumType,
        path: String,
    ): Any {
        val constants = targetType.enumConstants
            ?: throw JpaMappingException("Type ${targetType.name} has no enum constants at $path")
        return when (enumType) {
            EnumType.STRING -> {
                val name = requireString(node, path)
                constants.firstOrNull { (it as Enum<*>).name == name }
                    ?: throw JpaMappingException("Unknown enum constant '$name' for ${targetType.name} at $path")
            }

            EnumType.ORDINAL -> {
                val ordinal = try {
                    requireNumber(node, path).toInt()
                } catch (e: ArithmeticException) {
                    throw JpaMappingException("Enum ordinal must be an integer at $path", e)
                }
                if (ordinal < 0 || ordinal >= constants.size) {
                    throw JpaMappingException(
                        "Enum ordinal $ordinal out of range for ${targetType.name} at $path",
                    )
                }
                constants[ordinal]
            }

            else -> throw JpaMappingException("Unsupported enum strategy $enumType at $path")
        }
    }

    private fun readDate(
        node: Node,
        targetType: Class<*>,
        temporalType: TemporalType?,
        path: String,
    ): Any {
        val text = requireString(node, path)
        val zone = ZoneId.systemDefault()
        val effective = temporalType ?: when (targetType) {
            java.sql.Date::class.java -> TemporalType.DATE
            java.sql.Time::class.java -> TemporalType.TIME
            else -> TemporalType.TIMESTAMP
        }
        val localDateTime = when (effective) {
            TemporalType.DATE -> LocalDate.parse(text).atStartOfDay()
            TemporalType.TIME -> LocalDateTime.of(LocalDate.ofEpochDay(0), parseLocalTime(text, path))
            TemporalType.TIMESTAMP -> parseLocalDateTime(text, path)
            else -> throw JpaMappingException("Unsupported temporal type $effective at $path")
        }
        return when (targetType) {
            Date::class.java -> Date.from(localDateTime.atZone(zone).toInstant())
            java.sql.Date::class.java -> java.sql.Date.valueOf(localDateTime.toLocalDate())
            java.sql.Time::class.java -> java.sql.Time.valueOf(localDateTime.toLocalTime())
            java.sql.Timestamp::class.java -> java.sql.Timestamp.from(localDateTime.atZone(zone).toInstant())
            else -> throw JpaMappingException("Unsupported date target type ${targetType.name} at $path")
        }
    }

    private fun readCalendar(node: Node, temporalType: TemporalType?, path: String): Any {
        val text = requireString(node, path)
        val localDateTime = when (temporalType) {
            null, TemporalType.TIMESTAMP -> parseLocalDateTime(text, path)
            TemporalType.DATE -> LocalDate.parse(text).atStartOfDay()
            TemporalType.TIME -> LocalDateTime.of(LocalDate.ofEpochDay(0), parseLocalTime(text, path))
            else -> throw JpaMappingException("Unsupported temporal type $temporalType at $path")
        }
        return GregorianCalendar.from(localDateTime.atZone(ZoneId.systemDefault()))
    }

    private fun readJavaTime(node: Node, targetType: Class<*>, path: String): Any {
        val text = requireString(node, path)
        return try {
            when (targetType) {
                LocalDate::class.java -> LocalDate.parse(text)
                LocalTime::class.java -> LocalTime.parse(text)
                LocalDateTime::class.java -> LocalDateTime.parse(text)
                Instant::class.java -> Instant.parse(text)
                OffsetDateTime::class.java -> OffsetDateTime.parse(text)
                OffsetTime::class.java -> OffsetTime.parse(text)
                else -> ZonedDateTime.parse(text)
            }
        } catch (e: DateTimeParseException) {
            throw JpaMappingException("Cannot parse ${targetType.simpleName} value '$text' at $path", e)
        }
    }

    private fun parseLocalTime(text: String, path: String): LocalTime = try {
        LocalTime.parse(text)
    } catch (e: DateTimeParseException) {
        throw JpaMappingException("Cannot parse time value '$text' at $path", e)
    }

    private fun parseLocalDateTime(text: String, path: String): LocalDateTime = try {
        LocalDateTime.parse(text)
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.ofInstant(Instant.parse(text), ZoneId.systemDefault())
        } catch (e: DateTimeParseException) {
            throw JpaMappingException("Cannot parse timestamp value '$text' at $path", e)
        }
    }

    private fun readCollection(
        node: Node,
        targetType: Class<*>,
        enumType: EnumType?,
        temporalType: TemporalType?,
        enumFallback: EnumType,
        path: String,
        genericType: Type?,
    ): Any {
        val array = node.asArrayOrNull()
            ?: throw JpaMappingException("Expected array at $path but was ${node.type}")
        val elementType = genericArgumentTypeOf(genericType, targetType, 0)
        val target = newCollectionInstance(targetType, path)
        for (index in 0 until array.size) {
            val element = array[index]
            val value = if (elementType == null || elementType == Any::class.java) {
                readUntyped(element, "$path[$index]")
            } else {
                readValue(element, elementType, enumType, temporalType, enumFallback, "$path[$index]")
            }
            target.add(value)
        }
        return target
    }

    private fun readMap(
        node: Node,
        targetType: Class<*>,
        enumType: EnumType?,
        temporalType: TemporalType?,
        enumFallback: EnumType,
        path: String,
        genericType: Type?,
    ): Any {
        val objectNode = node.asObjectOrNull()
            ?: throw JpaMappingException("Expected object at $path but was ${node.type}")
        val valueType = genericArgumentTypeOf(genericType, targetType, 1)
        val target = newMapInstance(targetType, path)
        for ((key, entryNode) in objectNode.asMap()) {
            val value = if (valueType == null || valueType == Any::class.java) {
                readUntyped(entryNode, "$path.$key")
            } else {
                readValue(entryNode, valueType, enumType, temporalType, enumFallback, "$path.$key")
            }
            target[key] = value
        }
        return target
    }

    private fun readArray(
        node: Node,
        targetType: Class<*>,
        enumType: EnumType?,
        temporalType: TemporalType?,
        enumFallback: EnumType,
        path: String,
    ): Any {
        val array = node.asArrayOrNull()
            ?: throw JpaMappingException("Expected array at $path but was ${node.type}")
        val componentType = targetType.componentType
        val result = ReflectArray.newInstance(componentType, array.size)
        for (index in 0 until array.size) {
            val value = readValue(array[index], componentType, enumType, temporalType, enumFallback, "$path[$index]")
            ReflectArray.set(result, index, value)
        }
        return result
    }

    private fun readUntyped(node: Node, path: String): Any? = when (node) {
        is NullNode -> null
        is StringNode -> node.value
        is NumberNode -> node.value
        is BooleanNode -> node.value
        is BinaryNode -> node.asBinary()
        is ArrayNode -> node.asList().map { readUntyped(it, path) }
        is ObjectNode -> {
            val fields = LinkedHashMap<String, Any?>(node.size)
            for ((key, value) in node.asMap()) {
                fields[key] = readUntyped(value, "$path.$key")
            }
            fields
        }
    }

    private fun genericArgumentTypeOf(genericType: Type?, targetType: Class<*>, index: Int): Class<*>? {
        val parameterized = genericType as? ParameterizedType
        if (parameterized != null) {
            rawClassOf(parameterized.actualTypeArguments.getOrNull(index))?.let { return it }
        }
        return genericArgumentType(targetType, index)
    }

    private fun genericArgumentType(type: Class<*>, index: Int): Class<*>? {
        val parameterized = genericTypeOf(type) ?: return null
        return rawClassOf(parameterized.actualTypeArguments.getOrNull(index))
    }

    private fun genericTypeOf(type: Class<*>): ParameterizedType? {
        for (genericInterface in type.genericInterfaces) {
            if (genericInterface is ParameterizedType) {
                return genericInterface
            }
        }
        return type.genericSuperclass as? ParameterizedType
    }

    private fun rawClassOf(type: Type?): Class<*>? = when (type) {
        null -> null
        is Class<*> -> type
        is ParameterizedType -> rawClassOf(type.rawType)
        is WildcardType -> rawClassOf(type.upperBounds.firstOrNull())
        is TypeVariable<*> -> rawClassOf(type.bounds.firstOrNull())
        else -> null
    }

    private fun requireNumber(node: Node, path: String): NumberNode = node.asNumberOrNull()
        ?: throw JpaMappingException("Expected number at $path but was ${node.type}")

    private fun requireString(node: Node, path: String): String = node.asStringOrNull()
        ?: throw JpaMappingException("Expected string at $path but was ${node.type}")

    private fun requireBoolean(node: Node, path: String): Boolean = node.asBooleanOrNull()
        ?: throw JpaMappingException("Expected boolean at $path but was ${node.type}")

    private fun requireBinary(node: Node, path: String): ByteArray = node.asBinaryOrNull()
        ?: throw JpaMappingException("Expected binary at $path but was ${node.type}")

    private fun boxed(type: Class<*>): Class<*> {
        if (!type.isPrimitive) {
            return type
        }
        return when (type.name) {
            "boolean" -> Boolean::class.javaObjectType
            "byte" -> Byte::class.javaObjectType
            "short" -> Short::class.javaObjectType
            "int" -> Int::class.javaObjectType
            "long" -> Long::class.javaObjectType
            "float" -> Float::class.javaObjectType
            "double" -> Double::class.javaObjectType
            "char" -> Char::class.javaObjectType
            else -> type
        }
    }
}
