package com.mingliqiye.document.format.ion

/**
 * Ion 写出特性，对齐 Ion 底层库 的格式级 Generator Feature。
 *
 * 上游实现 2.x 中为 `IonGenerator.Feature`，上游实现 3.x 更名为 `IonWriteFeature`；
 * 本枚举同时补充通用 上游实现 生成器特性中可作用于 Ion 的
 * [STRICT_DUPLICATE_DETECTION]。
 *
 * 官方枚举核对（Ion 底层库 2.15、2.23 与 3.3 源码）：
 * 格式级写出特性仅有 [USE_NATIVE_TYPE_ID]，不存在 `WRITE_*` 等常量
 * （那些属于其他格式）。
 *
 * 默认值对齐 上游实现：[USE_NATIVE_TYPE_ID] 默认开启，
 * [STRICT_DUPLICATE_DETECTION] 默认关闭。
 *
 * 通过 [IonConfigBuilder.enable] / [IonConfigBuilder.disable] /
 * [IonConfigBuilder.configure] 配置，[IonConfig.isEnabled] 查询。
 */
public enum class IonWriteFeature {

    /**
     * 使用 Ion 原生 type id（Ion annotation）表示类型，默认开启。
     *
     * 对应 上游实现 `IonGenerator.Feature.USE_NATIVE_TYPE_ID`（2.x）/
     * `IonWriteFeature.USE_NATIVE_TYPE_ID`（3.x）。写出时识别读取端在保真模式
     * （[IonConfig.preserveTypes] = `true`）生成的注解包装对象
     * `{"$ionAnnotations":["ann1","ann2"],"value":<node>}`，用
     * `IonWriter.setTypeAnnotations` 写为原生 Ion annotation。
     *
     * 关闭时该包装对象按普通 struct 写出（不丢失数据，但不再是原生注解）。
     */
    USE_NATIVE_TYPE_ID,

    /**
     * 写出对象时遇到重复字段名抛异常，默认关闭。
     *
     * 对应 上游实现 `StreamWriteFeature.STRICT_DUPLICATE_DETECTION`
     * （上游实现 3.x 的 IonGenerator 已接入该特性）。Bean / Map / Node 树
     * 序列化不会产生重复字段名，该特性主要约束直接使用 Token 写出器
     * （[IonBinaryTokenWriter] / [IonTextTokenWriter]）的调用方。
     * 关闭时保持既有行为：重复字段名原样写出。
     */
    STRICT_DUPLICATE_DETECTION,
}
