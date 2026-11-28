package com.mingliqiye.document.core

import java.io.Closeable
import java.util.IdentityHashMap

/**
 * 可复用的 [ByteArray] 缓冲区池。
 *
 * 用于在高频编解码路径上复用字节缓冲区，减少 `byte[]` 分配与 GC 压力。池本身只
 * 负责缓冲区的借出/归还，不解释内容，也不清空缓冲区：
 *
 * ```kotlin
 * val pool = ByteArrayPool()
 * try {
 *     val buffer = pool.borrow(4096) // 长度 >= 4096
 *     // 只使用前 4096 个字节，写完后归还
 *     pool.release(buffer)
 * } finally {
 *     pool.close()
 * }
 * ```
 *
 * 语义约定：
 * - [borrow] 返回的数组长度 **>= 请求长度**（可能来自更大的空闲缓冲区），调用方只应
 *   使用前 `size` 个字节，并按自己的需求初始化内容；返回的数组内容无任何保证。
 * - 归还的缓冲区按“最小可用长度”复用；长度超过 [maxBufferSize] 或空闲数已达
 *   [maxIdleBuffers] 上限时，缓冲区在 [release] 时被直接丢弃（不计入空闲）。
 * - 线程安全：内部所有状态由同一把锁保护，可被多个线程同时借出/归还。
 * - 缓冲区所有权：只有由本池 [borrow] 借出的数组才允许 [release]（按引用身份判断）；
 *   重复归还未借用/已归还的数组会抛 [IllegalStateException]。
 * - 借出的缓冲区必须且只能归还一次；归还后不得再读写该数组（可能已被其他线程借走）。
 * - [close] 之后不能再 [borrow]/[release]；[clear] 只丢弃空闲缓冲区，池仍可继续使用。
 *
 * 池不感知借用者是否发生泄漏：未被归还的缓冲区会一直计入 [borrowedBufferCount]，
 * 直到被 [release]。
 *
 * @param maxIdleBuffers 最多保留的空闲缓冲区数量；`0` 表示不缓存（每次借出都新分配）。
 * @param maxBufferSize 允许回池的单个缓冲区最大长度，超过该长度的缓冲区在 [release]
 *   时丢弃；必须大于 `0`。
 */
public class ByteArrayPool @JvmOverloads constructor(
    /** 最多保留的空闲缓冲区数量；`0` 表示不缓存。 */
    public val maxIdleBuffers: Int = DEFAULT_MAX_IDLE_BUFFERS,
    /** 允许回池的单个缓冲区最大长度。 */
    public val maxBufferSize: Int = DEFAULT_MAX_BUFFER_SIZE,
) : Closeable {

    private val lock: Any = Any()
    private val idleBuffers: MutableList<ByteArray> = ArrayList()
    private val borrowedBuffers: MutableMap<ByteArray, Boolean> = IdentityHashMap()

    private var closed: Boolean = false
    private var borrowedTotal: Long = 0
    private var releasedTotal: Long = 0
    private var allocatedTotal: Long = 0
    private var reusedTotal: Long = 0
    private var discardedTotal: Long = 0

    init {
        require(maxIdleBuffers >= 0) {
            "maxIdleBuffers must be >= 0 but was $maxIdleBuffers"
        }
        require(maxBufferSize > 0) {
            "maxBufferSize must be > 0 but was $maxBufferSize"
        }
    }

    /** 池是否已关闭。 */
    public val isClosed: Boolean
        get() = synchronized(lock) { closed }

    /** 当前空闲缓冲区数量。 */
    public val idleBufferCount: Int
        get() = synchronized(lock) { idleBuffers.size }

    /** 当前已借出但尚未归还的缓冲区数量。 */
    public val borrowedBufferCount: Int
        get() = synchronized(lock) { borrowedBuffers.size }

    /** 累计借出次数（含复用与新分配）。 */
    public val totalBorrowed: Long
        get() = synchronized(lock) { borrowedTotal }

    /** 累计成功归还次数（包括归还后被丢弃的缓冲区）。 */
    public val totalReleased: Long
        get() = synchronized(lock) { releasedTotal }

    /** 累计新分配缓冲区次数。 */
    public val totalAllocated: Long
        get() = synchronized(lock) { allocatedTotal }

    /** 累计命中空闲缓冲区（复用）次数。 */
    public val totalReused: Long
        get() = synchronized(lock) { reusedTotal }

    /** 累计归还时被丢弃的缓冲区次数（超大或空闲已满）。 */
    public val totalDiscarded: Long
        get() = synchronized(lock) { discardedTotal }

    /**
     * 借出一个长度不小于 [size] 的缓冲区。
     *
     * 优先复用最小的可用空闲缓冲区，否则新分配 `ByteArray(size)`。返回的数组内容
     * 未被初始化，长度可能大于 [size]；调用方只能使用前 [size] 个字节，并在使用完
     * 后通过 [release] 归还该数组本身。
     *
     * @param size 需要的字节数，必须 `>= 0`
     * @return 长度 `>= size` 的缓冲区
     * @throws IllegalArgumentException [size] 为负数时抛出
     * @throws IllegalStateException 池已关闭时抛出
     */
    public fun borrow(size: Int): ByteArray {
        require(size >= 0) { "size must be >= 0 but was $size" }
        synchronized(lock) {
            check(!closed) { "ByteArrayPool is closed" }
            borrowedTotal++
            val index = bestFitIndex(size)
            if (index >= 0) {
                val buffer = idleBuffers.removeAt(index)
                borrowedBuffers[buffer] = true
                reusedTotal++
                return buffer
            }
            val buffer = ByteArray(size)
            borrowedBuffers[buffer] = true
            allocatedTotal++
            return buffer
        }
    }

    /**
     * 归还由本池 [borrow] 借出的缓冲区。
     *
     * 缓冲区是否进入空闲列表取决于 [maxBufferSize] 与 [maxIdleBuffers]；被丢弃的
     * 缓冲区同样视为已归还。归还后调用方不得再持有或修改该数组。
     *
     * @param buffer 要归还的缓冲区
     * @throws IllegalStateException 池已关闭，或 [buffer] 不是本池借出的/已被归还时抛出
     */
    public fun release(buffer: ByteArray) {
        synchronized(lock) {
            check(!closed) { "ByteArrayPool is closed" }
            check(borrowedBuffers.remove(buffer) != null) {
                "Buffer was not borrowed from this pool or has already been released"
            }
            releasedTotal++
            if (buffer.size == 0 || buffer.size > maxBufferSize || idleBuffers.size >= maxIdleBuffers) {
                discardedTotal++
                return
            }
            idleBuffers.add(buffer)
        }
    }

    /**
     * 丢弃全部空闲缓冲区；已借出的缓冲区不受影响，池仍可继续使用。
     */
    public fun clear() {
        synchronized(lock) {
            idleBuffers.clear()
        }
    }

    /**
     * 关闭池：丢弃空闲缓冲区，并拒绝后续 [borrow]/[release] 调用。
     *
     * 重复调用是幂等的；已借出的缓冲区不会自动回收。
     */
    override fun close() {
        synchronized(lock) {
            if (closed) {
                return
            }
            closed = true
            idleBuffers.clear()
            borrowedBuffers.clear()
        }
    }

    override fun toString(): String = synchronized(lock) {
        "ByteArrayPool(idle=${idleBuffers.size}, borrowed=${borrowedBuffers.size}, closed=$closed)"
    }

    private fun bestFitIndex(size: Int): Int {
        var bestIndex = -1
        var bestSize = Int.MAX_VALUE
        for (index in idleBuffers.indices) {
            val candidateSize = idleBuffers[index].size
            if (candidateSize >= size && candidateSize < bestSize) {
                bestIndex = index
                bestSize = candidateSize
            }
        }
        return bestIndex
    }

    public companion object {

        /** 默认最多保留的空闲缓冲区数量：`16`。 */
        public const val DEFAULT_MAX_IDLE_BUFFERS: Int = 16

        /** 默认允许回池的单个缓冲区最大长度：1 MiB。 */
        public const val DEFAULT_MAX_BUFFER_SIZE: Int = 1 shl 20
    }
}

/**
 * 借用 [size] 字节的缓冲区执行 [block]，退出前自动归还（异常时也会归还）。
 *
 * @param size 需要的字节数
 * @param block 使用缓冲区前 [size] 个字节的逻辑
 * @return [block] 的返回值
 */
public inline fun <R> ByteArrayPool.withBorrowedBuffer(size: Int, block: (ByteArray) -> R): R {
    val buffer = borrow(size)
    try {
        return block(buffer)
    } finally {
        release(buffer)
    }
}
