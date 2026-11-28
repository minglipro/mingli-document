package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentBuild
import com.mingliqiye.document.annotation.DocumentBuilder
import com.mingliqiye.document.annotation.DocumentConverter
import com.mingliqiye.document.annotation.DocumentCreator
import com.mingliqiye.document.annotation.DocumentDateTimeFormat
import com.mingliqiye.document.annotation.DocumentDeserializer
import com.mingliqiye.document.annotation.DocumentInclude
import com.mingliqiye.document.annotation.DocumentIncludePolicy
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.annotation.DocumentNaming
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
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
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
 * - 通过 [com.mingliqiye.document.annotation.DocumentCreator] 标注构造器的不可变 Bean：
 *   按构造参数名（[DocumentName] 或 `-parameters` 保留的参数名）从文档取值，
 *   支持泛型、嵌套 Bean、集合、Map、可空与基本类型；构造参数与属性同名时，
 *   参数的 [DocumentName] 同时用于写出，保证读写字段名对称。
 * - 通过 [com.mingliqiye.document.annotation.DocumentBuilder] 静态工厂 + Builder 模式
 *   创建的不可变 Bean：没有 [com.mingliqiye.document.annotation.DocumentCreator] 构造器且
 *   没有无参构造器时，先调用静态工厂方法创建 Builder，按普通 Bean 规则填充 Builder，
 *   再调用 [com.mingliqiye.document.annotation.DocumentBuild] 标注的构建方法
 *   （未标注时回退到无参 `build()`）得到目标对象。
 * - Java record（运行在 JDK 16+ 时）：组件访问器（`name()` 形式）作为可读属性写出；
 *   反序列化优先使用 `@DocumentCreator` 构造器，没有标注时按 canonical constructor
 *   的组件顺序注入，组件名默认作为文档字段名（可用 [DocumentName] 覆盖）。
 * - 只读属性（没有 setter 且不是可写构造参数）：序列化正常写出；反序列化跳过该字段
 *   而不是当作未知字段报错；存在 canonical / [DocumentCreator] 构造器时按参数注入。
 * - 通过 [merge] 把文档树合并到已有对象，保留文档中没有出现的字段。
 * - 通过 [DocumentMapperConfig.beanValidator] 在根对象序列化入口与反序列化出口执行一次
 *   Bean 校验（嵌套对象与内部编解码器回调不会重复校验），校验失败抛
 *   [DocumentSerializationException]。
 * - 通过配置显式注册的多态基类/接口（字段声明为基类时写入/读取子类型信息）
 *
 * 优先使用 [com.mingliqiye.document.annotation.DocumentCreator] 构造器；没有标注的实体
 * 仍使用无参构造器 + setter；嵌套泛型属性需要通过 getter/setter 的泛型信息解析。
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
     * 当前 Mapper 的默认写出是否使用 pretty 格式。
     */
    protected open fun prettyOutputEnabled(): Boolean = false

    /**
     * 创建直接写入输出流的 Token 写出器。
     *
     * 返回 `null` 时回退到先构造完整 String/ByteArray 再写出。
     *
     * @param output 目标输出流；调用方负责关闭
     * @param pretty 是否 pretty 输出
     * @return 流式 Token 写出器或 `null`
     */
    protected open fun createStreamingTokenWriter(output: OutputStream, pretty: Boolean): DocumentTokenWriter? = null

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
    private val beanCreatorCache = ConcurrentHashMap<Class<*>, Any>()
    private val noBeanCreator = Any()
    private val beanBuilderCache = ConcurrentHashMap<Class<*>, Any>()
    private val noBeanBuilder = Any()
    private val noArgConstructorCache = ConcurrentHashMap<Class<*>, Boolean>()
    private val documentPropertyCache = ConcurrentHashMap<Class<*>, ConcurrentHashMap<String, DocumentProperty>>()
    private val beanMetadataCache = ConcurrentHashMap<Class<*>, BeanMetadata>()
    private val hasSerializers = serializers.isNotEmpty()
    private val hasDeserializers = deserializers.isNotEmpty()
    private val converterCache = ConcurrentHashMap<Class<*>, Any>()
    private val propertyConverterCache = ConcurrentHashMap<DocumentProperty, Any>()
    private val noConverter = Any()

    /**
     * 当前线程是否已处于一次带校验的根操作内。
     *
     * 生成式编解码器、值类转换器等内部实现会回调公开的 [toNode]/[fromNode]；
     * 这些嵌套调用不应再次触发 Bean 校验，由该标记保证每次根操作只校验一次。
     */
    private val beanValidationScope: ThreadLocal<Boolean> = ThreadLocal()

    /**
     * 序列化根入口：先校验根对象，再在“已校验”作用域内执行序列化。
     */
    private inline fun <T> validatingSerializationRoot(value: Any?, block: () -> T): T {
        if (!beanValidationEnabled()) {
            return block()
        }
        validateRootBean(value)
        beanValidationScope.set(java.lang.Boolean.TRUE)
        try {
            return block()
        } finally {
            beanValidationScope.remove()
        }
    }

    /**
     * 反序列化根出口：在“已校验”作用域内完成反序列化，最后校验结果对象。
     */
    private inline fun <T> validatingDeserializationRoot(block: () -> T): T {
        if (!beanValidationEnabled()) {
            return block()
        }
        beanValidationScope.set(java.lang.Boolean.TRUE)
        try {
            val result = block()
            validateRootBean(result)
            return result
        } finally {
            beanValidationScope.remove()
        }
    }

    /** 是否需要在当前调用层执行根对象校验；嵌套调用返回 `false`。 */
    private fun beanValidationEnabled(): Boolean = config.beanValidator != null && beanValidationScope.get() == null

    /**
     * 调用配置的校验器校验根对象。
     *
     * 校验器直接抛出的 [DocumentSerializationException] 原样传播；其他异常包装为
     * [DocumentSerializationException] 并通过 cause 保留原始异常。
     */
    private fun validateRootBean(value: Any?) {
        val validator = config.beanValidator ?: return
        if (value == null) {
            return
        }
        try {
            validator.validate(value)
        } catch (e: DocumentSerializationException) {
            throw e
        } catch (e: Exception) {
            throw DocumentSerializationException("Bean validation failed for ${value.javaClass.name}", e)
        }
    }

    /**
     * 将 Java 对象序列化为文档树。
     *
     * [DocumentMapperConfig.writeReferences] 开启时按对象身份写出本地 `$ref`：
     * 同一对象只在首次出现处展开，再次出现时写出 `{"$ref":"#/首次路径"}`，循环引用
     * 也会以引用代替环形展开。关闭时保持原有循环检测行为。该能力只作用于
     * Node/[toNode] 路径，流式 [writeTo] 路径不参与引用写出。
     *
     * @param value Java 对象
     * @return 文档树
     * @throws DocumentSerializationException 序列化失败、存在循环引用或配置了
     * [DocumentMapperConfig.beanValidator] 且根对象校验失败时抛出
     */
    override fun toNode(value: Any?): Node = validatingSerializationRoot(value) {
        val references = if (config.writeReferences) IdentityReferenceTracker() else null
        val node = serializeValue(value, IdentityHashMap(), 0, null, "$", references = references)
        NodeLimits.validate(node, config.maxNodeDepth, config.maxNodeCount, config.maxStringLength)
        node
    }

    /**
     * 按当前配置校验 Node 树深度/节点数；供文本格式 Token 路径使用。
     */
    protected fun validateNodeLimits(node: Node) {
        NodeLimits.validate(node, config.maxNodeDepth, config.maxNodeCount, config.maxStringLength)
    }

    /**
     * 将文档树反序列化为指定 Java 类型。
     *
     * @param node 文档树
     * @param targetType 目标类型
     * @return Java 对象
     * @throws DocumentSerializationException 反序列化失败或根对象校验失败时抛出
     */
    override fun <T> fromNode(node: Node, targetType: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return validatingDeserializationRoot {
            NodeLimits.validate(node, config.maxNodeDepth, config.maxNodeCount, config.maxStringLength)
            val source = if (config.resolveReferences) ReferenceResolver.resolve(node, config.remoteReferenceResolver) else node
            deserializeValue(source, targetType, "$")
        } as T
    }

    /**
     * 将文档树反序列化为指定泛型类型。
     *
     * @param node 文档树
     * @param targetType 目标泛型类型，例如 List&lt;String&gt;
     * @return Java 对象
     * @throws DocumentSerializationException 反序列化失败或根对象校验失败时抛出
     */
    public fun fromNode(node: Node, targetType: Type): Any? = validatingDeserializationRoot {
        deserializeValue(node, targetType, "$")
    }

    /**
     * 将文档树反序列化为指定泛型类型。
     *
     * @param node 文档树
     * @param targetType 目标类型引用
     * @return Java 对象
     * @throws DocumentSerializationException 反序列化失败或根对象校验失败时抛出
     */
    override fun <T> fromNode(node: Node, targetType: DocumentTypeReference<T>): T {
        @Suppress("UNCHECKED_CAST")
        return validatingDeserializationRoot {
            NodeLimits.validate(node, config.maxNodeDepth, config.maxNodeCount, config.maxStringLength)
            val source = if (config.resolveReferences) ReferenceResolver.resolve(node, config.remoteReferenceResolver) else node
            deserializeValue(source, targetType.type, "$")
        } as T
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
        parseBytes?.let { return fromNode(it(source), targetType) }
        if (usesTextFormat) {
            return parse(String(source, config.fileCharset), targetType)
        }
        throw DocumentSerializationException("$formatName mapper does not support binary parsing")
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
        parseBytes?.let { return fromNode(it(source), targetType) }
        if (usesTextFormat) {
            return parse(String(source, config.fileCharset), targetType)
        }
        throw DocumentSerializationException("$formatName mapper does not support binary parsing")
    }

    /**
     * 将 Java 实体序列化为字节数组。
     *
     * @param value Java 实体
     * @return 二进制内容
     */
    override fun formatBytes(value: Any?): ByteArray {
        val writer = writeBytes
        if (writer != null) {
            return writer(toNode(value), false)
        }
        if (usesTextFormat) {
            return format(value).toByteArray(config.fileCharset)
        }
        throw DocumentSerializationException("$formatName mapper does not support binary writing")
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
        val streaming = createStreamingTokenWriter(output, prettyOutputEnabled())
        if (streaming != null) {
            writeTo(value, streaming)
            streaming.flush()
            output.flush()
            return
        }
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

    override fun formatPretty(value: Any?, file: File) {
        formatPretty(value, file.toPath())
    }

    override fun formatPretty(value: Any?, path: Path) {
        writeFile(path, prettyBytes(value))
    }

    override fun formatPretty(value: Any?, output: OutputStream) {
        val streaming = createStreamingTokenWriter(output, true)
        if (streaming != null) {
            writeTo(value, streaming)
            streaming.flush()
            output.flush()
            return
        }
        output.write(prettyBytes(value))
        output.flush()
    }

    override fun formatPretty(value: Any?, writer: Writer) {
        if (!usesTextFormat) {
            throw DocumentSerializationException("$formatName mapper does not support character streams")
        }
        writer.write(formatPretty(value))
        writer.flush()
    }

    private fun prettyBytes(value: Any?): ByteArray {
        if (!usesTextFormat) {
            throw DocumentSerializationException("$formatName mapper does not support pretty printing")
        }
        return formatPretty(value).toByteArray(config.fileCharset)
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

    // ------------------------------------------------------------------
    // 对象映射便捷 API
    //
    // 语义在 DocumentMapperApi 默认实现里已经定义；这里逐项显式覆写，让 Java 调用方
    // 通过 DocumentMapper / JsonMapper 等具体类型也能解析到这些方法（JVM 上接口默认
    // 方法编译为 DefaultImpls，Java 按类的成员查找看不到它们）。
    // ------------------------------------------------------------------

    /** 将文本解析为文档树；读取规则与 `parse` 一致，顶层 `null` 返回 [NullNode.INSTANCE]。 */
    override fun readTree(source: String): Node = parse(source, Any::class.java) as? Node ?: NullNode.INSTANCE

    /** 将字节数组解析为文档树；顶层 `null` 返回 [NullNode.INSTANCE]。 */
    override fun readTree(source: ByteArray): Node = parse(source, Any::class.java) as? Node ?: NullNode.INSTANCE

    /** 从文件解析文档树。 */
    override fun readTree(file: File): Node = parse(file, Any::class.java) as? Node ?: NullNode.INSTANCE

    /** 从路径解析文档树。 */
    override fun readTree(path: Path): Node = parse(path, Any::class.java) as? Node ?: NullNode.INSTANCE

    /** 从输入流解析文档树。 */
    override fun readTree(input: InputStream): Node = parse(input, Any::class.java) as? Node ?: NullNode.INSTANCE

    /** 从字符流解析文档树；只支持文本格式。 */
    override fun readTree(reader: Reader): Node = parse(reader, Any::class.java) as? Node ?: NullNode.INSTANCE

    /** 将文档树写为文本；等价于 `format(node)`。 */
    override fun writeTree(node: Node): String = format(node)

    /** 将文档树写为字节数组；等价于 `formatBytes(node)`。 */
    override fun writeTreeAsBytes(node: Node): ByteArray = formatBytes(node)

    /** 将任意对象转换为文档树；等价于 [toNode]。 */
    override fun valueToTree(value: Any?): Node = toNode(value)

    /** 将文档树转换为指定 Java 类型；等价于 `fromNode(node, targetType)`。 */
    override fun <T> treeToValue(node: Node, targetType: Class<T>): T = fromNode(node, targetType)

    /** 将文档树转换为指定泛型类型；等价于 `fromNode(node, targetType)`。 */
    override fun <T> treeToValue(node: Node, targetType: DocumentTypeReference<T>): T = fromNode(node, targetType)

    /** 将文本解析为 Java 实体；等价于 `parse(source, targetType)`。 */
    override fun <T> readValue(source: String, targetType: Class<T>): T = parse(source, targetType)

    /** 将文本解析为指定泛型类型。 */
    override fun <T> readValue(source: String, targetType: DocumentTypeReference<T>): T = parse(source, targetType)

    /** 将字节数组解析为 Java 实体。 */
    override fun <T> readValue(source: ByteArray, targetType: Class<T>): T = parse(source, targetType)

    /** 将字节数组解析为指定泛型类型。 */
    override fun <T> readValue(source: ByteArray, targetType: DocumentTypeReference<T>): T = parse(source, targetType)

    /** 从文件解析 Java 实体。 */
    override fun <T> readValue(file: File, targetType: Class<T>): T = parse(file, targetType)

    /** 从文件解析指定泛型类型。 */
    override fun <T> readValue(file: File, targetType: DocumentTypeReference<T>): T = parse(file, targetType)

    /** 从路径解析 Java 实体。 */
    override fun <T> readValue(path: Path, targetType: Class<T>): T = parse(path, targetType)

    /** 从路径解析指定泛型类型。 */
    override fun <T> readValue(path: Path, targetType: DocumentTypeReference<T>): T = parse(path, targetType)

    /** 从输入流解析 Java 实体。 */
    override fun <T> readValue(input: InputStream, targetType: Class<T>): T = parse(input, targetType)

    /** 从输入流解析指定泛型类型。 */
    override fun <T> readValue(input: InputStream, targetType: DocumentTypeReference<T>): T = parse(input, targetType)

    /** 从字符流解析 Java 实体；只支持文本格式。 */
    override fun <T> readValue(reader: Reader, targetType: Class<T>): T = parse(reader, targetType)

    /** 从字符流解析指定泛型类型；只支持文本格式。 */
    override fun <T> readValue(reader: Reader, targetType: DocumentTypeReference<T>): T = parse(reader, targetType)

    /** 将任意对象写为文本；等价于 [format]。 */
    override fun writeValueAsString(value: Any?): String = format(value)

    /** 将任意对象写为字节数组；等价于 [formatBytes]。 */
    override fun writeValueAsBytes(value: Any?): ByteArray = formatBytes(value)

    /** 将覆盖文档合并到已有对象；等价于 `merge(toNode(overrides), target)`。 */
    override fun <T> updateValue(target: T, overrides: Any?): T = merge(toNode(overrides), target)

    /** 将任意对象转换为指定 Java 类型；等价于 [convert]。 */
    override fun <D> convertValue(source: Any?, destinationClass: Class<D>): D = convert(source, destinationClass)

    /** 将任意对象转换为指定泛型类型；等价于 [convert]。 */
    override fun <D> convertValue(source: Any?, destinationType: DocumentTypeReference<D>): D = convert(source, destinationType)

    /** 创建绑定目标类型（[Class]）的对象读取器。 */
    override fun <T> readerFor(targetType: Class<T>): DocumentObjectReader<T> = DocumentObjectReader(this, DocumentTypeReference.of(targetType))

    /** 创建绑定目标泛型类型的对象读取器。 */
    override fun <T> readerFor(targetType: DocumentTypeReference<T>): DocumentObjectReader<T> = DocumentObjectReader(this, targetType)

    /** 创建紧凑风格的对象写出器。 */
    override fun writer(): DocumentObjectWriter = DocumentObjectWriter(this, pretty = false)

    /** 创建格式化（pretty）风格的对象写出器。 */
    override fun writerWithDefaultPrettyPrinter(): DocumentObjectWriter = DocumentObjectWriter(this, pretty = true)

    /**
     * 把文档树合并到已有对象，文档中没有出现的字段保持目标对象的原值。
     *
     * 可变 Bean 就地修改并返回同一实例；不可变 Bean（record、
     * [com.mingliqiye.document.annotation.DocumentCreator] 构造器 Bean、
     * [com.mingliqiye.document.annotation.DocumentBuilder] Bean）通过
     * “已有对象序列化结果 + 文档字段覆盖”重建实例后返回，原实例不变。
     *
     * @param node 合并来源文档树，必须是对象节点
     * @param target 已存在的目标对象
     * @param policy 合并策略，默认 [MergePolicy.REPLACE]
     * @return 合并后的对象
     * @throws DocumentSerializationException 节点不是对象、目标为 `null`，
     *   或目标类型不支持合并时抛出
     */
    override fun <T> merge(node: Node, target: T, policy: MergePolicy): T {
        val instance: Any = target ?: throw DocumentSerializationException("Merge target must not be null")
        NodeLimits.validate(node, config.maxNodeDepth, config.maxNodeCount, config.maxStringLength)
        val source = if (config.resolveReferences) ReferenceResolver.resolve(node, config.remoteReferenceResolver) else node
        @Suppress("UNCHECKED_CAST")
        return mergeValue(source, instance, instance.javaClass, DecodePath("$"), policy) as T
    }

    /**
     * 合并对象节点到已有实例。
     *
     * 可变 Bean 就地写入；不可变 Bean 重建实例。嵌套字段是否递归由
     * [MergePolicy.deepMerge] 决定。
     */
    private fun mergeValue(
        node: Node,
        instance: Any,
        type: Class<*>,
        path: DecodePath,
        policy: MergePolicy,
    ): Any {
        if (!node.isObject) {
            throw DocumentSerializationException(
                "Merge requires an object node at $path but was ${node.type}",
            )
        }
        val objectNode = node.asObject()
        val creator = beanCreator(type)
        if (creator != null || beanBuilderFor(type) != null) {
            return mergeImmutableBean(objectNode, instance, type, creator, path, policy)
        }
        if (!isMutableMergeType(type)) {
            throw DocumentSerializationException(
                "Type ${type.name} at $path does not support merge; merge target must be a Bean",
            )
        }
        failOnUnknownMergeFields(objectNode, type, creator = null, path = path)
        val metadata = beanMetadata(type)
        for ((fieldName, patchNode) in objectNode.asMap()) {
            val accessor = metadata.property(fieldName) ?: continue
            if (!accessor.canWrite) {
                // 只读属性：文档中即使出现也直接跳过，不报错。
                continue
            }
            if (policy.skipNulls && patchNode is NullNode) {
                continue
            }
            val current = if (accessor.canRead) accessor.read(instance) else null
            val mark = path.mark()
            path.field(fieldName)
            val merged = mergePropertyValue(patchNode, current, accessor, policy, path)
            path.truncate(mark)
            accessor.write(instance, merged)
        }
        return instance
    }

    /** 合并单个属性值：可深合并的嵌套 Bean 递归合并，其余按完整反序列化覆盖。 */
    private fun mergePropertyValue(
        patchNode: Node,
        current: Any?,
        accessor: BeanPropertyAccessor,
        policy: MergePolicy,
        path: DecodePath,
    ): Any? {
        if (!policy.deepMerge || current == null || patchNode !is ObjectNode) {
            return deserializeForMerge(patchNode, accessor.valueType, accessor.metadata, path)
        }
        val declaredType = rawClassOrNull(accessor.valueType)
            ?: return deserializeForMerge(patchNode, accessor.valueType, accessor.metadata, path)
        if (config.hasSubtypes && config.subtypeRegistry(declaredType) != null) {
            // 多态声明字段需要先按类型属性解析子类，直接整体替换。
            return deserializeForMerge(patchNode, accessor.valueType, accessor.metadata, path)
        }
        val actualType = current.javaClass
        if (!declaredType.isAssignableFrom(actualType) || !isRegularBeanType(declaredType)) {
            return deserializeForMerge(patchNode, accessor.valueType, accessor.metadata, path)
        }
        return mergeValue(patchNode, current, actualType, path, policy)
    }

    /**
     * 合并不可变 Bean：把已有对象的序列化结果与文档字段叠加后重新反序列化。
     *
     * 构造函数注入类型的字段没有 setter，无法就地写入；通过“已有值 + 覆盖字段”
     * 重新调用 canonical / `@DocumentCreator` 构造器得到新实例。
     */
    private fun mergeImmutableBean(
        objectNode: ObjectNode,
        instance: Any,
        type: Class<*>,
        creator: BeanCreatorMetadata?,
        path: DecodePath,
        policy: MergePolicy,
    ): Any {
        failOnUnknownMergeFields(objectNode, type, creator, path)
        val existing = serializeValue(instance, IdentityHashMap(), 0, null, path.toString())
        if (existing !is ObjectNode) {
            throw DocumentSerializationException(
                "Cannot merge immutable type ${type.name} at $path: serialized value is not an object",
            )
        }
        val mergedFields = LinkedHashMap<String, Node>(existing.asMap())
        for ((fieldName, patchNode) in objectNode.asMap()) {
            if (policy.skipNulls && patchNode is NullNode) {
                continue
            }
            val current = mergedFields[fieldName]
            mergedFields[fieldName] = overlayMergeNode(current, patchNode, policy)
        }
        return deserializeValue(ObjectNode.from(mergedFields), type, path.toString())
            ?: throw DocumentSerializationException("Cannot merge immutable type ${type.name} at $path")
    }

    /** 叠加单个字段节点；深合并时递归合并对象节点。 */
    private fun overlayMergeNode(current: Node?, patchNode: Node, policy: MergePolicy): Node = if (policy.deepMerge && current is ObjectNode && patchNode is ObjectNode) {
        val fields = LinkedHashMap<String, Node>(current.asMap())
        for ((fieldName, nestedPatch) in patchNode.asMap()) {
            if (policy.skipNulls && nestedPatch is NullNode) {
                continue
            }
            fields[fieldName] = overlayMergeNode(fields[fieldName], nestedPatch, policy)
        }
        ObjectNode.from(fields)
    } else {
        patchNode
    }

    /** 合并路径的未知字段校验：只允许 Bean 已知字段（含只读/忽略属性）与构造参数名。 */
    private fun failOnUnknownMergeFields(
        objectNode: ObjectNode,
        type: Class<*>,
        creator: BeanCreatorMetadata?,
        path: DecodePath,
    ) {
        if (!config.failOnUnknownFields) {
            return
        }
        val knownFields = beanMetadata(type).knownFields
        val creatorNames = creator?.documentNames.orEmpty()
        for (key in objectNode.keys) {
            if (key !in knownFields && key !in creatorNames) {
                throw DocumentSerializationException("Unknown field '$key' for ${type.name} at $path")
            }
        }
    }

    /** 目标是否为可就地写入的普通可变 Bean。 */
    private fun isMutableMergeType(type: Class<*>): Boolean = isRegularBeanType(type) &&
        !type.isInterface &&
        !Modifier.isAbstract(type.modifiers) &&
        beanCreator(type) == null &&
        beanBuilderFor(type) == null

    /** 排除标量、容器、数组、Node 等不能按 Bean 深合并的类型。 */
    private fun isRegularBeanType(type: Class<*>): Boolean = when {
        type.isPrimitive || type.isArray -> false
        type == String::class.java || type == CharSequence::class.java || type == Char::class.java -> false
        type == Boolean::class.java || type == java.lang.Boolean::class.java -> false
        Number::class.java.isAssignableFrom(type) -> false
        type.isEnum -> false
        Collection::class.java.isAssignableFrom(type) -> false
        Map::class.java.isAssignableFrom(type) -> false
        Node::class.java.isAssignableFrom(type) -> false
        type == Any::class.java -> false
        else -> true
    }

    private fun deserializeForMerge(
        node: Node,
        type: Type,
        property: DocumentProperty?,
        path: DecodePath,
    ): Any? = deserializeValue(node, type, path.toString(), property)

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
     * @throws DocumentSerializationException 反序列化失败或根对象校验失败时抛出
     */
    public fun <T> readFrom(reader: DocumentValueReader, targetType: DocumentTypeReference<T>): T {
        @Suppress("UNCHECKED_CAST")
        return validatingDeserializationRoot {
            readValue(reader, targetType.type, null, "$")
        } as T
    }

    private fun readValue(
        reader: DocumentValueReader,
        type: Type,
        property: DocumentProperty?,
        path: String,
    ): Any? {
        if (reader.tryReadNull()) {
            if (type is Class<*> && type.isPrimitive) {
                return nullPrimitiveValue(type, path)
            }
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
            if (beanCreator(rawType) != null) {
                return readBean(reader, rawType, path)
            }
            if (beanBuilderFor(rawType) != null) {
                return readBean(reader, rawType, path)
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
     * @throws DocumentSerializationException 反序列化失败或根对象校验失败时抛出
     */
    public fun fromTokens(reader: DocumentTokenReader, targetType: Type): Any? = validatingDeserializationRoot {
        reader.nextToken() ?: throw DocumentSerializationException("Empty token stream")
        val result = readTokenValue(reader, targetType, null, DecodePath("$"))
        if (reader.nextToken() != null) {
            throw DocumentSerializationException("Unexpected trailing token content")
        }
        result
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
                return nullPrimitiveValue(type, path.toString())
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
            if (beanCreator(rawType) != null) {
                return readTokenBean(reader, rawType, path)
            }
            if (beanBuilderFor(rawType) != null) {
                return readTokenBean(reader, rawType, path)
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
        val creator = beanCreator(type)
        if (creator != null) {
            return readTokenBeanWithCreator(reader, type, creator, path)
        }
        val builder = beanBuilderFor(type)
        if (builder != null) {
            return readTokenBeanWithBuilder(reader, type, builder, path)
        }
        val instance = instantiate(type)
        readTokenBeanFields(reader, instance, type, path)
        return instance
    }

    /**
     * 从 Token 流读取对象字段并写入已创建的实例。
     *
     * 供无参构造 Bean 与 Builder 填充共用：Builder 也用同一套 setter/字段逻辑填充。
     */
    private fun readTokenBeanFields(
        reader: DocumentTokenReader,
        instance: Any,
        type: Class<*>,
        path: DecodePath,
    ) {
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
    }

    /**
     * 使用 [com.mingliqiye.document.annotation.DocumentBuilder] 从 Token 流创建实例。
     */
    private fun readTokenBeanWithBuilder(
        reader: DocumentTokenReader,
        type: Class<*>,
        builder: BeanBuilderMetadata,
        path: DecodePath,
    ): Any {
        val builderInstance = newBuilderInstance(builder, path.toString())
        readTokenBeanFields(reader, builderInstance, builder.builderType, path)
        return buildBean(builder, builderInstance, path.toString())
    }

    /**
     * 使用 [DocumentCreator] 构造器从 Token 流创建实例。
     */
    private fun readTokenBeanWithCreator(
        reader: DocumentTokenReader,
        type: Class<*>,
        creator: BeanCreatorMetadata,
        path: DecodePath,
    ): Any {
        val arguments = creator.newArguments()
        val present = BooleanArray(creator.argumentCount)
        while (true) {
            when (reader.nextToken()) {
                null -> throw DocumentSerializationException("Unexpected end of object at $path")
                DocumentTokenType.END_OBJECT -> break
                DocumentTokenType.FIELD_NAME -> {
                    val fieldName = reader.textValue
                        ?: throw DocumentSerializationException("Missing field name at $path")
                    val parameter = creator.parameter(fieldName)
                    reader.nextToken() ?: throw DocumentSerializationException("Unexpected end of object at $path")
                    if (parameter != null) {
                        val mark = path.mark()
                        path.field(fieldName)
                        arguments[parameter.index] = readTokenValue(reader, parameter.type, null, path)
                        path.truncate(mark)
                        present[parameter.index] = true
                    } else {
                        if (config.failOnUnknownFields && fieldName !in beanMetadata(type).knownFields) {
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
        fillMissingCreatorArguments(creator, arguments, present, path.toString())
        return invokeCreator(creator, arguments, path.toString())
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
        val creator = beanCreator(type)
        if (creator != null) {
            return readBeanWithCreator(reader, type, creator, path)
        }
        val builder = beanBuilderFor(type)
        if (builder != null) {
            return readBeanWithBuilder(reader, type, builder, path)
        }
        val instance = instantiate(type)
        readBeanFields(reader, instance, type, path)
        return instance
    }

    /**
     * 从流式读取器读取对象字段并写入已创建的实例。
     *
     * 供无参构造 Bean 与 Builder 填充共用：Builder 也用同一套 setter/字段逻辑填充。
     */
    private fun readBeanFields(
        reader: DocumentValueReader,
        instance: Any,
        type: Class<*>,
        path: String,
    ) {
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
    }

    /**
     * 使用 [com.mingliqiye.document.annotation.DocumentBuilder] 从流式读取器创建实例。
     */
    private fun readBeanWithBuilder(
        reader: DocumentValueReader,
        type: Class<*>,
        builder: BeanBuilderMetadata,
        path: String,
    ): Any {
        val builderInstance = newBuilderInstance(builder, path)
        readBeanFields(reader, builderInstance, builder.builderType, path)
        return buildBean(builder, builderInstance, path)
    }

    /**
     * 使用 [DocumentCreator] 构造器从流式读取器创建实例。
     */
    private fun readBeanWithCreator(
        reader: DocumentValueReader,
        type: Class<*>,
        creator: BeanCreatorMetadata,
        path: String,
    ): Any {
        val arguments = creator.newArguments()
        val present = BooleanArray(creator.argumentCount)
        reader.beginObject()
        while (reader.hasNextField()) {
            val fieldName = reader.readFieldName()
            val parameter = creator.parameter(fieldName)
            if (parameter != null) {
                arguments[parameter.index] = readValue(reader, parameter.type, null, "$path.$fieldName")
                present[parameter.index] = true
            } else {
                if (config.failOnUnknownFields && fieldName !in beanMetadata(type).knownFields) {
                    throw DocumentSerializationException(
                        "Unknown field '$fieldName' for ${type.name} at $path",
                    )
                }
                reader.skipValue()
            }
        }
        reader.endObject()
        fillMissingCreatorArguments(creator, arguments, present, path)
        return invokeCreator(creator, arguments, path)
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

    /**
     * 为构造器注入中没有出现在文档里的参数填充默认值。
     *
     * 构造器没有 Java 字段的"原始默认值"概念：引用类型参数在
     * [MissingFieldPolicy.KEEP_DEFAULT] / [MissingFieldPolicy.SET_NULL] 下传 `null`，
     * 基本类型参数传 Java 默认值；[MissingFieldPolicy.SET_EMPTY] 额外对
     * String/集合/Map/数组传空值；[MissingFieldPolicy.FAIL] 抛异常。
     *
     * 被 `@DocumentIgnore` 标记的 record 组件不参与文档匹配，始终传 Java 默认值，
     * 不受 [MissingFieldPolicy.FAIL] 影响。
     */
    private fun fillMissingCreatorArguments(
        creator: BeanCreatorMetadata,
        arguments: Array<Any?>,
        present: BooleanArray,
        path: String,
    ) {
        for (parameter in creator.parameters) {
            if (present[parameter.index]) {
                continue
            }
            arguments[parameter.index] = if (parameter.ignored) {
                ignoredCreatorValue(parameter)
            } else {
                missingCreatorValue(parameter, path)
            }
        }
    }

    /** 被忽略的构造参数使用 Java 默认值（引用类型 `null`，基本类型 0/false）。 */
    private fun ignoredCreatorValue(parameter: BeanCreatorParameter): Any? {
        val rawType = rawClassOrNull(parameter.type)
        return if (rawType?.isPrimitive == true) primitiveDefault(rawType) else null
    }

    private fun missingCreatorValue(parameter: BeanCreatorParameter, path: String): Any? {
        val policy = config.missingFieldPolicy
        if (policy == MissingFieldPolicy.FAIL) {
            throw DocumentSerializationException("Missing field '${parameter.documentName}' at $path")
        }
        val rawType = rawClassOrNull(parameter.type)
        val value = if (policy == MissingFieldPolicy.SET_EMPTY) missingValue(policy, rawType) else null
        if (value == null && rawType?.isPrimitive == true) {
            return primitiveDefault(rawType)
        }
        return value
    }

    /**
     * 处理文档 `null` 赋给 Java 基本类型的场景。
     *
     * [DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES] 开启时抛出
     * [DocumentSerializationException]（信息包含字段路径与目标基本类型）；
     * 关闭（默认）时返回该基本类型的 Java 默认值，保持既有行为。
     */
    private fun nullPrimitiveValue(type: Class<*>, path: String): Any {
        if (config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)) {
            throw DocumentSerializationException(
                "Cannot assign null to primitive type ${type.name} at $path " +
                    "(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES is enabled)",
            )
        }
        return primitiveDefault(type)
    }

    private fun primitiveDefault(type: Class<*>): Any = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Character.TYPE -> '\u0000'
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0.0f
        java.lang.Double.TYPE -> 0.0
        else -> throw DocumentSerializationException("Unsupported primitive constructor parameter type ${type.name}")
    }

    /**
     * 通过 [DocumentCreator] 构造器创建实例。
     */
    private fun invokeCreator(creator: BeanCreatorMetadata, arguments: Array<Any?>, path: String): Any = try {
        creator.constructor.newInstance(*arguments)
    } catch (e: InvocationTargetException) {
        throw DocumentSerializationException(
            "Constructor of ${creator.ownerType.name} threw an exception at $path",
            e.targetException,
        )
    } catch (e: ReflectiveOperationException) {
        throw DocumentSerializationException(
            "Cannot invoke @DocumentCreator constructor of ${creator.ownerType.name} at $path",
            e,
        )
    }

    private fun serializeValue(
        value: Any?,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        property: DocumentProperty? = null,
        path: String = "$",
        declaredType: Type? = property?.type,
        polymorphic: Boolean = true,
        pointer: String = "",
        references: IdentityReferenceTracker? = null,
    ): Node {
        if (depth > MAX_SERIALIZATION_DEPTH) {
            throw DocumentSerializationException("Maximum serialization depth exceeded")
        }
        if (references != null && value != null && value !is Node) {
            references.pointerOf(value)?.let { firstPointer ->
                return JsonReference.referenceNode(firstPointer)
            }
        }
        if (value != null && value !is Node) {
            propertySerializer(property)?.let { serializer ->
                return serializeWithSerializer(serializer, value, property, path)
            }
            if (polymorphic && config.hasSubtypes) {
                polymorphicRegistry(declaredType, value.javaClass)?.let { registry ->
                    return serializePolymorphic(
                        value,
                        registry,
                        declaredType,
                        seen,
                        depth,
                        property,
                        path,
                        pointer,
                        references,
                    )
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
            is Map<*, *> -> serializeMap(value, seen, depth, declaredType, path, pointer, references)
            is Iterable<*> -> serializeIterable(value, seen, depth, declaredType, path, pointer, references)
            is Array<*> -> serializeArray(value, seen, depth, declaredType, path, pointer, references)
            else -> {
                val type = value.javaClass
                if (type.isArray) {
                    serializeReflectArray(value, seen, depth, declaredType, path, pointer, references)
                } else {
                    serializeBean(value, seen, depth, path, pointer, references)
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
        pointer: String = "",
        references: IdentityReferenceTracker? = null,
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
            pointer = pointer,
            references = references,
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
     * 流式路径不支持对象身份引用：即使配置了
     * [DocumentMapperConfig.writeReferences]，本方法仍按原有行为直接展开每个对象，
     * 循环引用继续抛出 [DocumentSerializationException]。需要 `$ref` 写出时使用
     * [toNode] 路径。
     *
     * @param value 任意实体、集合、Map 或 [Node]
     * @param writer 格式写出器
     * @throws DocumentSerializationException 序列化失败或配置了
     * [DocumentMapperConfig.beanValidator] 且根对象校验失败时抛出
     */
    public fun writeTo(value: Any?, writer: DocumentValueWriter) {
        validatingSerializationRoot(value) {
            writeValue(value, IdentityHashMap(), 0, null, DecodePath("$"), writer)
        }
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
            val creator = beanCreator(bean.javaClass)
            for (accessor in beanMetadata(bean.javaClass).readableProperties) {
                val metadata = accessor.metadata
                val value = accessor.read(bean)
                if (!shouldWriteProperty(value, metadata, bean.javaClass)) {
                    continue
                }
                writer.fieldName(serializedDocumentName(creator, metadata))
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
        pointer: String,
        references: IdentityReferenceTracker?,
    ): Node {
        references?.register(map, pointer)
        return withCycleCheck(map, seen) {
            val valueType = mapValueType(declaredType)
            val fields = LinkedHashMap<String, Node>(map.size)
            for ((key, value) in map) {
                val fieldName = key?.toString() ?: "null"
                fields[fieldName] = serializeValue(
                    value,
                    seen,
                    depth + 1,
                    null,
                    "$path.$fieldName",
                    valueType,
                    pointer = JsonReference.fieldPointer(pointer, fieldName),
                    references = references,
                )
            }
            ObjectNode.from(fields)
        }
    }

    private fun serializeIterable(
        iterable: Iterable<*>,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        declaredType: Type?,
        path: String,
        pointer: String,
        references: IdentityReferenceTracker?,
    ): Node {
        references?.register(iterable, pointer)
        return withCycleCheck(iterable, seen) {
            val elementType = elementType(declaredType)
            val elements = ArrayList<Node>()
            var index = 0
            for (element in iterable) {
                elements.add(
                    serializeValue(
                        element,
                        seen,
                        depth + 1,
                        null,
                        "$path[$index]",
                        elementType,
                        pointer = JsonReference.indexPointer(pointer, index),
                        references = references,
                    ),
                )
                index++
            }
            ArrayNode.from(elements)
        }
    }

    private fun serializeArray(
        array: Array<*>,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        declaredType: Type?,
        path: String,
        pointer: String,
        references: IdentityReferenceTracker?,
    ): Node {
        references?.register(array, pointer)
        return withCycleCheck(array, seen) {
            val componentType = elementType(declaredType) ?: array.javaClass.componentType
            val elements = ArrayList<Node>(array.size)
            var index = 0
            for (element in array) {
                elements.add(
                    serializeValue(
                        element,
                        seen,
                        depth + 1,
                        null,
                        "$path[$index]",
                        componentType,
                        pointer = JsonReference.indexPointer(pointer, index),
                        references = references,
                    ),
                )
                index++
            }
            ArrayNode.from(elements)
        }
    }

    private fun serializeReflectArray(
        array: Any,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        declaredType: Type?,
        path: String,
        pointer: String,
        references: IdentityReferenceTracker?,
    ): Node {
        references?.register(array, pointer)
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
                        pointer = JsonReference.indexPointer(pointer, index),
                        references = references,
                    ),
                )
            }
            ArrayNode.from(elements)
        }
    }

    /**
     * 判断 Bean 属性是否写出。
     *
     * 字段级 [DocumentInclude] > 类级 [DocumentInclude] > 全局 `writeNulls`。
     * [DocumentIncludePolicy.USE_DEFAULTS] 表示回退到下一优先级。
     */
    private fun shouldWriteProperty(value: Any?, metadata: DocumentProperty, beanClass: Class<*>): Boolean = when (resolveIncludePolicy(metadata, beanClass)) {
        DocumentIncludePolicy.ALWAYS -> true
        DocumentIncludePolicy.NON_NULL -> value != null
        DocumentIncludePolicy.NON_EMPTY -> !isEmptyPropertyValue(value)
        null, DocumentIncludePolicy.USE_DEFAULTS -> config.writeNulls || value != null
    }

    /** 解析字段级/类级 [DocumentInclude]；都没有显式策略时返回 `null`，由全局配置决定。 */
    private fun resolveIncludePolicy(metadata: DocumentProperty, beanClass: Class<*>): DocumentIncludePolicy? {
        val fieldPolicy = metadata.getAnnotation(DocumentInclude::class.java)?.value
        if (fieldPolicy != null && fieldPolicy != DocumentIncludePolicy.USE_DEFAULTS) {
            return fieldPolicy
        }
        val classPolicy = classIncludePolicy(beanClass)
        if (classPolicy != null && classPolicy != DocumentIncludePolicy.USE_DEFAULTS) {
            return classPolicy
        }
        return null
    }

    /** 查找类级 [DocumentInclude]（含父类链）。 */
    private fun classIncludePolicy(type: Class<*>): DocumentIncludePolicy? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            current.getAnnotation(DocumentInclude::class.java)?.let { return it.value }
            current = current.superclass
        }
        return null
    }

    /** [DocumentIncludePolicy.NON_EMPTY] 判空：null、空字符串、空集合/Map/数组都算空值。 */
    private fun isEmptyPropertyValue(value: Any?): Boolean = when (value) {
        null -> true
        is CharSequence -> value.isEmpty()
        is Collection<*> -> value.isEmpty()
        is Map<*, *> -> value.isEmpty()
        is ByteArray -> value.isEmpty()
        is Array<*> -> value.isEmpty()
        else -> value.javaClass.isArray && ReflectArray.getLength(value) == 0
    }

    private fun serializeBean(
        bean: Any,
        seen: IdentityHashMap<Any, Boolean>,
        depth: Int,
        path: String,
        pointer: String,
        references: IdentityReferenceTracker?,
    ): Node {
        references?.register(bean, pointer)
        return withCycleCheck(bean, seen) {
            val fields = LinkedHashMap<String, Node>()
            val creator = beanCreator(bean.javaClass)
            for (accessor in beanMetadata(bean.javaClass).readableProperties) {
                val metadata = accessor.metadata
                val value = accessor.read(bean)
                if (!shouldWriteProperty(value, metadata, bean.javaClass)) {
                    continue
                }
                val documentName = serializedDocumentName(creator, metadata)
                fields[documentName] = serializeValue(
                    value,
                    seen,
                    depth + 1,
                    metadata,
                    "$path.${metadata.name}",
                    metadata.type,
                    pointer = JsonReference.fieldPointer(pointer, documentName),
                    references = references,
                )
            }
            ObjectNode.from(fields)
        }
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
                return nullPrimitiveValue(type, path)
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
        val creator = beanCreator(type)
        if (creator != null) {
            return deserializeBeanWithCreator(objectNode, type, creator, path)
        }
        val builder = beanBuilderFor(type)
        if (builder != null) {
            return deserializeBeanWithBuilder(objectNode, type, builder, path)
        }
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
        fillBeanInstance(instance, type, objectNode, path)
        return instance
    }

    /**
     * 用对象节点填充已创建的实例。
     *
     * 供无参构造 Bean 与 Builder 填充共用：Builder 也用同一套 setter/字段逻辑填充。
     */
    private fun fillBeanInstance(
        instance: Any,
        type: Class<*>,
        objectNode: ObjectNode,
        path: String,
    ) {
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
    }

    /**
     * 使用 [com.mingliqiye.document.annotation.DocumentBuilder] 从对象节点创建实例。
     */
    private fun deserializeBeanWithBuilder(
        objectNode: ObjectNode,
        type: Class<*>,
        builder: BeanBuilderMetadata,
        path: String,
    ): Any {
        val builderInstance = newBuilderInstance(builder, path)
        fillBeanInstance(builderInstance, builder.builderType, objectNode, path)
        return buildBean(builder, builderInstance, path)
    }

    /**
     * 使用 [DocumentCreator] 构造器从对象节点创建实例。
     *
     * 只读属性（没有 setter、也不是构造参数）在文档中出现时直接跳过而不是
     * 按未知字段报错；`@DocumentIgnore` 属性同理。
     */
    private fun deserializeBeanWithCreator(
        objectNode: ObjectNode,
        type: Class<*>,
        creator: BeanCreatorMetadata,
        path: String,
    ): Any {
        if (config.failOnUnknownFields) {
            val readOnlyFields = beanMetadata(type).knownFields
            for (key in objectNode.keys) {
                if (key !in creator.documentNames && key !in readOnlyFields) {
                    throw DocumentSerializationException("Unknown field '$key' for ${type.name} at $path")
                }
            }
        }
        val arguments = creator.newArguments()
        val present = BooleanArray(creator.argumentCount)
        for (parameter in creator.parameters) {
            val valueNode = objectNode[parameter.documentName] ?: continue
            arguments[parameter.index] = deserializeValue(
                valueNode,
                parameter.type,
                "$path.${parameter.documentName}",
                null,
            )
            present[parameter.index] = true
        }
        fillMissingCreatorArguments(creator, arguments, present, path)
        return invokeCreator(creator, arguments, path)
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

    private fun instantiate(type: Class<*>): Any {
        val constructor = try {
            constructorCache.computeIfAbsent(type) {
                type.getDeclaredConstructor().apply { isAccessible = true }
            }
        } catch (e: NoSuchMethodException) {
            throw DocumentSerializationException(
                "Cannot instantiate ${type.name}: no @DocumentCreator constructor, " +
                    "no @DocumentBuilder static factory method and no no-arg constructor",
                e,
            )
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot instantiate ${type.name}", e)
        }
        return try {
            constructor.newInstance()
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot instantiate ${type.name}", e)
        }
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

    private fun documentProperty(
        type: Class<*>,
        name: String,
        elements: List<AnnotatedElement>,
        propertyType: Type,
    ): DocumentProperty = documentPropertyCache.computeIfAbsent(type) { ConcurrentHashMap() }
        .computeIfAbsent(name) { createDocumentProperty(type, name, elements, propertyType) }

    private fun createDocumentProperty(type: Class<*>, property: PropertyDescriptor): DocumentProperty {
        val field = findField(type, property.name)
        val elements = listOfNotNull(property.readMethod, property.writeMethod, field)
        val propertyType = property.readMethod?.genericReturnType
            ?: property.writeMethod?.genericParameterTypes?.firstOrNull()
            ?: property.propertyType
        return createDocumentProperty(type, property.name, elements, propertyType)
    }

    /**
     * 构造由任意注解元素（方法/字段）描述的属性元数据。
     *
     * 用于 JavaBeans Introspector 不识别、但符合 Builder 流式 setter 约定的属性：
     * `setXxx(value)` 返回 Builder 自身而不是 `void`。
     */
    private fun createDocumentProperty(
        type: Class<*>,
        name: String,
        elements: List<AnnotatedElement>,
        propertyType: Type,
    ): DocumentProperty = DefaultDocumentProperty(
        type,
        name,
        propertyType,
        elements,
        propertyMetadataResolver?.resolve(type, name).orEmpty(),
        config.namingStrategy,
    )

    private fun createDocumentProperty(type: Class<*>, field: Field): DocumentProperty = DefaultDocumentProperty(
        type,
        field.name,
        field.genericType,
        listOf(field),
        propertyMetadataResolver?.resolve(type, field.name).orEmpty(),
        config.namingStrategy,
    )

    /**
     * 获取类型反序列化时要使用的构造器元数据；没有标注时返回 `null`。
     *
     * 优先使用 [DocumentCreator] 标注的构造器；没有标注的 Java record 回退到
     * canonical constructor（运行在 JDK 8 时 record 不存在，不影响普通 Bean）。
     * 结果按类型缓存，同一类型只解析一次反射信息。
     */
    private fun beanCreator(type: Class<*>): BeanCreatorMetadata? {
        val cached = beanCreatorCache.computeIfAbsent(type) {
            createBeanCreator(type) ?: createRecordBeanCreator(type) ?: noBeanCreator
        }
        return if (cached === noBeanCreator) null else cached as BeanCreatorMetadata
    }

    private fun createBeanCreator(type: Class<*>): BeanCreatorMetadata? {
        var selected: Constructor<*>? = null
        for (constructor in type.declaredConstructors) {
            if (!constructor.isAnnotationPresent(DocumentCreator::class.java)) {
                continue
            }
            if (selected != null) {
                throw DocumentSerializationException("Multiple @DocumentCreator constructors on ${type.name}")
            }
            selected = constructor
        }
        val constructor = selected ?: return null
        try {
            constructor.isAccessible = true
        } catch (e: Exception) {
            throw DocumentSerializationException("Cannot access @DocumentCreator constructor of ${type.name}", e)
        }
        val parameters = constructor.parameters.mapIndexed { index, parameter ->
            createBeanCreatorParameter(type, index, parameter)
        }
        val duplicates = parameters.groupBy { it.documentName }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw DocumentSerializationException(
                "Duplicate @DocumentCreator parameter document names $duplicates on ${type.name}",
            )
        }
        return BeanCreatorMetadata(type, constructor, parameters)
    }

    private fun createBeanCreatorParameter(type: Class<*>, index: Int, parameter: Parameter): BeanCreatorParameter {
        val annotation = parameter.getAnnotation(DocumentName::class.java)
        val namingAnnotation = parameter.getAnnotation(DocumentNaming::class.java)
        val javaName = parameter.name.takeIf { parameter.isNamePresent }
        val parameterNaming = namingAnnotation?.value?.toPropertyNamingStrategy()
        val explicitDocumentName = annotation?.value
            ?: parameterNaming?.let { strategy ->
                javaName?.let { strategy.translate(it) }
                    ?: throw DocumentSerializationException(
                        "Constructor parameter $index of ${type.name} has no @DocumentName annotation and its name " +
                            "is not available at runtime; annotate the parameter with @DocumentName or compile with -parameters",
                    )
            }
        val documentName = explicitDocumentName
            ?: javaName?.let { creatorParameterProperty(type, it)?.documentName }
            ?: javaName?.let { (classNamingStrategy(type) ?: config.namingStrategy).translate(it) }
            ?: throw DocumentSerializationException(
                "Constructor parameter $index of ${type.name} has no @DocumentName annotation and its name " +
                    "is not available at runtime; annotate the parameter with @DocumentName or compile with -parameters",
            )
        return BeanCreatorParameter(
            index,
            javaName,
            documentName,
            explicitDocumentName,
            parameter.parameterizedType,
        )
    }

    /**
     * 查找与构造参数同名的 JavaBeans 属性元数据，用于让构造参数继承字段/getter 上的
     * [DocumentName] / [DocumentNaming] 命名结果，保证读写字段名对称。
     */
    private fun creatorParameterProperty(type: Class<*>, javaName: String): DocumentProperty? = try {
        beanProperties(type).firstOrNull { it.name == javaName }?.let { documentProperty(type, it) }
    } catch (_: DocumentSerializationException) {
        null
    }

    /**
     * 为 Java record 生成隐式 [DocumentCreator] 元数据：绑定 canonical constructor。
     *
     * record 没有无参构造器、组件访问器也不符合 JavaBeans getter 约定，因此没有
     * 显式 [DocumentCreator] 时按 record 组件顺序绑定 canonical constructor；
     * 组件名即规范构造参数名，默认作为文档字段名，可用 [DocumentName] 覆盖。
     * 运行在 JDK 8 时 [JavaRecordSupport] 不可用，本方法返回 `null`。
     */
    private fun createRecordBeanCreator(type: Class<*>): BeanCreatorMetadata? {
        if (!JavaRecordSupport.isRecord(type)) {
            return null
        }
        val components = JavaRecordSupport.components(type)
        val constructor = findCanonicalRecordConstructor(type, components)
            ?: throw DocumentSerializationException(
                "Cannot find canonical constructor of record ${type.name}; " +
                    "declare an @DocumentCreator constructor instead",
            )
        try {
            constructor.isAccessible = true
        } catch (e: Exception) {
            throw DocumentSerializationException(
                "Cannot access canonical constructor of record ${type.name}",
                e,
            )
        }
        val parameters = components.mapIndexed { index, component ->
            createRecordBeanCreatorParameter(type, constructor, index, component)
        }
        val duplicates = parameters.filterNot { it.ignored }
            .groupBy { it.documentName }
            .filterValues { it.size > 1 }
            .keys
        if (duplicates.isNotEmpty()) {
            throw DocumentSerializationException(
                "Duplicate record component document names $duplicates on ${type.name}",
            )
        }
        return BeanCreatorMetadata(type, constructor, parameters)
    }

    private fun createRecordBeanCreatorParameter(
        type: Class<*>,
        constructor: Constructor<*>,
        index: Int,
        component: JavaRecordComponent,
    ): BeanCreatorParameter {
        val elements = listOfNotNull(
            constructor.parameters.getOrNull(index),
            component.accessor,
            findField(type, component.name),
            component.element,
        )
        val property = documentProperty(type, component.name, elements, component.type)
        return BeanCreatorParameter(
            index,
            component.name,
            property.documentName,
            property.getAnnotation(DocumentName::class.java)?.value,
            component.type,
            property.isIgnored(),
        )
    }

    /**
     * 查找 record 的 canonical constructor。
     *
     * 以组件泛型类型的擦除类型按顺序匹配构造参数类型；record 的 canonical
     * constructor 签名必须与组件列表一致。
     */
    private fun findCanonicalRecordConstructor(
        type: Class<*>,
        components: List<JavaRecordComponent>,
    ): Constructor<*>? {
        val expectedTypes = components.map { component ->
            try {
                rawClass(component.type)
            } catch (_: DocumentSerializationException) {
                return null
            }
        }
        return type.declaredConstructors.firstOrNull { constructor ->
            constructor.parameterCount == expectedTypes.size &&
                constructor.parameterTypes.indices.all { constructor.parameterTypes[it] == expectedTypes[it] }
        }
    }

    /**
     * 获取类型上 [com.mingliqiye.document.annotation.DocumentBuilder] 标注的静态工厂
     * 元数据；没有标注时返回 `null`。
     *
     * 结果按类型缓存，同一类型只解析一次反射信息。
     */
    private fun beanBuilder(type: Class<*>): BeanBuilderMetadata? {
        val cached = beanBuilderCache.computeIfAbsent(type) { createBeanBuilder(type) ?: noBeanBuilder }
        return if (cached === noBeanBuilder) null else cached as BeanBuilderMetadata
    }

    /**
     * 判断类型是否适用 Builder 模式反序列化。
     *
     * 只有没有 [DocumentCreator] 构造器且没有无参构造器的类型，才回退到
     * [com.mingliqiye.document.annotation.DocumentBuilder] 静态工厂。
     */
    private fun beanBuilderFor(type: Class<*>): BeanBuilderMetadata? {
        if (beanCreator(type) != null || hasNoArgConstructor(type)) {
            return null
        }
        return beanBuilder(type)
    }

    private fun hasNoArgConstructor(type: Class<*>): Boolean = noArgConstructorCache.computeIfAbsent(type) {
        try {
            type.getDeclaredConstructor()
            true
        } catch (_: NoSuchMethodException) {
            false
        }
    }

    private fun createBeanBuilder(type: Class<*>): BeanBuilderMetadata? {
        var selected: Method? = null
        for (method in type.declaredMethods) {
            if (!method.isAnnotationPresent(DocumentBuilder::class.java)) {
                continue
            }
            if (selected != null) {
                throw DocumentSerializationException(
                    "Multiple @DocumentBuilder static factory methods on ${type.name}",
                )
            }
            selected = method
        }
        val factory = selected ?: return null
        if (!Modifier.isStatic(factory.modifiers)) {
            throw DocumentSerializationException(
                "@DocumentBuilder method ${factory.name} on ${type.name} must be static",
            )
        }
        if (factory.parameterCount != 0) {
            throw DocumentSerializationException(
                "@DocumentBuilder method ${factory.name} on ${type.name} must have no parameters",
            )
        }
        val builderType = factory.returnType
        if (builderType.isPrimitive || builderType.isArray || builderType == Void::class.java) {
            throw DocumentSerializationException(
                "@DocumentBuilder method ${factory.name} on ${type.name} must return a builder type",
            )
        }
        try {
            factory.isAccessible = true
        } catch (e: Exception) {
            throw DocumentSerializationException(
                "Cannot access @DocumentBuilder method ${factory.name} on ${type.name}",
                e,
            )
        }
        val buildMethod = findBuildMethod(type, builderType)
        return BeanBuilderMetadata(type, builderType, factory, buildMethod)
    }

    /**
     * 查找 Builder 上把 Builder 转换为目标对象的方法。
     *
     * 优先使用 [com.mingliqiye.document.annotation.DocumentBuild] 标注的方法；
     * 没有标注时回退到无参 `build()`。
     */
    private fun findBuildMethod(ownerType: Class<*>, builderType: Class<*>): Method {
        val annotated = builderType.declaredMethods.filter {
            it.isAnnotationPresent(DocumentBuild::class.java)
        }
        if (annotated.size > 1) {
            throw DocumentSerializationException(
                "Multiple @DocumentBuild methods on builder ${builderType.name} for ${ownerType.name}",
            )
        }
        val method = annotated.firstOrNull() ?: defaultBuildMethod(builderType)
            ?: throw DocumentSerializationException(
                "No build method on builder ${builderType.name} for ${ownerType.name}; " +
                    "annotate a no-arg method returning ${ownerType.name} with @DocumentBuild " +
                    "or add a no-arg build() method",
            )
        if (Modifier.isStatic(method.modifiers)) {
            throw DocumentSerializationException(
                "@DocumentBuild method ${method.name} on builder ${builderType.name} " +
                    "for ${ownerType.name} must not be static",
            )
        }
        if (method.parameterCount != 0) {
            throw DocumentSerializationException(
                "@DocumentBuild method ${method.name} on builder ${builderType.name} " +
                    "for ${ownerType.name} must have no parameters",
            )
        }
        if (!ownerType.isAssignableFrom(method.returnType)) {
            throw DocumentSerializationException(
                "@DocumentBuild method ${method.name} on builder ${builderType.name} " +
                    "for ${ownerType.name} must return ${ownerType.name} but returns " +
                    method.returnType.name,
            )
        }
        try {
            method.isAccessible = true
        } catch (e: Exception) {
            throw DocumentSerializationException(
                "Cannot access @DocumentBuild method ${method.name} on builder ${builderType.name}",
                e,
            )
        }
        return method
    }

    private fun defaultBuildMethod(builderType: Class<*>): Method? {
        var current: Class<*>? = builderType
        while (current != null && current != Any::class.java) {
            for (method in current.declaredMethods) {
                if (method.name != "build" ||
                    method.isSynthetic ||
                    method.isBridge ||
                    Modifier.isStatic(method.modifiers) ||
                    method.parameterCount != 0
                ) {
                    continue
                }
                return method
            }
            current = current.superclass
        }
        return null
    }

    /**
     * 调用 [com.mingliqiye.document.annotation.DocumentBuilder] 静态工厂方法创建 Builder 实例。
     */
    private fun newBuilderInstance(builder: BeanBuilderMetadata, path: String): Any {
        val instance = try {
            builder.factory.invoke(null)
        } catch (e: InvocationTargetException) {
            throw DocumentSerializationException(
                "@DocumentBuilder factory method of ${builder.ownerType.name} threw an exception at $path",
                e.targetException,
            )
        } catch (e: ReflectiveOperationException) {
            throw DocumentSerializationException(
                "Cannot invoke @DocumentBuilder factory method of ${builder.ownerType.name} at $path",
                e,
            )
        }
        return instance ?: throw DocumentSerializationException(
            "@DocumentBuilder factory method of ${builder.ownerType.name} returned null at $path",
        )
    }

    /**
     * 调用 [com.mingliqiye.document.annotation.DocumentBuild] 方法把 Builder 转换为目标对象。
     */
    private fun buildBean(builder: BeanBuilderMetadata, builderInstance: Any, path: String): Any {
        val result = try {
            builder.buildMethod.invoke(builderInstance)
        } catch (e: InvocationTargetException) {
            throw DocumentSerializationException(
                "@DocumentBuild method of ${builder.ownerType.name} threw an exception at $path",
                e.targetException,
            )
        } catch (e: ReflectiveOperationException) {
            throw DocumentSerializationException(
                "Cannot invoke @DocumentBuild method of ${builder.ownerType.name} at $path",
                e,
            )
        }
        if (!builder.ownerType.isInstance(result)) {
            throw DocumentSerializationException(
                "@DocumentBuild method of ${builder.ownerType.name} returned " +
                    "${result?.javaClass?.name ?: "null"} at $path",
            )
        }
        return result
    }

    /**
     * 计算序列化时使用的文档字段名。
     *
     * 类型存在 [DocumentCreator] 构造器、构造参数可反射出 Java 参数名且与属性同名时，
     * 参数上显式的 [DocumentName] 同时用于写出，保证不可变 Bean 的读写字段名对称；
     * 否则沿用属性自身的文档字段名。
     */
    private fun serializedDocumentName(creator: BeanCreatorMetadata?, metadata: DocumentProperty): String = creator?.parameterByJavaName(metadata.name)?.explicitDocumentName ?: metadata.documentName

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

        // JavaBeans Introspector 不识别 record 组件：组件访问器是 `name()` 而不是
        // `getName()`，字段是私有 final。这里按 record 组件补充只读访问器。
        for (component in JavaRecordSupport.components(type)) {
            if (component.name in handled) {
                continue
            }
            handled.add(component.name)
            val field = findField(type, component.name)
            val metadata = documentProperty(
                type,
                component.name,
                listOfNotNull(component.accessor, field, component.element),
                component.type,
            )
            knownFields.add(metadata.documentName)
            if (metadata.isIgnored()) {
                continue
            }
            val getter: java.util.function.Function<Any, Any?> = BeanAccessorFactory.getter(component.accessor)
                ?: java.util.function.Function<Any, Any?> { bean -> invoke(component.accessor, bean) }
            accessors[component.name] = BeanPropertyAccessor(metadata, getter, null, component.type)
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

        // Builder 模式常用 `setXxx` 返回 Builder 自身（流式 setter），JavaBeans
        // Introspector 只识别返回 void 的 setter，这里补充这类属性的写访问器。
        for (method in type.methods) {
            if (Modifier.isStatic(method.modifiers) ||
                method.isSynthetic ||
                method.isBridge ||
                method.parameterCount != 1 ||
                method.returnType == java.lang.Void.TYPE
            ) {
                continue
            }
            val name = method.name
            if (name.length <= 3 || !name.startsWith("set") || !name[3].isUpperCase()) {
                continue
            }
            val propertyName = Introspector.decapitalize(name.substring(3))
            if (propertyName in handled) {
                continue
            }
            handled.add(propertyName)
            val field = findField(type, propertyName)
            val valueType = method.genericParameterTypes[0]
            val metadata = documentProperty(
                type,
                propertyName,
                listOfNotNull(method, field),
                valueType,
            )
            knownFields.add(metadata.documentName)
            if (metadata.isIgnored()) {
                continue
            }
            method.isAccessible = true
            val setter: java.util.function.BiConsumer<Any, Any?> = BeanAccessorFactory.setter(method)
                ?: java.util.function.BiConsumer<Any, Any?> { bean, value ->
                    invoke(method, bean, value)
                }
            accessors[propertyName] = BeanPropertyAccessor(metadata, null, setter, valueType)
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
