package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.Node

/**
 * JSON 读取入口。
 *
 * 默认接受标准 JSON、额外空白、`//` 行注释和块注释（即 JSONC 风格）；
 * 传入 `allowComments = false` 可恢复严格 JSON 模式。
 *
 * @param source JSON 文本
 * @param allowComments 是否允许注释，默认 `true`
 */
class JsonReader(
    private val source: String,
    private val allowComments: Boolean = true,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws JsonParseException JSON 格式非法时抛出
     */
    fun read(): Node = JsonParser(source, allowComments).parse()

    companion object {

        /**
         * 解析 JSON 字符串，允许注释。
         *
         * @throws JsonParseException JSON 格式非法时抛出
         */
        @JvmStatic
        fun parse(source: String): Node = JsonReader(source).read()

        /**
         * 解析 JSON 字符串。
         *
         * @param source JSON 文本
         * @param allowComments 是否允许注释
         * @throws JsonParseException JSON 格式非法时抛出
         */
        @JvmStatic
        fun parse(source: String, allowComments: Boolean): Node = JsonReader(source, allowComments).read()
    }
}

/**
 * 将字符串解析为文档树（默认允许注释）。
 *
 * @throws JsonParseException JSON 格式非法时抛出
 */
fun String.toJsonNode(): Node = JsonReader(this).read()
