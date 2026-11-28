package com.mingliqiye.document.format.csv

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import java.nio.charset.Charset

/**
 * [CsvConfig] 构建器。
 *
 * 除 CSV 专属选项外，转发全部 [DocumentMapperConfig] 便捷选项（命名策略、字符集、文件
 * 读写、Node 限制、引用解析、Bean 校验、反序列化特性），并支持 上游实现 风格的
 * [CsvReadFeature] / [CsvWriteFeature] `enable` / `disable` / `configure`。
 */
public class CsvConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var delimiter: Char = DEFAULT_CSV_DELIMITER
    private var header: Boolean = DEFAULT_CSV_HEADER
    private var schema: CsvSchema? = null
    private var nestedMode: CsvNestedMode? = null
    private var maxDepth = CsvConfig.DEFAULT_MAX_DEPTH
    private val readFeatures = LinkedHashSet(CsvConfig.DEFAULT_READ_FEATURES)
    private val writeFeatures = LinkedHashSet(CsvConfig.DEFAULT_WRITE_FEATURES)

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
    ): CsvConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): CsvConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     */
    public fun failOnUnknownFields(fail: Boolean): CsvConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     */
    public fun writeNulls(write: Boolean): CsvConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * 设置未标注 `@DocumentName` 的字段命名策略。
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): CsvConfigBuilder {
        base.namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写字符集，默认 UTF-8。
     */
    public fun fileCharset(charset: Charset): CsvConfigBuilder {
        base.fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否临时文件 + 原子替换。
     */
    public fun atomicFileWrite(atomic: Boolean): CsvConfigBuilder {
        base.atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     */
    public fun createParentDirectories(create: Boolean): CsvConfigBuilder {
        base.createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数；0 表示不限制。
     */
    public fun maxInputSize(max: Long): CsvConfigBuilder {
        base.maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树单个字符串最大长度；0 表示不限制。
     */
    public fun maxStringLength(max: Int): CsvConfigBuilder {
        base.maxStringLength(max)
        return this
    }

    /**
     * 设置 Node 树最大深度；0 表示不限制。
     */
    public fun maxNodeDepth(max: Int): CsvConfigBuilder {
        base.maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数；0 表示不限制。
     */
    public fun maxNodeCount(max: Long): CsvConfigBuilder {
        base.maxNodeCount(max)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器；`null` 表示禁用远程引用。
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): CsvConfigBuilder {
        base.remoteReferenceResolver(resolver)
        return this
    }

    /**
     * 设置根对象 Bean 校验器；`null` 表示不校验。
     */
    public fun beanValidator(validator: DocumentBeanValidator?): CsvConfigBuilder {
        base.beanValidator(validator)
        return this
    }

    /**
     * 开启指定反序列化特性。
     */
    public fun enable(feature: DeserializationFeature): CsvConfigBuilder {
        base.enable(feature)
        return this
    }

    /**
     * 关闭指定反序列化特性。
     */
    public fun disable(feature: DeserializationFeature): CsvConfigBuilder {
        base.disable(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭指定反序列化特性。
     */
    public fun configure(feature: DeserializationFeature, enabled: Boolean): CsvConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 便捷设置 `FAIL_ON_NULL_FOR_PRIMITIVES`。
     */
    public fun failOnNullForPrimitives(fail: Boolean): CsvConfigBuilder = configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, fail)

    /**
     * 设置字段分隔符。
     *
     * 配置 Schema 后，[CsvSchema.columnSeparator] 非 null 时优先于本选项。
     */
    public fun delimiter(delimiter: Char): CsvConfigBuilder {
        this.delimiter = delimiter
        return this
    }

    /**
     * 设置是否使用表头。
     *
     * 设置 Schema 后本选项被 [CsvSchema.header] 覆盖。
     */
    public fun header(header: Boolean): CsvConfigBuilder {
        this.header = header
        return this
    }

    /**
     * 设置 CSV Schema。
     *
     * 设置后按 Schema 的列顺序与字段类型读写，且 [CsvSchema.header] 优先于 [header]。
     *
     * @param schema CSV Schema；null 表示不使用 Schema
     * @return 当前构建器
     */
    public fun schema(schema: CsvSchema?): CsvConfigBuilder {
        this.schema = schema
        return this
    }

    /**
     * 设置嵌套值读写方式。
     *
     * [CsvNestedMode.JSON] 把 `{...}` / `[...]` 单元格解析为嵌套节点、把嵌套节点写出为
     * JSON 文本；[CsvNestedMode.COLUMNS] 按点分列名读写嵌套对象。传 `null` 关闭嵌套
     * 支持（默认），保持旧版本行为。
     *
     * @param mode 嵌套值读写方式；null 表示不支持嵌套
     * @return 当前构建器
     */
    public fun nestedMode(mode: CsvNestedMode?): CsvConfigBuilder {
        this.nestedMode = mode
        return this
    }

    /**
     * 设置解析最大嵌套深度，默认 [CsvConfig.DEFAULT_MAX_DEPTH]（512）。
     *
     * 根数组深度为 `1`、行对象为 `2`、单元格标量为 `3`；点分列名/嵌套 Schema 每多一层
     * 加 `1`，JSON 文本单元格内部嵌套从 `3` 起算。超限抛
     * [com.mingliqiye.document.format.csv.exception.CsvParseException]。
     *
     * @param maxDepth 最大嵌套深度，必须为正数
     * @return 当前构建器
     * @throws IllegalArgumentException `maxDepth` 小于等于 0 时抛出
     */
    public fun maxDepth(maxDepth: Int): CsvConfigBuilder {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
        this.maxDepth = maxDepth
        return this
    }

    /**
     * 开启指定 CSV 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: CsvReadFeature): CsvConfigBuilder {
        readFeatures.add(feature)
        return this
    }

    /**
     * 关闭指定 CSV 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: CsvReadFeature): CsvConfigBuilder {
        readFeatures.remove(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭指定 CSV 读取特性。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: CsvReadFeature, enabled: Boolean): CsvConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启指定 CSV 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: CsvWriteFeature): CsvConfigBuilder {
        writeFeatures.add(feature)
        return this
    }

    /**
     * 关闭指定 CSV 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: CsvWriteFeature): CsvConfigBuilder {
        writeFeatures.remove(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭指定 CSV 写出特性。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: CsvWriteFeature, enabled: Boolean): CsvConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 注册多态子类型。
     *
     * @param baseClass 基类/接口
     * @param typeName 类型名
     * @param subtypeClass 子类型
     * @return 当前构建器
     */
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>): CsvConfigBuilder {
        base.subtype(baseClass, typeName, subtypeClass)
        return this
    }

    /**
     * 注册多态子类型并覆盖类型属性名。
     *
     * @param baseClass 基类/接口
     * @param typeName 类型名
     * @param subtypeClass 子类型
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>, typeProperty: String): CsvConfigBuilder {
        base.subtype(baseClass, typeName, subtypeClass, typeProperty)
        return this
    }

    /**
     * 覆盖指定多态基类的类型属性名。
     *
     * @param baseClass 基类/接口
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): CsvConfigBuilder {
        base.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 $ref。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): CsvConfigBuilder {
        base.resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否输出对象身份 $ref。
     *
     * @param write 是否输出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): CsvConfigBuilder {
        base.writeReferences(write)
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): CsvConfig = CsvConfig(
        base.build(),
        delimiter,
        header,
        schema,
        nestedMode,
        maxDepth,
        readFeatures.toSet(),
        writeFeatures.toSet(),
    )
}
