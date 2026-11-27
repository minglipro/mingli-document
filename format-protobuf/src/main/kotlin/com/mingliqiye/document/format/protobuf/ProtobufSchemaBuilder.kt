package com.mingliqiye.document.format.protobuf

import com.mingliqiye.document.schema.SchemaType

/**
 * [ProtobufSchema] 构建器。
 *
 * 与 [com.mingliqiye.document.schema.DocumentSchemaBuilder] 风格一致：字段按添加顺序保存，
 * 构建时校验字段编号与字段名的唯一性。
 *
 * @param messageName message 名称
 * @param packageName package 名；无 package 时传 `null`
 */
public class ProtobufSchemaBuilder(
    private val messageName: String,
    private val packageName: String? = null,
) {

    private val fields = ArrayList<ProtobufSchemaField>()

    init {
        ProtobufSchema.checkMessageName(messageName)
        ProtobufSchema.checkPackageName(packageName)
    }

    /**
     * 添加字段。
     *
     * @param name 字段名
     * @param fieldNumber 字段编号
     * @param type Protobuf 类型
     * @param repeated 是否为 repeated 字段
     * @param required 是否必填
     * @param nullable 是否允许 `null`
     * @param description 字段描述
     * @return 当前构建器
     */
    public fun field(
        name: String,
        fieldNumber: Int,
        type: ProtobufType,
        repeated: Boolean = false,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
    ): ProtobufSchemaBuilder {
        fields.add(ProtobufSchemaField(name, fieldNumber, type, repeated, required, nullable, description))
        return this
    }

    /**
     * 添加字段，使用格式无关类型并按 [ProtobufType.fromSchemaType] 映射为 Protobuf 类型。
     *
     * @param name 字段名
     * @param fieldNumber 字段编号
     * @param type 格式无关类型
     * @param repeated 是否为 repeated 字段
     * @param required 是否必填
     * @param nullable 是否允许 `null`
     * @param description 字段描述
     * @return 当前构建器
     */
    public fun field(
        name: String,
        fieldNumber: Int,
        type: SchemaType,
        repeated: Boolean = false,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
    ): ProtobufSchemaBuilder = field(
        name,
        fieldNumber,
        ProtobufType.fromSchemaType(type),
        repeated,
        required,
        nullable,
        description,
    )

    /**
     * 构建 Schema。
     *
     * @return Protobuf Schema
     * @throws IllegalArgumentException 当字段名或字段编号重复时
     */
    public fun build(): ProtobufSchema = ProtobufSchema(messageName, fields.toList(), packageName)
}
