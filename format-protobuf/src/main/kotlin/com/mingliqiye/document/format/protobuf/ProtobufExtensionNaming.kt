package com.mingliqiye.document.format.protobuf

/**
 * Protobuf extension 在文档对象中的字段命名方式。
 *
 * Protobuf 扩展字段的编号由 `extend` 声明决定、名称可以出现在任意作用域，因此同一消息可能同时
 * 使用多个扩展。不同命名方式在可读性与唯一性之间取舍，由 [ProtobufExtensionRegistry] 在注册
 * 时校验同一消息内文档字段名唯一。
 *
 * @see ProtobufExtensionRegistry
 * @see ProtobufMapper.extensions
 */
public enum class ProtobufExtensionNaming {

    /**
     * 使用扩展字段的简单名，如 `nickname`。
     *
     * 可读性最好，但不同作用域的同名扩展会冲突；注册时若同一消息内简单名重复，或与消息声明的
     * 普通字段同名，会抛出 [IllegalArgumentException]。
     */
    SIMPLE_NAME,

    /**
     * 使用扩展字段全名，如 `my.package.nickname`。默认方式。
     *
     * 全名由 Protobuf descriptor 保证唯一，不会与普通字段名或其它扩展冲突。
     */
    FULL_NAME,

    /**
     * 使用 Protobuf JSON 规范的方括号形式，如 `[my.package.nickname]`。
     *
     * 与 `protobuf` 官方 JSON 映射的扩展写法一致，同样不会冲突。
     */
    BRACKETED,
}
