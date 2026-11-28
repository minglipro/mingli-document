package com.mingliqiye.document.format.csv

import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType

/**
 * [CsvSchema] 构建器。
 *
 * 字段添加顺序即列顺序。便捷方法 [string]、[int]、[long]、[double]、[decimal]、
 * [boolean]、[bytes] 把常用类型映射到对应的 [SchemaType]；[objectField] 声明带嵌套子
 * Schema 的对象字段，[arrayField] 声明可带元素 Schema 的数组字段。
 *
 * 格式选项对齐 上游实现 `CsvSchema.Builder`：[columnSeparator]、[quoteChar]、
 * [escapeChar]、[lineSeparator]、[arrayElementSeparator]、[nullValue]、
 * [skipFirstDataRow]、[allowComments]、[header]；[orderColumns] 在构建时重排列顺序。
 *
 * 嵌套子 Schema 使字段获得点分列名（如 `address.city`），列名在 [build] 时统一校验重复。
 */
public class CsvSchemaBuilder(
    private val name: String,
) {

    private val fields = ArrayList<SchemaField>()
    private val nestedObjects = LinkedHashMap<String, CsvSchema>()
    private val nestedArrays = LinkedHashMap<String, CsvSchema>()
    private var header: Boolean = DEFAULT_CSV_SCHEMA_HEADER
    private var failOnUnknownColumns: Boolean = DEFAULT_CSV_SCHEMA_FAIL_ON_UNKNOWN_COLUMNS
    private var columnSeparator: Char? = null
    private var quoteChar: Char? = CsvSchema.DEFAULT_QUOTE_CHAR
    private var escapeChar: Char? = null
    private var lineSeparator: String = CsvSchema.DEFAULT_LINE_SEPARATOR
    private var arrayElementSeparator: String? = null
    private var nullValue: String? = null
    private var skipFirstDataRow = false
    private var allowComments = false
    private var columnOrder: List<String>? = null

    /**
     * 添加字段。
     *
     * @param name 列名
     * @param type 字段类型
     * @param required 是否必填
     * @param nullable 是否允许空值
     * @param description 字段描述
     * @return 当前构建器
     */
    public fun field(
        name: String,
        type: SchemaType,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
    ): CsvSchemaBuilder {
        fields.add(SchemaField(name, type, required, nullable, description))
        return this
    }

    /**
     * 添加已定义的字段。
     *
     * @param field 字段定义
     * @return 当前构建器
     */
    public fun addField(field: SchemaField): CsvSchemaBuilder {
        fields.add(field)
        return this
    }

    /**
     * 添加字符串列（对齐 上游实现 `CsvSchema.Builder.addColumn`）。
     *
     * @param name 列名
     * @param required 是否必填
     * @param nullable 是否允许空值
     * @return 当前构建器
     */
    public fun addColumn(
        name: String,
        required: Boolean = true,
        nullable: Boolean = false,
    ): CsvSchemaBuilder = field(name, SchemaType.STRING, required, nullable)

    /**
     * 批量添加字符串列（对齐 上游实现 `CsvSchema.Builder.addColumns`）。
     *
     * @param names 列名
     * @return 当前构建器
     */
    public fun addColumns(vararg names: String): CsvSchemaBuilder {
        for (column in names) {
            addColumn(column)
        }
        return this
    }

    /**
     * 添加带嵌套子 Schema 的对象字段。
     *
     * 该字段的子 Schema 字段展开为点分列名（如 `address.city`），支持多级嵌套；写出时按
     * 子 Schema 递归展开嵌套对象。
     *
     * @param name 列名（点分列名的前缀）
     * @param nested 嵌套子 Schema
     * @param required 是否必填
     * @param nullable 是否允许空值
     * @param description 字段描述
     * @return 当前构建器
     */
    public fun objectField(
        name: String,
        nested: CsvSchema,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
    ): CsvSchemaBuilder {
        fields.add(SchemaField(name, SchemaType.OBJECT, required, nullable, description))
        nestedObjects[name] = nested
        return this
    }

    /**
     * 添加数组字段。
     *
     * 数组默认以 JSON 文本单元格读写；提供 [element] 时每个元素按元素 Schema 校验与排序，
     * 未提供时元素按 JSON 原样保留（需要 [CsvConfig.nestedMode] 为
     * [CsvNestedMode.JSON]）。设置 [arrayElementSeparator] 后改为 上游实现 风格的
     * 分隔文本数组。
     *
     * @param name 列名
     * @param element 元素 Schema；null 表示元素不做 Schema 约束
     * @param required 是否必填
     * @param nullable 是否允许空值
     * @param description 字段描述
     * @return 当前构建器
     */
    public fun arrayField(
        name: String,
        element: CsvSchema? = null,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
    ): CsvSchemaBuilder {
        fields.add(SchemaField(name, SchemaType.ARRAY, required, nullable, description))
        if (element != null) {
            nestedArrays[name] = element
        }
        return this
    }

    /**
     * 添加 [SchemaType.STRING] 字段。
     */
    public fun string(
        name: String,
        required: Boolean = true,
        nullable: Boolean = false,
    ): CsvSchemaBuilder = field(name, SchemaType.STRING, required, nullable)

    /**
     * 添加 [SchemaType.INT] 字段。
     */
    public fun int(
        name: String,
        required: Boolean = true,
        nullable: Boolean = false,
    ): CsvSchemaBuilder = field(name, SchemaType.INT, required, nullable)

    /**
     * 添加 [SchemaType.LONG] 字段。
     */
    public fun long(
        name: String,
        required: Boolean = true,
        nullable: Boolean = false,
    ): CsvSchemaBuilder = field(name, SchemaType.LONG, required, nullable)

    /**
     * 添加 [SchemaType.DOUBLE] 字段。
     */
    public fun double(
        name: String,
        required: Boolean = true,
        nullable: Boolean = false,
    ): CsvSchemaBuilder = field(name, SchemaType.DOUBLE, required, nullable)

    /**
     * 添加 [SchemaType.DECIMAL] 字段。
     */
    public fun decimal(
        name: String,
        required: Boolean = true,
        nullable: Boolean = false,
    ): CsvSchemaBuilder = field(name, SchemaType.DECIMAL, required, nullable)

    /**
     * 添加 [SchemaType.BOOLEAN] 字段。
     */
    public fun boolean(
        name: String,
        required: Boolean = true,
        nullable: Boolean = false,
    ): CsvSchemaBuilder = field(name, SchemaType.BOOLEAN, required, nullable)

    /**
     * 添加 [SchemaType.BYTES] 字段。
     */
    public fun bytes(
        name: String,
        required: Boolean = true,
        nullable: Boolean = false,
    ): CsvSchemaBuilder = field(name, SchemaType.BYTES, required, nullable)

    /**
     * 设置是否读写表头行。
     *
     * @param header 是否读写表头行
     * @return 当前构建器
     */
    public fun header(header: Boolean): CsvSchemaBuilder {
        this.header = header
        return this
    }

    /**
     * 设置遇到未声明列时是否抛出异常。
     *
     * @param fail 是否抛出异常
     * @return 当前构建器
     */
    public fun failOnUnknownColumns(fail: Boolean): CsvSchemaBuilder {
        failOnUnknownColumns = fail
        return this
    }

    /**
     * 设置列分隔符（对齐 上游实现 `withColumnSeparator`）。
     *
     * @param separator 列分隔符
     * @return 当前构建器
     */
    public fun columnSeparator(separator: Char): CsvSchemaBuilder {
        columnSeparator = separator
        return this
    }

    /**
     * 设置引号字符；传 `null` 关闭引号（对齐 上游实现 `withQuoteChar` / `withoutQuoteChar`）。
     *
     * @param quoteChar 引号字符；null 表示关闭引号
     * @return 当前构建器
     */
    public fun quoteChar(quoteChar: Char?): CsvSchemaBuilder {
        this.quoteChar = quoteChar
        return this
    }

    /**
     * 设置转义字符；传 `null` 关闭转义字符（对齐 上游实现 `withEscapeChar` / `withoutEscapeChar`）。
     *
     * @param escapeChar 转义字符；null 表示关闭
     * @return 当前构建器
     */
    public fun escapeChar(escapeChar: Char?): CsvSchemaBuilder {
        this.escapeChar = escapeChar
        return this
    }

    /**
     * 设置行分隔符（写出用，对齐 上游实现 `withLineSeparator`）。
     *
     * @param separator 行分隔符，不能为空
     * @return 当前构建器
     */
    public fun lineSeparator(separator: String): CsvSchemaBuilder {
        require(separator.isNotEmpty()) { "lineSeparator must not be empty" }
        lineSeparator = separator
        return this
    }

    /**
     * 设置数组单元格元素分隔符；`null` 表示数组使用 JSON 文本（本库扩展）。
     *
     * @param separator 元素分隔符；null 表示 JSON 文本数组
     * @return 当前构建器
     */
    public fun arrayElementSeparator(separator: String?): CsvSchemaBuilder {
        require(separator == null || separator.isNotEmpty()) { "arrayElementSeparator must not be empty" }
        arrayElementSeparator = separator
        return this
    }

    /**
     * 设置 null 值写出文本；`null` 表示空串（对齐 上游实现 `withNullValue`）。
     *
     * @param value null 值文本
     * @return 当前构建器
     */
    public fun nullValue(value: String?): CsvSchemaBuilder {
        nullValue = value
        return this
    }

    /**
     * 设置无表头读取时是否跳过第一条数据行（对齐 上游实现 `withSkipFirstDataRow`）。
     *
     * @param skip 是否跳过首条数据行
     * @return 当前构建器
     */
    public fun skipFirstDataRow(skip: Boolean): CsvSchemaBuilder {
        skipFirstDataRow = skip
        return this
    }

    /**
     * 设置是否允许 `#` 注释行（对齐 上游实现 `withAllowComments`）。
     *
     * @param allow 是否允许注释
     * @return 当前构建器
     */
    public fun allowComments(allow: Boolean): CsvSchemaBuilder {
        allowComments = allow
        return this
    }

    /**
     * 设置顶层列顺序（对齐 上游实现 `sortedBy` 的构建期版本）。
     *
     * 未列出的字段保持原相对顺序排在末尾。
     *
     * @param columnNames 顶层字段名，按期望顺序
     * @return 当前构建器
     */
    public fun orderColumns(vararg columnNames: String): CsvSchemaBuilder {
        columnOrder = columnNames.toList()
        return this
    }

    /**
     * 构建 CSV Schema。
     *
     * @return CSV Schema
     * @throws IllegalArgumentException Schema 名称为空、没有字段、列名为空、列名重复（含嵌套
     *   展开后的点分列名）、嵌套字段类型不符、列重排引用未知列或格式选项非法时抛出
     */
    public fun build(): CsvSchema {
        require(name.isNotBlank()) { "CSV schema name must not be blank" }
        require(fields.isNotEmpty()) { "CSV schema '$name' must declare at least one field" }
        val seen = HashSet<String>(fields.size)
        for (field in fields) {
            require(field.name.isNotBlank()) { "CSV schema '$name' has a blank column name" }
            require(seen.add(field.name)) { "Duplicate CSV column '${field.name}' in schema '$name'" }
        }
        validateNestedFields()
        val base = CsvSchema(
            name,
            fields.toList(),
            header,
            failOnUnknownColumns,
            nestedObjects.toMap(),
            nestedArrays.toMap(),
            columnSeparator,
            quoteChar,
            escapeChar,
            lineSeparator,
            arrayElementSeparator,
            nullValue,
            skipFirstDataRow,
            allowComments,
        )
        val seenColumns = HashSet<String>(base.columns.size)
        for (column in base.columns) {
            require(seenColumns.add(column.column)) {
                "Duplicate CSV column '${column.column}' in schema '$name'"
            }
        }
        val order = columnOrder ?: return base
        return base.sortedBy(*order.toTypedArray())
    }

    private fun validateNestedFields() {
        val typeByName = fields.associate { it.name to it.type }
        for (fieldName in nestedObjects.keys) {
            require(typeByName[fieldName] == SchemaType.OBJECT) {
                "Nested CSV object field '$fieldName' in schema '$name' must use SchemaType.OBJECT"
            }
            require(fieldName.isNotBlank()) { "CSV schema '$name' has a blank column name" }
        }
        for (fieldName in nestedArrays.keys) {
            require(typeByName[fieldName] == SchemaType.ARRAY) {
                "CSV array field '$fieldName' in schema '$name' must use SchemaType.ARRAY"
            }
        }
    }
}
