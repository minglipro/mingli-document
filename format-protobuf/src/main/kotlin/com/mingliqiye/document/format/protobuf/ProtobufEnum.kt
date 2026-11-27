package com.mingliqiye.document.format.protobuf

/**
 * Protobuf enum 值定义。
 *
 * @property name 值名称，如 `ACTIVE`
 * @property number 值编号；proto2 允许负数，也允许不同名称对应相同编号（别名）
 */
public class ProtobufEnumValue(
    public val name: String,
    public val number: Int,
) {

    init {
        require(namePattern.matches(name)) { "非法 Protobuf enum 值名称: $name" }
    }

    /**
     * 转换为 JSON 风格描述。
     */
    public fun toJsonDescription(): Map<String, Any?> = linkedMapOf(
        "name" to name,
        "number" to number,
    )

    public companion object {

        private val namePattern: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

/**
 * Protobuf enum 定义，可嵌套在 [ProtobufSchema] 或 [ProtobufMessage] 中。
 *
 * @property name enum 名称
 * @property values enum 值列表，保持声明顺序
 */
public class ProtobufEnum(
    enumName: String,
    valueList: List<ProtobufEnumValue>,
) {

    /** enum 名称。 */
    public val name: String = enumName

    /** enum 值列表，保持声明顺序。 */
    public val values: List<ProtobufEnumValue> = valueList.toList()

    init {
        require(namePattern.matches(name)) { "非法 Protobuf enum 名称: $name" }
        require(values.isNotEmpty()) { "Protobuf enum $name 至少需要一个值" }
        val duplicateNames = values.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicateNames.isEmpty()) { "Protobuf enum $name 值名称重复: $duplicateNames" }
    }

    /**
     * 按名称查找 enum 值。
     *
     * @param name 值名称
     * @return enum 值；不存在时返回 `null`
     */
    public fun value(name: String): ProtobufEnumValue? = values.firstOrNull { it.name == name }

    /**
     * 转换为 JSON 风格描述。
     */
    public fun toJsonDescription(): Map<String, Any?> = linkedMapOf(
        "name" to name,
        "values" to values.map { it.toJsonDescription() },
    )

    public companion object {

        /**
         * 从名称与 `名称 to 编号` 列表创建 enum。
         *
         * @param name enum 名称
         * @param values enum 值，参数形式为 `"ACTIVE" to 1`
         * @return enum 定义
         */
        @JvmStatic
        public fun of(name: String, vararg values: Pair<String, Int>): ProtobufEnum = ProtobufEnum(name, values.map { ProtobufEnumValue(it.first, it.second) })

        private val namePattern: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
