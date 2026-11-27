package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.codec.DocumentCodec

/**
 * JSON 格式编解码器。
 */
object JsonCodec : DocumentCodec {

    override val format: String
        get() = "json"

    override fun parse(source: String): Node = JsonReader(source).read()

    override fun write(node: Node, pretty: Boolean): String = JsonWriter(pretty).write(node)
}
