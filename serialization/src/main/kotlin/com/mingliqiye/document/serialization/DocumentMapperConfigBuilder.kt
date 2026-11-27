package com.mingliqiye.document.serialization

import java.nio.charset.Charset
import java.util.LinkedHashMap

/**
 * [DocumentMapperConfig] 构建器。
 *
 * 除通用字段/读写选项外，可通过 [subtype] 与 [typeProperty] 显式注册多态子类型；
 * 只使用注册过的子类型，不会反射扫描子类。
 */
public class DocumentMapperConfigBuilder {

    private val fieldConverters = LinkedHashMap<FieldKey, DocumentBaseConverter<*>>()
    private val subtypeRegistrations = LinkedHashMap<Class<*>, MutableList<SubtypeRegistration>>()
    private val subtypeTypeProperties = LinkedHashMap<Class<*>, String>()
    private var missingFieldPolicy = MissingFieldPolicy.KEEP_DEFAULT
    private var failOnUnknownFields = false
    private var writeNulls = true
    private var namingStrategy = PropertyNamingStrategy.AS_IS
    private var fileCharset: Charset = Charsets.UTF_8
    private var atomicFileWrite = false
    private var createParentDirectories = false
    private var maxInputSize = 0L
    private var maxNodeDepth = 0
    private var maxNodeCount = 0L
    private var resolveReferences = false
    private var writeReferences = false
    private var remoteReferenceResolver: RemoteReferenceResolver? = null

    /**
     * 为指定字段绑定转换器。
     *
     * @param declaringClass 声明类
     * @param name 属性名
     * @param converter 字段转换器
     * @return 当前构建器
     */
    public fun field(
        declaringClass: Class<*>,
        name: String,
        converter: DocumentBaseConverter<*>,
    ): DocumentMapperConfigBuilder {
        fieldConverters[FieldKey(declaringClass, name)] = converter
        return this
    }

    /**
     * 注册多态子类型。
     *
     * 字段声明类型是 [baseClass] 且实际值类型是 [subtypeClass] 时，序列化会额外写出
     * 类型属性；反序列化按该属性解析到已注册的子类。类型属性名默认取基类上的
     * [com.mingliqiye.document.annotation.DocumentTypeInfo]，未标注时为 `type`；
     * 子类上的 [com.mingliqiye.document.annotation.DocumentTypeName] 优先于
     * [typeName]，注册名保留为反序列化别名。
     *
     * 只使用显式注册的子类型，不会反射扫描子类。
     *
     * @param baseClass 多态基类/接口
     * @param typeName 反序列化可识别的类型名，不能为空
     * @param subtypeClass 具体子类，必须可赋值给 [baseClass]
     * @return 当前构建器
     */
    public fun subtype(
        baseClass: Class<*>,
        typeName: String,
        subtypeClass: Class<*>,
    ): DocumentMapperConfigBuilder = registerSubtype(baseClass, typeName, subtypeClass, null)

    /**
     * 注册多态子类型，并覆盖该基类的类型属性名。
     *
     * @param baseClass 多态基类/接口
     * @param typeName 反序列化可识别的类型名，不能为空
     * @param subtypeClass 具体子类，必须可赋值给 [baseClass]
     * @param typeProperty 类型属性名，不能为空
     * @return 当前构建器
     */
    public fun subtype(
        baseClass: Class<*>,
        typeName: String,
        subtypeClass: Class<*>,
        typeProperty: String,
    ): DocumentMapperConfigBuilder = registerSubtype(baseClass, typeName, subtypeClass, typeProperty)

    /**
     * 覆盖指定基类在文档中的类型属性名。
     *
     * 优先级高于 [com.mingliqiye.document.annotation.DocumentTypeInfo]；必须在
     * [build] 前至少注册一个该基类的子类型。
     *
     * @param baseClass 多态基类/接口
     * @param typeProperty 类型属性名，不能为空
     * @return 当前构建器
     */
    public fun typeProperty(
        baseClass: Class<*>,
        typeProperty: String,
    ): DocumentMapperConfigBuilder {
        require(typeProperty.isNotBlank()) { "typeProperty must not be blank" }
        subtypeTypeProperties[baseClass] = typeProperty
        return this
    }

    private fun registerSubtype(
        baseClass: Class<*>,
        typeName: String,
        subtypeClass: Class<*>,
        typeProperty: String?,
    ): DocumentMapperConfigBuilder {
        require(typeName.isNotBlank()) { "typeName must not be blank" }
        if (typeProperty != null) {
            this.typeProperty(baseClass, typeProperty)
        }
        subtypeRegistrations.computeIfAbsent(baseClass) { ArrayList() }
            .add(SubtypeRegistration(typeName, subtypeClass))
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     *
     * @param policy 缺失字段策略
     * @return 当前构建器
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): DocumentMapperConfigBuilder {
        this.missingFieldPolicy = policy
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     *
     * @param fail 是否失败
     * @return 当前构建器
     */
    public fun failOnUnknownFields(fail: Boolean): DocumentMapperConfigBuilder {
        this.failOnUnknownFields = fail
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     *
     * @param write 是否写出 null 字段
     * @return 当前构建器
     */
    public fun writeNulls(write: Boolean): DocumentMapperConfigBuilder {
        this.writeNulls = write
        return this
    }

    /**
     * 设置未标注 [com.mingliqiye.document.annotation.DocumentName] 的字段命名策略。
     *
     * @param strategy 字段命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): DocumentMapperConfigBuilder {
        this.namingStrategy = strategy
        return this
    }

    /**
     * 设置文本文件/流读写字符集。
     *
     * @param charset 字符集
     * @return 当前构建器
     */
    public fun fileCharset(charset: Charset): DocumentMapperConfigBuilder {
        this.fileCharset = charset
        return this
    }

    /**
     * 设置写文件时是否原子替换。
     *
     * @param atomic 是否原子写入
     * @return 当前构建器
     */
    public fun atomicFileWrite(atomic: Boolean): DocumentMapperConfigBuilder {
        this.atomicFileWrite = atomic
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     *
     * @param create 是否创建父目录
     * @return 当前构建器
     */
    public fun createParentDirectories(create: Boolean): DocumentMapperConfigBuilder {
        this.createParentDirectories = create
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     *
     * @param max 最大字节数
     * @return 当前构建器
     */
    public fun maxInputSize(max: Long): DocumentMapperConfigBuilder {
        require(max >= 0) { "maxInputSize must be >= 0" }
        this.maxInputSize = max
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制。
     *
     * @param max 最大深度
     * @return 当前构建器
     */
    public fun maxNodeDepth(max: Int): DocumentMapperConfigBuilder {
        require(max >= 0) { "maxNodeDepth must be >= 0" }
        this.maxNodeDepth = max
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     *
     * @param max 最大节点数
     * @return 当前构建器
     */
    public fun maxNodeCount(max: Long): DocumentMapperConfigBuilder {
        require(max >= 0) { "maxNodeCount must be >= 0" }
        this.maxNodeCount = max
        return this
    }

    /**
     * 设置反序列化前是否解析本地 `$ref`。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): DocumentMapperConfigBuilder {
        this.resolveReferences = resolve
        return this
    }

    /**
     * 设置 [com.mingliqiye.document.serialization.DocumentMapper.toNode] 序列化时
     * 是否按对象身份写出本地 `$ref`。
     *
     * 开启后同一对象只在首次出现处展开，再次出现时写出 `{"$ref":"#/首次路径"}`，
     * 循环引用也会自然变成引用而不再抛出循环引用异常。流式 `writeTo` 路径不受影响。
     * 默认关闭。
     *
     * @param write 是否写出对象身份引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): DocumentMapperConfigBuilder {
        this.writeReferences = write
        return this
    }

    /**
     * 设置远程 `$ref` 解析器。
     *
     * @param resolver 远程引用解析器；`null` 表示禁用远程引用
     * @return 当前构建器
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): DocumentMapperConfigBuilder {
        this.remoteReferenceResolver = resolver
        return this
    }

    /**
     * 构建不可变配置。
     *
     * @throws IllegalArgumentException 子类型注册项非法（如子类不可赋值给基类、
     * 类型名重复或为空）时抛出
     */
    public fun build(): DocumentMapperConfig = DocumentMapperConfig.from(
        LinkedHashMap(fieldConverters),
        missingFieldPolicy,
        failOnUnknownFields,
        writeNulls,
        namingStrategy,
        fileCharset,
        atomicFileWrite,
        createParentDirectories,
        maxInputSize,
        maxNodeDepth,
        maxNodeCount,
        resolveReferences,
        writeReferences,
        remoteReferenceResolver,
        SubtypeRegistries.build(subtypeRegistrations, subtypeTypeProperties),
    )
}
