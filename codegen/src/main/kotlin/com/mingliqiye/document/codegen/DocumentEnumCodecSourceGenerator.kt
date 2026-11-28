package com.mingliqiye.document.codegen

/**
 * 枚举转换器与文档模块的源码生成器。
 *
 * 生成逻辑与 KSP 解耦，处理器只负责收集参数，便于单元测试生成的源码文本。
 */
public interface DocumentEnumCodecSourceGenerator {

    /**
     * 生成单个枚举的转换器源码。
     *
     * @param spec 枚举代码生成参数
     * @return 可直接编译的 Kotlin 源码
     */
    public fun converterSource(spec: DocumentEnumCodecSpec): String

    /**
     * 生成注册全部转换器的文档模块源码。
     *
     * @param spec 模块代码生成参数
     * @return 可直接编译的 Kotlin 源码
     */
    public fun moduleSource(spec: DocumentModuleSpec): String
}
