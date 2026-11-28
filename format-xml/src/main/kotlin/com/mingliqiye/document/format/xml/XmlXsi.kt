package com.mingliqiye.document.format.xml

import com.mingliqiye.document.format.xml.exception.XmlParseException
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.math.BigDecimal
import java.util.Base64

/**
 * `xsi:nil` / `xsi:type` 识别与 XSD 内建标量类型解析。
 *
 * 供 [XmlReader]（Node 路径）与 [XmlTokenReader]（默认配置 Token 路径）共用，
 * 保证两条读取路径对 `xsi` 语义一致。
 */
internal object XmlXsi {

    private const val PROTOCOL_NIL = "nil"
    private const val PROTOCOL_TYPE = "type"

    private val integerPattern = Regex("[+-]?\\d+")

    /**
     * 元素是否声明 `xsi:nil="true"`（也接受无前缀的 `nil="true"` 协议属性）。
     */
    fun isNil(element: Element): Boolean {
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index) as? Attr ?: continue
            if (isNilAttribute(element, attribute) && attribute.value == "true") {
                return true
            }
        }
        return false
    }

    /**
     * 判断属性是否为 nil 声明（`nil` 协议属性或 XSI 命名空间下的 `nil`）。
     */
    fun isNilAttribute(element: Element, attribute: Attr): Boolean {
        val name = attribute.name
        if (name == PROTOCOL_NIL) {
            return true
        }
        return resolveXsiLocalName(element, attribute) == PROTOCOL_NIL
    }

    /**
     * 判断属性是否为 `xsi:type` 声明。
     */
    fun isTypeAttribute(element: Element, attribute: Attr): Boolean = resolveXsiLocalName(element, attribute) == PROTOCOL_TYPE

    /**
     * 读取 `xsi:type` 的本地类型名（小写）；没有时返回 `null`。
     *
     * 同时兼容命名空间感知 DOM 与非命名空间感知 DOM（非感知时按 `xmlns:前缀`
     * 声明解析前缀），Clark 形式 `{uri}local` 会取其本地名。
     */
    fun typeName(element: Element): String? {
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index) as? Attr ?: continue
            if (isTypeAttribute(element, attribute)) {
                return localTypeName(attribute.value)
            }
        }
        return null
    }

    /**
     * 若属性属于 XSI 命名空间（任意前缀），返回其本地名；否则返回 `null`。
     *
     * 命名空间感知 DOM 直接看 `namespaceURI`；非感知 DOM 通过属性名前缀 + `xmlns:前缀`
     * 声明（含祖先元素）还原 URI。
     */
    private fun resolveXsiLocalName(element: Element, attribute: Attr): String? {
        if (attribute.namespaceURI == XmlConfig.XSI_NAMESPACE) {
            return attribute.localName ?: attribute.name.substringAfterLast(':')
        }
        val name = attribute.name
        val colon = name.indexOf(':')
        if (colon <= 0 || colon == name.length - 1) {
            return null
        }
        val prefix = name.substring(0, colon)
        if (!isXsiPrefixBound(element, prefix)) {
            return null
        }
        return name.substring(colon + 1)
    }

    private fun isXsiPrefixBound(element: Element, prefix: String): Boolean {
        var current: org.w3c.dom.Node? = element
        while (current is Element) {
            val declared = current.getAttribute("xmlns:$prefix")
            if (declared.isNotEmpty()) {
                return declared == XmlConfig.XSI_NAMESPACE
            }
            current = current.parentNode
        }
        return false
    }

    /**
     * 把 QName 文本还原为小写 XSD 本地类型名。
     */
    fun localTypeName(value: String): String {
        val trimmed = value.trim()
        val localName = when {
            trimmed.startsWith("{") && trimmed.indexOf('}') > 0 -> trimmed.substring(trimmed.indexOf('}') + 1)
            trimmed.contains(':') -> trimmed.substring(trimmed.lastIndexOf(':') + 1)
            else -> trimmed
        }
        return localName.lowercase()
    }

    /**
     * 解析 XSD 内建标量类型；未知类型返回 `null`（由调用方回退到结构推断）。
     */
    fun scalarType(typeName: String): XsdScalarType? = when (typeName) {
        "string", "normalizedstring", "token", "language", "name", "ncname", "nmtoken",
        "nmtokens", "id", "idref", "idrefs", "entity", "entities", "anyuri", "qname",
        "notation", "duration", "datetime", "date", "time", "gyear", "gyearmonth",
        "gmonth", "gmonthday", "gday",
        -> XsdScalarType.STRING

        "boolean" -> XsdScalarType.BOOLEAN
        "decimal", "double", "float" -> XsdScalarType.NUMBER
        "integer", "long", "int", "short", "byte", "nonpositiveinteger", "negativeinteger",
        "nonnegativeinteger", "positiveinteger", "unsignedlong", "unsignedint",
        "unsignedshort", "unsignedbyte",
        -> XsdScalarType.INTEGER

        "base64binary" -> XsdScalarType.BINARY
        "hexbinary" -> XsdScalarType.HEX_BINARY
        "anytype", "any", "anysimpletype" -> XsdScalarType.ANY
        else -> null
    }

    /**
     * 解析 XSD 浮点文本，保留 `INF` / `-INF` / `NaN` 的规范 raw。
     *
     * @throws XmlParseException 文本既不是合法数字也不是 XSD 浮点特殊值时抛出
     */
    fun numberRaw(text: String): String {
        val raw = text.trim()
        return when {
            isNumber(raw) -> raw
            raw == "INF" || raw == "+INF" -> "INF"
            raw == "-INF" -> "-INF"
            raw == "NaN" -> "NaN"
            else -> throw XmlParseException("Invalid number '$raw'")
        }
    }

    /**
     * 解析 XSD 整数文本。
     *
     * @throws XmlParseException 文本不是合法整数时抛出
     */
    fun integerRaw(text: String): String {
        val raw = text.trim()
        if (!integerPattern.matches(raw)) {
            throw XmlParseException("Invalid integer '$raw'")
        }
        return raw
    }

    /**
     * 解码 `xs:hexBinary` 文本（每两个十六进制字符一个字节）。
     *
     * @throws XmlParseException 长度为奇数或包含非十六进制字符时抛出
     */
    fun hexBinary(text: String): ByteArray {
        val raw = text.trim()
        if (raw.length % 2 != 0) {
            throw XmlParseException("Invalid hexBinary value '$raw'")
        }
        val result = ByteArray(raw.length / 2)
        for (index in result.indices) {
            val high = Character.digit(raw[index * 2], 16)
            val low = Character.digit(raw[index * 2 + 1], 16)
            if (high < 0 || low < 0) {
                throw XmlParseException("Invalid hexBinary value '$raw'")
            }
            result[index] = ((high shl 4) or low).toByte()
        }
        return result
    }

    /**
     * 解码 `xs:base64Binary` 文本。
     */
    fun base64Binary(text: String): ByteArray = Base64.getDecoder().decode(text.trim())

    private fun isNumber(text: String): Boolean = try {
        BigDecimal(text)
        true
    } catch (_: NumberFormatException) {
        false
    }
}

/**
 * `xsi:type` 归一化后的 XSD 标量类型。
 */
internal enum class XsdScalarType {

    /** 字符串/日期/URI 等文本类型。 */
    STRING,

    /** `xs:boolean`。 */
    BOOLEAN,

    /** `xs:decimal` / `xs:double` / `xs:float`。 */
    NUMBER,

    /** 整数族（`xs:int`、`xs:long` 等）。 */
    INTEGER,

    /** `xs:base64Binary`。 */
    BINARY,

    /** `xs:hexBinary`。 */
    HEX_BINARY,

    /** `xs:anyType` / `xs:anySimpleType` / `xs:any`，回退到结构推断。 */
    ANY,
}
