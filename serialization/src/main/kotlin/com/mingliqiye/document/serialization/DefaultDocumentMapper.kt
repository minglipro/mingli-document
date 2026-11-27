package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.core.codec.DocumentCodec
import com.mingliqiye.document.exception.DocumentException
import java.beans.IntrospectionException
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.math.BigDecimal
import java.math.BigInteger
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.Array as ReflectArray

private const val MAX_SERIALIZATION_DEPTH = 128

/**
 * Java 实体与文档树之间的序列化/反序列化异常。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 */
class DocumentSerializationException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : DocumentException(message, throwable)

/**
 * Java 实体与 [Node] 树之间的通用映射器。
 *
 * 该模块只负责实体与树之间的转换，不绑定 JSON/YAML/XML；具体格式由
 * `format-json` 等模块负责。
 *
 * 支持的 Java 类型：
 * - 基本类型及包装类型、String、枚举、BigDecimal、BigInteger
 * - 数组、Collection、Map
 * - 标准 Java Bean（public getter/setter、public 字段，或无参构造器）
 *
 * 不支持带参数构造器的实体；嵌套泛型属性需要通过 getter/setter 的泛型信息解析。
 */
class DefaultDocumentMapper(
    /** 可选的文本格式编解码器；用于 [parse] / [format] 便捷方法。 */
    val codec: DocumentCodec? = null,
) : DocumentMapperApi {

    private val propertyCache = ConcurrentHashMap<Class<*>, List<PropertyDescriptor>>()

    /**
     * 将 Java 对象序列化为文档树。
     *
     * @param value Java 对象
     * @return 文档树
     * @throws DocumentSerializationException 序列化失败或存在循环引用时抛出
     */
    override fun toNode(value: Any?): Node = serializeValue(value, IdentityHashMap(), 0)

    /**
     * 将文档树反序列化为指定 Java 类型。
     *
     * @param node 文档树
     * @param targetType 目标类型
     * @return Java 对象
     * @throws DocumentSerializationException 反序列化失败时抛出
     */
    override fun <T> fromNode(node: Node, targetType: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return deserializeValue(node, targetType, "$") as T
    }

    /**
     * 将文档树反序列化为指定泛型类型。
     *
     * @param node 文档树
     * @param targetType 目标泛型类型，例如 List&lt;String&gt;
     * @return Java 对象
     * @throws DocumentSerializationException 反序列化失败时抛出
     */
    fun fromNode(node: Node, targetType: Type): Any? = deserializeValue(node, targetType, "$")

    /**
     * 将文档树反序列化为指定泛型类型。
     *
     * @param node 文档树
     * @param targetType 目标类型引用
     * @return Java 对象
     * @throws DocumentSerializationException 反序列化失败时抛出
     */
    override fun <T> fromNode(node: Node, targetType: DocumentTypeReference<T>): T {
        @Suppress("UNCHECKED_CAST")
        return deserializeValue(node, targetType.type, "$") as T
    }

    /**
     * 使用配置的 [codec] 将文本解析为 Java 实体。
     *
     * @param source 格式文本
     * @param targetType 目标类型
     * @return Java 实体
     * @throws DocumentSerializationException 未配置 codec 或反序列化失败时抛出
     */
    override fun <T> parse(source: String, targetType: Class<T>): T = fromNode(requireCodec().parse(source), targetType)

    /**
     * 使用配置的 [codec] 将文本解析为指定泛型类型。
     *
     * @param source 格式文本
     * @param targetType 目标类型引用
     * @return Java 实体
     * @throws DocumentSerializationException 未配置 codec 或反序列化失败时抛出
     */
    override fun <T> parse(source: String, targetType: DocumentTypeReference<T>): T = fromNode(requireCodec().parse(source), targetType)

    /**
     * 使用配置的 [codec] 将 Java 实体序列化为文本。
     *
     * @param value Java 实体
     * @return 格式文本
     * @throws DocumentSerializationException 未配置 codec 或序列化失败时抛出
     */
    override fun format(value: Any?): String = requireCodec().write(toNode(value), false)

    /**
     * 使用配置的 [codec] 将 Java 实体序列化为格式化文本。
     *
     * @param value Java 实体
     * @return 格式化文本
     * @throws DocumentSerializationException 未配置 codec 或序列化失败时抛出
     */
    override fun formatPretty(value: Any?): String = requireCodec().write(toNode(value), true)

    /**
     * 将任意对象转换为指定 Java 类型。
     *
     * @param source 源对象，可以是 Java 实体或 [Node]
     * @param destinationClass 目标类型
     * @return 目标对象
     */
    override fun <D> convert(source: Any?, destinationClass: Class<D>): D = fromNode(toNode(source), destinationClass)

    /**
     * 将任意对象转换为指定泛型类型。
     *
     * @param source 源对象，可以是 Java 实体或 [Node]
     * @param destinationType 目标类型引用
     * @return 目标对象
     */
    override fun <D> convert(source: Any?, destinationType: DocumentTypeReference<D>): D = fromNode(toNode(source), destinationType)

    private fun requireCodec(): DocumentCodec = codec ?: throw DocumentSerializationException(
        "DefaultDocumentMapper has no DocumentCodec; pass one to the constructor to use parse/format",
    )

    private fun serializeValue(
        value: Any?,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
    ): Node {
        if (depth > MAX_SERIALIZATION_DEPTH) {
            throw DocumentSerializationException("Maximum serialization depth exceeded")
        }
        return when (value) {
            null -> NullNode.INSTANCE
            is Node -> value
            is CharSequence -> StringNode.of(value.toString())
            is Char -> StringNode.of(value.toString())
            is Boolean -> BooleanNode.of(value)
            is Number -> serializeNumber(value)
            is Enum<*> -> StringNode.of(value.name)
            is Map<*, *> -> serializeMap(value, seen, depth)
            is Iterable<*> -> serializeIterable(value, seen, depth)
            is Array<*> -> serializeArray(value, seen, depth)
            else -> {
                val type = value.javaClass
                if (type.isArray) {
                    serializeReflectArray(value, seen, depth)
                } else {
                    serializeBean(value, seen, depth)
                }
            }
        }
    }

    private fun serializeNumber(value: Number): Node = when (value) {
        is BigDecimal -> NumberNode.of(value)
        is BigInteger -> NumberNode.of(value)
        is Double -> {
            require(value.isFinite()) { "NaN and Infinity cannot be serialized" }
            NumberNode.of(value)
        }

        is Float -> {
            require(value.isFinite()) { "NaN and Infinity cannot be serialized" }
            NumberNode.of(value)
        }

        else -> NumberNode.ofRaw(value.toString())
    }

    private fun serializeMap(
        map: Map<*, *>,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
    ): Node = withCycleCheck(map, seen) {
        val fields = LinkedHashMap<String, Node>(map.size)
        for ((key, value) in map) {
            fields[key?.toString() ?: "null"] = serializeValue(value, seen, depth + 1)
        }
        ObjectNode.from(fields)
    }

    private fun serializeIterable(
        iterable: Iterable<*>,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
    ): Node = withCycleCheck(iterable, seen) {
        val elements = ArrayList<Node>()
        for (element in iterable) {
            elements.add(serializeValue(element, seen, depth + 1))
        }
        ArrayNode.from(elements)
    }

    private fun serializeArray(
        array: Array<*>,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
    ): Node = withCycleCheck(array, seen) {
        val elements = ArrayList<Node>(array.size)
        for (element in array) {
            elements.add(serializeValue(element, seen, depth + 1))
        }
        ArrayNode.from(elements)
    }

    private fun serializeReflectArray(
        array: Any,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
    ): Node {
        val size = ReflectArray.getLength(array)
        val elements = ArrayList<Node>(size)
        return withCycleCheck(array, seen) {
            for (index in 0 until size) {
                elements.add(
                    serializeValue(
                        ReflectArray.get(array, index),
                        seen,
                        depth + 1,
                    ),
                )
            }
            ArrayNode.from(elements)
        }
    }

    private fun serializeBean(
        bean: Any,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
    ): Node = withCycleCheck(bean, seen) {
        val fields = LinkedHashMap<String, Node>()
        for (property in beanProperties(bean.javaClass)) {
            val reader = property.readMethod ?: continue
            if (Modifier.isStatic(reader.modifiers) || reader.parameterCount != 0) {
                continue
            }
            fields[property.name] = serializeValue(invoke(reader, bean), seen, depth + 1)
        }
        for (field in bean.javaClass.fields) {
            if (Modifier.isStatic(field.modifiers) || field.isSynthetic || fields.containsKey(field.name)) {
                continue
            }
            fields[field.name] = serializeValue(field.get(bean), seen, depth + 1)
        }
        ObjectNode.from(fields)
    }

    private fun deserializeValue(node: Node, type: Type, path: String): Any? {
        if (node is NullNode) {
            if (type is Class<*> && type.isPrimitive) {
                throw DocumentSerializationException("Cannot assign null to primitive type at $path")
            }
            return null
        }
        return when (type) {
            is Class<*> -> deserializeClass(node, type, path)
            is ParameterizedType -> deserializeParameterized(node, type, path)
            is GenericArrayType -> deserializeArray(node, type.genericComponentType, path)
            is WildcardType -> {
                val upperBound = type.upperBounds.firstOrNull() ?: Any::class.java
                deserializeValue(node, upperBound, path)
            }

            is TypeVariable<*> -> {
                val bound = type.bounds.firstOrNull() ?: Any::class.java
                deserializeValue(node, bound, path)
            }

            else -> throw DocumentSerializationException("Unsupported type at $path: $type")
        }
    }

    private fun deserializeClass(node: Node, type: Class<*>, path: String): Any? {
        if (Node::class.java.isAssignableFrom(type)) {
            return node
        }
        if (type == Any::class.java) {
            return node
        }
        return when {
            type == String::class.java || type == CharSequence::class.java -> node.asString()
            type == Char::class.java || type == Character::class.java -> {
                val value = node.asString()
                if (value.length != 1) {
                    throw DocumentSerializationException("Expected single character at $path")
                }
                value[0]
            }

            type == Boolean::class.java || type == java.lang.Boolean::class.java -> node.asBoolean()
            type == Byte::class.java || type == java.lang.Byte::class.java -> requireNumber(node, path).toByte()
            type == Short::class.java || type == java.lang.Short::class.java -> requireNumber(node, path).toShort()
            type == Int::class.java || type == java.lang.Integer::class.java -> requireNumber(node, path).toInt()
            type == Long::class.java || type == java.lang.Long::class.java -> requireNumber(node, path).toLong()
            type == Float::class.java || type == java.lang.Float::class.java -> requireNumber(node, path).toFloat()
            type == Double::class.java || type == java.lang.Double::class.java -> requireNumber(node, path).toDouble()
            type == BigDecimal::class.java -> requireNumber(node, path).value
            type == BigInteger::class.java -> requireNumber(node, path).toBigInteger()
            type == Number::class.java -> requireNumber(node, path).value
            type.isEnum -> enumValue(type, node.asString(), path)
            type.isArray -> deserializeArray(node, type.componentType, path)
            Collection::class.java.isAssignableFrom(type) -> {
                val objectArray = node.asArray()
                val collection = newCollection(type)
                for (index in 0 until objectArray.size) {
                    collection.add(
                        deserializeValue(objectArray[index], Any::class.java, "$path[$index]"),
                    )
                }
                collection
            }

            Map::class.java.isAssignableFrom(type) -> {
                val objectNode = node.asObject()
                val map = newMap(type)
                for ((key, value) in objectNode.asMap()) {
                    map[key] = deserializeValue(value, Any::class.java, "$path.$key")
                }
                map
            }

            else -> deserializeBean(node, type, path)
        }
    }

    private fun deserializeParameterized(
        node: Node,
        type: ParameterizedType,
        path: String,
    ): Any? {
        val rawType = type.rawType as? Class<*>
            ?: return deserializeValue(node, type.rawType, path)
        val actualArguments = type.actualTypeArguments
        return when {
            Collection::class.java.isAssignableFrom(rawType) -> {
                val elementType = actualArguments.firstOrNull() ?: Any::class.java
                val arrayNode = node.asArray()
                val collection = newCollection(rawType)
                for (index in 0 until arrayNode.size) {
                    collection.add(
                        deserializeValue(arrayNode[index], elementType, "$path[$index]"),
                    )
                }
                collection
            }

            Map::class.java.isAssignableFrom(rawType) -> {
                val keyType = actualArguments.getOrNull(0) ?: String::class.java
                val valueType = actualArguments.getOrNull(1) ?: Any::class.java
                val objectNode = node.asObject()
                val map = newMap(rawType)
                for ((key, value) in objectNode.asMap()) {
                    val mapKey = deserializeMapKey(key, keyType, path)
                    map[mapKey] = deserializeValue(value, valueType, "$path.$key")
                }
                map
            }

            else -> deserializeClass(node, rawType, path)
        }
    }

    private fun deserializeMapKey(key: String, type: Type, path: String): Any? {
        if (type !is Class<*>) {
            throw DocumentSerializationException("Unsupported map key type at $path: $type")
        }
        return when {
            type == String::class.java || type == CharSequence::class.java -> key
            type.isEnum -> enumValue(type, key, path)
            type == Boolean::class.java || type == java.lang.Boolean::class.java -> key.toBoolean()
            type == Byte::class.java || type == java.lang.Byte::class.java -> key.toByte()
            type == Short::class.java || type == java.lang.Short::class.java -> key.toShort()
            type == Int::class.java || type == java.lang.Integer::class.java -> key.toInt()
            type == Long::class.java || type == java.lang.Long::class.java -> key.toLong()
            type == Float::class.java || type == java.lang.Float::class.java -> key.toFloat()
            type == Double::class.java || type == java.lang.Double::class.java -> key.toDouble()
            type == BigDecimal::class.java -> BigDecimal(key)
            type == BigInteger::class.java -> BigInteger(key)
            else -> throw DocumentSerializationException("Unsupported map key type at $path: $type")
        }
    }

    private fun deserializeArray(node: Node, componentType: Type, path: String): Any {
        val arrayNode = node.asArray()
        val rawComponentType = rawClass(componentType)
        val result = ReflectArray.newInstance(rawComponentType, arrayNode.size)
        for (index in 0 until arrayNode.size) {
            ReflectArray.set(
                result,
                index,
                deserializeValue(arrayNode[index], componentType, "$path[$index]"),
            )
        }
        return result
    }

    private fun deserializeBean(node: Node, type: Class<*>, path: String): Any {
        if (!node.isObject) {
            throw DocumentSerializationException("Expected object node at $path")
        }
        val objectNode = node.asObject()
        val instance = instantiate(type)
        val handled = HashSet<String>()

        for (property in beanProperties(type)) {
            val writer = property.writeMethod ?: continue
            if (Modifier.isStatic(writer.modifiers)) {
                continue
            }
            val valueNode = objectNode[property.name] ?: continue
            val parameterType = writer.genericParameterTypes.firstOrNull() ?: property.propertyType
            invoke(writer, instance, deserializeValue(valueNode, parameterType, "$path.${property.name}"))
            handled.add(property.name)
        }

        for (field in type.fields) {
            if (Modifier.isStatic(field.modifiers) ||
                field.isSynthetic ||
                Modifier.isFinal(field.modifiers) ||
                field.name in handled
            ) {
                continue
            }
            val valueNode = objectNode[field.name] ?: continue
            field.set(
                instance,
                deserializeValue(valueNode, field.genericType, "$path.${field.name}"),
            )
        }
        return instance
    }

    private fun requireNumber(node: Node, path: String): NumberNode = node.asNumberOrNull()
        ?: throw DocumentSerializationException("Expected number node at $path")

    private fun enumValue(type: Class<*>, name: String, path: String): Any {
        val constants = type.enumConstants ?: throw DocumentSerializationException("Not an enum at $path")
        return constants.firstOrNull { (it as Enum<*>).name == name }
            ?: throw DocumentSerializationException("Unknown enum value '$name' at $path")
    }

    @Suppress("UNCHECKED_CAST")
    private fun newCollection(type: Class<*>): MutableCollection<Any?> {
        if (!type.isInterface && !Modifier.isAbstract(type.modifiers)) {
            try {
                return newInstance(type) as MutableCollection<Any?>
            } catch (_: Exception) {
                // 回退到标准集合。
            }
        }
        return when {
            Set::class.java.isAssignableFrom(type) -> LinkedHashSet()
            java.util.Queue::class.java.isAssignableFrom(type) -> LinkedList()
            else -> ArrayList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun newMap(type: Class<*>): MutableMap<Any?, Any?> {
        if (!type.isInterface && !Modifier.isAbstract(type.modifiers)) {
            try {
                return newInstance(type) as MutableMap<Any?, Any?>
            } catch (_: Exception) {
                // 回退到 LinkedHashMap。
            }
        }
        return LinkedHashMap()
    }

    private fun instantiate(type: Class<*>): Any = try {
        newInstance(type)
    } catch (e: Exception) {
        throw DocumentSerializationException("Cannot instantiate ${type.name}", e)
    }

    private fun newInstance(type: Class<*>): Any {
        val constructor = type.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    private fun invoke(method: Method, target: Any, vararg args: Any?): Any? = try {
        method.isAccessible = true
        method.invoke(target, *args)
    } catch (e: Exception) {
        throw DocumentSerializationException("Cannot invoke ${method.name}", e)
    }

    private fun beanProperties(type: Class<*>): List<PropertyDescriptor> = propertyCache.computeIfAbsent(type) {
        try {
            Introspector.getBeanInfo(type, Any::class.java).propertyDescriptors.toList()
        } catch (e: IntrospectionException) {
            throw DocumentSerializationException("Cannot introspect ${type.name}", e)
        }
    }

    private fun rawClass(type: Type): Class<*> = when (type) {
        is Class<*> -> type
        is ParameterizedType -> type.rawType as? Class<*>
            ?: throw DocumentSerializationException("Unsupported component type: $type")

        is GenericArrayType -> ReflectArray.newInstance(rawClass(type.genericComponentType), 0).javaClass
        is WildcardType -> rawClass(type.upperBounds.firstOrNull() ?: Any::class.java)
        is TypeVariable<*> -> rawClass(type.bounds.firstOrNull() ?: Any::class.java)
        else -> throw DocumentSerializationException("Unsupported component type: $type")
    }

    private inline fun <T> withCycleCheck(
        value: Any,
        seen: IdentityHashMap<Any, Boolean>,
        block: () -> T,
    ): T {
        if (seen.put(value, true) != null) {
            throw DocumentSerializationException("Circular reference detected: ${value.javaClass.name}")
        }
        return try {
            block()
        } finally {
            seen.remove(value)
        }
    }
}
