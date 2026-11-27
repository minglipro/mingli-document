package com.mingliqiye.document.serialization

/**
 * 文档 Mapper 模块。
 *
 * 模块通过 [setupModule] 向 Mapper 注册序列化器、反序列化器或对象实例化策略。
 * 类似 Jackson 的 `Module`。
 */
public interface DocumentModule {

    /** 模块唯一名称，用于去重。 */
    public val moduleName: String

    /**
     * 配置 Mapper。
     *
     * @param context 模块配置上下文
     */
    public fun setupModule(context: ModuleSetupContext)
}
