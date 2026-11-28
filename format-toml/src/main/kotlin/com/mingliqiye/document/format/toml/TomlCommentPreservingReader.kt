package com.mingliqiye.document.format.toml

/**
 * 保留注释的 TOML 读取器。
 *
 * 先用 [TomlReader] 调用 tomlj 解析出文档树并完成语法校验，再用独立的行扫描器
 * （[TomlCommentScanner]）识别注释、空行与表/数组表结构，最终组合成 [TomlCommentedDocument]。
 * 由于 tomlj 的表内部使用 `HashMap`，读取结果会按源文本语句顺序重排对象字段
 * （[TomlNodeOrderer]），保证写出时表/键顺序与注释位置和源文本一致。
 *
 * tomlj 1.0.0 的解析结果不包含注释，所以注释无法从文档树反推，必须单独扫描源文本。
 *
 * @param source TOML 文本
 * @param maxDepth 解析最大嵌套深度，默认 [TomlMapper.DEFAULT_MAX_DEPTH]；超限抛
 *   [com.mingliqiye.document.format.toml.exception.TomlParseException]
 */
public class TomlCommentPreservingReader(
    private val source: String,
    private val maxDepth: Int = TomlMapper.DEFAULT_MAX_DEPTH,
) {

    /**
     * 读取并保留注释。
     *
     * @return 保留注释的文档
     * @throws com.mingliqiye.document.format.toml.exception.TomlParseException TOML 文本非法或
     *   嵌套深度超过 [maxDepth] 时抛出
     */
    public fun read(): TomlCommentedDocument {
        val node = TomlReader(source, maxDepth).read()
        val scanned = TomlCommentScanner(source).scan()
        val ordered = TomlNodeOrderer.reorder(node.asObject(), scanned.events, scanned.structure)
        return TomlCommentedDocument(ordered, scanned.comments, scanned.blankLines, scanned.structure)
    }

    public companion object {

        /**
         * 解析保留注释的 TOML 文本。
         *
         * @param source TOML 文本
         * @param maxDepth 解析最大嵌套深度，默认 [TomlMapper.DEFAULT_MAX_DEPTH]
         * @return 保留注释的文档
         * @throws com.mingliqiye.document.format.toml.exception.TomlParseException TOML 文本非法或
         *   嵌套深度超过 [maxDepth] 时抛出
         */
        @JvmStatic
        @JvmOverloads
        public fun parse(source: String, maxDepth: Int = TomlMapper.DEFAULT_MAX_DEPTH): TomlCommentedDocument = TomlCommentPreservingReader(source, maxDepth).read()
    }
}
