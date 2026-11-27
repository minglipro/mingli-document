package com.mingliqiye.document.annotation

/**
 * 注解层使用的反序列化器标记。
 *
 * 真正的反序列化接口在 `serialization` 模块中，并继承本标记。
 *
 * @param T 反序列化类型
 */
public interface DocumentBaseDeserializer<T>
