package com.mingliqiye.document.format.xml

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * [XmlNameProcessor] 预置实现，对齐 上游实现 XML 的 `XmlNameProcessors`。
 *
 * - [passthrough]：默认，原样透传；
 * - [replacement]：把非法 XML 名称字符替换为指定字符串（默认 `_`，不可逆）；
 * - [base64]：仅对非法 XML 名称做可逆 Base64 编码（前缀 `base64_`）；
 * - [alwaysOnBase64]：所有名称都 Base64 编码（前缀 `base64_`，可逆）。
 */
public object XmlNameProcessors {

    private const val BASE64_PREFIX = "base64_"

    private val passthroughProcessor: XmlNameProcessor = XmlNameProcessor { name -> name }

    private val base64Encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val base64Decoder: Base64.Decoder = Base64.getUrlDecoder()

    /**
     * 原样透传，默认处理器。
     *
     * @return 不做任何转换的处理器
     */
    @JvmStatic
    public fun passthrough(): XmlNameProcessor = passthroughProcessor

    /**
     * 把非法 XML 名称字符替换为指定字符串，不可逆（[XmlNameProcessor.decode] 原样返回）。
     *
     * @param replacement 替换字符串，不能为空
     * @return 替换处理器
     * @throws IllegalArgumentException 替换字符串为空时抛出
     */
    @JvmStatic
    @JvmOverloads
    public fun replacement(replacement: String = "_"): XmlNameProcessor {
        require(replacement.isNotEmpty()) { "replacement must not be empty" }
        return object : XmlNameProcessor {
            override fun encode(name: String): String {
                if (name.isEmpty() || isValidXmlName(name)) {
                    return name
                }
                val builder = StringBuilder(name.length + replacement.length)
                for ((index, ch) in name.withIndex()) {
                    if (index == 0) {
                        builder.append(if (isValidNameStartChar(ch)) ch else replacement)
                    } else {
                        builder.append(if (isValidNameChar(ch)) ch else replacement)
                    }
                }
                return builder.toString()
            }
        }
    }

    /**
     * 仅对非法 XML 名称做 Base64 编码（前缀 `base64_`），可逆。
     *
     * @return Base64 处理器
     */
    @JvmStatic
    public fun base64(): XmlNameProcessor = object : XmlNameProcessor {
        override fun encode(name: String): String = if (name.isEmpty() || isValidXmlName(name)) name else BASE64_PREFIX + encodeBase64(name)

        override fun decode(encodedName: String): String = decodeBase64(encodedName)
    }

    /**
     * 对所有名称都做 Base64 编码（前缀 `base64_`），可逆。
     *
     * @return 总是编码的 Base64 处理器
     */
    @JvmStatic
    public fun alwaysOnBase64(): XmlNameProcessor = object : XmlNameProcessor {
        override fun encode(name: String): String = BASE64_PREFIX + encodeBase64(name)

        override fun decode(encodedName: String): String = decodeBase64(encodedName)
    }

    private fun encodeBase64(name: String): String = base64Encoder.encodeToString(name.toByteArray(StandardCharsets.UTF_8))

    private fun decodeBase64(encodedName: String): String {
        if (!encodedName.startsWith(BASE64_PREFIX)) {
            return encodedName
        }
        val payload = encodedName.substring(BASE64_PREFIX.length)
        return try {
            String(base64Decoder.decode(payload), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            encodedName
        }
    }

    private fun isValidXmlName(name: String): Boolean {
        if (name.isEmpty() || !isValidNameStartChar(name[0])) {
            return false
        }
        for (index in 1 until name.length) {
            if (!isValidNameChar(name[index])) {
                return false
            }
        }
        return true
    }

    private fun isValidNameStartChar(ch: Char): Boolean = ch == '_' || ch.isLetter()

    private fun isValidNameChar(ch: Char): Boolean = ch == '_' || ch == '-' || ch == '.' || ch.isLetterOrDigit()
}
