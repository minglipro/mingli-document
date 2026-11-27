package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.Node

/**
 * JSON 文本编解码内部实现，仅供 [JsonMapper] 与同模块测试使用。
 */
internal object JsonCodec {

    /** 格式名称。 */
    val format: String = "json"

    /** 解析 JSON 文本。 */
    fun parse(source: String): Node = JsonReader(source).read()

    /** 写出 JSON 文本。 */
    fun write(node: Node, pretty: Boolean): String = JsonWriter(pretty).write(node)
}
