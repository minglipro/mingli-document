package com.mingliqiye.document.format.protobuf

import com.google.protobuf.Descriptors.Descriptor
import java.util.concurrent.ConcurrentHashMap

/**
 * Protobuf Mapper 工厂。
 *
 * 同一个 `Descriptor` 共享一个 [ProtobufMapper] 实例，避免为每个 Java 类或每次调用
 * 重复创建 Mapper。不同 message 类型使用不同 Descriptor，因此各自对应一个 Mapper。
 *
 * 典型用法：
 *
 * ```kotlin
 * val factory = ProtobufMapperFactory(
 *     unknownFieldPolicy = ProtobufUnknownFieldPolicy.IGNORE,
 * )
 * val userMapper = factory.mapper(userDescriptor)
 * val orderMapper = factory.mapper(orderDescriptor)
 * ```
 *
 * 也可以直接传入完整 [ProtobufConfig]（上游实现 对齐特性、通用配置等）：
 *
 * ```kotlin
 * val factory = ProtobufMapperFactory(
 *     ProtobufConfig.builder()
 *         .enable(ProtobufReadFeature.IGNORE_UNDEFINED)
 *         .enable(ProtobufWriteFeature.IGNORE_UNKNOWN)
 *         .build(),
 * )
 * ```
 *
 * Mapper 是线程安全的读对象，可以在多线程间共享。
 *
 * @param unknownFieldPolicy 未知字段策略
 * @param maxDepth 解析最大嵌套深度，`0` 表示不限制
 * @param extensions extension 注册表
 * @param protobufConfig 完整 Protobuf 配置；默认由 [unknownFieldPolicy]、[maxDepth]、
 *   [extensions] 组合而成
 */
public class ProtobufMapperFactory @JvmOverloads constructor(
    unknownFieldPolicy: ProtobufUnknownFieldPolicy = ProtobufUnknownFieldPolicy.FAIL,
    maxDepth: Int = 0,
    extensions: ProtobufExtensionRegistry = ProtobufExtensionRegistry.EMPTY,
    public val protobufConfig: ProtobufConfig = ProtobufConfig.of(unknownFieldPolicy, maxDepth, extensions),
) {

    private val mappers = ConcurrentHashMap<Descriptor, ProtobufMapper>()

    /**
     * 使用完整 [ProtobufConfig] 创建工厂。
     *
     * @param config Protobuf 配置
     */
    public constructor(config: ProtobufConfig) : this(
        config.unknownFieldPolicy,
        config.maxDepth,
        config.extensions,
        config,
    )

    /**
     * 获取指定 message Descriptor 对应的共享 Mapper。
     *
     * @param descriptor message Descriptor
     * @return 该 Descriptor 的共享 Mapper
     */
    public fun mapper(descriptor: Descriptor): ProtobufMapper = mappers.computeIfAbsent(descriptor) {
        ProtobufMapper(descriptor, protobufConfig)
    }
}
