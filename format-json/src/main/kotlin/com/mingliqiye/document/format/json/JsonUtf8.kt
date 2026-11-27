package com.mingliqiye.document.format.json

import com.mingliqiye.document.format.json.exception.JsonParseException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * 严格 UTF-8 解码字节数组。
 *
 * 使用 [java.nio.charset.CharsetDecoder] 的 `REPORT` 模式，非法字节序列
 * （截断、孤立续字节、过长编码等）抛 [JsonParseException]；不使用会静默替换为
 * `U+FFFD` 的宽松解码。
 *
 * @param source UTF-8 字节
 * @return 解码后的文本
 * @throws JsonParseException 字节序列不是合法 UTF-8 时抛出
 */
internal fun decodeUtf8(source: ByteArray): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(source))
        .toString()
} catch (e: CharacterCodingException) {
    throw JsonParseException("Invalid UTF-8 input", e)
}
