package com.mingliqiye.document.serialization

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [JavaRecordSupportLoader] 安全降级测试。
 *
 * `serialization` 模块自身不依赖 `record-support`，因此默认测试类路径上没有 SPI 实现；
 * 无论运行在 JDK 8 还是 JDK 17+，加载器都必须安全返回「不可用」而不抛异常。
 */
class JavaRecordSupportLoaderTest {

    @Test
    fun loaderIsUnavailableWithoutProvider() {
        // serialization 测试类路径没有 record-support；即使跑在 JDK 17+ 也应降级。
        assertFalse(JavaRecordSupportLoader.isAvailable, "record-support must not be on serialization test classpath")
        assertFalse(JavaRecordSupportLoader.isRecord(JavaRecordSupportLoaderTest::class.java))
        assertTrue(JavaRecordSupportLoader.components(JavaRecordSupportLoaderTest::class.java).isEmpty())
    }

    @Test
    fun fallbackProbeDoesNotThrowForRegularTypes() {
        assertFalse(JavaRecordSupportLoader.isRecord(String::class.java))
        assertTrue(JavaRecordSupportLoader.components(String::class.java).isEmpty())
        assertFalse(JavaRecordSupportLoader.isRecord(User::class.java))
        assertTrue(JavaRecordSupportLoader.components(User::class.java).isEmpty())
    }
}
