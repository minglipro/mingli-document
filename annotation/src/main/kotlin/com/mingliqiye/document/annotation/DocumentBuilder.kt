package com.mingliqiye.document.annotation

/**
 * 标记目标类中用于创建 Builder 的静态工厂方法。
 *
 * 用于无法通过 [DocumentCreator] 或无参构造器创建的 Bean（例如只有私有构造器、
 * 字段全部 `final` 的不可变类）。反序列化时 Mapper 按以下步骤创建实例：
 * - 调用标注该注解的静态工厂方法得到 Builder 实例；
 * - 按普通 Bean 规则（无参构造 + setter/字段）用文档内容填充 Builder；
 * - 调用 Builder 上 [DocumentBuild] 标注的方法，未标注时回退到无参 `build()`，
 *   得到目标对象。
 *
 * 工厂方法必须是无参静态方法，且返回 Builder 类型；一个类型最多只能有一个
 * 方法标注该注解。目标类存在 [DocumentCreator] 构造器时优先使用构造器注入。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class DocumentBuilder
