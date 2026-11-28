package com.mingliqiye.document.serialization

import java.util.ServiceConfigurationError
import java.util.ServiceLoader

/**
 * [JavaRecordSupport] 实现加载器。
 *
 * 按以下规则加载 SPI 实现：
 * - JDK 8-15 没有 record 反射 API，直接视为不可用（不尝试加载，避免
 *   `record-support` 被误放进 JDK 8 classpath 时产生类版本错误）；
 * - JDK 16+ 通过 [ServiceLoader] 查找 `META-INF/services` 注册的实现；
 * - 找不到实现、provider 类版本不兼容或服务配置损坏时安全降级为不可用
 *   （捕获 [ServiceConfigurationError] 与 [LinkageError]，不向调用方抛异常）。
 *
 * 不可用时 [isRecord] 恒为 `false`、[components] 恒为空列表，Mapper 按普通 Bean 处理。
 * 加载结果只在首次使用时执行一次（provider 会被缓存）。
 */
internal object JavaRecordSupportLoader {

    /**
     * 是否加载到了可用的 record 支持实现。
     *
     * JDK 8-15、或 classpath / module path 上没有 `record-support` 时返回 `false`。
     */
    internal val isAvailable: Boolean
        get() = support != null

    /**
     * SPI 实现；不可用时为 `null`。
     *
     * 使用 `by lazy`：静态初始化阶段不触碰 [ServiceLoader]，兼容 GraalVM Native Image
     * 的构建期类初始化分析。
     */
    internal val support: JavaRecordSupport? by lazy(::loadSupport)

    /**
     * 判断类型是否为 Java record；不可用时恒为 `false`。
     */
    internal fun isRecord(type: Class<*>): Boolean = support?.isRecord(type) ?: false

    /**
     * 读取 record 组件元数据；不可用或不是 record 时返回空列表。
     */
    internal fun components(type: Class<*>): List<JavaRecordComponentInfo> = support?.components(type).orEmpty()

    private fun loadSupport(): JavaRecordSupport? {
        if (!recordApiAvailable()) {
            return null
        }
        return try {
            val classLoader = Thread.currentThread().contextClassLoader ?: JavaRecordSupport::class.java.classLoader
            ServiceLoader.load(JavaRecordSupport::class.java, classLoader).firstOrNull()
        } catch (_: ServiceConfigurationError) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    /**
     * 当前运行时是否提供 Java 16+ 的 record 反射 API。
     *
     * 通过 `java.specification.version` 判断（JDK 8 为 `1.8`，JDK 9+ 为 `17` 等），
     * 不依赖反射调用，避免 JDK 8 上的 `NoSuchMethodError` / 额外反射配置。
     */
    private fun recordApiAvailable(): Boolean {
        val specification = System.getProperty("java.specification.version") ?: return false
        val major = if (specification.startsWith("1.")) {
            specification.removePrefix("1.").substringBefore('.').toIntOrNull() ?: 0
        } else {
            specification.substringBefore('.').toIntOrNull() ?: 0
        }
        return major >= 16
    }
}
