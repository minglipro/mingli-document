package com.mingliqiye.document.serialization

import java.lang.reflect.Method

/**
 * [com.mingliqiye.document.annotation.DocumentBuilder] /
 * [com.mingliqiye.document.annotation.DocumentBuild] 元数据。
 *
 * 每个目标类型只解析一次并缓存，反序列化时直接调用缓存的工厂方法与构建方法，
 * 避免重复反射与注解扫描。
 *
 * @property ownerType 目标类型，即需要反序列化出的类型
 * @property builderType 静态工厂方法返回的 Builder 类型
 * @property factory 创建 Builder 实例的静态工厂方法
 * @property buildMethod 把 Builder 转换为目标实例的构建方法
 */
internal class BeanBuilderMetadata(
    val ownerType: Class<*>,
    val builderType: Class<*>,
    val factory: Method,
    val buildMethod: Method,
)
