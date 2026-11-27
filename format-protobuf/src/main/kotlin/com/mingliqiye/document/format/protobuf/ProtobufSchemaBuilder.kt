package com.mingliqiye.document.format.protobuf

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.EnumDescriptor
import com.mingliqiye.document.schema.SchemaType

/**
 * [ProtobufSchema] 构建器。
 *
 * 与 [com.mingliqiye.document.schema.DocumentSchemaBuilder] 风格一致：字段按添加顺序保存，
 * 构建时校验字段编号、字段名与嵌套类型名的唯一性。
 *
 * 根 message 使用 [ProtobufSchema] 的 message 名称（允许包含表示嵌套点的点号，如 `Outer.Inner`），
 * 字段与嵌套定义附加到最内层 message 上。
 *
 * @param messageName message 名称
 * @param packageName package 名；无 package 时传 `null`
 */
public class ProtobufSchemaBuilder(
    private val messageName: String,
    private val packageName: String? = null,
) {

    private val definitionBuilder = ProtobufMessageBuilder(messageName.substringAfterLast('.'))

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
     * @param typeReference message/enum/map 类型引用；标量字段应为 `null`
     * @param oneofName 所属 oneof 名称；`null` 表示不属于 oneof
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
        typeReference: ProtobufTypeReference? = null,
        oneofName: String? = null,
    ): ProtobufSchemaBuilder {
        definitionBuilder.field(name, fieldNumber, type, repeated, required, nullable, description, typeReference, oneofName)
        return this
    }

    /**
     * 添加已构建的字段。
     *
     * @param field 字段定义
     * @return 当前构建器
     */
    public fun field(field: ProtobufSchemaField): ProtobufSchemaBuilder {
        definitionBuilder.field(field)
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
     * @param typeReference message/enum/map 类型引用；标量字段应为 `null`
     * @param oneofName 所属 oneof 名称
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
        typeReference: ProtobufTypeReference? = null,
        oneofName: String? = null,
    ): ProtobufSchemaBuilder {
        definitionBuilder.field(name, fieldNumber, type, repeated, required, nullable, description, typeReference, oneofName)
        return this
    }

    /**
     * 添加 message 字段。
     *
     * @param name 字段名
     * @param fieldNumber 字段编号
     * @param messageTypeName 引用的 message 类型名，相对当前 message 或 `.` 开头的全限定名
     * @param repeated 是否为 repeated 字段
     * @param required 是否必填
     * @param nullable 是否允许 `null`
     * @param description 字段描述
     * @param oneofName 所属 oneof 名称
     * @return 当前构建器
     */
    public fun messageField(
        name: String,
        fieldNumber: Int,
        messageTypeName: String,
        repeated: Boolean = false,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
        oneofName: String? = null,
    ): ProtobufSchemaBuilder {
        definitionBuilder.messageField(
            name,
            fieldNumber,
            messageTypeName,
            repeated,
            required,
            nullable,
            description,
            oneofName,
        )
        return this
    }

    /**
     * 使用外部 [Descriptor] 添加 message 字段。
     *
     * 类型引用记录为该 Descriptor 以 `.` 开头的全限定名；[ProtobufSchema.toDescriptor] 需要
     * 通过依赖 FileDescriptor 解析该类型。
     *
     * @param name 字段名
     * @param fieldNumber 字段编号
     * @param messageType 外部 message 描述符
     * @param repeated 是否为 repeated 字段
     * @param required 是否必填
     * @param nullable 是否允许 `null`
     * @param description 字段描述
     * @param oneofName 所属 oneof 名称
     * @return 当前构建器
     */
    public fun messageField(
        name: String,
        fieldNumber: Int,
        messageType: Descriptor,
        repeated: Boolean = false,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
        oneofName: String? = null,
    ): ProtobufSchemaBuilder {
        definitionBuilder.messageField(
            name,
            fieldNumber,
            messageType,
            repeated,
            required,
            nullable,
            description,
            oneofName,
        )
        return this
    }

    /**
     * 添加 enum 字段。
     *
     * @param name 字段名
     * @param fieldNumber 字段编号
     * @param enumTypeName 引用的 enum 类型名，相对当前 message 或 `.` 开头的全限定名
     * @param repeated 是否为 repeated 字段
     * @param required 是否必填
     * @param nullable 是否允许 `null`
     * @param description 字段描述
     * @param oneofName 所属 oneof 名称
     * @return 当前构建器
     */
    public fun enumField(
        name: String,
        fieldNumber: Int,
        enumTypeName: String,
        repeated: Boolean = false,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
        oneofName: String? = null,
    ): ProtobufSchemaBuilder {
        definitionBuilder.enumField(
            name,
            fieldNumber,
            enumTypeName,
            repeated,
            required,
            nullable,
            description,
            oneofName,
        )
        return this
    }

    /**
     * 使用外部 [EnumDescriptor] 添加 enum 字段。
     *
     * 类型引用记录为该 Descriptor 以 `.` 开头的全限定名；[ProtobufSchema.toDescriptor] 需要
     * 通过依赖 FileDescriptor 解析该类型。
     *
     * @param name 字段名
     * @param fieldNumber 字段编号
     * @param enumType 外部 enum 描述符
     * @param repeated 是否为 repeated 字段
     * @param required 是否必填
     * @param nullable 是否允许 `null`
     * @param description 字段描述
     * @param oneofName 所属 oneof 名称
     * @return 当前构建器
     */
    public fun enumField(
        name: String,
        fieldNumber: Int,
        enumType: EnumDescriptor,
        repeated: Boolean = false,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
        oneofName: String? = null,
    ): ProtobufSchemaBuilder {
        definitionBuilder.enumField(
            name,
            fieldNumber,
            enumType,
            repeated,
            required,
            nullable,
            description,
            oneofName,
        )
        return this
    }

    /**
     * 添加 map 字段。
     *
     * @param name 字段名
     * @param fieldNumber 字段编号
     * @param keyType map key 类型，必须是标量
     * @param valueType map value 类型
     * @param valueReference value 为 message/enum 时的类型引用
     * @param description 字段描述
     * @return 当前构建器
     */
    public fun mapField(
        name: String,
        fieldNumber: Int,
        keyType: ProtobufType,
        valueType: ProtobufType,
        valueReference: ProtobufTypeReference? = null,
        description: String? = null,
    ): ProtobufSchemaBuilder {
        definitionBuilder.mapField(name, fieldNumber, keyType, valueType, valueReference, description)
        return this
    }

    /**
     * 添加已构建的嵌套 message。
     *
     * @param message 嵌套 message 定义
     * @return 当前构建器
     */
    public fun nestedMessage(message: ProtobufMessage): ProtobufSchemaBuilder {
        definitionBuilder.nestedMessage(message)
        return this
    }

    /**
     * 通过 DSL 添加嵌套 message。
     *
     * @param name message 名称
     * @param block 构建嵌套 message 的代码块
     * @return 当前构建器
     */
    public fun nestedMessage(name: String, block: ProtobufMessageBuilder.() -> Unit = {}): ProtobufSchemaBuilder {
        definitionBuilder.nestedMessage(name, block)
        return this
    }

    /**
     * 添加已构建的嵌套 enum。
     *
     * @param enumSchema enum 定义
     * @return 当前构建器
     */
    public fun enumeration(enumSchema: ProtobufEnum): ProtobufSchemaBuilder {
        definitionBuilder.enumeration(enumSchema)
        return this
    }

    /**
     * 通过名称与值列表添加嵌套 enum。
     *
     * @param name enum 名称
     * @param values enum 值，参数形式为 `"ACTIVE" to 1`
     * @return 当前构建器
     */
    public fun enumeration(name: String, vararg values: Pair<String, Int>): ProtobufSchemaBuilder {
        definitionBuilder.enumeration(name, *values)
        return this
    }

    /**
     * 构建 Schema。
     *
     * @return Protobuf Schema
     * @throws IllegalArgumentException 当字段名、字段编号或嵌套类型名重复时
     */
    public fun build(): ProtobufSchema {
        val definition = definitionBuilder.build()
        return ProtobufSchema(
            messageName,
            definition.fields,
            packageName,
            definition.nestedMessages,
            definition.enums,
        )
    }
}
