package com.mingliqiye.document.serialization

import com.mingliqiye.document.serialization.exception.DocumentSerializationException

/**
 * 根对象 Bean 校验扩展点。
 *
 * [DocumentMapperConfig.beanValidator] 配置后，Mapper 在根对象序列化入口
 * （[DocumentMapper.toNode]、[DocumentMapper.writeTo]）与反序列化出口
 * （[DocumentMapper.fromNode]、[DocumentMapper.fromTokens]、[DocumentMapper.readFrom]）
 * 各调用一次；嵌套对象、生成式编解码器与值类转换器回调 Mapper 的嵌套调用不会重复校验。
 *
 * 实现方需要处理任意非 `null` 根值（Bean、集合、Map、[com.mingliqiye.document.core.Node]
 * 等）：只关心 Bean 时自行按类型过滤，直接忽略不关心的值即可。
 *
 * 校验失败时抛出 [DocumentSerializationException]；抛出其他异常时由 Mapper 包装为
 * [DocumentSerializationException] 并通过 cause 保留原始异常。
 *
 * 典型实现见 `validation` 模块的 `JakartaDocumentBeanValidator`。
 */
public fun interface DocumentBeanValidator {

    /**
     * 校验根对象。
     *
     * @param value 非 `null` 根值
     * @throws DocumentSerializationException 校验失败时抛出
     */
    public fun validate(value: Any)
}
