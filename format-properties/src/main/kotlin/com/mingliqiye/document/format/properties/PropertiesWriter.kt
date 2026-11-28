package com.mingliqiye.document.format.properties

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.properties.exception.PropertiesException
import java.util.Base64

/**
 * 将文档树写成 Java Properties 文本。
 *
 * 写出语义对齐 上游实现 `JavaPropsGenerator` / `JavaPropsSchema`：
 * - 根节点必须是对象或数组（上游实现 允许任意根，本模块为兼容既有行为保持不变）；
 * - 对象字段按 `pathSeparator` 拼接路径（可配置前缀 [PropertiesConfig.prefix]）；
 * - 数组：默认“简单下标”写法（`firstArrayOffset` 起连续编号，如 `tags.1`），
 *   开启 [PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS] 后写作 `tags[1]`；
 * - 支持 `keyValueSeparator`、`lineEnding`、`lineIndentation`、`header`；
 * - 键/值转义对齐 上游实现 `JPropEscapes`（本模块额外保护值行首空格）；
 * - 本模块扩展：字段名中的 `%`、`.`、`[`、`]` 默认百分号编码，保证可往返。
 */
internal class PropertiesWriter(private val config: PropertiesConfig) {

    private val indentLength: Int = config.lineIndentation.length
    private val rootBasePath: String = config.lineIndentation + (config.prefix ?: "")
    private val percentEscape: Boolean = config.escapePercentEncodedSegments
    private val escapeNonAscii: Boolean = config.escapeNonAscii
    private val marker: PropertiesIndexMarker? = config.indexMarker
    private val writeIndexUsingMarkers: Boolean = config.writeIndexUsingMarkers

    fun write(node: Node): String {
        val out = StringBuilder()
        writeTo(node, out)
        return out.toString()
    }

    /**
     * 直接写出到 [Appendable]，不先构造完整 String。
     */
    fun writeTo(node: Node, out: Appendable) {
        when (node) {
            is ObjectNode, is ArrayNode -> Unit
            else -> throw PropertiesException("Properties root must be an object or array")
        }
        val lines = ArrayList<String>()
        writeValue(node, rootBasePath, lines)
        if (lines.isNotEmpty() && config.header.isNotEmpty()) {
            out.append(config.header)
        }
        for (line in lines) {
            out.append(line)
        }
    }

    private fun writeValue(node: Node, path: String, lines: MutableList<String>) {
        when (node) {
            is ObjectNode -> writeObject(node, path, lines)
            is ArrayNode -> writeArray(node, path, lines)
            is StringNode -> addLine(path, PropertiesPath.escapeValue(node.value, escapeNonAscii), lines)
            is NumberNode -> addLine(path, node.raw, lines)
            is BooleanNode -> addLine(path, node.value.toString(), lines)
            is NullNode -> addLine(path, "", lines)
            is BinaryNode -> addLine(
                path,
                PropertiesPath.escapeValue(Base64.getEncoder().encodeToString(node.value), escapeNonAscii),
                lines,
            )
        }
    }

    private fun writeObject(node: ObjectNode, path: String, lines: MutableList<String>) {
        for ((key, value) in node.asMap()) {
            writeValue(value, appendField(path, key), lines)
        }
    }

    private fun writeArray(node: ArrayNode, path: String, lines: MutableList<String>) {
        for (index in 0 until node.size) {
            writeValue(node[index], arrayElementPath(path, index), lines)
        }
    }

    /**
     * 追加对象字段路径段：非根（或配置了前缀）时先追加路径分隔符，再追加转义后的字段名。
     */
    private fun appendField(parent: String, key: String): String {
        val builder = StringBuilder(parent.length + key.length + 8)
        builder.append(parent)
        if (builder.length > indentLength && config.pathSeparator.isNotEmpty()) {
            builder.append(config.pathSeparator)
        }
        builder.append(PropertiesPath.escapeKeySegment(key, percentEscape, escapeNonAscii))
        return builder.toString()
    }

    /**
     * 数组元素路径：默认简单下标（`firstArrayOffset` 起），开启标记特性时写作 `[index]`。
     */
    private fun arrayElementPath(parent: String, index: Int): String {
        val position = index + config.firstArrayOffset
        val marker = marker
        if (writeIndexUsingMarkers && marker != null) {
            return parent + marker.start + position + marker.end
        }
        val builder = StringBuilder(parent.length + 12)
        builder.append(parent)
        if (builder.isNotEmpty() && config.pathSeparator.isNotEmpty()) {
            builder.append(config.pathSeparator)
        }
        builder.append(position)
        return builder.toString()
    }

    private fun addLine(path: String, value: String, lines: MutableList<String>) {
        lines.add(path + config.keyValueSeparator + value + config.lineEnding)
    }
}

/**
 * 将文档树写成 Java Properties 字符串（使用 [PropertiesConfig] 默认格式选项）。
 *
 * @return Properties 文本
 */
public fun Node.toPropertiesString(): String = PropertiesWriter(PropertiesConfig.empty()).write(this)

/**
 * 将文档树写成 Java Properties 字符串。
 *
 * @param config Properties 配置
 * @return Properties 文本
 */
public fun Node.toPropertiesString(config: PropertiesConfig): String = PropertiesWriter(config).write(this)
