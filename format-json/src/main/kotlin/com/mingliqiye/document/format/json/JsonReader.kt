package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.json.exception.JsonParseException

/**
 * JSON 读取入口。
 *
 * 默认接受标准 JSON、额外空白、`//` 行注释和块注释（即 JSONC 风格）；
 * 传入 `allowComments = false` 可恢复严格 JSON 模式。
 *
 * @param source JSON 文本
 * @param allowComments 是否允许注释，默认 `true`
 * @param duplicateKeyPolicy 对象内重复 key 的处理策略，默认 [DuplicateKeyPolicy.LAST_WINS]
 * @param maxDepth 对象/数组最大嵌套深度，必须为正数，默认 [JsonConfig.DEFAULT_MAX_DEPTH]
 */
public class JsonReader(
    private val source: String,
    private val allowComments: Boolean = true,
    private val duplicateKeyPolicy: DuplicateKeyPolicy = DuplicateKeyPolicy.LAST_WINS,
    private val maxDepth: Int = JsonConfig.DEFAULT_MAX_DEPTH,
) {

    /**
     * 兼容旧构造器。
     */
    public constructor(source: String, allowComments: Boolean) :
        this(source, allowComments, DuplicateKeyPolicy.LAST_WINS, JsonConfig.DEFAULT_MAX_DEPTH)

    /**
     * 解析并返回文档树。
     *
     * @throws JsonParseException JSON 格式非法、重复 key 策略为
     * [DuplicateKeyPolicy.FAIL] 且存在重复 key、或嵌套深度超过 [maxDepth] 时抛出
     */
    public fun read(): Node = JsonParser(source, allowComments, duplicateKeyPolicy, maxDepth).parse()

    public companion object {

        /**
         * 解析 JSON 字符串，允许注释。
         *
         * @throws JsonParseException JSON 格式非法时抛出
         */
        @JvmStatic
        public fun parse(source: String): Node = JsonReader(source).read()

        /**
         * 解析 JSON 字符串。
         *
         * @param source JSON 文本
         * @param allowComments 是否允许注释
         * @throws JsonParseException JSON 格式非法时抛出
         */
        @JvmStatic
        public fun parse(source: String, allowComments: Boolean): Node = JsonReader(source, allowComments).read()
    }
}

/**
 * 将字符串解析为文档树（默认允许注释）。
 *
 * @throws JsonParseException JSON 格式非法时抛出
 */
public fun String.toJsonNode(): Node = JsonReader(this).read()
