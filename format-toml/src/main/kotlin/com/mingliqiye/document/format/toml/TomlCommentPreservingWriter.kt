package com.mingliqiye.document.format.toml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode

/**
 * 保留注释的 TOML 写出器。
 *
 * 按 [TomlCommentedDocument] 中的表结构信息输出 `[table]` / `[[array-table]]` 表节，
 * 其余对象写成内联 table；注释按关联路径放回对应语句：
 * - [TomlCommentKind.FILE_HEADER] 输出在文件开头；
 * - [TomlCommentKind.LEADING] 输出在对应表头/键值行上方；
 * - [TomlCommentKind.INLINE] 输出在对应表头/键值行行尾；
 * - [TomlCommentKind.INTERIOR] 输出在所属键值行上方（独立注释行）；
 * - [TomlCommentKind.FILE_TRAILER] 输出在文件末尾；
 * - [TomlCommentKind.ORPHAN] 按 [TomlCommentConfig.orphanCommentPolicy] 处理。
 *
 * 关联的键/表在 [TomlCommentedDocument.withRoot] 之后已不存在时，相关注释同样按孤立注释策略处理。
 */
public class TomlCommentPreservingWriter @JvmOverloads constructor(
    /** 写出配置。 */
    public val config: TomlCommentConfig = TomlCommentConfig(),
    /** TOML 写出特性集合，默认 [TomlConfig.DEFAULT_WRITE_FEATURES]。 */
    public val writeFeatures: Set<TomlWriteFeature> = TomlConfig.DEFAULT_WRITE_FEATURES,
) {

    /**
     * 写出保留注释的 TOML 文本。
     *
     * @param document 保留注释的文档
     * @return TOML 文本
     */
    public fun write(document: TomlCommentedDocument): String = TomlDocumentRenderer(document, config, writeFeatures).render()
}

/**
 * [TomlCommentedDocument] -> TOML 文本的渲染计划/执行器。
 *
 * 分三步：先遍历文档树生成输出语句序列（同时记录每条语句的锚点），再按路径把注释/空行
 * 分配到锚点（找不到锚点的按孤立注释策略处理），最后按顺序输出文本。
 */
internal class TomlDocumentRenderer(
    private val document: TomlCommentedDocument,
    private val config: TomlCommentConfig,
    writeFeatures: Set<TomlWriteFeature> = TomlConfig.DEFAULT_WRITE_FEATURES,
) {

    private val structure = document.structure
    private val emissions = ArrayList<TomlEmission>()
    private val sections = ArrayList<SectionPlan>()
    private val rootSection = SectionPlan(emptyList(), -1, -1)
    private val fileHeaderComments = ArrayList<TomlComment>()
    private val fileTrailerComments = ArrayList<TomlComment>()
    private val endOrphanComments = ArrayList<TomlComment>()
    private var endOfFileBlankLines = 0
    private val valueWriter = TomlWriter(writeFeatures)

    fun render(): String {
        buildEmissions()
        resolveComments()
        return joinLines(renderLines())
    }

    // ------------------------------------------------------------------
    // 第一步：遍历文档树生成输出语句
    // ------------------------------------------------------------------

    private fun buildEmissions() {
        for ((key, value) in document.root.asMap()) {
            if (isSectionValue(listOf(key), value)) {
                continue
            }
            appendKeyLine(emptyList(), -1, key, value)
        }
        rootSection.bodyEndIndex = emissions.size - 1
        sections.add(rootSection)
        for ((key, value) in document.root.asMap()) {
            if (isSectionValue(listOf(key), value)) {
                appendSectionValue(listOf(key), -1, value)
            }
        }
    }

    /**
     * 判断字段值是否应写成表节。
     *
     * 读取时记录的表路径一定是表节；源文本中只有子表头（例如只有 `[a.b]`）的对象
     * 也按表节处理。数组表要求非空且元素全是对象。
     */
    private fun isSectionValue(path: List<String>, value: Node): Boolean = when (value) {
        is ObjectNode -> structure.tablePaths.contains(path) || structure.hasDescendantSection(path)
        is ArrayNode -> structure.arrayTablePaths.contains(path) &&
            value.size > 0 &&
            (0 until value.size).all { value[it] is ObjectNode }
        else -> false
    }

    private fun appendKeyLine(tablePath: List<String>, tableIndex: Int, key: String, value: Node) {
        val text = valueWriter.formatKeyText(key) + " = " + valueWriter.formatValueText(value)
        emissions.add(TomlEmission(TomlEmissionKind.KEY_VALUE, tablePath, tableIndex, listOf(key), text))
    }

    private fun appendSectionValue(path: List<String>, enclosingIndex: Int, value: Node) {
        when (value) {
            is ObjectNode -> {
                if (structure.tablePaths.contains(path) || hasBodyValues(path, value)) {
                    appendObjectSection(path, enclosingIndex, value)
                } else {
                    appendChildSections(path, enclosingIndex, value)
                }
            }
            is ArrayNode -> for (index in 0 until value.size) {
                val element = value[index]
                if (element is ObjectNode) {
                    appendArrayTableSection(path, index, element)
                }
            }
            else -> Unit
        }
    }

    private fun hasBodyValues(sectionPath: List<String>, value: ObjectNode): Boolean = value.asMap().entries.any { (key, child) ->
        !isSectionValue(sectionPath + key, child)
    }

    private fun appendObjectSection(path: List<String>, tableIndex: Int, value: ObjectNode) {
        val headerIndex = emissions.size
        emissions.add(
            TomlEmission(
                TomlEmissionKind.TABLE_HEADER,
                path,
                tableIndex,
                emptyList(),
                "[" + formatPath(path) + "]",
            ),
        )
        val section = SectionPlan(path, tableIndex, headerIndex)
        appendBody(section, value)
        sections.add(section)
        appendChildSections(path, tableIndex, value)
    }

    private fun appendArrayTableSection(path: List<String>, elementIndex: Int, value: ObjectNode) {
        val headerIndex = emissions.size
        emissions.add(
            TomlEmission(
                TomlEmissionKind.TABLE_HEADER,
                path,
                elementIndex,
                emptyList(),
                "[[" + formatPath(path) + "]]",
            ),
        )
        val section = SectionPlan(path, elementIndex, headerIndex)
        appendBody(section, value)
        sections.add(section)
        appendChildSections(path, elementIndex, value)
    }

    private fun appendBody(section: SectionPlan, value: ObjectNode) {
        for ((key, child) in value.asMap()) {
            if (isSectionValue(section.tablePath + key, child)) {
                continue
            }
            appendKeyLine(section.tablePath, section.tableIndex, key, child)
        }
        section.bodyEndIndex = emissions.size - 1
    }

    private fun appendChildSections(sectionPath: List<String>, enclosingIndex: Int, value: ObjectNode) {
        for ((key, child) in value.asMap()) {
            val childPath = sectionPath + key
            if (!isSectionValue(childPath, child)) {
                continue
            }
            appendSectionValue(childPath, enclosingIndex, child)
        }
    }

    private fun formatPath(path: List<String>): String = path.joinToString(".") { valueWriter.formatKeyText(it) }

    // ------------------------------------------------------------------
    // 第二步：把注释/空行分配到输出语句
    // ------------------------------------------------------------------

    private fun resolveComments() {
        for (comment in document.comments) {
            when (comment.kind) {
                TomlCommentKind.FILE_HEADER -> fileHeaderComments.add(comment)
                TomlCommentKind.FILE_TRAILER -> fileTrailerComments.add(comment)
                TomlCommentKind.ORPHAN -> placeOrphan(comment)
                else -> placeAnchoredComment(comment)
            }
        }
        if (!config.preserveBlankLines) {
            return
        }
        for (blankLine in document.blankLines) {
            if (blankLine.endOfFile) {
                endOfFileBlankLines++
                continue
            }
            val index = resolveEmission(blankLine.tablePath, blankLine.tableIndex, blankLine.keyPath)
            if (index >= 0) {
                emissions[index].blankLines.add(blankLine)
            }
        }
    }

    private fun placeAnchoredComment(comment: TomlComment) {
        val index = resolveEmission(comment.tablePath, comment.tableIndex, comment.keyPath)
        if (index < 0) {
            placeOrphan(comment)
            return
        }
        if (comment.kind == TomlCommentKind.INLINE) {
            emissions[index].inlineComments.add(comment)
        } else {
            emissions[index].leadingComments.add(comment)
        }
    }

    private fun placeOrphan(comment: TomlComment) {
        when (config.orphanCommentPolicy) {
            TomlOrphanCommentPolicy.DROP -> Unit
            TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_FILE -> endOrphanComments.add(comment)
            TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_TABLE -> {
                val section = findSection(comment.tablePath, comment.tableIndex)
                if (section == null) {
                    endOrphanComments.add(comment)
                } else {
                    section.orphanComments.add(comment)
                }
            }
        }
    }

    private fun findSection(tablePath: List<String>, tableIndex: Int): SectionPlan? {
        if (tablePath.isEmpty() && tableIndex < 0) {
            return rootSection
        }
        return sections.firstOrNull { it.tablePath == tablePath && it.tableIndex == tableIndex }
    }

    /**
     * 把注释/空行的关联路径解析成输出语句下标。
     *
     * - 表头注释（[keyPath] 为空）匹配同路径的表头语句；
     * - 键值行注释优先精确匹配，其次匹配“键路径是其前缀”的键值行
     *   （例如带点键 `a.b` 被写成内联 table `a = { b = ... }` 时，注释挂到 `a` 行）。
     *
     * @return 输出语句下标；找不到时返回 `-1`
     */
    private fun resolveEmission(tablePath: List<String>, tableIndex: Int, keyPath: List<String>): Int {
        if (keyPath.isEmpty()) {
            return emissions.indexOfFirst {
                it.kind == TomlEmissionKind.TABLE_HEADER &&
                    it.tablePath == tablePath &&
                    it.tableIndex == tableIndex
            }
        }
        var bestIndex = -1
        var bestLength = -1
        for (index in emissions.indices) {
            val emission = emissions[index]
            if (emission.kind != TomlEmissionKind.KEY_VALUE) {
                continue
            }
            if (emission.tablePath != tablePath || emission.tableIndex != tableIndex) {
                continue
            }
            if (emission.keyPath == keyPath) {
                return index
            }
            if (emission.keyPath.size >= keyPath.size) {
                continue
            }
            if (keyPath.subList(0, emission.keyPath.size) != emission.keyPath) {
                continue
            }
            if (emission.keyPath.size > bestLength) {
                bestLength = emission.keyPath.size
                bestIndex = index
            }
        }
        return bestIndex
    }

    // ------------------------------------------------------------------
    // 第三步：输出
    // ------------------------------------------------------------------

    private fun renderLines(): List<String> {
        val lines = ArrayList<String>()
        for (comment in fileHeaderComments) {
            lines.add(renderComment(comment))
        }
        if (rootSection.bodyEndIndex < 0) {
            flushOrphanComments(rootSection, lines)
        }
        for (index in emissions.indices) {
            val emission = emissions[index]
            if (config.preserveBlankLines && lines.isNotEmpty()) {
                repeat(emission.blankLines.size) { lines.add("") }
            }
            for (comment in emission.leadingComments) {
                lines.add(renderComment(comment))
            }
            lines.add(renderEmission(emission))
            for (section in sections) {
                if (section.bodyEndIndex == index) {
                    flushOrphanComments(section, lines)
                }
            }
        }
        for (comment in endOrphanComments) {
            lines.add(renderComment(comment))
        }
        if (config.preserveBlankLines) {
            repeat(endOfFileBlankLines) { lines.add("") }
        }
        for (comment in fileTrailerComments) {
            lines.add(renderComment(comment))
        }
        return lines
    }

    private fun flushOrphanComments(section: SectionPlan, lines: MutableList<String>) {
        for (comment in section.orphanComments) {
            lines.add(renderComment(comment))
        }
    }

    private fun renderComment(comment: TomlComment): String = "#" + comment.text

    private fun renderEmission(emission: TomlEmission): String {
        if (emission.inlineComments.isEmpty()) {
            return emission.text
        }
        val builder = StringBuilder(emission.text)
        for (comment in emission.inlineComments) {
            builder.append(" #").append(comment.text)
        }
        return builder.toString()
    }

    private fun joinLines(lines: List<String>): String {
        if (lines.isEmpty()) {
            return ""
        }
        val text = lines.joinToString("\n")
        return if (config.trailingNewline) text + "\n" else text
    }

    private enum class TomlEmissionKind {
        TABLE_HEADER,
        KEY_VALUE,
    }

    private class TomlEmission(
        val kind: TomlEmissionKind,
        val tablePath: List<String>,
        val tableIndex: Int,
        val keyPath: List<String>,
        val text: String,
    ) {
        val leadingComments = ArrayList<TomlComment>()
        val inlineComments = ArrayList<TomlComment>()
        val blankLines = ArrayList<TomlBlankLine>()
    }

    private class SectionPlan(
        val tablePath: List<String>,
        val tableIndex: Int,
        val headerIndex: Int,
    ) {
        var bodyEndIndex: Int = headerIndex
        val orphanComments = ArrayList<TomlComment>()
    }
}
