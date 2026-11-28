package com.mingliqiye.document.serialization

/**
 * 反序列化特性开关，对应 Jackson 的 `DeserializationFeature`。
 *
 * 通过 [DocumentMapperConfigBuilder.enable] / [DocumentMapperConfigBuilder.disable] /
 * [DocumentMapperConfigBuilder.configure] 显式开启或关闭，也可直接构造
 * [DocumentMapperConfig.deserializationFeatures] 集合；查询使用
 * [DocumentMapperConfig.isEnabled]。
 *
 * 默认全部关闭，与 Jackson 默认配置保持一致，因此升级不会改变既有反序列化行为。
 */
public enum class DeserializationFeature {

    /**
     * 反序列化时禁止把文档中的 `null` 赋给 Java 基本类型。
     *
     * 开启后，字段注入、`@DocumentCreator` 构造参数、Builder 属性、Java record 组件、
     * 基本类型数组元素等位置遇到 `null` 时抛出
     * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]，
     * 异常信息包含字段路径与目标基本类型；不再静默写入 `0`/`false`。
     *
     * 关闭（默认）时保持既有行为：使用基本类型默认值（`0` / `false` / `'\u0000'`），
     * 不抛异常。
     */
    FAIL_ON_NULL_FOR_PRIMITIVES,
}
