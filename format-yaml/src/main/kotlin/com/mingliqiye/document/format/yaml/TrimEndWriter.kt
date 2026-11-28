package com.mingliqiye.document.format.yaml

import java.io.Writer

/**
 * 延迟写出尾部空白的 Writer。
 *
 * SnakeYAML 的 dump 会追加换行；该包装器只保留连续尾部空白，遇到非空白字符时
 * 再写出，结束时丢弃尾部空白，从而与 `String.trimEnd()` 的结果一致。
 */
internal class TrimEndWriter(
    private val target: Writer,
) : Writer() {

    private val pending = StringBuilder()

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        var index = off
        val end = off + len
        while (index < end) {
            write(cbuf[index].code)
            index++
        }
    }

    override fun write(c: Int) {
        val char = c.toChar()
        if (char.isWhitespace()) {
            pending.append(char)
            return
        }
        flushPending()
        target.write(c)
    }

    override fun flush() {
        target.flush()
    }

    override fun close() {
        finish()
    }

    fun finish() {
        pending.setLength(0)
        target.flush()
    }

    private fun flushPending() {
        if (pending.isEmpty()) {
            return
        }
        target.write(pending.toString())
        pending.setLength(0)
    }
}
