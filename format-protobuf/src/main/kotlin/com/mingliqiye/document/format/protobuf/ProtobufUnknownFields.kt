package com.mingliqiye.document.format.protobuf

/**
 * Protobuf 未知字段在文档对象中的保留约定。
 *
 * 当 [ProtobufUnknownFieldPolicy.PRESERVE] 生效时，无法映射为声明字段（也不是已知 extension）的
 * wire 数据会写入对象节点中名为 [FIELD_NAME] 的字段，结构为：
 *
 * ```
 * {
 *   "$unknownFields": {
 *     "99": { "varint": 1 },
 *     "100": { "varint": [1, 2] },
 *     "101": { "fixed32": 4294967295 },
 *     "102": { "fixed64": 18446744073709551615 },
 *     "103": { "lengthDelimited": "<BinaryNode>" },
 *     "104": { "groups": { "1": { "varint": 5 } } }
 *   }
 * }
 * ```
 *
 * - 键为十进制字段编号（按编号升序）。
 * - 值为对象，最多包含 `varint`、`fixed32`、`fixed64`、`lengthDelimited`、`groups` 五种 wire
 *   类型键；同一编号出现多次时值为数组。
 * - `varint`/`fixed64` 用无符号 64 位十进制数字表示，`fixed32` 用无符号 32 位数字表示，
 *   `lengthDelimited` 用 [com.mingliqiye.document.core.BinaryNode]，`groups` 为嵌套的相同结构。
 *
 * 该结构可以再次交给 [ProtobufMapper] 写出（仅 [ProtobufUnknownFieldPolicy.PRESERVE] 模式下
 * 接受），从而在“解析 -> 修改声明字段 -> 写回”的流程中保留未知字段。字段名包含 `$`，不会与
 * `.proto` 声明的字段名或 extension 文档名冲突。
 *
 * MessageSet wire format 的未知 item 在解析后也以 `lengthDelimited` 形式出现在这里，键为
 * type ID；写出时会按 MessageSet item 格式还原。
 */
public object ProtobufUnknownFields {

    /** 保留未知字段的文档字段名。 */
    public const val FIELD_NAME: String = "\$unknownFields"

    /** 存放 varint 未知字段值的键。 */
    public const val VARINT: String = "varint"

    /** 存放 fixed32 未知字段值的键。 */
    public const val FIXED32: String = "fixed32"

    /** 存放 fixed64 未知字段值的键。 */
    public const val FIXED64: String = "fixed64"

    /** 存放 length-delimited 未知字段值的键。 */
    public const val LENGTH_DELIMITED: String = "lengthDelimited"

    /** 存放 group 未知字段值的键。 */
    public const val GROUPS: String = "groups"
}
