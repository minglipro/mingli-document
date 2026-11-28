# jpa-hibernate

Hibernate `Session` / `EntityManager` 运行期集成模块：在 `jpa` 模块的注解映射之上，
处理 Hibernate 代理、懒加载集合/属性、关联深度、循环引用与按标识符加载。

模块复用 `jpa` 模块的全部注解元数据（列名、枚举、日期、`@Embedded`、
`@ElementCollection`、`@Convert`/autoApply 转换器、Map 关联、`@Inheritance` 多态、
命名策略、null 策略、访问策略），**不重复实现元数据**；Hibernate 运行期只使用
JPA 元模型（`jakarta.persistence.metamodel`）区分关联属性，不再扫描注解。
不负责持久化/事务：`Session`/`EntityManager` 的打开、关闭、事务边界
全部由调用方管理。

## Maven

```kotlin
implementation("com.mingliqiye.document:jpa-hibernate:2.5.0")
```

需要 Hibernate ORM **6.5.x**（开发与测试使用 `6.5.3.Final`），它会作为传递依赖引入。

### 版本与 Java 要求

- 主代码与发布字节码目标为 **Java 8**（与其它模块一致）；
- Hibernate ORM 6.5 的 class 文件版本为 55，**运行期需要 Java 11+**，
  本模块的测试在 **JDK 17** 上执行（`build.gradle.kts` 中为测试 configuration 声明了
  目标 JVM 17，避免 Gradle 变体选择拒绝 Java 11+ 依赖）；
- 推荐生产运行环境使用 Java 17。

## 主要 API

```text
HibernateDocumentMapper   实体 ⇄ Node，Hibernate 感知的 toNode / fromNode
HibernateConfig           Hibernate 运行期配置（懒加载策略、循环引用策略）
HibernateConfigBuilder    配置构建器
HibernateLazyLoadPolicy   INITIALIZE / FAIL
HibernateCyclePolicy      FAIL / OMIT
```

`HibernateDocumentMapper` 的构造参数：

```kotlin
HibernateDocumentMapper(
    jpaConfig = JpaConfig.builder().build(),          // jpa 模块映射配置
    config = HibernateConfig.builder().build(),       // Hibernate 运行期配置
)
```

异常位于 `com.mingliqiye.document.jpa.hibernate.exception`：
`HibernateJpaException`（继承 `DocumentException`）、`HibernateJpaMappingException`
（Hibernate 运行期映射错误）、`HibernateJpaLazyLoadException`（懒加载初始化失败）。
jpa 模块自身的元数据/字段映射错误仍以 `jpa` 模块的 `JpaException` 子类抛出。

## 快速开始

```kotlin
import com.mingliqiye.document.jpa.hibernate.HibernateDocumentMapper
import jakarta.persistence.EntityManager

val mapper = HibernateDocumentMapper()

// Hibernate 感知：实体/代理 + 打开的 Session/EntityManager
entityManager.use { em ->
    em.transaction.use { tx ->
        val department = em.find(Department::class.java, 1L)!!

        // 懒加载 @OneToMany 会被 Hibernate.initialize 初始化，
        // 懒加载 @ManyToOne 代理会被 Hibernate.unproxy 还原
        val node: ObjectNode = mapper.toNode(department, em)

        // 按标识符加载并转换（EntityManager.find / Session.get）
        val same: ObjectNode? = mapper.findAsNode(Department::class.java, 1L, em)

        // 节点 -> 实体：返回 detach 实体图；`{"id": 5}` 形式的关联节点
        // 会在传入 EntityManager 时解析为持久化上下文中的受管实例
        val employee = mapper.fromNode(employeeNode, Employee::class.java, em)

        tx.commit()
    }
}
```

也可以直接传 Hibernate `Session`（`Session` 本身是 `EntityManager` 子类型，
模块提供了显式的 `Session` 重载）：

```kotlin
mapper.toNode(department, session)
mapper.fromNode(employeeNode, Employee::class.java, session)
mapper.getAsNode(Department::class.java, 1L, session)
```

不传 `EntityManager` / `Session` 时，映射完全委托给 `jpa` 模块：

```kotlin
val detachedNode = mapper.toNode(detachedEntity)       // 适用于已完成初始化的 detach 图
val restored = mapper.fromNode(detachedNode, Employee::class.java)
```

## API 一览

| 方法 | 说明 |
|---|---|
| `toNode(entity)` | 委托 jpa 模块映射 detach 图；不处理代理/懒加载 |
| `toNode(entity, EntityManager?)` | `null` 等价于单参数版本；否则启用 Hibernate 感知映射 |
| `toNode(entity, Session)` | 显式 Session 入口，语义同上 |
| `fromNode(node, entityClass)` | 委托 jpa 模块构建 detach 实体图 |
| `fromNode(node, entityClass, EntityManager?)` | `null` 等价于两参数版本；否则启用引用解析 |
| `fromNode(node, entityClass, Session)` | 显式 Session 入口 |
| `findAsNode(entityClass, id, EntityManager)` | `EntityManager.find` 后转 `ObjectNode`；不存在返回 `null` |
| `getAsNode(entityClass, id, Session)` | `Session.get` 后转 `ObjectNode`；不存在返回 `null` |

## toNode：Hibernate 感知映射

传入打开的 `EntityManager`/`Session` 时：

1. **代理还原**：根实体和递归访问到的关联值先按懒加载策略初始化，再
   `Hibernate.unproxy`，因此 `find`、`getReference`、`load` 得到的代理都可以直接映射；
2. **懒加载初始化**：关联集合/`@ElementCollection`/字节码增强懒加载属性按
   `HibernateLazyLoadPolicy` 初始化（可能产生额外 SELECT）；
3. **关联深度**：使用 `JpaConfig.associationDepth`（根实体的关联为第 1 层）；
   超过深度的关联字段在写出时被省略，且**不会触发**该关联的懒加载查询；
4. **循环引用**：按对象身份（代理已还原）检测递归路径上的重复实体，按
   `HibernateCyclePolicy` 抛异常或省略该引用；
5. **普通字段**：id/列名/枚举/日期/`@Embedded`/`@ElementCollection` 等仍由 jpa 模块
   按注解元数据写出，因此与 `JpaDocumentMapper` 的输出保持一致。

字段顺序为：非关联字段（沿用 jpa 模块顺序）在前，关联字段（Hibernate 元模型顺序）
在后。没有关联的实体与 jpa 模块输出完全一致。

## fromNode：引用解析

传入打开的 `EntityManager`/`Session` 时，返回的根实体始终是**根据节点新建的 detach
实例**（不会被 `persist`/`merge`/附加到持久化上下文），额外提供：

- 关联节点只含标识符（如 `{"id": 5}`，且键是标识符属性名/列名的别名）时，通过
  `EntityManager.find` 解析为持久化上下文中的**受管实例**；找不到时退化为只带标识符的
  detach 实例；
- 关联节点含其他字段时，递归构建 detach 实体（受关联深度限制）；
- 必填关联（`optional = false`）读到 `NullNode` 抛
  `HibernateJpaMappingException`；
- 未知属性、字段类型/枚举/日期转换错误仍由 jpa 模块按 `JpaConfig` 校验/抛出。

引用解析只支持**单个基本类型标识符**（`@Id` 基本类型）；复合主键、`@EmbeddedId`
或标识符字段名不是节点键别名时按普通 detach 递归处理。

## 高级映射：@Convert / Map 关联 / 多态

本模块不重复实现这些 JPA 语义，而是在 Hibernate 运行期映射中复用 jpa 模块的规则：

- **`@Convert` / autoApply `AttributeConverter`**：非关联属性由 jpa 模块按元数据
  写出/读入，因此文档中表示数据库列值；autoApply 转换器通过
  `JpaConfig.builder().autoApplyConverter(...)` 注册后同样生效（`HibernateDocumentMapper`
  会把 `JpaConfig` 原样透传给内部 jpa 映射器）。注意：**持久化到数据库**时，
  Hibernate `SessionFactory` 也需要认识该转换器（例如
  `MetadataBuilder.applyAttributeConverter(...)`），这是 JPA 提供者的要求。
- **Map 关联（`Map<K, V>`）与 Map `@ElementCollection`**：Hibernate 元模型中的
  `MapAttribute` 会按 jpa 模块相同规则展开为对象节点（键为字符串化 Map 键），
  lazy `PersistentMap` 按懒加载策略初始化；键格式由 jpa 模块公开的 `JpaMapKeys`
  统一，`@MapKey`/`@MapKeyColumn`/`@MapKeyEnumerated`/`@MapKeyTemporal` 语义一致。
  键文本冲突/无法解析同样抛异常。
- **多态（`@Inheritance`/`@DiscriminatorColumn`/`@DiscriminatorValue`）**：
  写出方向按实际实体类型（Hibernate 代理会先还原）写出 discriminator 与子类字段；
  读入方向由 `JpaConfig.entityTypes` 注册的子类决定实例化类型，未知 discriminator
  抛 `JpaMappingException`；关联值的声明类型是父类时同样按 discriminator 选择子类。
  Hibernate 感知映射的关联遍历会使用实例的**实际实体类型**，因此子类新增的关联也
  会被读取。
- **关系属性上的显式 `@Convert`**：jpa 模块把整个关联值按基本属性写出/读入
  （不展开关联）；Hibernate 感知映射识别这类属性并跳过关联遍历，不会用关联展开
  覆盖转换后的标量值。数据库持久化仍需 `SessionFactory` 注册该转换器。
- **`@MapKeyJoinColumn` 实体 Map 键**：文档键为键实体标识符文本（复用 jpa 模块
  规则与复合主键文本格式）；写出时先把代理/懒加载键还原再取标识符；读入时由
  `JpaDocumentMapper.entityKeyFromText` 重建 detach 键，再通过 `EntityManager.find`
  解析为持久化上下文中的受管实例（找不到时回退 detach 实例）。复合主键
  （`@EmbeddedId`/`@IdClass`）标识符对象由 `identifierValue` 构造后传给 `find`。
- **关联引用解析（`fromNode`）**：只含标识符的关联节点除单基本主键外，现在支持
  `@EmbeddedId`（例如 `{"key": {"orderNo": "A-1", "region": "EU"}}`）与 `@IdClass`
  （例如 `{"shelf": 1, "slot": 2}`）复合主键；`EntityManager.find` 使用对应复合 id
  对象解析为受管实例，找不到时退化为 detach 实例。单主键行为不变。

```kotlin
val mapper = HibernateDocumentMapper(
    jpaConfig = JpaConfig.builder()
        .autoApplyConverter(EmailConverter::class.java)   // autoApply 转换器
        .entityType(Cat::class.java)                      // 多态读入候选子类
        .entityType(Dog::class.java)
        .build(),
)

entityManager.use { em ->
    val zoo = em.find(Zoo::class.java, 1L)
    val node = mapper.toNode(zoo, em)                     // 子类字段 + discriminator + Map 关联
    val copy = mapper.fromNode(node, Zoo::class.java, em) // 关联解析 + 多态实例化
}
```

## 配置

```kotlin
import com.mingliqiye.document.jpa.hibernate.HibernateConfig
import com.mingliqiye.document.jpa.hibernate.HibernateCyclePolicy
import com.mingliqiye.document.jpa.hibernate.HibernateLazyLoadPolicy

val config = HibernateConfig.builder()
    .lazyLoadPolicy(HibernateLazyLoadPolicy.INITIALIZE)   // INITIALIZE / FAIL
    .cyclePolicy(HibernateCyclePolicy.FAIL)               // FAIL / OMIT
    .build()

val mapper = HibernateDocumentMapper(config = config)
```

| 配置 | 默认值 | 说明 |
|---|---|---|
| `lazyLoadPolicy` | `INITIALIZE` | `INITIALIZE`：需要时用 `Hibernate.initialize` 初始化；`FAIL`：遇到未初始化懒加载数据直接抛 `HibernateJpaLazyLoadException`，不产生额外查询 |
| `cyclePolicy` | `FAIL` | `FAIL`：抛 `HibernateJpaMappingException`（消息含首次出现路径）；`OMIT`：单值关联字段省略、集合中的循环元素跳过 |

`JpaConfig` 的 `associationDepth`、`namingStrategy`、`nullPolicy`、
`unknownPropertyPolicy`、`accessType`、`includeTransient`、`autoApplyConverters`、
`entityTypes` 在 Hibernate 感知映射中同样生效（基础映射器原样透传 jpa 配置）。
`INITIALIZE` 需要传入与实体关联的、打开的 `Session`/`EntityManager`；否则抛
`HibernateJpaLazyLoadException`。

## Session 生命周期

- 映射器**不打开/关闭** `Session`、**不开启/提交/回滚**事务；只在需要时通过传入的
  `Session` 初始化懒加载数据（读取副作用，可能产生额外 SELECT）；
- 传入 `Session`/`EntityManager` 时必须处于打开状态，映射器不接管其生命周期；
- 关闭 `Session` 后：
  - 调用不传 `EntityManager` 的 `toNode(detached)` 映射**已完成初始化**的 detach 图仍然可用；
  - 传已关闭的 `EntityManager`/`Session` 会抛 `HibernateJpaMappingException`（消息包含
    `closed`）；
  - 未初始化懒加载数据绑定到已关闭 Session 时，初始化失败会包装为
    `HibernateJpaLazyLoadException`；
- `fromNode` 的引用解析与 `findAsNode`/`getAsNode` 会读取数据库（`find`/`get`），
  但不修改持久化上下文；实体是否 `persist`/`merge` 由调用方决定。

线程安全：映射器本身不保存可变状态，可跨线程复用；传入的 `EntityManager`/`Session`
本身不是线程安全的。

## 异常

```kotlin
import com.mingliqiye.document.exception.DocumentException
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaLazyLoadException
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaMappingException

try {
    mapper.toNode(entity, session)
} catch (e: HibernateJpaLazyLoadException) {
    // 懒加载初始化失败：策略为 FAIL / 无可用 Session / Session 已关闭
} catch (e: HibernateJpaMappingException) {
    // 循环引用、代理还原失败、实体不受当前 EntityManager 管理、EntityManager 已关闭等
} catch (e: DocumentException) {
    // 统一捕获（包含 jpa 模块的 JpaException 子类）
}
```

## 限制与注意事项

- **Hibernate 专有**：模块依赖 Hibernate ORM 的 `Hibernate.unproxy`/`Hibernate.initialize`
  与 Hibernate Session 实现；其它 JPA 提供者的代理不会被识别（会被当作普通实体字段映射）；
- **不加载关联内部的关联**：`@Embeddable` 内部的关系映射由 jpa 模块按深度规则处理，
  本模块的代理/懒加载遍历只覆盖实体自身的关联属性；
- 高级语义（`@Convert`/autoApply、Map 关联、多态 discriminator）由 jpa 模块实现，
  本模块只负责 Hibernate 运行期遍历；因此：
  - autoApply 转换器与多态子类需要通过 `JpaConfig` 注册；
  - Map 键格式完全沿用 jpa 模块（`JpaMapKeys`），Hibernate 侧不额外解释 `@MapKeyColumn` 列名；
  - 多态读入时若没有注册任何子类，未知 discriminator 会抛 jpa 模块的 `JpaMappingException`；
- 转换为数据库列值的 `@Convert` 若需要在数据库读写，Hibernate `SessionFactory` 也必须
  注册同一转换器（JPA 提供者要求），否则实体引导/持久化会失败；
- 字节码增强的懒加载属性依赖 `Hibernate.initialize` 能整体初始化实体；单属性增强代理
  无法初始化时会抛 `HibernateJpaLazyLoadException`（请在映射前通过 getter 初始化）；
- 关联深度为 0 时不读取关联字段，因此不会触发任何关联懒加载查询；
- `fromNode` 不负责级联、脏检查与事务；受管引用解析仅对单基本类型标识符生效；
- 循环引用不做 `$ref` 语法：`FAIL` 抛异常、`OMIT` 省略；集合形状可能因省略循环元素而变化；
- 字段顺序：非关联字段在前、关联字段在后（jpa 模块按声明顺序混合排列），两者内容语义一致。

## 构建

```bash
# 主代码按 JDK 8 目标编译；测试在 JDK 17 上执行（Hibernate ORM 6.5 运行期要求 Java 11+）
gradle :jpa-hibernate:ktlintFormat :jpa-hibernate:test
```

测试使用 H2 内存数据库 + 以编程方式 bootstrap 的 Hibernate `SessionFactory`，
覆盖：懒加载 `@OneToMany`/`@ManyToOne`、Hibernate 代理（含 `getReference` 根代理）、
`@Embedded`、枚举、懒加载 `@ElementCollection`、关联深度、双向/自引用循环、
`EntityManager` 与 `Session` 两种入口、`fromNode` 引用解析与 `findAsNode`/`getAsNode`，
以及 `@Convert`/autoApply 转换器、Map 关联（`@MapKey`/`@MapKeyColumn`，与纯 jpa
映射的键格式一致性）、`@Inheritance` 多态的写出与按 discriminator 读入，还有关系
属性 `@Convert`、`@MapKeyJoinColumn` 实体 Map 键（受管实例解析）、`@EmbeddedId` /
`@IdClass` 复合主键关联引用解析。

文档：[根 README](../README.md) · [jpa](../jpa/README.md) · [core](../core/README.md) · [贡献指南](../CONTRIBUTING.md)
