package com.mingliqiye.document.serialization

import java.nio.charset.Charset

/**
 * Mapper 配置。
 *
 * 支持字段级转换器、缺字段策略、未知字段策略、null 字段写出策略、
 * 字段命名策略、多态子类型注册、根对象 Bean 校验与文件读写选项。
 */
public open class DocumentMapperConfig protected constructor(
    private val fieldConverters: Map<FieldKey, DocumentBaseConverter<*>>,
    /** 文档缺少 Bean 字段时的处理策略。 */
    public val missingFieldPolicy: MissingFieldPolicy = MissingFieldPolicy.KEEP_DEFAULT,
    /** 遇到未知字段是否抛出异常。 */
    public val failOnUnknownFields: Boolean = false,
    /** Bean 字段为 null 时是否写出。 */
    public val writeNulls: Boolean = true,
    /**
     * 未标注 [com.mingliqiye.document.annotation.DocumentName] 的字段使用的
     * 文档字段命名策略，默认 [PropertyNamingStrategy.AS_IS]。
     */
    public val namingStrategy: PropertyNamingStrategy = PropertyNamingStrategy.AS_IS,
    /** 文本文件/流读写使用的字符集，默认 UTF-8。 */
    public val fileCharset: Charset = Charsets.UTF_8,
    /** 写文件时是否使用临时文件 + 原子替换。 */
    public val atomicFileWrite: Boolean = false,
    /** 写文件时是否自动创建父目录。 */
    public val createParentDirectories: Boolean = false,
    /** 单次输入最大字节数；0 表示不限制。 */
    public val maxInputSize: Long = 0L,
    /** Node 树中单个字符串最大长度；0 表示不限制。 */
    public val maxStringLength: Int = 0,
    /** Node 树最大深度；0 表示不限制。 */
    public val maxNodeDepth: Int = 0,
    /** Node 树最大节点数；0 表示不限制。 */
    public val maxNodeCount: Long = 0L,
    /** 是否在反序列化前解析本地 `$ref`。 */
    public val resolveReferences: Boolean = false,
    /**
     * 是否在 [DocumentMapper.toNode] 序列化时按对象身份写出本地 `$ref`。
     *
     * 开启后同一对象（按引用身份而非 equals）只在首次出现处展开，再次出现时
     * 写出 `{"$ref":"#/首次路径"}`，循环引用也会以引用代替环形展开。
     * 引用属性名固定为 `$ref`，与输入侧本地 `$ref` 解析保持一致；
     * 流式 `writeTo` 路径不参与引用写出。默认关闭，关闭时保持原有循环检测行为。
     */
    public val writeReferences: Boolean = false,
    /** 可选远程 `$ref` 解析器；为 `null` 时远程引用会报错。 */
    public val remoteReferenceResolver: RemoteReferenceResolver? = null,
    private val subtypeRegistries: Map<Class<*>, SubtypeRegistry> = emptyMap(),
    /**
     * 可选根对象 Bean 校验器；为 `null` 时不校验（默认）。
     *
     * 配置后在根对象序列化入口（`toNode`/`writeTo`）与反序列化出口
     * （`fromNode`/`fromTokens`/`readFrom`）各调用一次，嵌套调用不重复校验。
     * 校验失败统一抛出
     * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]。
     */
    public val beanValidator: DocumentBeanValidator? = null,
) {

    private val subtypeRegistryMap: SubtypeRegistries = SubtypeRegistries(subtypeRegistries)

    /**
     * 复制另一个配置。
     */
    protected constructor(other: DocumentMapperConfig) : this(
        other.fieldConverters,
        other.missingFieldPolicy,
        other.failOnUnknownFields,
        other.writeNulls,
        other.namingStrategy,
        other.fileCharset,
        other.atomicFileWrite,
        other.createParentDirectories,
        other.maxInputSize,
        other.maxStringLength,
        other.maxNodeDepth,
        other.maxNodeCount,
        other.resolveReferences,
        other.writeReferences,
        other.remoteReferenceResolver,
        other.subtypeRegistries,
        other.beanValidator,
    )

    internal fun fieldConverter(property: DocumentProperty): DocumentBaseConverter<*>? = fieldConverters[FieldKey(property.declaringClass, property.name)]

    internal val hasFieldConverters: Boolean
        get() = fieldConverters.isNotEmpty()

    /**
     * 是否为指定基类注册了多态子类型。
     */
    internal fun subtypeRegistry(baseClass: Class<*>): SubtypeRegistry? = subtypeRegistryMap.registryFor(baseClass)

    /**
     * 全部多态基类的子类型注册表；未注册任何子类型时为空集合。
     */
    internal fun subtypeRegistries(): Collection<SubtypeRegistry> = subtypeRegistryMap.all()

    /**
     * 是否注册了多态子类型。
     */
    internal val hasSubtypes: Boolean
        get() = !subtypeRegistryMap.isEmpty

    public companion object {

        /**
         * 创建配置构建器。
         */
        @JvmStatic
        public fun builder(): DocumentMapperConfigBuilder = DocumentMapperConfigBuilder()

        /**
         * 空配置。
         */
        @JvmStatic
        public fun empty(): DocumentMapperConfig = DocumentMapperConfig(emptyMap())

        internal fun from(
            fieldConverters: Map<FieldKey, DocumentBaseConverter<*>>,
            missingFieldPolicy: MissingFieldPolicy,
            failOnUnknownFields: Boolean,
            writeNulls: Boolean,
            namingStrategy: PropertyNamingStrategy,
            fileCharset: Charset,
            atomicFileWrite: Boolean,
            createParentDirectories: Boolean,
            maxInputSize: Long,
            maxStringLength: Int,
            maxNodeDepth: Int,
            maxNodeCount: Long,
            resolveReferences: Boolean,
            writeReferences: Boolean = false,
            remoteReferenceResolver: RemoteReferenceResolver? = null,
            subtypes: SubtypeRegistries = SubtypeRegistries.EMPTY,
            beanValidator: DocumentBeanValidator? = null,
        ): DocumentMapperConfig = DocumentMapperConfig(
            fieldConverters,
            missingFieldPolicy,
            failOnUnknownFields,
            writeNulls,
            namingStrategy,
            fileCharset,
            atomicFileWrite,
            createParentDirectories,
            maxInputSize,
            maxStringLength,
            maxNodeDepth,
            maxNodeCount,
            resolveReferences,
            writeReferences,
            remoteReferenceResolver,
            subtypes.toMap(),
            beanValidator,
        )
    }
}
