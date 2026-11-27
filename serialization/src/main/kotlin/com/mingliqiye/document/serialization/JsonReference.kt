package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode

/**
 * 本地 `$ref` 序列化辅助：引用属性名、JSON Pointer 构建与引用节点。
 *
 * 内部对象路径以空串表示根，已包含前导 `/`（例如 `/users/0`）；写成 `$ref` 时
 * 根为 `#`、其余为 `#/users/0`，与 [ReferenceResolver] 的解析格式一致。
 */
internal object JsonReference {

    /** 引用属性名；与 [ReferenceResolver] 的解析属性保持一致。 */
    const val PROPERTY: String = "\$ref"

    private const val ROOT_POINTER: String = "#"

    /** 把内部对象路径转换为 `$ref` 的指针值；根对象为 `#`。 */
    fun pointerValue(pointer: String): String = if (pointer.isEmpty()) ROOT_POINTER else "$ROOT_POINTER$pointer"

    /**
     * 追加对象字段 token。
     *
     * 按 RFC 6901 转义 `~` 为 `~0`、`/` 为 `~1`；字段名使用实际写出的文档字段名。
     */
    fun fieldPointer(pointer: String, field: String): String = "$pointer/${escapeToken(field)}"

    /** 追加数组下标 token。 */
    fun indexPointer(pointer: String, index: Int): String = "$pointer/$index"

    /** 生成首次出现对象的引用节点 `{"$ref":"#/..."}`。 */
    fun referenceNode(pointer: String): Node = ObjectNode.of(PROPERTY to StringNode.of(pointerValue(pointer)))

    private fun escapeToken(token: String): String = token.replace("~", "~0").replace("/", "~1")
}
