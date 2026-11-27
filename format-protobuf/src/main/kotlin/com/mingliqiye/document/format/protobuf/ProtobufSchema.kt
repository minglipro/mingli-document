package com.mingliqiye.document.format.protobuf

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
