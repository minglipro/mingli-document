package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.codec.DocumentCodec

/**
 * YAML 格式编解码器。
 */
public object YamlCodec : DocumentCodec {

    override val format: String
        get() = "yaml"

    override fun parse(source: String): Node = YamlReader(source).read()

    override fun write(node: Node, pretty: Boolean): String = YamlWriter(pretty).write(node)
}
