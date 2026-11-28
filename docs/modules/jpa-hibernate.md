# Hibernate 运行期集成

## 坐标

```kotlin
implementation("com.mingliqiye.document:jpa-hibernate:2.4.0")
```

## 能力

```text
JPA 实体 <-> Node
Hibernate 代理解包（Hibernate.unproxy）
懒加载集合/属性初始化（Hibernate.initialize）
EntityManager / Session 两种入口
按 id 加载实体后转 Node
关联深度、环检测
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

## 限制

```text
不管理 Session / EntityManager / 事务 / 持久化
不识别非 Hibernate 提供者代理
复合主键不做引用解析
@Convert / Map 关联 / 多态鉴别器不支持
字节码增强的单属性懒加载可能无法强制初始化
```
