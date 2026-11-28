package com.mingliqiye.document.format.toml

import com.mingliqiye.document.format.toml.exception.TomlParseException
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseError
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.util.ArrayDeque

/**
 * TOML 解析入口（内部实现）。
 *
 * 统一封装 [TomlReader] / [TomlTokenReader] / 注释保留读取器的三件事：
 * 1. 解析前做词法预扫描，拒绝嵌套深度超过 `maxDepth` 的数组/内联 table，
 *    避免把超深输入交给 tomlj 递归下降解析器导致 StackOverflowError；
 * 2. 调用 tomlj 解析并把语法错误包装成 [TomlParseException]（含行列信息），
 *    额外捕获降级失败的 StackOverflowError 转成受控异常；
 * 3. 解析后对结果树做迭代深度校验，覆盖表格路径/点分 key 等词法预扫描不覆盖的深度。
 *
 * TOML 文档根是表，深度从 `1` 开始：`[a]` 中 `a` 的深度为 `2`，`a = [1]` 的数组深度
 * 为 `2`、元素为 `3`。
 */
internal object TomlParser {

    /**
     * 解析 TOML 文本。
     *
     * @param source TOML 文本
     * @param maxDepth 最大嵌套深度；`0` 或负数表示不限制
     * @return tomlj 解析结果
     * @throws TomlParseException 语法错误或嵌套深度超限时抛出
     */
    fun parse(source: String, maxDepth: Int): TomlParseResult {
        if (maxDepth > 0) {
            TomlDepthScanner.checkSource(source, maxDepth)
        }
        val result = try {
            Toml.parse(source)
        } catch (e: TomlParseError) {
            throw parseException(e)
        } catch (e: StackOverflowError) {
            throw TomlParseException("Invalid TOML: nesting too deep to parse", e)
        } catch (e: RuntimeException) {
            throw TomlParseException("Invalid TOML: ${e.message}", e)
        }
        if (result.hasErrors()) {
            throw parseException(result.errors().first())
        }
        if (maxDepth > 0) {
            checkTreeDepth(result, maxDepth)
        }
        return result
    }

    private fun parseException(error: TomlParseError): TomlParseException {
        val position = error.position()
        return TomlParseException(
            "Invalid TOML at line ${position.line()}, column ${position.column()}: ${error.message}",
            error,
        )
    }

    /**
     * 迭代校验结果树的节点深度；根表深度为 `1`。
     *
     * 使用显式栈，避免对深度已超限/极端输入递归遍历。
     */
    private fun checkTreeDepth(root: TomlTable, maxDepth: Int) {
        val stack = ArrayDeque<Pair<Any, Int>>()
        stack.addLast(root to 1)
        while (stack.isNotEmpty()) {
            val (value, depth) = stack.removeLast()
            if (depth > maxDepth) {
                throw TomlParseException("TOML nesting depth $depth exceeds maxDepth $maxDepth")
            }
            val childDepth = depth + 1
            when (value) {
                is TomlTable -> for (key in value.keySet()) {
                    val child = value.get(listOf(key)) ?: continue
                    stack.addLast(child to childDepth)
                }

                is TomlArray -> for (index in 0 until value.size()) {
                    val child = value.get(index) ?: continue
                    stack.addLast(child to childDepth)
                }

                else -> Unit
            }
        }
    }
}

/**
 * TOML 源码嵌套深度词法预扫描。
 *
 * 只关心结构字符：数组 `[ ... ]` 与内联 table `{ ... }` 的嵌套层数。扫描时跳过
 * 注释、基本/字面量字符串与多行字符串（其中的括号不参与计数），并跳过行首的
 * `[table]` / `[[array-table]]` 表头（表头本身不是数组值，其路径深度由解析后的
 * 树校验覆盖）。
 *
 * 目的：在进入 tomlj 的递归下降解析器之前拒绝超深数组/内联 table，保证恶意输入
 * 得到 [TomlParseException] 而不是 StackOverflowError。
 */
private object TomlDepthScanner {

    fun checkSource(source: String, maxDepth: Int) {
        var index = 0
        val length = source.length
        var depth = 0
        var atLineStart = true
        while (index < length) {
            val char = source[index]
            when {
                char == '\n' -> {
                    index++
                    atLineStart = true
                }

                char == ' ' || char == '\t' || char == '\r' -> index++
                char == '#' -> index = skipComment(source, index)
                char == '[' && atLineStart && depth == 0 -> {
                    index = skipTableHeader(source, index)
                    atLineStart = false
                }

                char == '"' -> {
                    index = skipString(source, index)
                    atLineStart = false
                }

                char == '\'' -> {
                    index = skipString(source, index)
                    atLineStart = false
                }

                char == '[' || char == '{' -> {
                    depth++
                    if (depth > maxDepth - 1) {
                        throw depthException(source, index, depth + 1, maxDepth)
                    }
                    atLineStart = false
                    index++
                }

                char == ']' || char == '}' -> {
                    if (depth > 0) {
                        depth--
                    }
                    atLineStart = false
                    index++
                }

                else -> {
                    atLineStart = false
                    index++
                }
            }
        }
    }

    private fun depthException(source: String, index: Int, depth: Int, maxDepth: Int): TomlParseException {
        var line = 1
        var lineStart = 0
        for (position in 0 until index) {
            if (source[position] == '\n') {
                line++
                lineStart = position + 1
            }
        }
        val column = index - lineStart + 1
        return TomlParseException(
            "TOML nesting depth $depth exceeds maxDepth $maxDepth at line $line, column $column",
        )
    }

    private fun skipComment(source: String, start: Int): Int {
        var index = start
        while (index < source.length && source[index] != '\n') {
            index++
        }
        return index
    }

    /**
     * 跳过行首 `[table]` / `[[array-table]]` 表头，返回表头结束后的下标。
     */
    private fun skipTableHeader(source: String, start: Int): Int {
        val arrayTable = start + 1 < source.length && source[start + 1] == '['
        var index = if (arrayTable) start + 2 else start + 1
        while (index < source.length) {
            val char = source[index]
            when {
                char == '\'' || char == '"' -> index = skipString(source, index)
                char == ']' -> {
                    if (arrayTable && index + 1 < source.length && source[index + 1] == ']') {
                        return index + 2
                    }
                    if (!arrayTable) {
                        return index + 1
                    }
                    return index + 1
                }

                char == '\n' -> return index
                else -> index++
            }
        }
        return index
    }

    /**
     * 跳过字符串字面量（单行/多行基本字符串、字面量字符串），返回结束后的下标。
     */
    private fun skipString(source: String, start: Int): Int {
        val quote = source[start]
        if (start + 2 < source.length && source[start + 1] == quote && source[start + 2] == quote) {
            // 多行字符串；多行基本字符串中的反斜杠转义需要跳过下一个字符。
            var index = start + 3
            while (index < source.length) {
                val char = source[index]
                if (char == '\\' && quote == '"') {
                    index += 2
                    continue
                }
                if (char == quote) {
                    var run = 1
                    while (index + run < source.length && source[index + run] == quote) {
                        run++
                    }
                    if (run >= 3) {
                        return index + run
                    }
                    index += run
                    continue
                }
                index++
            }
            return source.length
        }
        var index = start + 1
        while (index < source.length) {
            val char = source[index]
            when {
                char == '\\' && quote == '"' -> index += 2
                char == quote -> return index + 1
                char == '\n' -> return index
                else -> index++
            }
        }
        return source.length
    }
}
