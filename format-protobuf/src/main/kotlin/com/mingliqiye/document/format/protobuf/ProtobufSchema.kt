package com.mingliqiye.document.format.protobuf

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FileDescriptor
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType

/**
 * Protobuf 文档 Schema。
 *
 * 保存 message 名称、可选的 package、带字段编号与 repeated 标记的 Protobuf 字段列表，以及可选的
 * 嵌套 message/enum 定义；同时通过 [fields] 提供格式无关的 [SchemaField] 视图。
 *
 * [fields] 中 repeated 字段的类型为 [SchemaType.ARRAY]；需要字段编号、`.proto` 类型名、复杂类型引用
 * 或 repeated 标记时使用 [protobufFields] 与 [protobufField]。
 *
 * message/enum/map 字段通过 [ProtobufSchemaField.typeReference] 引用类型；[toDescriptor] 可把
 * Schema 生成完整的 [Descriptor]，嵌套定义通过 [nestedMessages] 与 [enums] 提供。
 *
 * @property messageName message 名称，可含嵌套点号，如 `Outer.Inner`
 * @property packageName package 名；没有 package 时为 `null`
 * @property messageFullName message 全名，即 `package.message`，无 package 时等于 [messageName]
 * @property protobufFields Protobuf 字段列表，保持声明顺序
 * @property nestedMessages 嵌套 message 定义
 * @property enums 嵌套 enum 定义
 */
public class ProtobufSchema(
    messageName: String,
    fieldList: List<ProtobufSchemaField>,
    packageName: String? = null,
    nestedMessageList: List<ProtobufMessage> = emptyList(),
    enumList: List<ProtobufEnum> = emptyList(),
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

    /** 嵌套 message 定义。 */
    public val nestedMessages: List<ProtobufMessage> = nestedMessageList.toList()

    /** 嵌套 enum 定义。 */
    public val enums: List<ProtobufEnum> = enumList.toList()

    /** 格式无关字段视图，repeated 字段映射为 [SchemaType.ARRAY]。 */
    override val fields: List<SchemaField> = protobufFields.map { it.toSchemaField() }

    /** Schema 名称，取 [messageFullName]。 */
    override val name: String get() = messageFullName

    private val protobufFieldByName: Map<String, ProtobufSchemaField> = protobufFields.associateBy { it.name }

    private val protobufFieldByNumber: Map<Int, ProtobufSchemaField> = protobufFields.associateBy { it.fieldNumber }

    private val schemaFieldByName: Map<String, SchemaField> =
        protobufFields.associate { it.name to it.toSchemaField() }

    private val nestedMessageByName: Map<String, ProtobufMessage> = nestedMessages.associateBy { it.name }

    private val enumByName: Map<String, ProtobufEnum> = enums.associateBy { it.name }

    init {
        checkMessageName(messageName)
        checkPackageName(packageName)
        val duplicateNames = protobufFields.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicateNames.isEmpty()) { "Protobuf 字段名重复: $duplicateNames" }
        val duplicateNumbers = protobufFields.groupBy { it.fieldNumber }.filterValues { it.size > 1 }.keys
        require(duplicateNumbers.isEmpty()) { "Protobuf 字段编号重复: $duplicateNumbers" }
        ProtobufMessage(messageName.substringAfterLast('.'), protobufFields, nestedMessages, enums)
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
     * 按名称查找嵌套 message。
     *
     * @param name message 名称
     * @return 嵌套 message；不存在时返回 `null`
     */
    public fun nestedMessage(name: String): ProtobufMessage? = nestedMessageByName[name]

    /**
     * 按名称查找嵌套 enum。
     *
     * @param name enum 名称
     * @return 嵌套 enum；不存在时返回 `null`
     */
    public fun enumeration(name: String): ProtobufEnum? = enumByName[name]

    /**
     * 生成完整的 Protobuf [Descriptor]。
     *
     * 生成 proto2 语法的单文件 descriptor：字段类型/编号/repeated/required/oneof 映射为对应
     * FieldDescriptorProto，message/enum 类型引用解析为文件内的绝对类型名，map 字段生成
     * `map_entry = true` 的嵌套 entry message，[nestedMessages]/[enums] 递归生成。
     *
     * @param dependencies 依赖的 FileDescriptor，用于解析 `.` 开头的全限定类型引用；默认无依赖
     * @return 根 message 描述符
     * @throws com.mingliqiye.document.format.protobuf.exception.ProtobufException 类型引用缺失/
     *   无法解析或 Descriptor 校验失败时抛出
     */
    public fun toDescriptor(dependencies: Array<FileDescriptor> = emptyArray()): Descriptor = ProtobufDescriptorGenerator.generate(this, dependencies)

    /**
     * 转换为 JSON 风格描述。
     */
    public fun toJsonDescription(): Map<String, Any?> = linkedMapOf(
        "message" to messageName,
        "package" to packageName,
        "fullName" to messageFullName,
        "fields" to protobufFields.map { it.toJsonDescription() },
        "nestedMessages" to nestedMessages.map { it.toJsonDescription() },
        "enums" to enums.map { it.toJsonDescription() },
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
         * 从 [Descriptor] 还原 Schema。
         *
         * 字段类型/编号/repeated/required/presence 与 oneof 归属保持不变；嵌套 message/enum 递归
         * 还原为 [ProtobufMessage]/[ProtobufEnum]；map 字段由 map entry 还原为
         * [ProtobufTypeReference.MapType]。根 message 之外的类型引用记录为全限定名。
         *
         * @param descriptor Protobuf message 描述符
         * @return Protobuf Schema
         * @throws com.mingliqiye.document.format.protobuf.exception.ProtobufException group 字段
         *   或非法 map entry 时抛出
         */
        @JvmStatic
        public fun fromDescriptor(descriptor: Descriptor): ProtobufSchema = ProtobufSchemaConverter.convert(descriptor)

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
            val builder = builder(messageName, packageName)
            rawList(description, "fields").forEach { rawField ->
                val fieldDescription = rawField as? Map<*, *>
                    ?: throw IllegalArgumentException("非法字段描述: $rawField")
                builder.field(parseFieldDescription(fieldDescription))
            }
            parseNestedMessages(description).forEach { builder.nestedMessage(it) }
            parseEnums(description).forEach { builder.enumeration(it) }
            return builder.build()
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

        private fun parseFieldDescription(description: Map<*, *>): ProtobufSchemaField {
            val name = description["name"] as? String
                ?: throw IllegalArgumentException("字段描述缺少 name: $description")
            val number = (description["number"] as? Number)?.toInt()
                ?: throw IllegalArgumentException("字段描述缺少 number: $description")
            val typeName = description["type"] as? String
                ?: throw IllegalArgumentException("字段描述缺少 type: $description")
            val protobufType = ProtobufType.fromTypeName(typeName)
                ?: throw IllegalArgumentException("未识别的 Protobuf 类型: $typeName")
            return ProtobufSchemaField(
                name = name,
                fieldNumber = number,
                protobufType = protobufType,
                repeated = description["repeated"] as? Boolean ?: false,
                required = description["required"] as? Boolean ?: true,
                nullable = description["nullable"] as? Boolean ?: false,
                description = description["description"] as? String,
                typeReference = parseTypeReference(description, protobufType),
                oneofName = description["oneof"] as? String,
            )
        }

        private fun parseTypeReference(description: Map<*, *>, protobufType: ProtobufType): ProtobufTypeReference? = when (protobufType) {
            ProtobufType.MESSAGE -> (description["messageType"] as? String)?.let { ProtobufTypeReference.MessageType(it) }
            ProtobufType.ENUM -> (description["enumType"] as? String)?.let { ProtobufTypeReference.EnumType(it) }
            ProtobufType.MAP -> parseMapType(description)
            else -> null
        }

        private fun parseMapType(description: Map<*, *>): ProtobufTypeReference.MapType {
            val keyTypeName = description["mapKeyType"] as? String
                ?: throw IllegalArgumentException("map 字段描述缺少 mapKeyType: $description")
            val valueTypeName = description["mapValueType"] as? String
                ?: throw IllegalArgumentException("map 字段描述缺少 mapValueType: $description")
            val keyType = ProtobufType.fromTypeName(keyTypeName)
                ?: throw IllegalArgumentException("未识别的 map key 类型: $keyTypeName")
            val valueType = ProtobufType.fromTypeName(valueTypeName)
                ?: throw IllegalArgumentException("未识别的 map value 类型: $valueTypeName")
            val valueReference = when (valueType) {
                ProtobufType.MESSAGE ->
                    (description["mapValueMessageType"] as? String)?.let { ProtobufTypeReference.MessageType(it) }

                ProtobufType.ENUM ->
                    (description["mapValueEnumType"] as? String)?.let { ProtobufTypeReference.EnumType(it) }

                else -> null
            }
            return ProtobufTypeReference.MapType(keyType, valueType, valueReference)
        }

        private fun parseNestedMessages(description: Map<*, *>): List<ProtobufMessage> = rawList(description, "nestedMessages").map { rawMessage ->
            val messageDescription = rawMessage as? Map<*, *>
                ?: throw IllegalArgumentException("非法嵌套 message 描述: $rawMessage")
            parseMessageDescription(messageDescription)
        }

        private fun parseMessageDescription(description: Map<*, *>): ProtobufMessage {
            val name = description["name"] as? String
                ?: throw IllegalArgumentException("嵌套 message 描述缺少 name: $description")
            val fields = rawList(description, "fields").map { rawField ->
                val fieldDescription = rawField as? Map<*, *>
                    ?: throw IllegalArgumentException("非法字段描述: $rawField")
                parseFieldDescription(fieldDescription)
            }
            return ProtobufMessage(name, fields, parseNestedMessages(description), parseEnums(description))
        }

        private fun parseEnums(description: Map<*, *>): List<ProtobufEnum> = rawList(description, "enums").map { rawEnum ->
            val enumDescription = rawEnum as? Map<*, *>
                ?: throw IllegalArgumentException("非法 enum 描述: $rawEnum")
            parseEnumDescription(enumDescription)
        }

        private fun parseEnumDescription(description: Map<*, *>): ProtobufEnum {
            val name = description["name"] as? String
                ?: throw IllegalArgumentException("enum 描述缺少 name: $description")
            val values = rawList(description, "values").map { rawValue ->
                val valueDescription = rawValue as? Map<*, *>
                    ?: throw IllegalArgumentException("非法 enum 值描述: $rawValue")
                val valueName = valueDescription["name"] as? String
                    ?: throw IllegalArgumentException("enum 值描述缺少 name: $valueDescription")
                val number = (valueDescription["number"] as? Number)?.toInt()
                    ?: throw IllegalArgumentException("enum 值描述缺少 number: $valueDescription")
                ProtobufEnumValue(valueName, number)
            }
            return ProtobufEnum(name, values)
        }

        private fun rawList(description: Map<*, *>, key: String): List<*> = description[key] as? List<*> ?: emptyList<Any?>()

        private val qualifiedNamePattern: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
    }
}
