package com.mingliqiye.document.annotation

/**
 * 标记反序列化时用于创建实例的构造器。
 *
 * 与 [DocumentName] 的 [AnnotationTarget.VALUE_PARAMETER] 目标配合使用，
 * 让没有无参构造器的不可变 Bean（例如 Java 中字段为 `final` 的类、
 * Kotlin data class）也能被反序列化：
 * - Mapper 反序列化对象时优先使用标注该注解的构造器，按构造参数名从文档取值；
 * - 构造参数名取参数上的 [DocumentName] 值；未标注时使用编译时保留的参数名
 *   （Java 编译需 `-parameters`，Kotlin 需 `-java-parameters`）；
 * - 参数类型参与递归反序列化，支持泛型、嵌套 Bean、集合、Map、可空与基本类型。
 *
 * 一个类型最多只能有一个构造器标注该注解；没有标注时 Mapper 保持原有的
 * 无参构造 + setter 行为。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CONSTRUCTOR)
public annotation class DocumentCreator
