package com.mingliqiye.document.codegen.test

import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.ModuleSetupContext
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * 记录模块 setup 注册内容的测试用 [ModuleSetupContext]。
 *
 * 与默认实现一致，`addConverter` 会同时记录到序列化器与反序列化器集合。
 */
internal class RecordingModuleSetupContext : ModuleSetupContext {

    val serializers: MutableList<DocumentBaseSerializer<*>> = mutableListOf()
    val deserializers: MutableList<DocumentBaseDeserializer<*>> = mutableListOf()
    val converters: MutableList<DocumentBaseConverter<*>> = mutableListOf()

    override fun addSerializer(serializer: DocumentBaseSerializer<*>) {
        serializers += serializer
    }

    override fun addDeserializer(deserializer: DocumentBaseDeserializer<*>) {
        deserializers += deserializer
    }

    override fun addConverter(converter: DocumentBaseConverter<*>) {
        converters += converter
        serializers += converter
        deserializers += converter
    }

    override fun setObjectInstantiator(instantiator: ObjectInstantiator?) {
        // 测试用上下文不处理实例化策略
    }

    override fun setPropertyMetadataResolver(resolver: PropertyMetadataResolver?) {
        // 测试用上下文不处理元数据解析器
    }
}
