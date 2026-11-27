package com.mingliqiye.document.annotation

/**
 * 标注多态基类/接口，指定文档中用于记录子类型名称的属性名。
 *
 * 与 [DocumentTypeName] 和 Mapper 的子类型注册配合使用：
 * - 字段声明类型是标注该注解的基类/接口时，序列化会在对象节点中额外写入
 *   一个类型属性，值为具体子类的 [DocumentTypeName] 或注册名；
 * - 反序列化读取该属性解析到已注册的子类，再按子类映射字段。
 *
 * 没有显式注册时该注解不会生效；Mapper 只会使用注册过的子类型，
 * 不会通过反射扫描全部子类。注册时可以显式覆盖属性名，优先级高于本注解。
 *
 * @property property 文档中的类型属性名，默认 `type`
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class DocumentTypeInfo(
    val property: String = "type",
)
