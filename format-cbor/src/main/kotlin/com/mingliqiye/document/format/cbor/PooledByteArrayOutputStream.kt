package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ByteArrayPool
import java.io.OutputStream

/**
 * 使用 [ByteArrayPool] 缓冲的[输出流]。
 *
 * 只在内存中增长缓冲区：容量不足时向池借一个更大的缓冲区、复制旧内容并归还旧
 * 缓冲区；[close] 时把当前缓冲区归还池。[toByteArray] 返回当前内容的副本（不会
 * 破坏缓冲区内容），因此必须在 [close] 之前调用。
 *
 * 该实现供 CBOR 内存写出路径复用中间缓冲区，减少 `byte[]` 分配。返回给调用方的
 * 最终 `ByteArray` 仍是一次复制：它的所有权归调用方，不能随池被复用。
 *
 * @param pool 缓冲区池
 * @param initialCapacity 初始缓冲区容量（实际长度以池的借出结果为准，可能更大）
 */
internal class PooledByteArrayOutputStream(
    private val pool: ByteArrayPool,
    initialCapacity: Int = DEFAULT_INITIAL_CAPACITY,
) : OutputStream() {

    private val lock: Any = Any()
    private var buffer: ByteArray = pool.borrow(initialCapacity)
    private var count: Int = 0
    private var closed: Boolean = false

    override fun write(b: Int) {
        synchronized(lock) {
            check(!closed) { "PooledByteArrayOutputStream is closed" }
            ensureCapacity(1)
            buffer[count++] = b.toByte()
        }
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset + length > bytes.size) {
            throw IndexOutOfBoundsException(
                "offset=$offset, length=$length, array size=${bytes.size}",
            )
        }
        synchronized(lock) {
            check(!closed) { "PooledByteArrayOutputStream is closed" }
            if (length == 0) {
                return
            }
            ensureCapacity(length)
            System.arraycopy(bytes, offset, buffer, count, length)
            count += length
        }
    }

    override fun flush() {
        // 纯内存缓冲，无需刷新。
    }

    /**
     * 返回已写入内容的副本。
     *
     * @throws IllegalStateException 缓冲区已归还池（即已 [close]）时抛出
     */
    fun toByteArray(): ByteArray = synchronized(lock) {
        check(!closed) { "PooledByteArrayOutputStream is closed" }
        buffer.copyOf(count)
    }

    /**
     * 把缓冲区归还池；重复调用是幂等的。
     */
    override fun close() {
        val released = synchronized(lock) {
            if (closed) {
                return
            }
            closed = true
            val current = buffer
            buffer = EMPTY_BYTES
            count = 0
            current
        }
        pool.release(released)
    }

    private fun ensureCapacity(extra: Int) {
        val required = count.toLong() + extra.toLong()
        if (required <= buffer.size) {
            return
        }
        if (required > MAX_ARRAY_SIZE) {
            throw OutOfMemoryError("Required array size too large: $required")
        }
        var newCapacity = buffer.size * 2
        if (newCapacity < required) {
            newCapacity = required.toInt()
        }
        if (newCapacity < MIN_GROWTH) {
            newCapacity = MIN_GROWTH
        }
        val grown = pool.borrow(newCapacity)
        System.arraycopy(buffer, 0, grown, 0, count)
        val replaced = buffer
        buffer = grown
        pool.release(replaced)
    }

    private companion object {

        /** 默认初始容量。 */
        private const val DEFAULT_INITIAL_CAPACITY: Int = 256

        /** 最小增长步长。 */
        private const val MIN_GROWTH: Int = 64

        /** 与 JDK 容器实现一致的数组安全上限。 */
        private const val MAX_ARRAY_SIZE: Int = Int.MAX_VALUE - 8

        private val EMPTY_BYTES: ByteArray = ByteArray(0)
    }
}
