# Hibernate 运行期集成

## 坐标

```kotlin
implementation("com.mingliqiye.document:jpa-hibernate:2.5.0")
```

## 能力

```text
JPA 实体 <-> Node
Hibernate 代理解包（Hibernate.unproxy）
懒加载集合/属性初始化（Hibernate.initialize）
EntityManager / Session 两种入口
按 id 加载实体后转 Node
关联深度、环检测
@Convert / autoApply、Map 关联、@Inheritance 多态（复用 jpa 模块语义）
```

不负责持久化与事务。

## 快速开始

```kotlin
val mapper = HibernateDocumentMapper()

val node = mapper.toNode(user, entityManager)
val restored = mapper.fromNode(node, User::class.java, entityManager)
```

按 id：

```kotlin
val node = mapper.findAsNode(User::class.java, 1L, entityManager)
```

Session 入口：

```kotlin
val node = mapper.toNode(user, session)
val node = mapper.getAsNode(User::class.java, 1L, session)
```

## 懒加载策略

```kotlin
HibernateConfig.builder()
    .lazyLoadPolicy(HibernateLazyLoadPolicy.INITIALIZE)
    .cyclePolicy(HibernateCyclePolicy.FAIL)
    .build()
```

```text
INITIALIZE  初始化懒加载属性和集合
FAIL        遇到未初始化代理/集合抛异常
FAIL        环引用抛异常
OMIT        单值循环字段省略、集合循环元素跳过
```

## 要求

```text
Hibernate ORM 6.x
运行期 Java 17+
主代码仍为 JDK 8 字节码
```

## 高级映射

高级语义（`@Convert`/autoApply、Map 关联、多态 discriminator）由 jpa 模块实现，
本模块在 Hibernate 运行期映射中复用：

```kotlin
val mapper = HibernateDocumentMapper(
    jpaConfig = JpaConfig.builder()
        .autoApplyConverter(EmailConverter::class.java)  // 本模块透传 jpa 配置
        .entityType(Cat::class.java)                     // 多态读入候选子类
        .build(),
)
```

- 非关联属性（含转换器）由 jpa 模块按元数据映射；
- `MapAttribute` 关联展开为对象节点，键格式与 `JpaMapKeys` 一致；实体 Map 键
  （`@MapKeyJoinColumn`）写出标识符文本，读入经 `EntityManager.find` 解析为受管实例；
- 关系属性上的显式 `@Convert` 按基本属性写/读，Hibernate 关联遍历自动跳过；
- 写出按实际实体类型写 discriminator；读入按 discriminator 实例化并在关联遍历中
  使用实例的实际实体类型；
- `fromNode` 关联引用节点支持单主键、`@EmbeddedId` 与 `@IdClass` 复合主键
  （`EntityManager.find` 使用复合 id 对象）；
- 持久化到数据库时 Hibernate `SessionFactory` 也需注册同一转换器。

## 限制

```text
不管理 Session / EntityManager / 事务 / 持久化
不识别非 Hibernate 提供者代理
复合主键关联引用已支持（@EmbeddedId / @IdClass）
实体 Map 键类型须可实例化、复合主键组件须为基本属性
autoApply 转换器与多态子类需通过 JpaConfig 注册
字节码增强的单属性懒加载可能无法强制初始化
```
