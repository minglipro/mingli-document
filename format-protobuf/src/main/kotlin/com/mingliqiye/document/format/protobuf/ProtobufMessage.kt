package com.mingliqiye.document.format.protobuf

/**
 * 嵌套 message 定义。
 *
 * 可作为 [ProtobufSchema] 或其它 [ProtobufMessage] 的嵌套类型，通过 [nestedMessages] 递归嵌套，
 * 通过 [enums] 定义嵌套 enum。字段通过 [ProtobufTypeReference.MessageType]/[ProtobufTypeReference.EnumType]
 * 的相对类型名引用这些定义。
 *
 * @property name message 名称（当前作用域内的简单名称）
 * @property fields 字段列表，保持声明顺序
 * @property nestedMessages 嵌套 message 定义
 * @property enums 嵌套 enum 定义
 */
public class ProtobufMessage(
    messageName: String,
    fieldList: List<ProtobufSchemaField>,
    nestedMessageList: List<ProtobufMessage> = emptyList(),
    enumList: List<ProtobufEnum> = emptyList(),
) {

    /** message 名称（当前作用域内的简单名称）。 */
    public val name: String = messageName

    /** 字段列表，保持声明顺序。 */
    public val fields: List<ProtobufSchemaField> = fieldList.toList()

    /** 嵌套 message 定义。 */
    public val nestedMessages: List<ProtobufMessage> = nestedMessageList.toList()

    /** 嵌套 enum 定义。 */
    public val enums: List<ProtobufEnum> = enumList.toList()

    private val fieldByName: Map<String, ProtobufSchemaField> = fields.associateBy { it.name }

    init {
        require(namePattern.matches(name)) { "非法 Protobuf message 名称: $name" }
        val duplicateNames = fields.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicateNames.isEmpty()) { "Protobuf 字段名重复: $duplicateNames" }
        val duplicateNumbers = fields.groupBy { it.fieldNumber }.filterValues { it.size > 1 }.keys
        require(duplicateNumbers.isEmpty()) { "Protobuf 字段编号重复: $duplicateNumbers" }
        val typeNames = ArrayList<String>()
        nestedMessages.forEach { typeNames.add(it.name) }
        enums.forEach { typeNames.add(it.name) }
        val duplicateTypeNames = typeNames.groupBy { it }.filterValues { it.size > 1 }.keys
        require(duplicateTypeNames.isEmpty()) { "Protobuf 嵌套类型名重复: $duplicateTypeNames" }
    }

    /**
     * 按字段名查找字段。
     *
     * @param name 字段名
     * @return 字段；不存在时返回 `null`
     */
    public fun field(name: String): ProtobufSchemaField? = fieldByName[name]

    /**
     * 按名称查找嵌套 message。
     *
     * @param name message 名称
     * @return 嵌套 message；不存在时返回 `null`
     */
    public fun nestedMessage(name: String): ProtobufMessage? = nestedMessages.firstOrNull { it.name == name }

    /**
     * 按名称查找嵌套 enum。
     *
     * @param name enum 名称
     * @return 嵌套 enum；不存在时返回 `null`
     */
    public fun enumeration(name: String): ProtobufEnum? = enums.firstOrNull { it.name == name }

    /**
     * 转换为 JSON 风格描述。
     */
    public fun toJsonDescription(): Map<String, Any?> = linkedMapOf(
        "name" to name,
        "fields" to fields.map { it.toJsonDescription() },
        "nestedMessages" to nestedMessages.map { it.toJsonDescription() },
        "enums" to enums.map { it.toJsonDescription() },
    )

    public companion object {

        /**
         * 创建嵌套 message 构建器。
         *
         * @param name message 名称
         * @return 构建器
         */
        @JvmStatic
        public fun builder(name: String): ProtobufMessageBuilder = ProtobufMessageBuilder(name)

        private val namePattern: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
