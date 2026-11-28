package com.mingliqiye.document.format.ion

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * Ion Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 Ion 专属选项：类型保真与 上游实现 Ion 风格的
 * 读/写特性（见 [IonReadFeature]、[IonWriteFeature]）。
 *
 * 读/写特性默认值对齐 Ion 底层库：读取默认开启
 * [IonReadFeature.USE_NATIVE_TYPE_ID] 与 [IonReadFeature.READ_NULL_AS_IONVALUE]
 * （上游实现 2.15 无后者开关，但其行为等价于开启），写出默认开启
 * [IonWriteFeature.USE_NATIVE_TYPE_ID]；`STRICT_DUPLICATE_DETECTION` 默认关闭。
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
     * timestamp / symbol / clob / blob。开启后同时启用 [IonReadFeature.USE_NATIVE_TYPE_ID] /
     * [IonWriteFeature.USE_NATIVE_TYPE_ID] 的注解保真：带注解的值读写为
     * `{"$ionAnnotations":[...],"value":<node>}` 包装对象。Token 直读/直写路径在开启后
     * 会被禁用（统一走 Node 树），保证保真语义一致。
     */
    public val preserveTypes: Boolean,
    /** Ion 读取特性；默认 [DEFAULT_READ_FEATURES]，对齐 上游实现。 */
    public val readFeatures: Set<IonReadFeature> = DEFAULT_READ_FEATURES,
    /** Ion 写出特性；默认 [DEFAULT_WRITE_FEATURES]，对齐 上游实现。 */
    public val writeFeatures: Set<IonWriteFeature> = DEFAULT_WRITE_FEATURES,
) : DocumentMapperConfig(base) {

    /**
     * 指定 Ion 读取特性是否开启。
     *
     * @param feature 读取特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: IonReadFeature): Boolean = feature in readFeatures

    /**
     * 指定 Ion 写出特性是否开启。
     *
     * @param feature 写出特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: IonWriteFeature): Boolean = feature in writeFeatures

    public companion object {

        /**
         * 默认读取特性，对齐 Ion 底层库 2.15 / 3.x 的有效行为。
         *
         * [IonReadFeature.USE_NATIVE_TYPE_ID] 为 上游实现 2.12+ 默认开启；
         * [IonReadFeature.READ_NULL_AS_IONVALUE] 为 上游实现 2.19+/3.x 默认开启
         * （2.15 无该开关，行为等价于开启）；[IonReadFeature.STRICT_DUPLICATE_DETECTION]
         * 默认关闭。
         */
        public val DEFAULT_READ_FEATURES: Set<IonReadFeature> = setOf(
            IonReadFeature.USE_NATIVE_TYPE_ID,
            IonReadFeature.READ_NULL_AS_IONVALUE,
        )

        /**
         * 默认写出特性，对齐 Ion 底层库 2.15 / 3.x。
         *
         * [IonWriteFeature.USE_NATIVE_TYPE_ID] 默认开启（2.12+），
         * [IonWriteFeature.STRICT_DUPLICATE_DETECTION] 默认关闭。
         */
        public val DEFAULT_WRITE_FEATURES: Set<IonWriteFeature> = setOf(
            IonWriteFeature.USE_NATIVE_TYPE_ID,
        )

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

/**
 * 当前配置的 Ion 读取特性；非 [IonConfig] 时使用默认特性。
 */
internal val DocumentMapperConfig.ionReadFeatures: Set<IonReadFeature>
    get() = (this as? IonConfig)?.readFeatures ?: IonConfig.DEFAULT_READ_FEATURES

/**
 * 当前配置的 Ion 写出特性；非 [IonConfig] 时使用默认特性。
 */
internal val DocumentMapperConfig.ionWriteFeatures: Set<IonWriteFeature>
    get() = (this as? IonConfig)?.writeFeatures ?: IonConfig.DEFAULT_WRITE_FEATURES
