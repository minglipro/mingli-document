package com.mingliqiye.document.format.protobuf

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.DescriptorValidationException
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FileDescriptor
import com.mingliqiye.document.format.protobuf.exception.ProtobufException
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType

/**
 * Protobuf 文档 Schema。
 *
 * 保存 message 名称、可选的 package 以及带字段编号与 repeated 标记的 Protobuf 字段列表，
 * 同时通过 [fields] 提供格式无关的 [SchemaField] 视图。
 *
 * [fields] 中 repeated 字段的类型为 [SchemaType.ARRAY]；需要字段编号、`.proto` 类型名或 repeated
 * 标记时使用 [protobufFields] 与 [protobufField]。
 *
 * @property messageName message 名称，可含嵌套点号，如 `Outer.Inner`
 * @property packageName package 名；没有 package 时为 `null`
 * @property messageFullName message 全名，即 `package.message`，无 package 时等于 [messageName]
 * @property protobufFields Protobuf 字段列表，保持声明顺序
 */
public class ProtobufSchema(
    messageName: String,
    fieldList: List<ProtobufSchemaField>,
    packageName: String? = null,
) : DocumentSchema {

    /** message 名称，可含嵌套点号，如 `Outer.Inner`。 */
    public val messageName: String = messageName

    /** package 名；没有 package 时为 `null`。 */
    public val packageName: String? = packageName

    /** message 全名，即 `package.message`，无 package 时等于 [messageName]。 */
    public val messageFullName: String =
        if (packageName.isNullOrEmpty()) messageName else "$packageName.$messageName"

    /** Protobuf 字段列表，保持声明顺序。 */
    public val protobufFields: List<ProtobufSchemaField> = fieldList.toList()

    /** 格式无关字段视图，repeated 字段映射为 [SchemaType.ARRAY]。 */
    override val fields: List<SchemaField> = protobufFields.map { it.toSchemaField() }

    /** Schema 名称，取 [messageFullName]。 */
    override val name: String get() = messageFullName

    private val protobufFieldByName: Map<String, ProtobufSchemaField> = protobufFields.associateBy { it.name }

    private val protobufFieldByNumber: Map<Int, ProtobufSchemaField> = protobufFields.associateBy { it.fieldNumber }

    private val schemaFieldByName: Map<String, SchemaField> =
        protobufFields.associate { it.name to it.toSchemaField() }

    init {
        checkMessageName(messageName)
        checkPackageName(packageName)
        val duplicateNames = protobufFields.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicateNames.isEmpty()) { "Protobuf 字段名重复: $duplicateNames" }
        val duplicateNumbers = protobufFields.groupBy { it.fieldNumber }.filterValues { it.size > 1 }.keys
        require(duplicateNumbers.isEmpty()) { "Protobuf 字段编号重复: $duplicateNumbers" }
    }

    /**
     * 按字段名查找格式无关字段。
     *
     * @param name 字段名
     * @return 字段；不存在时返回 `null`
     */
    override fun field(name: String): SchemaField? = schemaFieldByName[name]

    /**
     * 按字段名查找 Protobuf 字段。
     *
     * @param name 字段名
     * @return 字段；不存在时返回 `null`
     */
    public fun protobufField(name: String): ProtobufSchemaField? = protobufFieldByName[name]

    /**
     * 按字段编号查找 Protobuf 字段。
     *
     * @param fieldNumber 字段编号
     * @return 字段；不存在时返回 `null`
     */
    public fun protobufField(fieldNumber: Int): ProtobufSchemaField? = protobufFieldByNumber[fieldNumber]

    /**
     * 转换为 Protobuf [Descriptor]。
     *
     * 生成 proto2 语法的单 message 临时文件：标量字段按 [ProtobufType] 映射为对应的
     * `FieldDescriptorProto.Type`，repeated/required/optional 映射为 field label，
     * [messageName] 中的嵌套点号（如 `Outer.Inner`）映射为嵌套 message。
     *
     * 该 Schema 适配层只保存标量类型，无法表达自定义 enum/message/map 的类型名，因此包含
     * [ProtobufType.ENUM]、[ProtobufType.MESSAGE] 或 [ProtobufType.MAP] 字段时会抛出
     * [ProtobufException]；这类场景请直接使用外部构造好的 [Descriptor]。
     *
     * @return Protobuf message 描述符
     * @throws ProtobufException 字段类型无法还原或 Descriptor 构建失败时抛出
     */
    public fun toDescriptor(): Descriptor {
        val messageParts = messageName.split('.')
        val innermostMessage = DescriptorProtos.DescriptorProto.newBuilder().setName(messageParts.last())
        protobufFields.forEach { field -> innermostMessage.addField(toFieldDescriptorProto(field)) }
        var messageProto = innermostMessage.build()
        for (index in messageParts.size - 2 downTo 0) {
            messageProto = DescriptorProtos.DescriptorProto.newBuilder()
                .setName(messageParts[index])
                .addNestedType(messageProto)
                .build()
        }
        val packageName = this.packageName
        val fileProtoBuilder = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName(fileName(messageName, packageName))
            .addMessageType(messageProto)
        if (!packageName.isNullOrEmpty()) {
            fileProtoBuilder.setPackage(packageName)
        }
        val fileDescriptor = try {
            FileDescriptor.buildFrom(fileProtoBuilder.build(), emptyArray())
        } catch (e: DescriptorValidationException) {
            throw ProtobufException("构建 Protobuf Descriptor 失败: ${e.message}", e)
        }
        var descriptor = fileDescriptor.findMessageTypeByName(messageParts.first())
            ?: throw ProtobufException("未找到 Protobuf message: $messageName")
        for (index in 1 until messageParts.size) {
            descriptor = descriptor.findNestedTypeByName(messageParts[index])
                ?: throw ProtobufException("未找到嵌套 Protobuf message: $messageName")
        }
        return descriptor
    }

    /**
     * 转换为 JSON 风格描述。
     */
    public fun toJsonDescription(): Map<String, Any?> = linkedMapOf(
        "message" to messageName,
        "package" to packageName,
        "fullName" to messageFullName,
        "fields" to protobufFields.map { it.toJsonDescription() },
    )

    public companion object {

        /**
         * 创建 Protobuf Schema 构建器。
         *
         * @param messageName message 名称
         * @param packageName package 名；无 package 时传 `null`
         * @return 构建器
         */
        @JvmStatic
        public fun builder(messageName: String, packageName: String? = null): ProtobufSchemaBuilder = ProtobufSchemaBuilder(messageName, packageName)

        /**
         * 从 [toJsonDescription] 生成的 JSON 风格描述还原 Schema。
         *
         * @param description JSON 风格描述
         * @return Schema
         * @throws IllegalArgumentException 当描述缺少必要信息或字段非法时
         */
        @JvmStatic
        public fun fromJsonDescription(description: Map<String, Any?>): ProtobufSchema {
            val messageName = description["message"] as? String
                ?: throw IllegalArgumentException("JSON 描述缺少 message: $description")
            val packageName = description["package"] as? String
            val rawFields = description["fields"] as? List<*> ?: emptyList<Any?>()
            val builder = builder(messageName, packageName)
            rawFields.forEach { rawField ->
                val fieldDescription = rawField as? Map<*, *>
                    ?: throw IllegalArgumentException("非法字段描述: $rawField")
                val name = fieldDescription["name"] as? String
                    ?: throw IllegalArgumentException("字段描述缺少 name: $fieldDescription")
                val number = (fieldDescription["number"] as? Number)?.toInt()
                    ?: throw IllegalArgumentException("字段描述缺少 number: $fieldDescription")
                val typeName = fieldDescription["type"] as? String
                    ?: throw IllegalArgumentException("字段描述缺少 type: $fieldDescription")
                val protobufType = ProtobufType.fromTypeName(typeName)
                    ?: throw IllegalArgumentException("未识别的 Protobuf 类型: $typeName")
                builder.field(
                    name = name,
                    fieldNumber = number,
                    type = protobufType,
                    repeated = fieldDescription["repeated"] as? Boolean ?: false,
                    required = fieldDescription["required"] as? Boolean ?: true,
                    nullable = fieldDescription["nullable"] as? Boolean ?: false,
                    description = fieldDescription["description"] as? String,
                )
            }
            return builder.build()
        }

        /**
         * 从 Protobuf [Descriptor] 还原 Schema。
         *
         * 字段类型映射为 [ProtobufType]（map 字段映射为 [ProtobufType.MAP]），字段编号、repeated、
         * required 与 presence（nullable）保持不变；package 与嵌套 message 名称从
         * [Descriptor.fullName]、[Descriptor.file] 推导。
         *
         * @param descriptor Protobuf message 描述符
         * @return Protobuf Schema
         * @throws IllegalArgumentException 字段编号或名称不合法时抛出
         */
        @JvmStatic
        public fun fromDescriptor(descriptor: Descriptor): ProtobufSchema {
            val packageName = descriptor.file.getPackage().takeIf { it.isNotEmpty() }
            val messageName = if (packageName == null) {
                descriptor.fullName
            } else {
                descriptor.fullName.removePrefix("$packageName.")
            }
            val builder = builder(messageName, packageName)
            for (field in descriptor.fields) {
                builder.field(
                    name = field.name,
                    fieldNumber = field.number,
                    type = protobufTypeOf(field),
                    repeated = field.isRepeated && !field.isMapField,
                    required = field.isRequired,
                    nullable = field.hasPresence() && !field.isRequired,
                    description = null,
                )
            }
            return builder.build()
        }

        private fun protobufTypeOf(field: FieldDescriptor): ProtobufType {
            if (field.isMapField) {
                return ProtobufType.MAP
            }
            if (field.type == FieldDescriptor.Type.GROUP) {
                return ProtobufType.MESSAGE
            }
            return ProtobufType.fromTypeName(field.type.name.lowercase())
                ?: throw ProtobufException("不支持的 Protobuf 字段类型: ${field.type}")
        }

        private fun fileName(messageName: String, packageName: String?): String {
            val messagePath = messageName.replace('.', '/')
            val prefix = packageName?.replace('.', '/')
            return if (prefix.isNullOrEmpty()) "$messagePath.proto" else "$prefix/$messagePath.proto"
        }

        private fun toFieldDescriptorProto(field: ProtobufSchemaField): DescriptorProtos.FieldDescriptorProto {
            val label = when {
                field.repeated -> DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED
                field.required -> DescriptorProtos.FieldDescriptorProto.Label.LABEL_REQUIRED
                else -> DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL
            }
            return DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName(field.name)
                .setNumber(field.fieldNumber)
                .setLabel(label)
                .setType(toFieldDescriptorType(field))
                .build()
        }

        private fun toFieldDescriptorType(field: ProtobufSchemaField): DescriptorProtos.FieldDescriptorProto.Type = when (field.protobufType) {
            ProtobufType.DOUBLE -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE
            ProtobufType.FLOAT -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT
            ProtobufType.INT32 -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32
            ProtobufType.INT64 -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64
            ProtobufType.UINT32 -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32
            ProtobufType.UINT64 -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64
            ProtobufType.SINT32 -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32
            ProtobufType.SINT64 -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64
            ProtobufType.FIXED32 -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32
            ProtobufType.FIXED64 -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64
            ProtobufType.SFIXED32 -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32
            ProtobufType.SFIXED64 -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64
            ProtobufType.BOOL -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL
            ProtobufType.STRING -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING
            ProtobufType.BYTES -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES
            ProtobufType.ENUM,
            ProtobufType.MESSAGE,
            ProtobufType.MAP,
            -> throw ProtobufException(
                "字段 '${field.name}' 的类型 '${field.protobufTypeName}' 需要显式 Descriptor，" +
                    "无法从 ProtobufSchema 还原",
            )
        }

        /**
         * 校验 message 名称。
         */
        internal fun checkMessageName(messageName: String) {
            require(qualifiedNamePattern.matches(messageName)) { "非法 Protobuf message 名称: $messageName" }
        }

        /**
         * 校验 package 名称。
         */
        internal fun checkPackageName(packageName: String?) {
            if (packageName == null) {
                return
            }
            require(qualifiedNamePattern.matches(packageName)) { "非法 Protobuf package 名称: $packageName" }
        }

        private val qualifiedNamePattern: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
    }
}
