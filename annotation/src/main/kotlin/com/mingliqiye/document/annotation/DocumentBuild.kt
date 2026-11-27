package com.mingliqiye.document.annotation

/**
 * 标记 Builder 上把 Builder 转换为目标对象的无参构建方法。
 *
 * 与 [DocumentBuilder] 配合使用：Mapper 用文档内容填充完 Builder 后，调用
 * 标注该注解的方法得到目标对象。方法必须是无参实例方法，且返回值可以赋值给
 * 目标类型；Builder 上没有标注该注解时，Mapper 回退到无参 `build()` 方法。
 *
 * 一个 Builder 类型最多只能有一个方法标注该注解。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class DocumentBuild
