package com.mingliqiye.document.format.ion

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * Ion Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 Ion 专属选项。
 */
public class IonConfig internal constructor(
    base: DocumentMapperConfig,
    /**
     * 是否保留 Ion 专属类型。
     *
     * 默认 `false`：timestamp / symbol 映射为 [com.mingliqiye.document.core.StringNode]，
     * blob / clob 映射为 [com.mingliqiye.document.core.BinaryNode]，与历史行为一致。
     *
     * 开启后 Node 路径使用包装对象保留类型：`{"$ionType":"timestamp","value":"..."}`、
     * `{"$ionType":"symbol","value":"..."}`、`{"$ionType":"clob","value":<BinaryNode>}`、
     * `{"$ionType":"blob","value":<BinaryNode>}`；写出时反向还原为 ion-java 的
     * timestamp / symbol / clob / blob。Token 直读/直写路径在开启后会被禁用（统一走
     * Node 树），保证保真语义一致。
     */
    public val preserveTypes: Boolean,
) : DocumentMapperConfig(base) {

    public companion object {

        /**
         * 创建 Ion 配置构建器。
         */
        @JvmStatic
        public fun builder(): IonConfigBuilder = IonConfigBuilder()
    }
}

/**
 * 当前配置是否开启 Ion 类型保真；非 [IonConfig] 时视为关闭。
 */
internal val DocumentMapperConfig.ionPreserveTypes: Boolean
    get() = (this as? IonConfig)?.preserveTypes == true
