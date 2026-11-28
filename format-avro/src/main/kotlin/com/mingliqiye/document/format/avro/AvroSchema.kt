package com.mingliqiye.document.format.avro

import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import org.apache.avro.Schema

/**
 * Avro 记录 Schema。
 *
 * 只描述记录结构与字段类型映射：不依赖 Apache Avro 库，也不负责编解码。
 * 记录名（Avro 规范要求必填）对应 [DocumentSchema.name]，命名空间可空；
 * 字段保持声明顺序，字段类型为通用 [SchemaType]，通过 [avroType] 映射为 Avro 类型名。
 *
 * 字段名、记录名与命名空间按 Avro 规范校验；字段名不允许重复。
 *
 * @property name Avro 记录名
 * @property namespace Avro 命名空间；`null` 表示不带命名空间
 * @property fields 字段列表，保持声明顺序
 */
public class AvroSchema(
    override val name: String,
    public val namespace: String? = null,
    override val fields: List<SchemaField>,
) : DocumentSchema {

    /** 完整记录名：`namespace.name`；无命名空间时等于 [name]。 */
    public val fullName: String = if (namespace == null) name else "$namespace.$name"

    private val byName: Map<String, SchemaField> = fields.associateBy { it.name }

    init {
        validateAvroName("record name", name, allowNamespace = false)
        namespace?.let { validateAvroName("namespace", it, allowNamespace = true) }
        val seen = HashSet<String>(fields.size)
        for (field in fields) {
            validateAvroName("field name", field.name, allowNamespace = false)
            require(seen.add(field.name)) { "Duplicate Avro field name: '${field.name}'" }
        }
    }

    /**
     * 按字段名查找字段。
     *
     * @param name 字段名
     * @return 字段；不存在时返回 `null`
     */
    override fun field(name: String): SchemaField? = byName[name]

    /**
     * 查询字段对应的 Avro 类型。
     *
     * @param field 字段
     * @return Avro 类型
     */
    public fun avroType(field: SchemaField): AvroType = AvroType.fromSchemaType(field.type)

    /**
     * 按字段名查询 Avro 类型。
     *
     * @param name 字段名
     * @return Avro 类型；字段不存在时返回 `null`
     */
    public fun avroType(name: String): AvroType? = field(name)?.let(::avroType)

    /**
     * 按字段名查询 Avro 类型名。
     *
     * @param name 字段名
     * @return Avro 类型名（如 `string`）；字段不存在时返回 `null`
     */
    public fun avroTypeName(name: String): String? = avroType(name)?.typeName

    /**
     * 输出轻量 Avro Schema JSON 表示。
     *
     * 字段可空或非必填时按 Avro 惯用方式写成 `["null", "<type>"]` 并带 `"default": null`
     * （Avro 没有必填概念，非必填字段必须可空）；字段描述写为 `"doc"`。
     *
     * @return Avro Schema JSON 文本
     */
    public fun toJson(): String = buildString {
        append("{\"type\":\"record\",\"name\":\"")
        append(name)
        append('"')
        if (namespace != null) {
            append(",\"namespace\":\"")
            append(namespace)
            append('"')
        }
        append(",\"fields\":[")
        fields.forEachIndexed { index, field ->
            if (index > 0) {
                append(',')
            }
            append(fieldJson(field))
        }
        append("]}")
    }

    private fun fieldJson(field: SchemaField): String = buildString {
        val avroType = AvroType.fromSchemaType(field.type)
        append("{\"name\":\"")
        append(field.name)
        append('"')
        if (field.nullable || !field.required) {
            append(",\"type\":[\"null\",\"")
            append(avroType.typeName)
            append("\"],\"default\":null")
        } else {
            append(",\"type\":\"")
            append(avroType.typeName)
            append('"')
        }
        field.description?.let { description ->
            append(",\"doc\":\"")
            append(escapeJson(description))
            append('"')
        }
        append('}')
    }

    /**
     * 转换为 Apache Avro [Schema]。
     *
     * 记录名、命名空间、字段顺序与字段描述原样保留；可空或非必填字段写成
     * `["null", "<type>"]` 并带 `null` 默认值，与 Avro 推荐写法一致。
     *
     * 通用 [SchemaType] 不携带嵌套结构信息，复合字段按以下约定映射：
     * - [SchemaType.ARRAY] 映射为 `array<string>`
     * - [SchemaType.MAP] 映射为 `map<string>`
     * - [SchemaType.OBJECT] 映射为以 `<记录名>_<字段名>` 命名的空嵌套 record
     * - [SchemaType.DECIMAL] 映射为不含 precision/scale 的 `bytes`
     *
     * 通用 [SchemaType] 也不携带 logical type 属性，需要 date/time/timestamp/
     * decimal precision-scale/uuid/duration 等完整逻辑类型时，应直接使用 Apache
     * Avro Schema 构造 [AvroMapper]。
     *
     * 需要完整嵌套类型（自定义子 record、enum、fixed、非 string 元素）时，
     * 应直接使用 Apache Avro Schema 构造 [AvroMapper]。
     *
     * @return Apache Avro Schema
     */
    public fun toAvroSchema(): Schema {
        val record = Schema.createRecord(name, null, namespace, false)
        val avroFields = ArrayList<Schema.Field>(fields.size)
        for (field in fields) {
            avroFields.add(toAvroField(field))
        }
        record.setFields(avroFields)
        return record
    }

    private fun toAvroField(field: SchemaField): Schema.Field {
        val fieldSchema = toAvroFieldSchema(field)
        if (fieldSchema.type == Schema.Type.NULL) {
            return Schema.Field(field.name, fieldSchema, field.description, Schema.Field.NULL_DEFAULT_VALUE)
        }
        return if (field.nullable || !field.required) {
            val union = Schema.createUnion(Schema.create(Schema.Type.NULL), fieldSchema)
            Schema.Field(field.name, union, field.description, Schema.Field.NULL_DEFAULT_VALUE)
        } else {
            Schema.Field(field.name, fieldSchema, field.description)
        }
    }

    private fun toAvroFieldSchema(field: SchemaField): Schema = when (AvroType.fromSchemaType(field.type)) {
        AvroType.STRING -> Schema.create(Schema.Type.STRING)
        AvroType.INT -> Schema.create(Schema.Type.INT)
        AvroType.LONG -> Schema.create(Schema.Type.LONG)
        AvroType.DOUBLE -> Schema.create(Schema.Type.DOUBLE)
        AvroType.BOOLEAN -> Schema.create(Schema.Type.BOOLEAN)
        AvroType.BYTES -> Schema.create(Schema.Type.BYTES)
        AvroType.NULL -> Schema.create(Schema.Type.NULL)
        AvroType.ARRAY -> Schema.createArray(Schema.create(Schema.Type.STRING))
        AvroType.MAP -> Schema.createMap(Schema.create(Schema.Type.STRING))
        AvroType.OBJECT -> Schema.createRecord(
            "${name}_${field.name}",
            null,
            namespace,
            false,
            emptyList<Schema.Field>(),
        )
    }

    public companion object {

        /**
         * 从 Apache Avro [Schema] 构造 Avro Schema 视图。
         *
         * 只接受 record；字段顺序、字段描述与命名空间保留，`["null", X]` 形式的
         * 字段标记为可空且非必填。通用 [SchemaType] 无法表达的类型按以下方式归一：
         * - `float` 归一为 [SchemaType.DOUBLE]
         * - `enum` 归一为 [SchemaType.STRING]
         * - `fixed` 归一为 [SchemaType.BYTES]（decimal 逻辑类型归一为 [SchemaType.DECIMAL]）
         * - 其它已知 logical type 只保留底层类型：`date`/`time-millis` 归一为
         *   [SchemaType.INT]，`time-micros`/`timestamp-millis`/`timestamp-micros`/
         *   `local-timestamp-millis`/`local-timestamp-micros` 归一为
         *   [SchemaType.LONG]，`uuid` 归一为 [SchemaType.STRING]，`duration`
         *   归一为 [SchemaType.BYTES]；完整逻辑类型信息请直接查看 Apache Avro
         *   Schema 或 [AvroLogicalType.fromSchema]
         * - 含多个分支的 union 只保留第一个非 null 分支
         * - 嵌套 record 归一为 [SchemaType.OBJECT]
         *
         * @param schema Apache Avro Schema
         * @return Avro Schema 视图
         * @throws IllegalArgumentException Schema 不是 record 时抛出
         */
        @JvmStatic
        public fun fromAvroSchema(schema: Schema): AvroSchema {
            require(schema.type == Schema.Type.RECORD) {
                "Avro schema must be a record but was ${schema.type}"
            }
            val schemaFields = ArrayList<SchemaField>(schema.fields.size)
            for (field in schema.fields) {
                val nullable = isNullableAvroType(field.schema())
                schemaFields.add(
                    SchemaField(
                        field.name(),
                        schemaTypeOf(nonNullAvroType(field.schema())),
                        required = !nullable,
                        nullable = nullable,
                        description = field.doc(),
                    ),
                )
            }
            return AvroSchema(schema.name, schema.namespace, schemaFields)
        }

        /**
         * 创建 Avro Schema 构建器。
         *
         * @param name Avro 记录名
         * @return 构建器
         */
        @JvmStatic
        public fun builder(name: String): AvroSchemaBuilder = AvroSchemaBuilder(name)

        /**
         * 创建 Avro Schema。
         *
         * @param name Avro 记录名
         * @param namespace Avro 命名空间；`null` 表示不带命名空间
         * @param fields 字段列表
         * @return Schema
         */
        @JvmStatic
        public fun of(name: String, namespace: String?, vararg fields: SchemaField): AvroSchema = AvroSchema(name, namespace, fields.toList())

        /**
         * 创建不带命名空间的 Avro Schema。
         *
         * @param name Avro 记录名
         * @param fields 字段列表
         * @return Schema
         */
        @JvmStatic
        public fun of(name: String, vararg fields: SchemaField): AvroSchema = AvroSchema(name, null, fields.toList())
    }
}

/** Avro 简单名（记录名、字段名）校验：字母或下划线开头，只包含字母、数字、下划线。 */
private val avroNamePattern = Regex("[A-Za-z_][A-Za-z0-9_]*")

/** Avro 命名空间校验：点分简单名。 */
private val avroNamespacePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

private fun validateAvroName(kind: String, value: String, allowNamespace: Boolean) {
    require(value.isNotEmpty()) { "Avro $kind must not be empty" }
    val pattern = if (allowNamespace) avroNamespacePattern else avroNamePattern
    require(pattern.matches(value)) { "Invalid Avro $kind: '$value'" }
}

private fun escapeJson(value: String): String = buildString(value.length) {
    for (char in value) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (char < ' ') {
                append("\\u")
                append(char.code.toString(16).padStart(4, '0'))
            } else {
                append(char)
            }
        }
    }
}

/** 判断 Avro 类型是否可空：`null` 类型或包含 `null` 分支的 union。 */
private fun isNullableAvroType(schema: Schema): Boolean = schema.type == Schema.Type.NULL ||
    (schema.type == Schema.Type.UNION && schema.types.any { it.type == Schema.Type.NULL })

/** 取 Avro union 中第一个非 `null` 分支；非 union 原样返回。 */
private fun nonNullAvroType(schema: Schema): Schema = if (schema.type != Schema.Type.UNION) {
    schema
} else {
    schema.types.firstOrNull { it.type != Schema.Type.NULL } ?: Schema.create(Schema.Type.NULL)
}

/** 将 Avro 类型归一为通用 [SchemaType]。 */
private fun schemaTypeOf(schema: Schema): SchemaType = when (schema.type) {
    Schema.Type.NULL -> SchemaType.NULL
    Schema.Type.BOOLEAN -> SchemaType.BOOLEAN
    Schema.Type.INT -> SchemaType.INT
    Schema.Type.LONG -> SchemaType.LONG
    Schema.Type.FLOAT, Schema.Type.DOUBLE -> SchemaType.DOUBLE
    Schema.Type.STRING -> SchemaType.STRING
    Schema.Type.BYTES -> if (isDecimalAvroType(schema)) SchemaType.DECIMAL else SchemaType.BYTES
    Schema.Type.FIXED -> if (isDecimalAvroType(schema)) SchemaType.DECIMAL else SchemaType.BYTES
    Schema.Type.ENUM -> SchemaType.STRING
    Schema.Type.ARRAY -> SchemaType.ARRAY
    Schema.Type.MAP -> SchemaType.MAP
    Schema.Type.RECORD -> SchemaType.OBJECT
    Schema.Type.UNION -> schemaTypeOf(nonNullAvroType(schema))
}

/** 判断 Avro 类型是否带 `decimal` 逻辑类型。 */
private fun isDecimalAvroType(schema: Schema): Boolean = schema.logicalType?.name == "decimal"
