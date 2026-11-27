package com.mingliqiye.document.format.csv

import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy

/**
 * [CsvConfig] 构建器。
 */
public class CsvConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var delimiter: Char = DEFAULT_CSV_DELIMITER
    private var header: Boolean = DEFAULT_CSV_HEADER
    private var schema: CsvSchema? = null

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
     * 设置字段分隔符。
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
    public fun build(): CsvConfig = CsvConfig(base.build(), delimiter, header, schema)
}
