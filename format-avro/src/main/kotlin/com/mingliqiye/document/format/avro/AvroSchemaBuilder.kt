package com.mingliqiye.document.format.avro

import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType

/**
 * [AvroSchema] 构建器。
 *
 * 用法与 [com.mingliqiye.document.schema.DocumentSchemaBuilder] 一致，
 * 额外支持 Avro 命名空间与 Avro 类型名；字段在 [build] 时统一校验。
 */
public class AvroSchemaBuilder(
    private val name: String,
) {

    private val fields = ArrayList<SchemaField>()
    private var namespace: String? = null

    /**
     * 设置 Avro 命名空间。
     *
     * @param namespace 命名空间；`null` 表示不带命名空间
     * @return 当前构建器
     */
    public fun namespace(namespace: String?): AvroSchemaBuilder {
        this.namespace = namespace
        return this
    }

    /**
     * 添加字段（使用通用 Schema 类型）。
     *
     * @param name 字段名
     * @param type 通用 Schema 类型
     * @param required 是否必填
     * @param nullable 是否允许 null
     * @param description 字段描述
     * @return 当前构建器
     */
    public fun field(
        name: String,
        type: SchemaType,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
    ): AvroSchemaBuilder {
        fields.add(SchemaField(name, type, required, nullable, description))
        return this
    }

    /**
     * 添加字段（使用 Avro 类型）。
     *
     * @param name 字段名
     * @param avroType Avro 类型
     * @param required 是否必填
     * @param nullable 是否允许 null
     * @param description 字段描述
     * @return 当前构建器
     */
    public fun field(
        name: String,
        avroType: AvroType,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
    ): AvroSchemaBuilder {
        fields.add(SchemaField(name, avroType.schemaType, required, nullable, description))
        return this
    }

    /**
     * 构建 Schema。
     *
     * @return Avro Schema
     * @throws IllegalArgumentException 记录名、命名空间或字段非法时抛出
     */
    public fun build(): AvroSchema = AvroSchema(name, namespace, fields.toList())
}
