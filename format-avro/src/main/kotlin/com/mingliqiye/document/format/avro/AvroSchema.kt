package com.mingliqiye.document.format.avro

import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType

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

    public companion object {

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
