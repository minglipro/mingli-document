package com.mingliqiye.document.annotation

/**
 * 注解层使用的双向转换器标记。
 *
 * 真正的序列化/反序列化接口在 `serialization` 模块中，并继承本标记。
 *
 * @param T 转换类型
 */
public interface DocumentBaseConverter<T>
