package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.yaml.exception.YamlParseException

/**
 * YAML 单文档读取入口。
 *
 * 使用 SnakeYAML 的安全构造器，只还原标准 YAML 标量、集合和映射，
 * 不实例化任意 Java 类型。`!!binary` 解码为
 * [com.mingliqiye.document.core.BinaryNode] 而不是 Base64 字符串节点。
 *
 * 输入包含多个文档时请使用 [YamlMultiDocumentReader] 或 [YamlMapper.parseAllToNodes]。
 *
 * @param source YAML 文本
 */
public class YamlReader @JvmOverloads constructor(
    private val source: String,
    private val maxDepth: Int = 0,
) {

    /**
     * 解析并返回文档树。
     *
     * 空文本或只含注释的文本返回 [com.mingliqiye.document.core.NullNode]；
     * 多个文档的输入会作为格式错误抛出，多文档读取请使用 [YamlMultiDocumentReader]。
     *
     * @throws YamlParseException YAML 格式非法或包含不支持的类型时抛出
     */
    public fun read(): Node = YamlNodeMapping.readRoot(source, maxDepth)

    public companion object {

        /**
         * 解析 YAML 字符串。
         *
         * @param source YAML 文本
         * @return 文档树
         * @throws YamlParseException YAML 格式非法时抛出
         */
        @JvmStatic
        public fun parse(source: String): Node = YamlReader(source).read()
    }
}

/**
 * 将字符串解析为文档树。
 *
 * @return 文档树
 * @throws YamlParseException YAML 格式非法时抛出
 */
public fun String.toYamlNode(): Node = YamlReader(this).read()
