package com.mingliqiye.document.format.json

import com.mingliqiye.document.format.json.exception.JsonParseException
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.FilterReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 流式读取使用的默认字符缓冲区大小。 */
internal const val JSON_STREAM_BUFFER_SIZE: Int = 8 * 1024

/**
 * 统计底层输入流实际读取的字节数。
 *
 * 供流式 API 的 `maxInputSize` 累计校验使用：流式读取无法预先知道总长度，
 * 因此按“已从输入流读出的字节数”做上限判断（可能包含缓冲区预读）。
 *
 * @param delegate 被包装的输入流
 */
internal class CountingInputStream(delegate: InputStream) : FilterInputStream(delegate) {

    /** 已读取的字节数。 */
    var count: Long = 0L
        private set

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) {
            count++
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) {
            count += read
        }
        return read
    }

    override fun skip(byteCount: Long): Long {
        val skipped = super.skip(byteCount)
        if (skipped > 0) {
            count += skipped
        }
        return skipped
    }
}

/**
 * 统计底层字符流实际读取的字符数。
 *
 * 供流式 API 的 `maxInputSize` 累计校验使用；`Reader` 输入按字符数而非字节数统计。
 *
 * @param delegate 被包装的字符流
 */
internal class CountingReader(delegate: Reader) : FilterReader(delegate) {

    /** 已读取的字符数。 */
    var count: Long = 0L
        private set

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) {
            count++
        }
        return value
    }

    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) {
            count += read
        }
        return read
    }

    override fun skip(charCount: Long): Long {
        val skipped = super.skip(charCount)
        if (skipped > 0) {
            count += skipped
        }
        return skipped
    }
}

/**
 * 把字符解码错误统一转换为 [JsonParseException]。
 *
 * [InputStreamReader] 的 [java.nio.charset.CharsetDecoder] 以 `REPORT` 模式工作，
 * 遇到非法字节序列抛出 [CharacterCodingException]；本包装器把它转换成 JSON
 * 模块统一的解析异常，避免把底层 `IOException` 直接抛给调用方。
 *
 * @param delegate 被包装的字符流
 * @param charsetName 字符集名称，用于异常消息
 */
internal class DecodingErrorReader(
    private val delegate: Reader,
    private val charsetName: String,
) : Reader() {

    override fun read(buffer: CharArray, offset: Int, length: Int): Int = try {
        delegate.read(buffer, offset, length)
    } catch (e: CharacterCodingException) {
        throw invalidEncoding(e)
    }

    override fun read(): Int = try {
        delegate.read()
    } catch (e: CharacterCodingException) {
        throw invalidEncoding(e)
    }

    override fun skip(charCount: Long): Long = try {
        delegate.skip(charCount)
    } catch (e: CharacterCodingException) {
        throw invalidEncoding(e)
    }

    override fun ready(): Boolean = delegate.ready()

    override fun close() {
        delegate.close()
    }

    private fun invalidEncoding(cause: CharacterCodingException): JsonParseException = JsonParseException("Invalid $charsetName input", cause)
}

/**
 * 按指定字符集创建严格解码的 [Reader]。
 *
 * @param input 输入流
 * @param charset 字符集
 * @return 严格的字符流
 */
internal fun decodingReader(input: InputStream, charset: Charset): Reader {
    val decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return DecodingErrorReader(InputStreamReader(input, decoder), charset.name())
}

/**
 * 把字符流包装为带缓冲的读取器。
 *
 * 调用方传入的 [BufferedReader] 直接复用；本方法不会关闭底层流。
 *
 * @param reader 字符流
 * @return 带缓冲的字符流
 */
internal fun bufferedReader(reader: Reader): BufferedReader = reader as? BufferedReader
    ?: BufferedReader(reader, JSON_STREAM_BUFFER_SIZE)
