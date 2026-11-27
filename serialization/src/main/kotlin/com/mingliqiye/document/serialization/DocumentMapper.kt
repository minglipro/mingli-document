package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentConverter
import com.mingliqiye.document.annotation.DocumentDateTimeFormat
import com.mingliqiye.document.annotation.DocumentDeserializer
import com.mingliqiye.document.annotation.DocumentNumberFormat
import com.mingliqiye.document.annotation.DocumentSerializer
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.beans.IntrospectionException
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.Array as ReflectArray

private const val MAX_SERIALIZATION_DEPTH = 128

/**
 * Java 实体与 [Node] 树之间的通用映射器基类。
 *
 * 该模块只负责实体与树之间的转换，不绑定 JSON/YAML/XML；具体格式由
 * `format-json`、`format-yaml` 等模块提供 [DocumentMapper] 子类。
 *
 * 支持的 Java 类型：
 * - 基本类型及包装类型、String、枚举、BigDecimal、BigInteger
 * - 数组、Collection、Map
 * - 标准 Java Bean（public getter/setter、public 字段，或无参构造器）
 * - 通过配置显式注册的多态基类/接口（字段声明为基类时写入/读取子类型信息）
 *
 * 不支持带参数构造器的实体；嵌套泛型属性需要通过 getter/setter 的泛型信息解析。
 */
public abstract class DocumentMapper protected constructor(
    /** 格式名称，例如 json、yaml。 */
    public val formatName: String,
    private val parseText: (String) -> Node,
    private val writeText: (Node, Boolean) -> String,
    private val serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    private val deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    private val objectInstantiator: ObjectInstantiator?,
    private val propertyMetadataResolver: PropertyMetadataResolver?,
    private val config: DocumentMapperConfig,
    private val parseBytes: ((ByteArray) -> Node)? = null,
    private val writeBytes: ((Node, Boolean) -> ByteArray)? = null,
) : DocumentMapperApi {

    /** 该 Mapper 是否使用文本文件/流的 UTF-8 语义；二进制 Mapper 覆盖为 false。 */
    protected open val usesTextFormat: Boolean = true

    /**
     * 创建不带文本编解码能力的映射器基类构造器。
     *
     * @param formatName 格式名称
     * @param parseText 文本解析函数
     * @param writeText 文本写出函数
     */
    protected constructor(
        formatName: String,
        parseText: (String) -> Node,
        writeText: (Node, Boolean) -> String,
    ) : this(formatName, parseText, writeText, emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    private val propertyCache = ConcurrentHashMap<Class<*>, List<PropertyDescriptor>>()
    private val constructorCache = ConcurrentHashMap<Class<*>, Constructor<*>>()
    private val documentPropertyCache = ConcurrentHashMap<Class<*>, ConcurrentHashMap<String, DocumentProperty>>()
    private val beanMetadataCache = ConcurrentHashMap<Class<*>, BeanMetadata>()
    private val hasSerializers = serializers.isNotEmpty()
    private val hasDeserializers = deserializers.isNotEmpty()
    private val converterCache = ConcurrentHashMap<Class<*>, Any>()
    private val propertyConverterCache = ConcurrentHashMap<DocumentProperty, Any>()
    private val noConverter = Any()

    /**
     * 将 Java 对象序列化为文档树。
     *
     * @param value Java 对象
     * @return 文档树
     * @throws DocumentSerializationException 序列化失败或存在循环引用时抛出
     */
    override fun toNode(value: Any?): Node = serializeValue(value, IdentityHashMap(), 0, null, "$")

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
    public fun fromNode(node: Node, targetType: Type): Any? = deserializeValue(node, targetType, "$")

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
     * 使用格式解析函数将文本解析为 Java 实体。
     *
     * @param source 格式文本
     * @param targetType 目标类型
     * @return Java 实体
     * @throws DocumentSerializationException 反序列化失败时抛出
     */
    override fun <T> parse(source: String, targetType: Class<T>): T {
        checkInputSize(source.length.toLong())
        createTextTokenReader(source)?.let { return fromTokens(it, targetType) }
        return fromNode(parseText(source), targetType)
    }

    /**
     * 使用格式解析函数将文本解析为指定泛型类型。
     *
     * @param source 格式文本
     * @param targetType 目标类型引用
     * @return Java 实体
     * @throws DocumentSerializationException 反序列化失败时抛出
     */
    override fun <T> parse(source: String, targetType: DocumentTypeReference<T>): T {
        checkInputSize(source.length.toLong())
        createTextTokenReader(source)?.let { return fromTokens(it, targetType) }
        return fromNode(parseText(source), targetType)
    }

    /**
     * 为文本格式创建 Token Reader。
     *
     * 返回 `null` 时回退到 Node 解析函数。
     *
     * @param source 文本内容
     * @return Token Reader 或 `null`
     */
    protected open fun createTextTokenReader(source: String): DocumentTokenReader? = null

    /**
     * 为二进制格式创建 Token Reader。
     *
     * 返回 `null` 时回退到 Node 解析函数。
     *
     * @param source 二进制内容
     * @return Token Reader 或 `null`
     */
    protected open fun createTokenReader(source: ByteArray): DocumentTokenReader? = null

    /**
     * 将字节数组解析为 Java 实体。
     *
     * @param source 二进制内容
     * @param targetType 目标类型
     * @return Java 实体
     */
    override fun <T> parse(source: ByteArray, targetType: Class<T>): T {
        checkInputSize(source.size.toLong())
        createTokenReader(source)?.let { return fromTokens(it, targetType) }
        val parser = parseBytes
            ?: throw DocumentSerializationException("$formatName mapper does not support binary parsing")
        return fromNode(parser(source), targetType)
    }

    /**
     * 将字节数组解析为指定泛型类型。
     *
     * @param source 二进制内容
     * @param targetType 目标类型引用
     * @return Java 实体
     */
    override fun <T> parse(source: ByteArray, targetType: DocumentTypeReference<T>): T {
        checkInputSize(source.size.toLong())
        createTokenReader(source)?.let { return fromTokens(it, targetType) }
        val parser = parseBytes
            ?: throw DocumentSerializationException("$formatName mapper does not support binary parsing")
        return fromNode(parser(source), targetType)
    }

    /**
     * 将 Java 实体序列化为字节数组。
     *
     * @param value Java 实体
     * @return 二进制内容
     */
    override fun formatBytes(value: Any?): ByteArray {
        val writer = writeBytes
            ?: throw DocumentSerializationException("$formatName mapper does not support binary writing")
        return writer(toNode(value), false)
    }

    /**
     * 使用格式写出函数将 Java 实体序列化为文本。
     *
     * @param value Java 实体
     * @return 格式文本
     * @throws DocumentSerializationException 序列化失败时抛出
     */
    override fun format(value: Any?): String = writeText(toNode(value), false)

    /**
     * 使用格式写出函数将 Java 实体序列化为格式化文本。
     *
     * @param value Java 实体
     * @return 格式化文本
     * @throws DocumentSerializationException 序列化失败时抛出
     */
    override fun formatPretty(value: Any?): String = writeText(toNode(value), true)

    override fun <T> parse(file: File, targetType: Class<T>): T = parse(file.toPath(), targetType)

    override fun <T> parse(file: File, targetType: DocumentTypeReference<T>): T = parse(file.toPath(), targetType)

    override fun <T> parse(path: Path, targetType: Class<T>): T = parse(path, DocumentTypeReference.of(targetType))

    override fun <T> parse(path: Path, targetType: DocumentTypeReference<T>): T {
        checkInputSize(Files.size(path))
        return parseBytesOrText(Files.readAllBytes(path), targetType)
    }

    override fun <T> parse(input: InputStream, targetType: Class<T>): T = parse(input, DocumentTypeReference.of(targetType))

    override fun <T> parse(input: InputStream, targetType: DocumentTypeReference<T>): T {
        val bytes = input.readBytes()
        checkInputSize(bytes.size.toLong())
        return parseBytesOrText(bytes, targetType)
    }

    override fun <T> parse(reader: Reader, targetType: Class<T>): T = parse(reader, DocumentTypeReference.of(targetType))

    override fun <T> parse(reader: Reader, targetType: DocumentTypeReference<T>): T {
        if (!usesTextFormat) {
            throw DocumentSerializationException("$formatName mapper does not support character streams")
        }
        val source = reader.readText()
        checkInputSize(source.length.toLong())
        return parse(source, targetType)
    }

    override fun format(value: Any?, file: File) {
        format(value, file.toPath())
    }

    override fun format(value: Any?, path: Path) {
        writeFile(path, outputBytes(value))
    }

    override fun format(value: Any?, output: OutputStream) {
        output.write(outputBytes(value))
        output.flush()
    }

    override fun format(value: Any?, writer: Writer) {
        if (!usesTextFormat) {
            throw DocumentSerializationException("$formatName mapper does not support character streams")
        }
        writer.write(format(value))
        writer.flush()
    }

    private fun <T> parseBytesOrText(bytes: ByteArray, targetType: DocumentTypeReference<T>): T {
        checkInputSize(bytes.size.toLong())
        return if (usesTextFormat) {
            parse(String(bytes, config.fileCharset), targetType)
        } else {
            parse(bytes, targetType)
        }
    }

    private fun outputBytes(value: Any?): ByteArray = if (usesTextFormat) {
        format(value).toByteArray(config.fileCharset)
    } else {
        formatBytes(value)
    }

    protected fun checkInputSize(size: Long) {
        val limit = config.maxInputSize
        if (limit > 0 && size > limit) {
            throw DocumentSerializationException("Input size $size exceeds maxInputSize $limit")
        }
    }

    private fun writeFile(path: Path, bytes: ByteArray) {
        val absolute = path.toAbsolutePath()
        val parent = absolute.parent
        if (config.createParentDirectories && parent != null) {
            Files.createDirectories(parent)
        }
        if (!config.atomicFileWrite) {
            Files.write(path, bytes)
            return
        }
        val directory = parent ?: throw DocumentSerializationException("Cannot resolve parent directory for $path")
        val temporary = Files.createTempFile(directory, ".${absolute.fileName}.", ".tmp")
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

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

    private fun serializeWithSerializer(
        serializer: DocumentBaseSerializer<*>,
        value: Any,
        property: DocumentProperty?,
        path: String,
    ): Node {
        @Suppress("UNCHECKED_CAST")
        return (serializer as DocumentBaseSerializer<Any>).serialize(
            value,
            DefaultSerializationContext(this, property, path),
        )
    }

    private fun deserializeWithDeserializer(
        deserializer: DocumentBaseDeserializer<*>,
        node: Node,
        property: DocumentProperty?,
        path: String,
    ): Any? {
        @Suppress("UNCHECKED_CAST")
        return (deserializer as DocumentBaseDeserializer<Any>).deserialize(
            node,
            DefaultDeserializationContext(this, property, path),
        )
    }

    private fun findSerializer(type: Class<*>): DocumentBaseSerializer<*>? {
        val boxed = boxedType(type)
        return serializers[type]
            ?: serializers[boxed]
            ?: serializers.entries.firstOrNull { it.key.isAssignableFrom(boxed) }?.value
    }

    private fun findDeserializer(type: Class<*>): DocumentBaseDeserializer<*>? {
        val boxed = boxedType(type)
        return deserializers[type]
            ?: deserializers[boxed]
            ?: deserializers.entries.firstOrNull { it.key.isAssignableFrom(boxed) }?.value
    }

    private fun boxedType(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        else -> type
    }

    /**
     * 解析字段级 [DocumentConverter] 注解。
     *
     * 字段级转换器优先于 Builder 注册的全局转换器。
     */
    private fun propertyConverter(property: DocumentProperty?): Any? {
        if (property == null) {
            return null
        }
        config.fieldConverter(property)?.let { return it }
        val cached = propertyConverterCache[property]
        if (cached != null) {
            return if (cached === noConverter) null else cached
        }
        val annotation = property.getAnnotation(DocumentConverter::class.java)
        if (annotation == null) {
            val dateTimeFormat = property.getAnnotation(DocumentDateTimeFormat::class.java)
            if (dateTimeFormat == null) {
                val numberFormat = property.getAnnotation(DocumentNumberFormat::class.java)
                if (numberFormat == null) {
                    propertyConverterCache[property] = noConverter
                    return null
                }
                val converter = try {
                    DocumentNumberFormatConverter(numberFormat.value, numberFormat.locale)
                } catch (e: Exception) {
                    throw DocumentSerializationException(
                        "Invalid NumberFormat '${numberFormat.value}' for " +
                            "${property.declaringClass.name}.${property.name}",
                        e,
                    )
                }
                propertyConverterCache[property] = converter
                return converter
            }
            val converter = try {
                DocumentDateTimeFormatConverter(dateTimeFormat.value, dateTimeFormat.timezone)
            } catch (e: Exception) {
                throw DocumentSerializationException(
                    "Invalid DateTimeFormat '${dateTimeFormat.value}' for " +
                        "${property.declaringClass.name}.${property.name}",
                    e,
                )
            }
            propertyConverterCache[property] = converter
            return converter
        }
        val converterType = annotation.value.java
        val converter = converterCache.computeIfAbsent(converterType) {
            instantiateConverter(property, converterType)
        }
        propertyConverterCache[property] = converter
        return converter
    }

    private fun propertySerializer(property: DocumentProperty?): DocumentBaseSerializer<*>? {
        if (property != null) {
            val annotation = property.getAnnotation(DocumentSerializer::class.java)
            if (annotation != null) {
                val instance = annotationConverter(property, annotation.value.java)
                return instance as? DocumentBaseSerializer<*>
                    ?: throw DocumentSerializationException(
                        "Serializer ${annotation.value.java.name} for " +
                            "${property.declaringClass.name}.${property.name} " +
                            "must implement DocumentBaseSerializer",
                    )
            }
        }
        val converter = propertyConverter(property) ?: return null
        return converter as? DocumentBaseSerializer<*>
    }

    private fun propertyDeserializer(property: DocumentProperty?): DocumentBaseDeserializer<*>? {
        if (property != null) {
            val annotation = property.getAnnotation(DocumentDeserializer::class.java)
            if (annotation != null) {
                val instance = annotationConverter(property, annotation.value.java)
                return instance as? DocumentBaseDeserializer<*>
                    ?: throw DocumentSerializationException(
                        "Deserializer ${annotation.value.java.name} for " +
                            "${property.declaringClass.name}.${property.name} " +
                            "must implement DocumentBaseDeserializer",
                    )
            }
        }
        val converter = propertyConverter(property) ?: return null
        return converter as? DocumentBaseDeserializer<*>
    }

    private fun annotationConverter(property: DocumentProperty, converterType: Class<*>): Any = converterCache.computeIfAbsent(converterType) {
        instantiateConverter(property, converterType)
    }

    private fun instantiateConverter(property: DocumentProperty, converterType: Class<*>): Any {
        val instance = objectInstance(converterType) ?: constructorInstance(property, converterType)
        if (instance !is DocumentBaseSerializer<*> && instance !is DocumentBaseDeserializer<*>) {
            throw DocumentSerializationException(
                "Converter ${converterType.name} for " +
                    "${property.declaringClass.name}.${property.name} must implement DocumentBaseSerializer or DocumentBaseDeserializer",
            )
        }
        return instance
    }

    private fun objectInstance(type: Class<*>): Any? {
        val field = try {
            type.getDeclaredField("INSTANCE")
        } catch (_: NoSuchFieldException) {
            return null
        }
        if (!Modifier.isStatic(field.modifiers)) {
            return null
        }
        return try {
            field.isAccessible = true
            val instance = field.get(null)
            instance.takeIf { type.isInstance(it) }
        } catch (e: ReflectiveOperationException) {
            throw DocumentSerializationException("Cannot access converter instance ${type.name}", e)
        }
    }

    private fun constructorInstance(property: DocumentProperty, type: Class<*>): Any {
        val constructor = try {
            type.getDeclaredConstructor()
        } catch (e: NoSuchMethodException) {
            throw DocumentSerializationException(
                "Converter ${type.name} for ${property.declaringClass.name}.${property.name} " +
                    "must have a no-arg constructor",
                e,
            )
        }
        return try {
            constructor.isAccessible = true
            constructor.newInstance()
        } catch (e: ReflectiveOperationException) {
            throw DocumentSerializationException(
                "Cannot instantiate converter ${type.name} for " +
                    "${property.declaringClass.name}.${property.name}",
                e,
            )
        }
    }

    /**
     * 直接从流式读取器反序列化实体，不构建完整文档树。
     *
     * @param reader 格式读取器
     * @param targetType 目标类型
     * @return 实体
     */
    public fun <T> readFrom(reader: DocumentValueReader, targetType: DocumentTypeReference<T>): T {
        @Suppress("UNCHECKED_CAST")
        return readValue(reader, targetType.type, null, "$") as T
    }

    private fun readValue(
        reader: DocumentValueReader,
        type: Type,
        property: DocumentProperty?,
        path: String,
    ): Any? {
        if (reader.tryReadNull()) {
            return null
        }
        val rawType = when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as? Class<*>
            else -> null
        }
        if (rawType != null) {
            propertyDeserializer(property)?.let { deserializer ->
                return deserializeWithDeserializer(deserializer, reader.readNode(), property, path)
            }
            if (config.hasSubtypes) {
                config.subtypeRegistry(rawType)?.let { registry ->
                    return deserializePolymorphic(reader.readNode(), registry, path, property)
                }
            }
            findDeserializer(rawType)?.let { deserializer ->
                return deserializeWithDeserializer(deserializer, reader.readNode(), property, path)
            }
            if (objectInstantiator?.canInstantiate(rawType) == true) {
                val streamed = objectInstantiator.instantiate(
                    reader,
                    rawType,
                    DefaultStreamingInstantiationContext(this, path),
                )
                if (streamed != null) {
                    return streamed
                }
                return deserializeValue(reader.readNode(), type, path, property)
            }
        }
        return when (type) {
            is Class<*> -> readClass(reader, type, path)
            is ParameterizedType -> readParameterized(reader, type, path)
            is GenericArrayType -> readReflectArray(reader, type.genericComponentType, path)
            is WildcardType -> {
                val upperBound = type.upperBounds.firstOrNull() ?: Any::class.java
                readValue(reader, upperBound, property, path)
            }

            is TypeVariable<*> -> {
                val bound = type.bounds.firstOrNull() ?: Any::class.java
                readValue(reader, bound, property, path)
            }

            else -> throw DocumentSerializationException("Unsupported type at $path: $type")
        }
    }

    internal fun readValueForInstantiator(
        reader: DocumentValueReader,
        targetType: Type,
        property: DocumentProperty?,
        path: String,
    ): Any? = readValue(reader, targetType, property, path)

    internal fun readTokenValueForInstantiator(
        reader: DocumentTokenReader,
        targetType: Type,
        property: DocumentProperty?,
        path: DecodePath,
    ): Any? = readTokenValue(reader, targetType, property, path)

    /**
     * 从 Token 流反序列化为指定类型。
     *
     * @param reader Token 读取器
     * @param targetType 目标类型
     * @return Java 实体
     */
    public fun <T> fromTokens(reader: DocumentTokenReader, targetType: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return fromTokens(reader, targetType as Type) as T
    }

    /**
     * 从 Token 流反序列化为指定泛型类型。
     *
     * @param reader Token 读取器
     * @param targetType 目标类型引用
     * @return Java 实体
     */
    public fun <T> fromTokens(reader: DocumentTokenReader, targetType: DocumentTypeReference<T>): T {
        @Suppress("UNCHECKED_CAST")
        return fromTokens(reader, targetType.type) as T
    }

    /**
     * 从 Token 流反序列化为指定 [Type]。
     *
     * @param reader Token 读取器
     * @param targetType 目标类型
     * @return Java 实体
     */
    public fun fromTokens(reader: DocumentTokenReader, targetType: Type): Any? {
        reader.nextToken() ?: throw DocumentSerializationException("Empty token stream")
        val result = readTokenValue(reader, targetType, null, DecodePath("$"))
        if (reader.nextToken() != null) {
            throw DocumentSerializationException("Unexpected trailing token content")
        }
        return result
    }

    private fun readTokenValue(
        reader: DocumentTokenReader,
        type: Type,
        property: DocumentProperty?,
        path: DecodePath,
    ): Any? {
        val token = reader.currentToken
            ?: throw DocumentSerializationException("No current token at $path")
        if (token == DocumentTokenType.NULL) {
            if (type is Class<*> && type.isPrimitive) {
                throw DocumentSerializationException("Cannot assign null to primitive type at $path")
            }
            return null
        }
        val rawType = when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as? Class<*>
            else -> null
        }
        if (rawType != null) {
            if (propertyDeserializer(property) != null) {
                return deserializeValue(TokenTreeReader.readNode(reader), type, path.toString(), property)
            }
            if (config.hasSubtypes) {
                config.subtypeRegistry(rawType)?.let { registry ->
                    return deserializePolymorphic(
                        TokenTreeReader.readNode(reader),
                        registry,
                        path.toString(),
                        property,
                    )
                }
            }
            if (hasDeserializers && findDeserializer(rawType) != null) {
                return deserializeValue(TokenTreeReader.readNode(reader), type, path.toString(), property)
            }
            val instantiator = objectInstantiator
            if (token == DocumentTokenType.START_OBJECT &&
                instantiator != null &&
                instantiator.canInstantiate(rawType)
            ) {
                val streamed = instantiator.instantiate(
                    reader,
                    rawType,
                    DefaultTokenInstantiationContext(this, path),
                )
                if (streamed != null) {
                    return streamed
                }
                return deserializeValue(TokenTreeReader.readNode(reader), type, path.toString(), property)
            }
        }
        return when (type) {
            is Class<*> -> readTokenClass(reader, type, path)
            is ParameterizedType -> readTokenParameterized(reader, type, path)
            is GenericArrayType -> readTokenArray(reader, type.genericComponentType, path)
            is WildcardType -> {
                val bound = type.upperBounds.firstOrNull() ?: Any::class.java
                readTokenValue(reader, bound, property, path)
            }

            is TypeVariable<*> -> {
                val bound = type.bounds.firstOrNull() ?: Any::class.java
                readTokenValue(reader, bound, property, path)
            }

            else -> throw DocumentSerializationException("Unsupported type at $path: $type")
        }
    }

    private fun readTokenClass(reader: DocumentTokenReader, type: Class<*>, path: DecodePath): Any? {
        if (Node::class.java.isAssignableFrom(type) || type == Any::class.java) {
            return TokenTreeReader.readNode(reader)
        }
        return when {
            type == String::class.java || type == CharSequence::class.java -> readTokenString(reader, path)
            type == Char::class.java || type == Character::class.java -> {
                val value = readTokenString(reader, path)
                if (value.length != 1) {
                    throw DocumentSerializationException("Expected single character at $path")
                }
                value[0]
            }

            type == Boolean::class.java || type == java.lang.Boolean::class.java -> readTokenBoolean(reader, path)
            type == Byte::class.java || type == java.lang.Byte::class.java -> readTokenNumber(reader, type, path)
            type == Short::class.java || type == java.lang.Short::class.java -> readTokenNumber(reader, type, path)
            type == Int::class.java || type == java.lang.Integer::class.java -> readTokenNumber(reader, type, path)
            type == Long::class.java || type == java.lang.Long::class.java -> readTokenNumber(reader, type, path)
            type == Float::class.java || type == java.lang.Float::class.java -> readTokenNumber(reader, type, path)
            type == Double::class.java || type == java.lang.Double::class.java -> readTokenNumber(reader, type, path)
            type == BigDecimal::class.java -> readTokenNumber(reader, type, path)
            type == BigInteger::class.java -> readTokenNumber(reader, type, path)
            type == ByteArray::class.java -> readTokenBinary(reader, path)
            type.isEnum -> enumValue(type, readTokenString(reader, path), path.toString())
            type.isArray -> readTokenArray(reader, type.componentType, path)
            Collection::class.java.isAssignableFrom(type) ->
                readTokenCollection(reader, Any::class.java, type, path)

            Map::class.java.isAssignableFrom(type) ->
                readTokenMap(reader, String::class.java, Any::class.java, type, path)

            else -> readTokenBean(reader, type, path)
        }
    }

    private fun readTokenParameterized(
        reader: DocumentTokenReader,
        type: ParameterizedType,
        path: DecodePath,
    ): Any? {
        val rawType = type.rawType as? Class<*>
            ?: return readTokenValue(reader, type.rawType, null, path)
        val arguments = type.actualTypeArguments
        return when {
            Collection::class.java.isAssignableFrom(rawType) ->
                readTokenCollection(reader, arguments.firstOrNull() ?: Any::class.java, rawType, path)

            Map::class.java.isAssignableFrom(rawType) ->
                readTokenMap(
                    reader,
                    arguments.getOrNull(0) ?: String::class.java,
                    arguments.getOrNull(1) ?: Any::class.java,
                    rawType,
                    path,
                )

            else -> deserializeValue(TokenTreeReader.readNode(reader), type, path.toString())
        }
    }

    private fun readTokenString(reader: DocumentTokenReader, path: DecodePath): String {
        if (reader.currentToken != DocumentTokenType.STRING) {
            throw DocumentSerializationException("Expected string at $path but was ${reader.currentToken}")
        }
        return reader.textValue ?: throw DocumentSerializationException("Missing string value at $path")
    }

    private fun readTokenBoolean(reader: DocumentTokenReader, path: DecodePath): Boolean {
        if (reader.currentToken != DocumentTokenType.BOOLEAN) {
            throw DocumentSerializationException("Expected boolean at $path but was ${reader.currentToken}")
        }
        return reader.booleanValue ?: throw DocumentSerializationException("Missing boolean value at $path")
    }

    private fun readTokenNumber(
        reader: DocumentTokenReader,
        type: Class<*>,
        path: DecodePath,
    ): Any {
        if (reader.currentToken != DocumentTokenType.NUMBER) {
            throw DocumentSerializationException("Expected number at $path but was ${reader.currentToken}")
        }
        return try {
            when (type) {
                Int::class.java, java.lang.Integer::class.java -> reader.intValue()

                Long::class.java, java.lang.Long::class.java -> reader.longValue()

                Double::class.java, java.lang.Double::class.java -> reader.doubleValue()

                BigDecimal::class.java -> reader.decimalValue()

                Byte::class.java, java.lang.Byte::class.java -> {
                    val raw = reader.textValue ?: throw DocumentSerializationException("Missing number value at $path")
                    raw.toByteOrNull() ?: BigDecimal(raw).byteValueExact()
                }

                Short::class.java, java.lang.Short::class.java -> {
                    val raw = reader.textValue ?: throw DocumentSerializationException("Missing number value at $path")
                    raw.toShortOrNull() ?: BigDecimal(raw).shortValueExact()
                }

                Float::class.java, java.lang.Float::class.java -> {
                    val raw = reader.textValue ?: throw DocumentSerializationException("Missing number value at $path")
                    raw.toFloatOrNull() ?: BigDecimal(raw).toFloat()
                }

                BigInteger::class.java -> {
                    val raw = reader.textValue ?: throw DocumentSerializationException("Missing number value at $path")
                    raw.toBigIntegerOrNull() ?: BigDecimal(raw).toBigIntegerExact()
                }

                else -> reader.decimalValue()
            }
        } catch (e: Exception) {
            throw DocumentSerializationException(
                "Cannot convert number '${reader.textValue}' to ${type.name} at $path",
                e,
            )
        }
    }

    private fun readTokenBinary(reader: DocumentTokenReader, path: DecodePath): ByteArray = when (reader.currentToken) {
        DocumentTokenType.BINARY ->
            reader.binaryValue
                ?: throw DocumentSerializationException("Missing binary value at $path")

        DocumentTokenType.STRING -> Base64.getDecoder().decode(
            reader.textValue ?: throw DocumentSerializationException("Missing binary value at $path"),
        )

        else -> throw DocumentSerializationException("Expected binary at $path but was ${reader.currentToken}")
    }

    private fun readTokenBean(reader: DocumentTokenReader, type: Class<*>, path: DecodePath): Any {
        if (reader.currentToken != DocumentTokenType.START_OBJECT) {
            throw DocumentSerializationException("Expected object at $path but was ${reader.currentToken}")
        }
        val instance = instantiate(type)
        val metadata = beanMetadata(type)
        val trackSeen = config.missingFieldPolicy != MissingFieldPolicy.KEEP_DEFAULT
        val seen: MutableSet<String>? = if (trackSeen) HashSet() else null
        while (true) {
            when (reader.nextToken()) {
                null -> throw DocumentSerializationException("Unexpected end of object at $path")
                DocumentTokenType.END_OBJECT -> break
                DocumentTokenType.FIELD_NAME -> {
                    val fieldName = reader.textValue
                        ?: throw DocumentSerializationException("Missing field name at $path")
                    val accessor = metadata.property(fieldName)
                    reader.nextToken() ?: throw DocumentSerializationException("Unexpected end of object at $path")
                    if (accessor != null && accessor.canWrite) {
                        seen?.add(fieldName)
                        val mark = path.mark()
                        path.field(fieldName)
                        val value = readTokenValue(reader, accessor.valueType, accessor.metadata, path)
                        path.truncate(mark)
                        accessor.write(instance, value)
                    } else {
                        if (config.failOnUnknownFields && fieldName !in metadata.knownFields) {
                            throw DocumentSerializationException(
                                "Unknown field '$fieldName' for ${type.name} at $path",
                            )
                        }
                        TokenTreeReader.skipValue(reader)
                    }
                }

                else -> throw DocumentSerializationException("Expected field name or end of object at $path")
            }
        }
        if (trackSeen) {
            applyMissingFields(instance, metadata, seen ?: emptySet(), path.toString())
        }
        return instance
    }

    private fun readTokenCollection(
        reader: DocumentTokenReader,
        elementType: Type,
        rawType: Class<*>,
        path: DecodePath,
    ): Any {
        if (reader.currentToken != DocumentTokenType.START_ARRAY) {
            throw DocumentSerializationException("Expected array at $path but was ${reader.currentToken}")
        }
        val collection = newCollection(rawType)
        var index = 0
        while (true) {
            when (reader.nextToken()) {
                null -> throw DocumentSerializationException("Unexpected end of array at $path")
                DocumentTokenType.END_ARRAY -> return collection
                else -> {
                    val mark = path.mark()
                    path.index(index)
                    collection.add(readTokenValue(reader, elementType, null, path))
                    path.truncate(mark)
                    index++
                }
            }
        }
    }

    private fun readTokenMap(
        reader: DocumentTokenReader,
        keyType: Type,
        valueType: Type,
        rawType: Class<*>,
        path: DecodePath,
    ): Any {
        if (reader.currentToken != DocumentTokenType.START_OBJECT) {
            throw DocumentSerializationException("Expected object at $path but was ${reader.currentToken}")
        }
        val map = newMap(rawType)
        while (true) {
            when (reader.nextToken()) {
                null -> throw DocumentSerializationException("Unexpected end of object at $path")
                DocumentTokenType.END_OBJECT -> return map
                DocumentTokenType.FIELD_NAME -> {
                    val key = deserializeMapKey(
                        reader.textValue ?: throw DocumentSerializationException("Missing field name at $path"),
                        keyType,
                        path.toString(),
                    )
                    reader.nextToken() ?: throw DocumentSerializationException("Unexpected end of object at $path")
                    val mark = path.mark()
                    path.field(key.toString())
                    map[key] = readTokenValue(reader, valueType, null, path)
                    path.truncate(mark)
                }

                else -> throw DocumentSerializationException("Expected field name or end of object at $path")
            }
        }
    }

    private fun readTokenArray(
        reader: DocumentTokenReader,
        componentType: Type,
        path: DecodePath,
    ): Any {
        if (reader.currentToken != DocumentTokenType.START_ARRAY) {
            throw DocumentSerializationException("Expected array at $path but was ${reader.currentToken}")
        }
        val values = ArrayList<Any?>()
        while (true) {
            when (reader.nextToken()) {
                null -> throw DocumentSerializationException("Unexpected end of array at $path")
                DocumentTokenType.END_ARRAY -> {
                    val componentClass = rawClass(componentType)
                    val result = ReflectArray.newInstance(componentClass, values.size)
                    for (index in values.indices) {
                        ReflectArray.set(result, index, values[index])
                    }
                    return result
                }

                else -> {
                    val mark = path.mark()
                    path.index(values.size)
                    values.add(readTokenValue(reader, componentType, null, path))
                    path.truncate(mark)
                }
            }
        }
    }

    private fun readClass(reader: DocumentValueReader, type: Class<*>, path: String): Any? {
        if (Node::class.java.isAssignableFrom(type) || type == Any::class.java) {
            return reader.readNode()
        }
        return when {
            type == ByteArray::class.java -> binaryValue(reader.readNode(), path)
            type == String::class.java || type == CharSequence::class.java -> reader.readString()
            type == Char::class.java || type == Character::class.java -> readCharacter(reader, path)
            type == Boolean::class.java || type == java.lang.Boolean::class.java -> reader.readBoolean()
            type == Byte::class.java || type == java.lang.Byte::class.java -> readNumberValue(reader, type)
            type == Short::class.java || type == java.lang.Short::class.java -> readNumberValue(reader, type)
            type == Int::class.java || type == java.lang.Integer::class.java -> readNumberValue(reader, type)
            type == Long::class.java || type == java.lang.Long::class.java -> readNumberValue(reader, type)
            type == Float::class.java || type == java.lang.Float::class.java -> readNumberValue(reader, type)
            type == Double::class.java || type == java.lang.Double::class.java -> readNumberValue(reader, type)
            type == BigDecimal::class.java -> readNumberValue(reader, type)
            type == BigInteger::class.java -> readNumberValue(reader, type)
            type == Number::class.java -> readNumberValue(reader, type)
            type.isEnum -> enumValue(type, reader.readString(), path)
            type.isArray -> readReflectArray(reader, type.componentType, path)
            Collection::class.java.isAssignableFrom(type) -> readCollection(reader, Any::class.java, type, path)
            Map::class.java.isAssignableFrom(type) -> readMap(reader, String::class.java, Any::class.java, type, path)
            else -> readBean(reader, type, path)
        }
    }

    private fun readParameterized(
        reader: DocumentValueReader,
        type: ParameterizedType,
        path: String,
    ): Any? {
        val rawType = type.rawType as? Class<*>
            ?: return readValue(reader, type.rawType, null, path)
        val arguments = type.actualTypeArguments
        return when {
            Collection::class.java.isAssignableFrom(rawType) -> {
                val elementType = arguments.firstOrNull() ?: Any::class.java
                readCollection(reader, elementType, rawType, path)
            }

            Map::class.java.isAssignableFrom(rawType) -> {
                val keyType = arguments.getOrNull(0) ?: String::class.java
                val valueType = arguments.getOrNull(1) ?: Any::class.java
                readMap(reader, keyType, valueType, rawType, path)
            }

            else -> deserializeValue(reader.readNode(), type, path)
        }
    }

    private fun binaryValue(node: Node, path: String): ByteArray = when (node) {
        is BinaryNode -> node.value
        is StringNode -> Base64.getDecoder().decode(node.value)
        else -> throw DocumentSerializationException("Expected binary or Base64 string at $path")
    }

    private fun readCharacter(reader: DocumentValueReader, path: String): Char {
        val value = reader.readString()
        if (value.length != 1) {
            throw DocumentSerializationException("Expected single character at $path")
        }
        return value[0]
    }

    private fun readNumberValue(reader: DocumentValueReader, type: Class<*>): Any = when (type) {
        Int::class.java, java.lang.Integer::class.java -> reader.readInt()
        Long::class.java, java.lang.Long::class.java -> reader.readLong()
        Double::class.java, java.lang.Double::class.java -> reader.readDouble()
        Byte::class.java, java.lang.Byte::class.java -> {
            val raw = reader.readNumberRaw()
            raw.toByteOrNull() ?: BigDecimal(raw).byteValueExact()
        }

        Short::class.java, java.lang.Short::class.java -> {
            val raw = reader.readNumberRaw()
            raw.toShortOrNull() ?: BigDecimal(raw).shortValueExact()
        }

        Float::class.java, java.lang.Float::class.java -> {
            val raw = reader.readNumberRaw()
            raw.toFloatOrNull() ?: BigDecimal(raw).toFloat()
        }

        BigInteger::class.java -> {
            val raw = reader.readNumberRaw()
            raw.toBigIntegerOrNull() ?: BigDecimal(raw).toBigIntegerExact()
        }

        else -> BigDecimal(reader.readNumberRaw())
    }

    private fun writeNumberValue(value: Number, writer: DocumentValueWriter) {
        when (value) {
            is Int -> writer.writeInt(value)
            is Long -> writer.writeLong(value)
            is Short -> writer.writeInt(value.toInt())
            is Byte -> writer.writeInt(value.toInt())
            is Double -> writer.writeDouble(value)
            else -> writer.writeNumber(serializeNumberRaw(value))
        }
    }

    private fun readCollection(
        reader: DocumentValueReader,
        elementType: Type,
        rawType: Class<*>,
        path: String,
    ): Any {
        val collection = newCollection(rawType)
        reader.beginArray()
        var index = 0
        while (reader.hasNextElement()) {
            collection.add(readValue(reader, elementType, null, "$path[$index]"))
            index++
        }
        reader.endArray()
        return collection
    }

    private fun readMap(
        reader: DocumentValueReader,
        keyType: Type,
        valueType: Type,
        rawType: Class<*>,
        path: String,
    ): Any {
        val map = newMap(rawType)
        reader.beginObject()
        while (reader.hasNextField()) {
            val fieldName = reader.readFieldName()
            val key = deserializeMapKey(fieldName, keyType, path)
            map[key] = readValue(reader, valueType, null, "$path.$fieldName")
        }
        reader.endObject()
        return map
    }

    private fun readReflectArray(
        reader: DocumentValueReader,
        componentType: Type,
        path: String,
    ): Any {
        val rawComponentType = rawClass(componentType)
        reader.beginArray()
        val values = ArrayList<Any?>()
        while (reader.hasNextElement()) {
            values.add(readValue(reader, componentType, null, "$path[${values.size}]"))
        }
        reader.endArray()
        val result = ReflectArray.newInstance(rawComponentType, values.size)
        for (index in values.indices) {
            ReflectArray.set(result, index, values[index])
        }
        return result
    }

    private fun readBean(
        reader: DocumentValueReader,
        type: Class<*>,
        path: String,
    ): Any {
        val instance = instantiate(type)
        val metadata = beanMetadata(type)
        val seen = HashSet<String>()
        reader.beginObject()
        while (reader.hasNextField()) {
            val fieldName = reader.readFieldName()
            val accessor = metadata.property(fieldName)
            if (accessor != null && accessor.canWrite) {
                seen.add(fieldName)
                val value = readValue(reader, accessor.valueType, accessor.metadata, "$path.$fieldName")
                accessor.write(instance, value)
            } else {
                if (config.failOnUnknownFields && fieldName !in metadata.knownFields) {
                    throw DocumentSerializationException(
                        "Unknown field '$fieldName' for ${type.name} at $path",
                    )
                }
                reader.skipValue()
            }
        }
        reader.endObject()
        applyMissingFields(instance, metadata, seen, path)
        return instance
    }

    private fun applyMissingFields(
        instance: Any,
        metadata: BeanMetadata,
        seen: Set<String>,
        path: String,
    ) {
        val policy = config.missingFieldPolicy
        if (policy == MissingFieldPolicy.KEEP_DEFAULT) {
            return
        }
        for (accessor in metadata.properties) {
            if (!accessor.canWrite) {
                continue
            }
            val fieldName = accessor.metadata.documentName
            if (fieldName in seen) {
                continue
            }
            if (policy == MissingFieldPolicy.FAIL) {
                throw DocumentSerializationException(
                    "Missing field '$fieldName' at $path",
                )
            }
            val rawType = try {
                rawClass(accessor.valueType)
            } catch (_: Exception) {
                null
            }
            if (rawType?.isPrimitive == true) {
                continue
            }
            accessor.write(instance, missingValue(policy, rawType))
        }
    }

    private fun missingValue(policy: MissingFieldPolicy, rawType: Class<*>?): Any? = when (policy) {
        MissingFieldPolicy.SET_EMPTY -> when {
            rawType == null -> null
            rawType == String::class.java || rawType == CharSequence::class.java -> ""
            Collection::class.java.isAssignableFrom(rawType) -> newCollection(rawType)
            Map::class.java.isAssignableFrom(rawType) -> newMap(rawType)
            rawType.isArray -> ReflectArray.newInstance(rawType.componentType, 0)
            else -> null
        }

        else -> null
    }

    private fun serializeValue(
        value: Any?,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        property: DocumentProperty? = null,
        path: String = "$",
        declaredType: Type? = property?.type,
        polymorphic: Boolean = true,
    ): Node {
        if (depth > MAX_SERIALIZATION_DEPTH) {
            throw DocumentSerializationException("Maximum serialization depth exceeded")
        }
        if (value != null && value !is Node) {
            propertySerializer(property)?.let { serializer ->
                return serializeWithSerializer(serializer, value, property, path)
            }
            if (polymorphic && config.hasSubtypes) {
                polymorphicRegistry(declaredType, value.javaClass)?.let { registry ->
                    return serializePolymorphic(value, registry, declaredType, seen, depth, property, path)
                }
            }
            val serializer = findSerializer(property?.getRawType() ?: value.javaClass)
                ?: findSerializer(value.javaClass)
            if (serializer != null) {
                return serializeWithSerializer(serializer, value, property, path)
            }
        }
        return when (value) {
            null -> NullNode.INSTANCE
            is Node -> value
            is ByteArray -> BinaryNode.of(value)
            is CharSequence -> StringNode.of(value.toString())
            is Char -> StringNode.of(value.toString())
            is Boolean -> BooleanNode.of(value)
            is Number -> serializeNumber(value)
            is Enum<*> -> StringNode.of(value.name)
            is Map<*, *> -> serializeMap(value, seen, depth, declaredType, path)
            is Iterable<*> -> serializeIterable(value, seen, depth, declaredType, path)
            is Array<*> -> serializeArray(value, seen, depth, declaredType, path)
            else -> {
                val type = value.javaClass
                if (type.isArray) {
                    serializeReflectArray(value, seen, depth, declaredType, path)
                } else {
                    serializeBean(value, seen, depth, path)
                }
            }
        }
    }

    private fun rawClassOrNull(type: Type?): Class<*>? = when (type) {
        null -> null
        is Class<*> -> type
        is ParameterizedType -> type.rawType as? Class<*>
        is GenericArrayType -> rawClassOrNull(type.genericComponentType)
            ?.let { ReflectArray.newInstance(it, 0).javaClass }

        is WildcardType -> rawClassOrNull(type.upperBounds.firstOrNull())
        is TypeVariable<*> -> rawClassOrNull(type.bounds.firstOrNull())
        else -> null
    }

    /**
     * 解析声明类型对应的多态注册表。
     *
     * 有声明类型时只匹配该类型本身（声明为具体子类不会回退到父类注册）；
     * 没有声明类型（顶层对象、原始集合元素）时按实际类型从注册表中反查。
     */
    private fun polymorphicRegistry(declaredType: Type?, actualClass: Class<*>): SubtypeRegistry? {
        val declaredRawType = rawClassOrNull(declaredType)
        if (declaredRawType != null) {
            return config.subtypeRegistry(declaredRawType)
        }
        return registryForValue(actualClass)
    }

    private fun registryForValue(actualClass: Class<*>): SubtypeRegistry? {
        val candidates = config.subtypeRegistries().filter {
            it.baseClass.isAssignableFrom(actualClass) && it.typeName(actualClass) != null
        }
        if (candidates.isEmpty()) {
            return null
        }
        if (candidates.size == 1) {
            return candidates.first()
        }
        val mostSpecific = candidates.firstOrNull { candidate ->
            candidates.all { other ->
                other === candidate || other.baseClass.isAssignableFrom(candidate.baseClass)
            }
        }
        return mostSpecific ?: throw DocumentSerializationException(
            "Ambiguous polymorphic bases for ${actualClass.name}: " +
                candidates.joinToString { it.baseClass.name },
        )
    }

    private fun serializePolymorphic(
        value: Any,
        registry: SubtypeRegistry,
        declaredType: Type?,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        property: DocumentProperty?,
        path: String,
    ): Node {
        val typeName = registry.typeName(value.javaClass)
            ?: throw DocumentSerializationException(
                "No subtype registered for ${value.javaClass.name} under polymorphic base " +
                    "${registry.baseClass.name} at $path",
            )
        val payload = serializeValue(
            value,
            seen,
            depth,
            property,
            path,
            declaredType = value.javaClass,
            polymorphic = false,
        )
        if (payload !is ObjectNode) {
            throw DocumentSerializationException(
                "Polymorphic subtype ${value.javaClass.name} must serialize to an object at $path " +
                    "but was ${payload.type}",
            )
        }
        if (payload.containsKey(registry.typeProperty)) {
            throw DocumentSerializationException(
                "Field '${registry.typeProperty}' of ${value.javaClass.name} conflicts with " +
                    "the polymorphic type property at $path",
            )
        }
        val fields = LinkedHashMap<String, Node>(payload.size + 1)
        fields[registry.typeProperty] = StringNode.of(typeName)
        for ((key, node) in payload.asMap()) {
            fields[key] = node
        }
        return ObjectNode.from(fields)
    }

    private fun elementType(declaredType: Type?): Type? = when (declaredType) {
        is ParameterizedType -> declaredType.actualTypeArguments.firstOrNull()
        is GenericArrayType -> declaredType.genericComponentType
        is Class<*> -> if (declaredType.isArray) declaredType.componentType else null
        is WildcardType -> elementType(declaredType.upperBounds.firstOrNull())
        is TypeVariable<*> -> elementType(declaredType.bounds.firstOrNull())
        else -> null
    }

    private fun mapValueType(declaredType: Type?): Type? = when (declaredType) {
        is ParameterizedType -> declaredType.actualTypeArguments.getOrNull(1)
        is WildcardType -> mapValueType(declaredType.upperBounds.firstOrNull())
        is TypeVariable<*> -> mapValueType(declaredType.bounds.firstOrNull())
        else -> null
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

    private fun serializeNumberRaw(value: Number): String = when (value) {
        is BigDecimal -> value.toPlainString()
        is BigInteger -> value.toString()
        is Double -> {
            require(value.isFinite()) { "NaN and Infinity cannot be serialized" }
            value.toString()
        }

        is Float -> {
            require(value.isFinite()) { "NaN and Infinity cannot be serialized" }
            value.toString()
        }

        else -> value.toString()
    }

    /**
     * 直接写出实体，不构建完整文档树。
     *
     * @param value 任意实体、集合、Map 或 [Node]
     * @param writer 格式写出器
     */
    public fun writeTo(value: Any?, writer: DocumentValueWriter) {
        writeValue(value, IdentityHashMap(), 0, null, DecodePath("$"), writer)
    }

    private fun writeValue(
        value: Any?,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        property: DocumentProperty?,
        path: DecodePath,
        writer: DocumentValueWriter,
        declaredType: Type? = property?.type,
        polymorphic: Boolean = true,
    ) {
        if (depth > MAX_SERIALIZATION_DEPTH) {
            throw DocumentSerializationException("Maximum serialization depth exceeded")
        }
        if (value != null && value !is Node) {
            propertySerializer(property)?.let { serializer ->
                writer.writeNode(serializeWithSerializer(serializer, value, property, path.toString()))
                return
            }
            if (polymorphic && config.hasSubtypes) {
                polymorphicRegistry(declaredType, value.javaClass)?.let { registry ->
                    writer.writeNode(
                        serializePolymorphic(value, registry, declaredType, seen, depth, property, path.toString()),
                    )
                    return
                }
            }
            val serializer = if (hasSerializers) {
                findSerializer(property?.getRawType() ?: value.javaClass)
                    ?: findSerializer(value.javaClass)
            } else {
                null
            }
            if (serializer != null) {
                writer.writeNode(serializeWithSerializer(serializer, value, property, path.toString()))
                return
            }
        }
        when (value) {
            null -> writer.writeNull()
            is Node -> writer.writeNode(value)
            is ByteArray -> writer.writeNode(BinaryNode.of(value))
            is CharSequence -> writer.writeString(value.toString())
            is Char -> writer.writeString(value.toString())
            is Boolean -> writer.writeBoolean(value)
            is Number -> writeNumberValue(value, writer)
            is Enum<*> -> writer.writeString(value.name)
            is Map<*, *> -> {
                val valueType = mapValueType(declaredType)
                writer.startObject()
                for ((key, child) in value) {
                    val fieldName = key?.toString() ?: "null"
                    writer.fieldName(fieldName)
                    val mark = path.mark()
                    path.field(fieldName)
                    writeValue(child, seen, depth + 1, null, path, writer, valueType)
                    path.truncate(mark)
                }
                writer.endObject()
            }

            is Iterable<*> -> {
                val elementType = elementType(declaredType)
                writer.startArray()
                for (element in value) {
                    val mark = path.mark()
                    path.literal("[]")
                    writeValue(element, seen, depth + 1, null, path, writer, elementType)
                    path.truncate(mark)
                }
                writer.endArray()
            }

            is Array<*> -> {
                val componentType = elementType(declaredType) ?: value.javaClass.componentType
                writer.startArray()
                for (element in value) {
                    val mark = path.mark()
                    path.literal("[]")
                    writeValue(element, seen, depth + 1, null, path, writer, componentType)
                    path.truncate(mark)
                }
                writer.endArray()
            }

            else -> {
                val type = value.javaClass
                if (type.isArray) {
                    writeReflectArray(value, seen, depth, path, writer, declaredType)
                } else {
                    writeBean(value, seen, depth, path, writer)
                }
            }
        }
    }

    private fun writeReflectArray(
        value: Any,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        path: DecodePath,
        writer: DocumentValueWriter,
        declaredType: Type?,
    ) {
        val length = ReflectArray.getLength(value)
        val componentType = elementType(declaredType) ?: value.javaClass.componentType
        writer.startArray()
        for (index in 0 until length) {
            val mark = path.mark()
            path.index(index)
            writeValue(ReflectArray.get(value, index), seen, depth + 1, null, path, writer, componentType)
            path.truncate(mark)
        }
        writer.endArray()
    }

    private fun writeBean(
        bean: Any,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        path: DecodePath,
        writer: DocumentValueWriter,
    ) {
        withCycleCheck(bean, seen) {
            writer.startObject()
            for (accessor in beanMetadata(bean.javaClass).readableProperties) {
                val metadata = accessor.metadata
                val value = accessor.read(bean)
                if (!config.writeNulls && value == null) {
                    continue
                }
                writer.fieldName(metadata.documentName)
                val mark = path.mark()
                path.field(metadata.name)
                writeValue(
                    value,
                    seen,
                    depth + 1,
                    metadata,
                    path,
                    writer,
                    metadata.type,
                )
                path.truncate(mark)
            }
            writer.endObject()
        }
    }

    private fun serializeMap(
        map: Map<*, *>,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        declaredType: Type?,
        path: String,
    ): Node = withCycleCheck(map, seen) {
        val valueType = mapValueType(declaredType)
        val fields = LinkedHashMap<String, Node>(map.size)
        for ((key, value) in map) {
            val fieldName = key?.toString() ?: "null"
            fields[fieldName] = serializeValue(value, seen, depth + 1, null, "$path.$fieldName", valueType)
        }
        ObjectNode.from(fields)
    }

    private fun serializeIterable(
        iterable: Iterable<*>,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        declaredType: Type?,
        path: String,
    ): Node = withCycleCheck(iterable, seen) {
        val elementType = elementType(declaredType)
        val elements = ArrayList<Node>()
        var index = 0
        for (element in iterable) {
            elements.add(serializeValue(element, seen, depth + 1, null, "$path[$index]", elementType))
            index++
        }
        ArrayNode.from(elements)
    }

    private fun serializeArray(
        array: Array<*>,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        declaredType: Type?,
        path: String,
    ): Node = withCycleCheck(array, seen) {
        val componentType = elementType(declaredType) ?: array.javaClass.componentType
        val elements = ArrayList<Node>(array.size)
        var index = 0
        for (element in array) {
            elements.add(serializeValue(element, seen, depth + 1, null, "$path[$index]", componentType))
            index++
        }
        ArrayNode.from(elements)
    }

    private fun serializeReflectArray(
        array: Any,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        declaredType: Type?,
        path: String,
    ): Node {
        val size = ReflectArray.getLength(array)
        val componentType = elementType(declaredType) ?: array.javaClass.componentType
        val elements = ArrayList<Node>(size)
        return withCycleCheck(array, seen) {
            for (index in 0 until size) {
                elements.add(
                    serializeValue(
                        ReflectArray.get(array, index),
                        seen,
                        depth + 1,
                        null,
                        "$path[$index]",
                        componentType,
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
        path: String,
    ): Node = withCycleCheck(bean, seen) {
        val fields = LinkedHashMap<String, Node>()
        for (accessor in beanMetadata(bean.javaClass).readableProperties) {
            val metadata = accessor.metadata
            val value = accessor.read(bean)
            if (!config.writeNulls && value == null) {
                continue
            }
            fields[metadata.documentName] = serializeValue(
                value,
                seen,
                depth + 1,
                metadata,
                "$path.${metadata.name}",
                metadata.type,
            )
        }
        ObjectNode.from(fields)
    }

    private fun deserializeValue(
        node: Node,
        type: Type,
        path: String,
        property: DocumentProperty? = null,
        polymorphic: Boolean = true,
    ): Any? {
        if (node is NullNode) {
            if (type is Class<*> && type.isPrimitive) {
                throw DocumentSerializationException("Cannot assign null to primitive type at $path")
            }
            return null
        }
        val propertyDeserializer = propertyDeserializer(property)
        if (propertyDeserializer != null) {
            return deserializeWithDeserializer(propertyDeserializer, node, property, path)
        }
        val rawType = when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as? Class<*>
            else -> null
        }
        if (rawType != null) {
            if (polymorphic && config.hasSubtypes) {
                config.subtypeRegistry(rawType)?.let { registry ->
                    return deserializePolymorphic(node, registry, path, property)
                }
            }
            val deserializer = findDeserializer(rawType)
            if (deserializer != null) {
                return deserializeWithDeserializer(deserializer, node, property, path)
            }
        }
        return when (type) {
            is Class<*> -> deserializeClass(node, type, path)
            is ParameterizedType -> deserializeParameterized(node, type, path)
            is GenericArrayType -> deserializeArray(node, type.genericComponentType, path)
            is WildcardType -> {
                val upperBound = type.upperBounds.firstOrNull() ?: Any::class.java
                deserializeValue(node, upperBound, path, property)
            }

            is TypeVariable<*> -> {
                val bound = type.bounds.firstOrNull() ?: Any::class.java
                deserializeValue(node, bound, path, property)
            }

            else -> throw DocumentSerializationException("Unsupported type at $path: $type")
        }
    }

    /**
     * 按类型属性解析具体子类，并以子类为目标类型重新反序列化。
     *
     * 子类路径屏蔽多态解析，避免注册表出现继承关系时无限递归；字段级反序列化器
     * 已在调用前处理，全局子类反序列化器仍然生效。
     */
    private fun deserializePolymorphic(
        node: Node,
        registry: SubtypeRegistry,
        path: String,
        property: DocumentProperty?,
    ): Any? {
        if (!node.isObject) {
            throw DocumentSerializationException(
                "Expected object node for polymorphic base ${registry.baseClass.name} at $path " +
                    "but was ${node.type}",
            )
        }
        val objectNode = node.asObject()
        val typeNode = objectNode[registry.typeProperty]
            ?: throw DocumentSerializationException(
                "Missing polymorphic type property '${registry.typeProperty}' for base " +
                    "${registry.baseClass.name} at $path",
            )
        if (!typeNode.isString) {
            throw DocumentSerializationException(
                "Polymorphic type property '${registry.typeProperty}' for base " +
                    "${registry.baseClass.name} must be a string at $path but was ${typeNode.type}",
            )
        }
        val typeName = typeNode.asString()
        val subtypeClass = registry.subtypeClass(typeName)
            ?: throw DocumentSerializationException(
                "Unknown polymorphic type name '$typeName' for type property " +
                    "'${registry.typeProperty}' of base ${registry.baseClass.name} at $path; " +
                    "registered names: ${registry.typeNames.joinToString()}",
            )
        val fields = LinkedHashMap<String, Node>(objectNode.size)
        for ((key, value) in objectNode.asMap()) {
            if (key != registry.typeProperty) {
                fields[key] = value
            }
        }
        return deserializeValue(
            ObjectNode.from(fields),
            subtypeClass,
            path,
            property,
            polymorphic = false,
        )
    }

    private fun deserializeClass(node: Node, type: Class<*>, path: String): Any? {
        if (Node::class.java.isAssignableFrom(type)) {
            return node
        }
        if (type == Any::class.java) {
            return node
        }
        return when {
            type == ByteArray::class.java -> binaryValue(node, path)
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

            else -> deserializeValue(node, rawType, path)
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
        val customInstance = objectInstantiator?.instantiate(
            type,
            objectNode,
            DefaultInstantiationContext(this, path),
        )
        if (customInstance != null) {
            if (!type.isInstance(customInstance)) {
                throw DocumentSerializationException(
                    "ObjectInstantiator returned ${customInstance.javaClass.name} for ${type.name}",
                )
            }
            return customInstance
        }
        val instance = instantiate(type)
        val metadata = beanMetadata(type)
        if (config.failOnUnknownFields) {
            for (key in objectNode.keys) {
                if (key !in metadata.knownFields) {
                    throw DocumentSerializationException(
                        "Unknown field '$key' for ${type.name} at $path",
                    )
                }
            }
        }
        val seen = HashSet<String>()

        for (accessor in metadata.properties) {
            if (!accessor.canWrite) {
                continue
            }
            val propertyMetadata = accessor.metadata
            val valueNode = objectNode[propertyMetadata.documentName] ?: continue
            seen.add(propertyMetadata.documentName)
            accessor.write(
                instance,
                deserializeValue(
                    valueNode,
                    accessor.valueType,
                    "$path.${propertyMetadata.name}",
                    propertyMetadata,
                ),
            )
        }
        applyMissingFields(instance, metadata, seen, path)
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

    /**
     * 供 [InstantiationContext] 递归反序列化构造参数。
     */
    internal fun deserializeForInstantiator(node: Node, targetType: Type, path: String): Any? = deserializeValue(node, targetType, path)

    private fun newInstance(type: Class<*>): Any {
        val constructor = constructorCache.computeIfAbsent(type) {
            type.getDeclaredConstructor().apply { isAccessible = true }
        }
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

    private fun documentProperty(type: Class<*>, property: PropertyDescriptor): DocumentProperty = documentPropertyCache.computeIfAbsent(type) { ConcurrentHashMap() }
        .computeIfAbsent(property.name) { createDocumentProperty(type, property) }

    private fun documentProperty(type: Class<*>, field: Field): DocumentProperty = documentPropertyCache.computeIfAbsent(type) { ConcurrentHashMap() }
        .computeIfAbsent(field.name) { createDocumentProperty(type, field) }

    private fun createDocumentProperty(type: Class<*>, property: PropertyDescriptor): DocumentProperty {
        val field = findField(type, property.name)
        val elements = listOfNotNull(property.readMethod, property.writeMethod, field)
        val propertyType = property.readMethod?.genericReturnType
            ?: property.writeMethod?.genericParameterTypes?.firstOrNull()
            ?: property.propertyType
        return DefaultDocumentProperty(
            type,
            property.name,
            propertyType,
            elements,
            propertyMetadataResolver?.resolve(type, property.name).orEmpty(),
            config.namingStrategy,
        )
    }

    private fun createDocumentProperty(type: Class<*>, field: Field): DocumentProperty = DefaultDocumentProperty(
        type,
        field.name,
        field.genericType,
        listOf(field),
        propertyMetadataResolver?.resolve(type, field.name).orEmpty(),
        config.namingStrategy,
    )

    private fun beanMetadata(type: Class<*>): BeanMetadata = beanMetadataCache.computeIfAbsent(type, ::createBeanMetadata)

    private fun createBeanMetadata(type: Class<*>): BeanMetadata {
        val accessors = LinkedHashMap<String, BeanPropertyAccessor>()
        val handled = HashSet<String>()
        val knownFields = LinkedHashSet<String>()

        for (property in beanProperties(type)) {
            handled.add(property.name)
            val metadata = documentProperty(type, property)
            knownFields.add(metadata.documentName)
            if (metadata.isIgnored()) {
                continue
            }
            val field = findField(type, property.name)
            val readMethod = property.readMethod
                ?.takeIf { !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 }
            val writeMethod = property.writeMethod
                ?.takeIf { !Modifier.isStatic(it.modifiers) && it.parameterCount == 1 }

            readMethod?.isAccessible = true
            writeMethod?.isAccessible = true
            field?.isAccessible = true
            val getter: java.util.function.Function<Any, Any?>? = when {
                readMethod != null -> BeanAccessorFactory.getter(readMethod) ?: java.util.function.Function<Any, Any?> { bean -> invoke(readMethod, bean) }
                field != null && !Modifier.isStatic(field.modifiers) && !field.isSynthetic ->
                    BeanAccessorFactory.fieldGetter(field) ?: java.util.function.Function<Any, Any?> { bean -> readField(field, bean) }

                else -> null
            }
            val setter: java.util.function.BiConsumer<Any, Any?>? = when {
                writeMethod != null -> BeanAccessorFactory.setter(writeMethod)
                    ?: java.util.function.BiConsumer<Any, Any?> { bean, value ->
                        invoke(writeMethod, bean, value)
                    }

                field != null &&
                    !Modifier.isStatic(field.modifiers) &&
                    !Modifier.isFinal(field.modifiers) &&
                    !field.isSynthetic ->
                    BeanAccessorFactory.fieldSetter(field)
                        ?: java.util.function.BiConsumer<Any, Any?> { bean, value ->
                            writeField(field, bean, value)
                        }

                else -> null
            }
            if (getter == null && setter == null) {
                continue
            }
            val valueType = writeMethod?.genericParameterTypes?.firstOrNull()
                ?: field?.genericType
                ?: property.propertyType
            accessors[property.name] = BeanPropertyAccessor(metadata, getter, setter, valueType)
        }

        for (field in type.fields) {
            if (Modifier.isStatic(field.modifiers) ||
                field.isSynthetic ||
                field.name in handled
            ) {
                continue
            }
            val metadata = documentProperty(type, field)
            knownFields.add(metadata.documentName)
            if (metadata.isIgnored()) {
                continue
            }
            field.isAccessible = true
            val setter: ((Any, Any?) -> Unit)? =
                if (Modifier.isFinal(field.modifiers)) {
                    null
                } else {
                    { bean, value -> writeField(field, bean, value) }
                }
            accessors[field.name] = BeanPropertyAccessor(
                metadata,
                { bean -> readField(field, bean) },
                setter,
                field.genericType,
            )
        }

        return BeanMetadata(accessors.values.toList(), knownFields)
    }

    private fun readField(field: Field, bean: Any): Any? = try {
        field.get(bean)
    } catch (e: Exception) {
        throw DocumentSerializationException("Cannot read field ${field.name}", e)
    }

    private fun writeField(field: Field, bean: Any, value: Any?) {
        try {
            field.set(bean, value)
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot write field ${field.name}", e)
        }
    }
    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
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
