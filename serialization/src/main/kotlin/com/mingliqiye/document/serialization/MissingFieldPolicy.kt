package com.mingliqiye.document.serialization

/**
 * JSON/XML/YAML 等文档缺少某个 Bean 字段时的处理策略。
 */
public enum class MissingFieldPolicy {

    /** 保留 Java 对象构造后的默认值。 */
    KEEP_DEFAULT,

    /** 引用类型字段设为 `null`；基本类型保留默认值。 */
    SET_NULL,

    /** String 设空串，集合/Map/数组设空集合；其他引用类型设为 `null`。 */
    SET_EMPTY,

    /** 缺少可写字段时抛出 [com.mingliqiye.document.serialization.exception.DocumentSerializationException]。 */
    FAIL,
}
