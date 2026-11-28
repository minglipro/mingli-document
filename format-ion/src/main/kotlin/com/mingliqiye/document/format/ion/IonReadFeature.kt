package com.mingliqiye.document.format.ion

/**
 * Ion 读取特性，对齐 Ion 底层库 的格式级 Parser Feature。
 *
 * 上游实现 2.x 中为 `IonParser.Feature`，上游实现 3.x 更名为 `IonReadFeature`；
 * 本枚举同时补充通用 上游实现 解析特性中可作用于 Ion 的
 * [STRICT_DUPLICATE_DETECTION]。
 *
 * 官方枚举核对（Ion 底层库 2.15、2.23 与 3.3 源码）：
 * 格式级读取特性仅有 [USE_NATIVE_TYPE_ID]（2.12+）与 [READ_NULL_AS_IONVALUE]
 * （2.19+/3.x），不存在 `LENIENT_*` / `ALLOW_*` 等常量（那些属于其他格式）。
 *
 * 默认值对齐 上游实现：[USE_NATIVE_TYPE_ID] 与 [READ_NULL_AS_IONVALUE] 默认开启
 * （上游实现 2.15 没有 READ_NULL_AS_IONVALUE 开关，其行为等价于开启），
 * [STRICT_DUPLICATE_DETECTION] 默认关闭。
 *
 * 通过 [IonConfigBuilder.enable] / [IonConfigBuilder.disable] /
 * [IonConfigBuilder.configure] 配置，[IonConfig.isEnabled] 查询。
 */
public enum class IonReadFeature {

    /**
     * 使用 Ion 原生 type id（Ion annotation）表示类型，默认开启。
     *
     * 对应 上游实现 `IonParser.Feature.USE_NATIVE_TYPE_ID`（2.x）/
     * `IonReadFeature.USE_NATIVE_TYPE_ID`（3.x）。本库的 Node 基础模型不携带
     * Ion annotation；该特性仅在保真模式（[IonConfig.preserveTypes] = `true`）下
     * 生效：带注解的值读取为包装对象
     * `{"$ionAnnotations":["ann1","ann2"],"value":<node>}`，可与
     * [IonWriteFeature.USE_NATIVE_TYPE_ID] 往返。关闭或非保真模式下注解被丢弃
     * （与历史行为一致）。
     */
    USE_NATIVE_TYPE_ID,

    /**
     * Ion `null` 读取为 Ion 原生 null 值，默认开启。
     *
     * 对应 上游实现 `IonReadFeature.READ_NULL_AS_IONVALUE`（2.19+/3.x）。
     * 开启时所有 null（含 `null.int` 等类型化 null）读取为
     * [com.mingliqiye.document.core.NullNode]。
     *
     * 关闭时按 上游实现 的“容器类型 null 与非容器 null 区分”语义做 Node 模型近似：
     * `null.list` / `null.sexp` → 空 [com.mingliqiye.document.core.ArrayNode]，
     * `null.struct` → 空 [com.mingliqiye.document.core.ObjectNode]，其余 null
     * （含类型化标量 null）仍为 [com.mingliqiye.document.core.NullNode]。
     */
    READ_NULL_AS_IONVALUE,

    /**
     * 同一 Ion struct 内出现重复字段名时抛解析异常，默认关闭。
     *
     * 对应 上游实现 通用解析特性 `JsonParser.Feature.STRICT_DUPLICATE_DETECTION`
     * （上游实现 Ion 解析器本身未接入 DupDetector，本库按通用语义实现）。
     * 关闭时保持既有行为：重复字段名后者覆盖前者。
     */
    STRICT_DUPLICATE_DETECTION,
}
