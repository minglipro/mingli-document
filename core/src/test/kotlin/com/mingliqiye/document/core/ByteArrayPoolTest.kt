package com.mingliqiye.document.core

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [ByteArrayPool] 行为测试：复用、容量上限、重复释放、关闭、并发。
 */
class ByteArrayPoolTest {

    @Test
    fun borrowReturnsBufferAtLeastRequestedSize() {
        val pool = ByteArrayPool()
        try {
            val buffer = pool.borrow(1000)
            assertTrue(buffer.size >= 1000)
            assertEquals(1, pool.borrowedBufferCount)
            assertEquals(1L, pool.totalBorrowed)
            assertEquals(1L, pool.totalAllocated)
            assertEquals(0L, pool.totalReused)

            pool.release(buffer)
            assertEquals(0, pool.borrowedBufferCount)
            assertEquals(1, pool.idleBufferCount)
        } finally {
            pool.close()
        }
    }

    @Test
    fun releasedBufferIsReusedForSmallerRequests() {
        val pool = ByteArrayPool()
        try {
            val buffer = pool.borrow(1000)
            pool.release(buffer)

            val reused = pool.borrow(100)
            assertSame(buffer, reused, "expected the released buffer instance to be reused")
            assertEquals(1L, pool.totalAllocated)
            assertEquals(1L, pool.totalReused)
        } finally {
            pool.close()
        }
    }

    @Test
    fun borrowPrefersSmallestFittingBuffer() {
        val pool = ByteArrayPool()
        try {
            val large = pool.borrow(64)
            val medium = pool.borrow(32)
            val small = pool.borrow(16)
            pool.release(large)
            pool.release(medium)
            pool.release(small)

            assertSame(small, pool.borrow(10))
            assertSame(medium, pool.borrow(20))
            assertSame(large, pool.borrow(64))
        } finally {
            pool.close()
        }
    }

    @Test
    fun idleBufferCapacityIsBounded() {
        val pool = ByteArrayPool(maxIdleBuffers = 2)
        try {
            val first = pool.borrow(8)
            val second = pool.borrow(8)
            val third = pool.borrow(8)
            pool.release(first)
            pool.release(second)
            pool.release(third)

            assertEquals(2, pool.idleBufferCount)
            assertEquals(3L, pool.totalReleased)
            assertEquals(1L, pool.totalDiscarded)
        } finally {
            pool.close()
        }
    }

    @Test
    fun oversizedBufferIsDiscarded() {
        val pool = ByteArrayPool(maxIdleBuffers = 4, maxBufferSize = 32)
        try {
            val buffer = pool.borrow(33)
            pool.release(buffer)

            assertEquals(0, pool.idleBufferCount)
            assertEquals(1L, pool.totalDiscarded)
            assertFailsWith<IllegalStateException> { pool.release(buffer) }
        } finally {
            pool.close()
        }
    }

    @Test
    fun emptyBufferIsDiscarded() {
        val pool = ByteArrayPool()
        try {
            val buffer = pool.borrow(0)
            assertEquals(0, buffer.size)
            pool.release(buffer)

            assertEquals(0, pool.idleBufferCount)
            assertEquals(1L, pool.totalDiscarded)
        } finally {
            pool.close()
        }
    }

    @Test
    fun duplicateReleaseIsRejected() {
        val pool = ByteArrayPool()
        try {
            val buffer = pool.borrow(16)
            pool.release(buffer)

            assertFailsWith<IllegalStateException> { pool.release(buffer) }
        } finally {
            pool.close()
        }
    }

    @Test
    fun foreignBufferReleaseIsRejected() {
        val pool = ByteArrayPool()
        try {
            assertFailsWith<IllegalStateException> { pool.release(ByteArray(4)) }
        } finally {
            pool.close()
        }
    }

    @Test
    fun cacheDisabledByZeroIdleCapacity() {
        val pool = ByteArrayPool(maxIdleBuffers = 0)
        try {
            val first = pool.borrow(16)
            pool.release(first)
            assertEquals(0, pool.idleBufferCount)
            assertEquals(1, pool.totalDiscarded)

            val second = pool.borrow(16)
            assertNotSame(first, second)
            assertEquals(2L, pool.totalAllocated)
        } finally {
            pool.close()
        }
    }

    @Test
    fun clearDropsIdleBuffersButKeepsPoolUsable() {
        val pool = ByteArrayPool()
        try {
            val buffer = pool.borrow(16)
            pool.release(buffer)
            assertEquals(1, pool.idleBufferCount)

            pool.clear()
            assertEquals(0, pool.idleBufferCount)

            val next = pool.borrow(16)
            assertNotSame(buffer, next)
            assertEquals(2L, pool.totalAllocated)
            pool.release(next)
        } finally {
            pool.close()
        }
    }

    @Test
    fun closeDropsBuffersAndRejectsOperations() {
        val pool = ByteArrayPool()
        val buffer = pool.borrow(8)
        pool.close()

        assertTrue(pool.isClosed)
        assertEquals(0, pool.idleBufferCount)
        assertEquals(0, pool.borrowedBufferCount)
        assertFailsWith<IllegalStateException> { pool.borrow(8) }
        assertFailsWith<IllegalStateException> { pool.release(buffer) }

        pool.close()
        assertTrue(pool.isClosed)
    }

    @Test
    fun invalidArgumentsAreRejected() {
        assertFailsWith<IllegalArgumentException> { ByteArrayPool(maxIdleBuffers = -1) }
        assertFailsWith<IllegalArgumentException> { ByteArrayPool(maxBufferSize = 0) }

        val pool = ByteArrayPool()
        try {
            assertFailsWith<IllegalArgumentException> { pool.borrow(-1) }
        } finally {
            pool.close()
        }
    }

    @Test
    fun withBorrowedBufferReleasesOnSuccessAndFailure() {
        val pool = ByteArrayPool()
        try {
            val size = pool.withBorrowedBuffer(16) { buffer -> buffer.size }
            assertTrue(size >= 16)
            assertEquals(0, pool.borrowedBufferCount)

            assertFailsWith<IllegalStateException> {
                pool.withBorrowedBuffer(16) { throw IllegalStateException("boom") }
            }
            assertEquals(0, pool.borrowedBufferCount)
            assertEquals(2L, pool.totalReleased)
        } finally {
            pool.close()
        }
    }

    @Test
    fun concurrentBorrowReleaseIsThreadSafe() {
        val pool = ByteArrayPool(maxIdleBuffers = 8)
        val threadCount = 8
        val iterations = 400
        val start = CountDownLatch(1)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            repeat(threadCount) { threadIndex ->
                executor.execute {
                    try {
                        start.await()
                        for (round in 0 until iterations) {
                            val size = 1 + ((threadIndex * 37 + round * 13) % 512)
                            val buffer = pool.borrow(size)
                            check(buffer.size >= size) { "buffer size ${buffer.size} < $size" }
                            buffer[0] = (round and 0xFF).toByte()
                            buffer[size - 1] = threadIndex.toByte()
                            pool.release(buffer)
                        }
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "concurrent workers timed out")

            assertTrue(errors.isEmpty(), "concurrent errors: $errors")
            assertEquals(threadCount.toLong() * iterations, pool.totalBorrowed)
            assertEquals(pool.totalBorrowed, pool.totalReleased)
            assertEquals(0, pool.borrowedBufferCount)
            assertTrue(pool.idleBufferCount <= 8)
            assertTrue(pool.totalReused > 0, "expected at least one buffer reuse")
        } finally {
            executor.shutdownNow()
            pool.close()
        }
    }
}
