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
 * Mapper 是线程安全的读对象，可以在多线程间共享。
 *
 * @param unknownFieldPolicy 未知字段策略
 * @param maxDepth 解析最大嵌套深度，`0` 表示不限制
 * @param extensions extension 注册表
 */
public class ProtobufMapperFactory @JvmOverloads constructor(
    private val unknownFieldPolicy: ProtobufUnknownFieldPolicy = ProtobufUnknownFieldPolicy.FAIL,
    private val maxDepth: Int = 0,
    private val extensions: ProtobufExtensionRegistry = ProtobufExtensionRegistry.EMPTY,
) {

    private val mappers = ConcurrentHashMap<Descriptor, ProtobufMapper>()

    /**
     * 获取指定 message Descriptor 对应的共享 Mapper。
     *
     * @param descriptor message Descriptor
     * @return 该 Descriptor 的共享 Mapper
     */
    public fun mapper(descriptor: Descriptor): ProtobufMapper = mappers.computeIfAbsent(descriptor) {
        ProtobufMapper(descriptor, unknownFieldPolicy, maxDepth, extensions)
    }
}
