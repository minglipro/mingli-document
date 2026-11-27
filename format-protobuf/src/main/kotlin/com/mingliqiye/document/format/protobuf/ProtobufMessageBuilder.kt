package com.mingliqiye.document.format.protobuf

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.EnumDescriptor
import com.mingliqiye.document.schema.SchemaType

/**
 * [ProtobufMessage] 构建器。
 *
 * 字段按添加顺序保存；字段级校验（类型引用一致性、oneof 约束、编号范围）在添加时立即执行，
 * 字段名/编号与嵌套类型名的唯一性在 [build] 时校验。
 *
 * @param messageName message 名称
 */
public class ProtobufMessageBuilder public constructor(private val messageName: String) {

    private val fields = ArrayList<ProtobufSchemaField>()
    private val nestedMessages = ArrayList<ProtobufMessage>()
    private val enums = ArrayList<ProtobufEnum>()

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
    ): ProtobufMessageBuilder {
        fields.add(
            ProtobufSchemaField(name, fieldNumber, type, repeated, required, nullable, description, typeReference, oneofName),
        )
        return this
    }

    /**
     * 添加已构建的字段。
     *
     * @param field 字段定义
     * @return 当前构建器
     */
    public fun field(field: ProtobufSchemaField): ProtobufMessageBuilder {
        fields.add(field)
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
    ): ProtobufMessageBuilder = field(
        name,
        fieldNumber,
        ProtobufType.fromSchemaType(type),
        repeated,
        required,
        nullable,
        description,
        typeReference,
        oneofName,
    )

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
    ): ProtobufMessageBuilder = field(
        name,
        fieldNumber,
        ProtobufType.MESSAGE,
        repeated,
        required,
        nullable,
        description,
        ProtobufTypeReference.MessageType(messageTypeName),
        oneofName,
    )

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
    ): ProtobufMessageBuilder = messageField(
        name,
        fieldNumber,
        ".${messageType.fullName}",
        repeated,
        required,
        nullable,
        description,
        oneofName,
    )

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
    ): ProtobufMessageBuilder = field(
        name,
        fieldNumber,
        ProtobufType.ENUM,
        repeated,
        required,
        nullable,
        description,
        ProtobufTypeReference.EnumType(enumTypeName),
        oneofName,
    )

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
    ): ProtobufMessageBuilder = enumField(
        name,
        fieldNumber,
        ".${enumType.fullName}",
        repeated,
        required,
        nullable,
        description,
        oneofName,
    )

    /**
     * 添加 map 字段。
     *
     * map 字段在 wire 上是 repeated 的 entry message，因此 [ProtobufSchemaField.repeated] 为 `false`
     * 且不能属于 oneof。
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
    ): ProtobufMessageBuilder = field(
        name,
        fieldNumber,
        ProtobufType.MAP,
        false,
        false,
        false,
        description,
        ProtobufTypeReference.MapType(keyType, valueType, valueReference),
        null,
    )

    /**
     * 添加已构建的嵌套 message。
     *
     * @param message 嵌套 message 定义
     * @return 当前构建器
     */
    public fun nestedMessage(message: ProtobufMessage): ProtobufMessageBuilder {
        nestedMessages.add(message)
        return this
    }

    /**
     * 通过 DSL 添加嵌套 message。
     *
     * @param name message 名称
     * @param block 构建嵌套 message 的代码块
     * @return 当前构建器
     */
    public fun nestedMessage(name: String, block: ProtobufMessageBuilder.() -> Unit = {}): ProtobufMessageBuilder {
        nestedMessages.add(ProtobufMessageBuilder(name).apply(block).build())
        return this
    }

    /**
     * 添加已构建的嵌套 enum。
     *
     * @param enumSchema enum 定义
     * @return 当前构建器
     */
    public fun enumeration(enumSchema: ProtobufEnum): ProtobufMessageBuilder {
        enums.add(enumSchema)
        return this
    }

    /**
     * 通过名称与值列表添加嵌套 enum。
     *
     * @param name enum 名称
     * @param values enum 值，参数形式为 `"ACTIVE" to 1`
     * @return 当前构建器
     */
    public fun enumeration(name: String, vararg values: Pair<String, Int>): ProtobufMessageBuilder = enumeration(ProtobufEnum(name, values.map { ProtobufEnumValue(it.first, it.second) }))

    /**
     * 构建 message 定义。
     *
     * @return message 定义
     * @throws IllegalArgumentException 字段或嵌套类型名/编号重复时抛出
     */
    public fun build(): ProtobufMessage = ProtobufMessage(
        messageName,
        fields.toList(),
        nestedMessages.toList(),
        enums.toList(),
    )
}
