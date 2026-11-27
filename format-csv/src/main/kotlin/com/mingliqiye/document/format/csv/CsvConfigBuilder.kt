package com.mingliqiye.document.format.csv

import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonConverter
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
        converter: JsonConverter<*>,
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
     * 构建配置。
     */
    public fun build(): CsvConfig = CsvConfig(base.build(), delimiter, header, schema)
}
