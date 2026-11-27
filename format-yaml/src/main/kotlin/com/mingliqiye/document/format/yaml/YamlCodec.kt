package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.Node

/**
 * YAML 文本编解码内部实现，仅供 [YamlMapper] 与同模块测试使用。
 */
internal object YamlCodec {

    /** 格式名称。 */
    val format: String = "yaml"

    /** 解析 YAML 文本。 */
    fun parse(source: String): Node = YamlReader(source).read()

    /** 写出 YAML 文本。 */
    fun write(node: Node, pretty: Boolean): String = YamlWriter(pretty).write(node)
}
