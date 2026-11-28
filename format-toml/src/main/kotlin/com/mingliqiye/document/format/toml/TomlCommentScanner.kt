package com.mingliqiye.document.format.toml

import com.mingliqiye.document.format.toml.exception.TomlParseException

/**
 * 注释扫描结果。
 *
 * @param comments 按源文本顺序排列的注释
 * @param blankLines 空行锚点
 * @param structure 源文本中的表/数组表结构
 * @param events 按源文本顺序记录的语句路径，用于重排文档树
 */
internal data class TomlScannedDocument(
    val comments: List<TomlComment>,
    val blankLines: List<TomlBlankLine>,
    val structure: TomlDocumentStructure,
    val events: List<TomlPathEvent>,
)

/**
 * TOML 注释扫描器。
 *
 * tomlj 只负责解析文档树、不保留注释，因此这里用独立的逐行扫描器识别注释、空行与表结构。
 * 扫描器假定输入已经是合法 TOML（调用方先用 [TomlReader] 完成校验），但仍会检查自身的结构
 * 假设，遇到无法识别的行时抛出 [TomlParseException]。
 *
 * 扫描器需要理解的结构很少：表头、键值行、行尾注释、跨行数组/内联 table 与跨行字符串；
 * 值本身交给 tomlj 解析，这里不做类型解析。
 */
internal class TomlCommentScanner(source: String) {

    private val lines: List<String> = splitSourceLines(source)
    private val comments = ArrayList<TomlComment>()
    private val blankLines = ArrayList<TomlBlankLine>()
    private val tablePaths = LinkedHashSet<List<String>>()
    private val arrayTablePaths = LinkedHashSet<List<String>>()
    private val arrayTableOccurrences = HashMap<List<String>, Int>()
    private val events = ArrayList<TomlPathEvent>()

    /** 当前字符串状态；多行字符串会跨行保留。 */
    private var stringMode: StringMode = StringMode.NONE

    /** 当前未闭合的 `[` / `{` 数量；大于 0 表示值还在跨行。 */
    private var bracketDepth = 0

    private var currentTablePath: List<String> = emptyList()
    private var currentTableIndex = -1
    private var activeStatement: Statement? = null
    private var pendingTrivia = ArrayList<Trivia>()
    private var firstContentSeen = false

    /**
     * 扫描源文本。
     *
     * @return 注释、空行与表结构信息
     */
    fun scan(): TomlScannedDocument {
        for ((index, raw) in lines.withIndex()) {
            val lineNumber = index + 1
            val line = if (index == 0 && raw.startsWith(BOM)) raw.substring(BOM.length) else raw
            if (stringMode != StringMode.NONE || bracketDepth > 0) {
                scanContinuationLine(line, lineNumber)
                continue
            }
            if (line.isBlank()) {
                pendingTrivia.add(Trivia.Blank(lineNumber))
                continue
            }
            val start = line.indexOfFirst { !it.isWhitespace() }
            val trimmed = line.substring(start)
            if (trimmed.startsWith("#")) {
                pendingTrivia.add(Trivia.Comment(trimmed.substring(1), lineNumber))
                continue
            }
            if (trimmed.startsWith("[")) {
                scanTableHeader(line, start, lineNumber)
                continue
            }
            scanKeyValueLine(line, lineNumber)
        }
        flushTrailingTrivia()
        return TomlScannedDocument(
            comments,
            blankLines,
            TomlDocumentStructure(tablePaths.toSet(), arrayTablePaths.toSet()),
            events,
        )
    }

    private fun scanTableHeader(line: String, start: Int, lineNumber: Int) {
        val header = parseHeader(line, start, lineNumber)
        val previousTablePath = currentTablePath
        val previousTableIndex = currentTableIndex
        if (header.array) {
            val occurrence = arrayTableOccurrences[header.path] ?: 0
            arrayTableOccurrences[header.path] = occurrence + 1
            arrayTablePaths.add(header.path)
            currentTablePath = header.path
            currentTableIndex = occurrence
        } else {
            tablePaths.add(header.path)
            currentTablePath = header.path
            currentTableIndex = enclosingArrayIndex(header.path)
        }
        flushTrivia(
            Anchor.Table(currentTablePath, currentTableIndex),
            previousTablePath,
            previousTableIndex,
        )
        events.add(TomlPathEvent(currentTablePath, currentTableIndex))
        val rest = line.substring(header.endIndex)
        val commentIndex = firstNonWhitespace(rest)
        if (commentIndex < 0) {
            return
        }
        if (rest[commentIndex] != '#') {
            throw TomlParseException("Invalid TOML at line $lineNumber: unexpected text after table header")
        }
        comments.add(
            TomlComment(
                text = rest.substring(commentIndex + 1),
                kind = TomlCommentKind.INLINE,
                line = lineNumber,
                tablePath = currentTablePath,
                tableIndex = currentTableIndex,
            ),
        )
    }

    private fun scanKeyValueLine(line: String, lineNumber: Int) {
        val equals = findTopLevelEquals(line)
        if (equals < 0) {
            throw TomlParseException("Invalid TOML at line $lineNumber: missing '=' in key/value line")
        }
        val keyPath = TomlKeyParser.parse(line.substring(0, equals), lineNumber)
        val statement = Statement(currentTablePath, currentTableIndex, keyPath)
        flushTrivia(Anchor.Key(statement.tablePath, statement.tableIndex, keyPath), currentTablePath, currentTableIndex)
        events.add(TomlPathEvent(statement.tablePath + statement.keyPath, statement.tableIndex))
        val hits = scanValueLine(line)
        val statementEnds = isNeutral()
        for (hit in hits) {
            comments.add(
                TomlComment(
                    text = hit.text,
                    kind = if (statementEnds) TomlCommentKind.INLINE else TomlCommentKind.INTERIOR,
                    line = lineNumber,
                    tablePath = statement.tablePath,
                    keyPath = statement.keyPath,
                    tableIndex = statement.tableIndex,
                ),
            )
        }
        if (!statementEnds) {
            activeStatement = statement
        }
    }

    private fun scanContinuationLine(line: String, lineNumber: Int) {
        val statement = activeStatement
            ?: throw TomlParseException("Invalid TOML at line $lineNumber: unexpected value continuation")
        val hits = scanValueLine(line)
        val statementEnds = isNeutral()
        for (hit in hits) {
            comments.add(
                TomlComment(
                    text = hit.text,
                    kind = if (statementEnds) TomlCommentKind.INLINE else TomlCommentKind.INTERIOR,
                    line = lineNumber,
                    tablePath = statement.tablePath,
                    keyPath = statement.keyPath,
                    tableIndex = statement.tableIndex,
                ),
            )
        }
        if (statementEnds) {
            activeStatement = null
        }
    }

    private fun flushTrivia(anchor: Anchor, orphanTablePath: List<String>, orphanTableIndex: Int) {
        val isFirstBlock = !firstContentSeen
        firstContentSeen = true
        if (pendingTrivia.isEmpty()) {
            return
        }
        val trivia = pendingTrivia
        pendingTrivia = ArrayList()
        val blanks = trivia.filterIsInstance<Trivia.Blank>()
        for (blank in blanks) {
            blankLines.add(blankLine(blank.line, anchor))
        }
        val groups = splitCommentGroups(trivia)
        val lastIsComment = trivia.last() is Trivia.Comment
        var groupIndex = 0
        if (isFirstBlock && groups.isNotEmpty()) {
            for (comment in groups[0]) {
                comments.add(TomlComment(comment.text, TomlCommentKind.FILE_HEADER, comment.line))
            }
            groupIndex = 1
        }
        val leadingGroupIndex = if (lastIsComment) groups.size - 1 else -1
        while (groupIndex < groups.size) {
            val group = groups[groupIndex]
            if (groupIndex == leadingGroupIndex) {
                addLeadingComments(group, anchor)
            } else {
                for (comment in group) {
                    comments.add(
                        TomlComment(
                            text = comment.text,
                            kind = TomlCommentKind.ORPHAN,
                            line = comment.line,
                            tablePath = orphanTablePath,
                            tableIndex = orphanTableIndex,
                        ),
                    )
                }
            }
            groupIndex++
        }
    }

    private fun addLeadingComments(group: List<Trivia.Comment>, anchor: Anchor) {
        when (anchor) {
            is Anchor.Table -> for (comment in group) {
                comments.add(
                    TomlComment(
                        text = comment.text,
                        kind = TomlCommentKind.LEADING,
                        line = comment.line,
                        tablePath = anchor.path,
                        tableIndex = anchor.index,
                    ),
                )
            }
            is Anchor.Key -> for (comment in group) {
                comments.add(
                    TomlComment(
                        text = comment.text,
                        kind = TomlCommentKind.LEADING,
                        line = comment.line,
                        tablePath = anchor.tablePath,
                        keyPath = anchor.keyPath,
                        tableIndex = anchor.index,
                    ),
                )
            }
        }
    }

    private fun flushTrailingTrivia() {
        activeStatement = null
        if (pendingTrivia.isEmpty()) {
            return
        }
        val trivia = pendingTrivia
        pendingTrivia = ArrayList()
        for (blank in trivia.filterIsInstance<Trivia.Blank>()) {
            blankLines.add(TomlBlankLine(line = blank.line, endOfFile = true))
        }
        for (group in splitCommentGroups(trivia)) {
            for (comment in group) {
                val kind = if (firstContentSeen) TomlCommentKind.FILE_TRAILER else TomlCommentKind.FILE_HEADER
                comments.add(TomlComment(comment.text, kind, comment.line))
            }
        }
    }

    private fun blankLine(line: Int, anchor: Anchor): TomlBlankLine = when (anchor) {
        is Anchor.Table -> TomlBlankLine(line = line, tablePath = anchor.path, tableIndex = anchor.index)
        is Anchor.Key -> TomlBlankLine(
            line = line,
            tablePath = anchor.tablePath,
            keyPath = anchor.keyPath,
            tableIndex = anchor.index,
        )
    }

    private fun splitCommentGroups(trivia: List<Trivia>): List<List<Trivia.Comment>> {
        val groups = ArrayList<MutableList<Trivia.Comment>>()
        var current: MutableList<Trivia.Comment>? = null
        for (item in trivia) {
            when (item) {
                is Trivia.Comment -> {
                    if (current == null) {
                        current = ArrayList()
                    }
                    current.add(item)
                }
                is Trivia.Blank -> {
                    if (current != null) {
                        groups.add(current)
                        current = null
                    }
                }
            }
        }
        if (current != null) {
            groups.add(current)
        }
        return groups
    }

    /**
     * 扫描一行，更新字符串/括号状态并返回本行注释。
     *
     * `#` 之前的内容按 TOML 语法识别字符串边界；遇到注释后本行剩余内容全部当作注释文本。
     */
    private fun scanValueLine(line: String): List<CommentHit> {
        val hits = ArrayList<CommentHit>(1)
        var inBasic = false
        var inLiteral = false
        var index = 0
        while (index < line.length) {
            when (stringMode) {
                StringMode.MULTILINE_BASIC -> {
                    if (line[index] == '\\') {
                        index += 2
                    } else if (line.startsWith(TRIPLE_DOUBLE_QUOTE, index)) {
                        stringMode = StringMode.NONE
                        index += 3
                    } else {
                        index++
                    }
                }
                StringMode.MULTILINE_LITERAL -> {
                    if (line.startsWith(TRIPLE_SINGLE_QUOTE, index)) {
                        stringMode = StringMode.NONE
                        index += 3
                    } else {
                        index++
                    }
                }
                StringMode.NONE -> {
                    if (inBasic) {
                        if (line[index] == '\\') {
                            index += 2
                        } else {
                            if (line[index] == '"') {
                                inBasic = false
                            }
                            index++
                        }
                    } else if (inLiteral) {
                        if (line[index] == '\'') {
                            inLiteral = false
                        }
                        index++
                    } else {
                        when {
                            line[index] == '#' -> {
                                hits.add(CommentHit(line.substring(index + 1)))
                                return hits
                            }
                            line.startsWith(TRIPLE_DOUBLE_QUOTE, index) -> {
                                stringMode = StringMode.MULTILINE_BASIC
                                index += 3
                            }
                            line.startsWith(TRIPLE_SINGLE_QUOTE, index) -> {
                                stringMode = StringMode.MULTILINE_LITERAL
                                index += 3
                            }
                            line[index] == '"' -> {
                                inBasic = true
                                index++
                            }
                            line[index] == '\'' -> {
                                inLiteral = true
                                index++
                            }
                            line[index] == '[' || line[index] == '{' -> {
                                bracketDepth++
                                index++
                            }
                            line[index] == ']' || line[index] == '}' -> {
                                if (bracketDepth > 0) {
                                    bracketDepth--
                                }
                                index++
                            }
                            else -> index++
                        }
                    }
                }
            }
        }
        return hits
    }

    private fun isNeutral(): Boolean = stringMode == StringMode.NONE && bracketDepth == 0

    private fun findTopLevelEquals(line: String): Int {
        var inBasic = false
        var inLiteral = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (inBasic) {
                if (char == '\\') {
                    index += 2
                    continue
                }
                if (char == '"') {
                    inBasic = false
                }
            } else if (inLiteral) {
                if (char == '\'') {
                    inLiteral = false
                }
            } else {
                when (char) {
                    '"' -> inBasic = true
                    '\'' -> inLiteral = true
                    '=' -> return index
                }
            }
            index++
        }
        return -1
    }

    private fun parseHeader(line: String, start: Int, lineNumber: Int): Header {
        var index = start
        val array: Boolean
        if (line.startsWith("[[", index)) {
            array = true
            index += 2
        } else if (line[index] == '[') {
            array = false
            index++
        } else {
            throw TomlParseException("Invalid TOML at line $lineNumber: expected table header")
        }
        index = skipWhitespace(line, index)
        val path = ArrayList<String>()
        while (true) {
            val segment = TomlKeyParser.parseSegment(line, index, lineNumber)
            path.add(segment.value)
            index = skipWhitespace(line, segment.nextIndex)
            if (index < line.length && line[index] == '.') {
                index = skipWhitespace(line, index + 1)
                continue
            }
            break
        }
        index = skipWhitespace(line, index)
        if (array) {
            if (!line.startsWith("]]", index)) {
                throw TomlParseException("Invalid TOML at line $lineNumber: unterminated array table header")
            }
            index += 2
        } else {
            if (index >= line.length || line[index] != ']') {
                throw TomlParseException("Invalid TOML at line $lineNumber: unterminated table header")
            }
            index++
        }
        return Header(array, path, index)
    }

    /** 表头路径中是否嵌套在数组表元素内；返回最近的数组表元素下标，未嵌套时返回 `-1`。 */
    private fun enclosingArrayIndex(path: List<String>): Int {
        var enclosing: List<String>? = null
        for (arrayPath in arrayTablePaths) {
            if (path.size <= arrayPath.size) {
                continue
            }
            if (path.subList(0, arrayPath.size) != arrayPath) {
                continue
            }
            if (enclosing == null || arrayPath.size > enclosing.size) {
                enclosing = arrayPath
            }
        }
        val enclosingPath = enclosing ?: return -1
        return (arrayTableOccurrences[enclosingPath] ?: 1) - 1
    }

    private fun firstNonWhitespace(text: String): Int = text.indexOfFirst { !it.isWhitespace() }

    private fun skipWhitespace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && (text[index] == ' ' || text[index] == '\t')) {
            index++
        }
        return index
    }

    private enum class StringMode {
        NONE,
        MULTILINE_BASIC,
        MULTILINE_LITERAL,
    }

    private sealed interface Trivia {
        data class Blank(val line: Int) : Trivia

        data class Comment(val text: String, val line: Int) : Trivia
    }

    private sealed interface Anchor {
        data class Table(val path: List<String>, val index: Int) : Anchor

        data class Key(val tablePath: List<String>, val index: Int, val keyPath: List<String>) : Anchor
    }

    private data class Statement(
        val tablePath: List<String>,
        val tableIndex: Int,
        val keyPath: List<String>,
    )

    private data class CommentHit(val text: String)

    private data class Header(val array: Boolean, val path: List<String>, val endIndex: Int)

    private companion object {
        const val BOM: String = "\uFEFF"
        const val TRIPLE_DOUBLE_QUOTE: String = "\"\"\""
        const val TRIPLE_SINGLE_QUOTE: String = "'''"
    }
}

/**
 * TOML 点分键解析器。
 *
 * 同时用于表头路径与键值行的键路径；支持裸 key、基本字符串 key、字面量字符串 key 与转义。
 */
internal object TomlKeyParser {

    /**
     * 解析完整的点分键路径。
     *
     * @param text 键文本（可包含首尾空白与点分隔符）
     * @param lineNumber 行号，仅用于异常信息
     * @return 解析出的键路径
     */
    fun parse(text: String, lineNumber: Int): List<String> {
        val path = ArrayList<String>()
        var index = skipWhitespace(text, 0)
        while (true) {
            val segment = parseSegment(text, index, lineNumber)
            path.add(segment.value)
            index = skipWhitespace(text, segment.nextIndex)
            if (index >= text.length) {
                return path
            }
            if (text[index] != '.') {
                throw TomlParseException("Invalid TOML at line $lineNumber: invalid key '$text'")
            }
            index = skipWhitespace(text, index + 1)
        }
    }

    /**
     * 从 [start] 开始解析一个键片段。
     *
     * @return 片段值与下一个未消费字符下标
     */
    fun parseSegment(text: String, start: Int, lineNumber: Int): Segment {
        if (start >= text.length) {
            throw TomlParseException("Invalid TOML at line $lineNumber: empty key")
        }
        return when (text[start]) {
            '"' -> parseBasicString(text, start, lineNumber)
            '\'' -> parseLiteralString(text, start, lineNumber)
            else -> parseBareKey(text, start, lineNumber)
        }
    }

    private fun parseBareKey(text: String, start: Int, lineNumber: Int): Segment {
        var index = start
        while (index < text.length && isBareKeyCharacter(text[index])) {
            index++
        }
        if (index == start) {
            throw TomlParseException("Invalid TOML at line $lineNumber: invalid bare key")
        }
        return Segment(text.substring(start, index), index)
    }

    private fun parseBasicString(text: String, start: Int, lineNumber: Int): Segment {
        val builder = StringBuilder()
        var index = start + 1
        while (index < text.length) {
            val char = text[index]
            if (char == '"') {
                return Segment(builder.toString(), index + 1)
            }
            if (char == '\\') {
                index = appendEscape(text, index, lineNumber, builder)
                continue
            }
            builder.append(char)
            index++
        }
        throw TomlParseException("Invalid TOML at line $lineNumber: unterminated quoted key")
    }

    private fun parseLiteralString(text: String, start: Int, lineNumber: Int): Segment {
        val end = text.indexOf('\'', start + 1)
        if (end < 0) {
            throw TomlParseException("Invalid TOML at line $lineNumber: unterminated literal key")
        }
        return Segment(text.substring(start + 1, end), end + 1)
    }

    private fun appendEscape(text: String, start: Int, lineNumber: Int, builder: StringBuilder): Int {
        if (start + 1 >= text.length) {
            throw TomlParseException("Invalid TOML at line $lineNumber: unterminated escape in key")
        }
        val escaped = text[start + 1]
        when (escaped) {
            '"' -> builder.append('"')
            '\\' -> builder.append('\\')
            'b' -> builder.append('\b')
            't' -> builder.append('\t')
            'n' -> builder.append('\n')
            'f' -> builder.append('\u000C')
            'r' -> builder.append('\r')
            'u' -> return appendUnicodeEscape(text, start + 2, 4, lineNumber, builder)
            'U' -> return appendUnicodeEscape(text, start + 2, 8, lineNumber, builder)
            else -> throw TomlParseException("Invalid TOML at line $lineNumber: invalid escape '\\$escaped' in key")
        }
        return start + 2
    }

    private fun appendUnicodeEscape(
        text: String,
        digitsStart: Int,
        digits: Int,
        lineNumber: Int,
        builder: StringBuilder,
    ): Int {
        val end = digitsStart + digits
        if (end > text.length) {
            throw TomlParseException("Invalid TOML at line $lineNumber: truncated unicode escape in key")
        }
        val codePoint = text.substring(digitsStart, end).toIntOrNull(16)
            ?: throw TomlParseException("Invalid TOML at line $lineNumber: invalid unicode escape in key")
        if (codePoint > 0x10FFFF) {
            throw TomlParseException("Invalid TOML at line $lineNumber: invalid unicode code point in key")
        }
        builder.appendCodePoint(codePoint)
        return end
    }

    private fun isBareKeyCharacter(char: Char): Boolean = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '_' || char == '-'

    private fun skipWhitespace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && (text[index] == ' ' || text[index] == '\t')) {
            index++
        }
        return index
    }

    /** 一个键片段解析结果。 */
    data class Segment(val value: String, val nextIndex: Int)
}

/** 按行拆分源文本；去掉 `\r`，并丢弃末尾换行产生的伪空行。 */
private fun splitSourceLines(source: String): List<String> {
    if (source.isEmpty()) {
        return emptyList()
    }
    val lines = source.split('\n')
    val result = ArrayList<String>(lines.size)
    for (index in lines.indices) {
        if (index == lines.size - 1 && lines.size > 1 && lines[index].isEmpty() && source.endsWith('\n')) {
            break
        }
        result.add(lines[index].removeSuffix("\r"))
    }
    return result
}
