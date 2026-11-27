package com.mingliqiye.document.annotation

/**
 * 标注具体子类在文档中使用的多态类型名。
 *
 * 字段声明类型是 [DocumentTypeInfo] 标注的基类/接口时，序列化写出本注解的值；
 * 反序列化按该值找到已注册的子类。显式注解优先于 Mapper 注册时提供的类型名：
 * 注册名会作为别名保留，仍可用于反序列化。
 *
 * @property value 类型名，不能为空
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class DocumentTypeName(
    val value: String,
)
