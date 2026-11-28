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
     * 数组以 JSON 文本单元格读写；提供 [element] 时每个元素按元素 Schema 校验与排序，
     * 未提供时元素按 JSON 原样保留（需要 [CsvConfig.nestedMode] 为
     * [CsvNestedMode.JSON]）。
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
     * 构建 CSV Schema。
     *
     * @return CSV Schema
     * @throws IllegalArgumentException Schema 名称为空、没有字段、列名为空、列名重复（含嵌套
     *   展开后的点分列名）、嵌套字段类型不符时抛出
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
        val schema = CsvSchema(
            name,
            fields.toList(),
            header,
            failOnUnknownColumns,
            nestedObjects.toMap(),
            nestedArrays.toMap(),
        )
        val seenColumns = HashSet<String>(schema.columns.size)
        for (column in schema.columns) {
            require(seenColumns.add(column.column)) {
                "Duplicate CSV column '${column.column}' in schema '$name'"
            }
        }
        return schema
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
