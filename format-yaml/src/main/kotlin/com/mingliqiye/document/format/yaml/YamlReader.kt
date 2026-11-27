package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.yaml.exception.YamlParseException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64
import java.util.Date

/**
 * YAML 读取入口。
 *
 * 使用 SnakeYAML 的安全构造器，只还原标准 YAML 标量、集合和映射，
 * 不实例化任意 Java 类型。
 *
 * @param source YAML 文本
 */
public class YamlReader(
    private val source: String,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws YamlParseException YAML 格式非法或包含不支持的类型时抛出
     */
    public fun read(): Node {
        val loaded = try {
            createYaml().load<Any?>(source)
        } catch (e: YAMLException) {
            throw YamlParseException("Invalid YAML: ${e.message}", e)
        }
        return toNode(loaded)
    }

    private fun createYaml(): Yaml {
        val options = LoaderOptions()
        options.setAllowDuplicateKeys(false)
        return Yaml(SafeConstructor(options))
    }

    private fun toNode(value: Any?): Node = when (value) {
        null -> NullNode.INSTANCE
        is Node -> value
        is Map<*, *> -> toObjectNode(value)
        is Iterable<*> -> toArrayNode(value)
        is String -> StringNode.of(value)
        is Char -> StringNode.of(value.toString())
        is Boolean -> BooleanNode.of(value)
        is Number -> toNumberNode(value)
        is Date -> StringNode.of(value.toInstant().toString())
        is ByteArray -> StringNode.of(Base64.getEncoder().encodeToString(value))
        else -> throw YamlParseException("Unsupported YAML value type: ${value.javaClass.name}")
    }

    private fun toObjectNode(value: Map<*, *>): ObjectNode {
        val fields = LinkedHashMap<String, Node>(value.size)
        for ((key, child) in value) {
            fields[toFieldName(key)] = toNode(child)
        }
        return ObjectNode.from(fields)
    }

    private fun toArrayNode(value: Iterable<*>): ArrayNode {
        val elements = ArrayList<Node>()
        for (element in value) {
            elements.add(toNode(element))
        }
        return ArrayNode.from(elements)
    }

    private fun toFieldName(key: Any?): String = when (key) {
        null -> "null"
        is String -> key
        is Number, is Boolean, is Char -> key.toString()
        else -> throw YamlParseException("Unsupported YAML mapping key type: ${key.javaClass.name}")
    }

    private fun toNumberNode(value: Number): NumberNode = when (value) {
        is BigDecimal -> NumberNode.of(value)
        is BigInteger -> NumberNode.of(value)
        is Double -> {
            if (!value.isFinite()) {
                throw YamlParseException("YAML NaN/Infinity numbers are not supported")
            }
            NumberNode.of(value)
        }

        is Float -> {
            if (!value.isFinite()) {
                throw YamlParseException("YAML NaN/Infinity numbers are not supported")
            }
            NumberNode.of(value)
        }

        is Int, is Short, is Byte -> NumberNode.of(value.toLong())
        is Long -> NumberNode.of(value)
        else -> NumberNode.ofRaw(value.toString())
    }

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
