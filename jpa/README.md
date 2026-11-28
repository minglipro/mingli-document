# jpa

JPA / Hibernate ORM 集成模块：JPA 实体 ⇄ `Node` 树。

模块读取 `jakarta.persistence` 注解构建实体元数据，把已经加载到内存中的实体转换为
格式无关的文档树，或把文档树填充回实体。复用 `core` 的 [Node](../core/README.md)、
`serialization` 的 `PropertyNamingStrategy` 与 `exception` 的异常体系；
**不依赖 Hibernate 运行期**，不管理 `Session`/`EntityManager`、事务与懒加载。

生成的 `ObjectNode` 可以直接交给任意格式 `DocumentMapper`（`JsonMapper`、`YamlMapper` 等）
继续序列化为文本，也可以从格式 Mapper 解析回来的树还原实体。

## Maven

```kotlin
implementation("com.mingliqiye.document:jpa:2.3.0")
```

`jakarta.persistence-api:3.1.0` 会作为传递依赖引入；实际运行时 Hibernate 等 JPA
实现自行提供 `EntityManager`，本模块只需要注解类。

## 主要 API

```text
JpaDocumentMapper       实体 ⇄ ObjectNode（toNode / fromNode / toNodes / fromNodes）
JpaConfig               命名策略、transient、关联深度、null 策略、未知属性策略、访问策略
JpaConfigBuilder        配置构建器
JpaNullPolicy           INCLUDE / OMIT
JpaUnknownPropertyPolicy FAIL / IGNORE
JpaAccessType           AUTO / FIELD / PROPERTY
```

异常位于 `com.mingliqiye.document.jpa.exception`：
`JpaException`（继承 `DocumentException`）、`JpaConfigException`（元数据/配置错误）、
`JpaMappingException`（运行时映射错误）。

## 快速开始

```kotlin
import com.mingliqiye.document.jpa.JpaDocumentMapper
import jakarta.persistence.*

@Entity
@Table(name = "users")
class User {
    @Id
    var id: Long? = null

    @Column(name = "user_name", nullable = false)
    var userName: String? = null

    @Enumerated(EnumType.STRING)
    var status: Status? = null

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: java.util.Date? = null

    @Transient
    var cached: String? = null
}

val mapper = JpaDocumentMapper()
val user = User().apply {
    id = 1
    userName = "alice"
    status = Status.ACTIVE
}

// 实体 -> 文档树
val node: com.mingliqiye.document.core.ObjectNode = mapper.toNode(user)
println(node["user_name"]?.asString()) // alice
println(node["status"]?.asString())    // ACTIVE

// 文档树 -> 实体（无参构造器 + 字段 setter 填充）
val copy = mapper.fromNode(node, User::class.java)
```

`fromNode` 只写入文档中出现的属性，缺失属性保持新建对象的默认值；
目标类型必须有可访问的无参构造器（JPA 规范的要求）。

## 与格式 Mapper 集成

`jpa` 只负责“实体 ⇄ 树”，文本/二进制格式交给格式模块：

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.jpa.JpaDocumentMapper

val jpaMapper = JpaDocumentMapper()
val jsonMapper = JsonMapper()

val text = jsonMapper.format(jpaMapper.toNode(user))          // ObjectNode -> JSON 文本
val tree = jsonMapper.parse(text, Node::class.java)           // JSON 文本 -> Node
val copy = jpaMapper.fromNode(tree, User::class.java)         // Node -> User
```

也可以把实体直接交给 `DocumentMapper`（走 serialization 的通用 Bean 映射），
本模块用于需要**按 JPA 列名/关联/枚举/日期语义**映射的场景。

## 配置

```kotlin
import com.mingliqiye.document.jpa.*
import com.mingliqiye.document.serialization.PropertyNamingStrategy

val config = JpaConfig.builder()
    .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)          // 节点字段命名策略
    .includeTransient(false)                                    // 是否包含 @Transient/transient 属性
    .associationDepth(1)                                        // 关联递归层数，0 = 不展开关联
    .nullPolicy(JpaNullPolicy.INCLUDE)                          // 写出 null / 读取 NullNode
    .unknownPropertyPolicy(JpaUnknownPropertyPolicy.FAIL)       // 未知属性：报错 / 忽略
    .accessType(JpaAccessType.AUTO)                             // AUTO / FIELD / PROPERTY
    .build()

val mapper = JpaDocumentMapper(config)
```

| 配置 | 默认值 | 说明 |
|---|---|---|
| `namingStrategy` | `AS_IS` | 由列名/属性名派生节点字段名；读取始终同时接受节点字段名、列名、属性名三种别名 |
| `includeTransient` | `false` | 是否把 `@Transient`/`transient` 属性按普通值读写 |
| `associationDepth` | `1` | 根实体的关联为第 1 层；超过层数的关联写出时省略、读取时跳过 |
| `nullPolicy` | `INCLUDE` | `INCLUDE` 写出 `NullNode`；`OMIT` 跳过 null 属性 |
| `unknownPropertyPolicy` | `FAIL` | `FAIL` 抛 `JpaMappingException`；`IGNORE` 忽略未知键 |
| `accessType` | `AUTO` | 决定读取字段还是 getter 上的注解 |

### 节点字段命名规则

1. 标量属性以列名（`@Column(name)`；未标注用属性名）作为基础名；
2. 关联属性与 `@Embedded` 属性以属性名作为基础名（关联不是数据库列）；
3. 基础名再经 `namingStrategy.translate` 转换。

`AS_IS`（默认）下 `@Column(name = "user_name")` 的节点字段就是 `user_name`；
`SNAKE_CASE` 下 `userName` 属性写出 `user_name`。读取时 `user_name`、`userName` 都能识别，
所以命名策略变化不会破坏旧文档的读取。

### 访问策略

- `AUTO`：类上 `@Access` → 第一个 `@Id` 所在成员（字段或 getter）→ 字段访问；
- `FIELD`：读取 `Field` 上的注解（可写字段直接反射赋值）；
- `PROPERTY`：读取 getter 上的注解，通过 getter/setter 读写（Kotlin 用 `@get:Id` 等）。

字段访问时如果 getter 上有注解（Kotlin 常见 `@get:` 写法）也会作为回退来源读取。

## 注解支持

| 注解 | 支持情况 |
|---|---|
| `@Entity` | 读取 `name`；沿类层次查找（兼容 Hibernate 代理子类），`JpaDocumentMapper.entityName` 可查询 |
| `@Table` | 读取 `name`；`JpaDocumentMapper.tableName` 可查询，不参与节点内容 |
| `@MappedSuperclass` | 父类字段/属性参与映射，父类属性排在子类之前 |
| `@Id` / `@EmbeddedId` | 作为普通属性参与映射（`@EmbeddedId` 生成嵌套对象） |
| `@Column` | 读取 `name`（列名/节点名）与 `nullable`（读取 null 时校验） |
| `@Embedded` | 嵌入对象生成嵌套对象节点 |
| `@Transient` | 默认忽略；`includeTransient(true)` 时按普通值读写 |
| `@Enumerated` | `STRING` 写名称、`ORDINAL` 写序号；JPA 默认 `ORDINAL` |
| `@Temporal` | `DATE` / `TIME` / `TIMESTAMP`，详见下方类型映射 |
| `@OneToOne` / `@ManyToOne` | 单值关联，生成嵌套对象；读取 `optional` 决定 null 校验 |
| `@OneToMany` / `@ManyToMany` | 集合关联，生成数组；元素类型取泛型参数或 `targetEntity` |
| `@ElementCollection` | 标量或嵌入对象的元素集合，生成数组 |
| `@Access` | 类级访问策略 |

不支持（会被忽略或按普通值处理）：`@JoinColumn` 的列名语义（关联节点使用属性名）、
`@AttributeOverride`、`@MapKey`（Map 关联直接抛 `JpaConfigException`）、
`@Convert`/`AttributeConverter`（节点表示 Java 属性值，本就与数据库转换无关）、
`@Inheritance`/`@DiscriminatorColumn` 多态写入、`@Version` 的乐观锁语义、`@Formula` 等。

## 类型映射

写出（实体属性值 -> Node）：

| Java 类型 | Node |
|---|---|
| `String`/`CharSequence`/`char` | `StringNode` |
| `boolean`/`Boolean` | `BooleanNode` |
| `byte`/`short`/`int`/`long`/`float`/`double`/包装类型/`BigDecimal`/`BigInteger` | `NumberNode`（精确 `BigDecimal`） |
| `byte[]` | `BinaryNode` |
| 枚举 | `@Enumerated(STRING)` 或名称/序号，默认 `ORDINAL` |
| `java.util.Date`/`Calendar` + `@Temporal` | ISO 字符串：`DATE` -> `yyyy-MM-dd`、`TIME` -> `HH:mm:ss`、`TIMESTAMP` -> `yyyy-MM-dd'T'HH:mm:ss`（系统时区） |
| `java.time.LocalDate/LocalTime/LocalDateTime/Instant/OffsetDateTime/OffsetTime/ZonedDateTime` | ISO-8601 字符串 |
| `java.util.UUID` | 字符串 |
| 集合/`Map`/数组（非关联属性、transient、`@ElementCollection` 元素） | 数组/对象/数组 |

读取时按属性声明类型反向转换；数字必须能精确收窄，枚举名称/序号非法、日期格式非法、
`@Column(nullable = false)` 读到 null、原始类型读到 null 都会抛 `JpaMappingException`。

`java.sql.Date`/`java.sql.Time`/`java.sql.Timestamp` 未标注 `@Temporal` 时按自身类型
（DATE/TIME/TIMESTAMP）处理。

## 关联、深度与循环

```kotlin
@Entity
class Department {
    @Id var id: Long? = null
    var name: String? = null

    @OneToMany(mappedBy = "department")
    var employees: MutableList<Employee> = mutableListOf()
}

@Entity
class Employee {
    @Id var id: Long? = null

    @ManyToOne
    @JoinColumn(name = "department_id")
    var department: Department? = null
}
```

- 根实体的关联是第 1 层：`associationDepth = 1` 时只展开根实体的关联；
- 超过层数的关联属性写出时省略、读取时跳过（不会成为未知属性报错）；
- 关联集合在读取时按声明类型实例化：`Set` -> `LinkedHashSet`、`List`/`Collection` ->
  `ArrayList`、具体集合类使用无参构造器；
- 同一对象在递归路径上再次出现（双向关联成环、自引用）会抛 `JpaMappingException`，
  消息包含首次出现路径；`associationDepth` 足够大时才会真正递归到环。
- 集合关联必须能从泛型参数或 `targetEntity` 解析元素类型，否则抛 `JpaConfigException`。

## null 与未知属性

- `INCLUDE`（默认）：null 属性写出 `NullNode`，读取 `NullNode` 写入 null；
- `OMIT`：写出跳过 null 属性，读取时缺失属性保持默认值（不会覆盖已有集合默认值）；
- `FAIL`（默认）：文档中出现无法映射到任何属性（节点字段名/列名/属性名）的键时抛异常；
- `IGNORE`：忽略这些键；
- 同一个属性通过多个别名（如同时出现 `user_name` 与 `userName`）重复出现会抛异常。

## 异常

```kotlin
import com.mingliqiye.document.jpa.exception.JpaConfigException
import com.mingliqiye.document.jpa.exception.JpaMappingException

try {
    mapper.fromNode(node, User::class.java)
} catch (e: JpaMappingException) {
    // 节点类型不匹配、未知属性、非空校验、枚举/日期转换、循环引用、无法实例化等
} catch (e: JpaConfigException) {
    // 元数据非法：重复字段名、不支持的关联类型、集合关联缺少元素类型等
}
```

两者都继承 `JpaException`（继承 `DocumentException`），可以统一按 `DocumentException` 捕获。
反射/构造函数抛出的底层异常作为 cause 保留。

## Hibernate 兼容说明

- 只读取 JPA 注解，不需要 Hibernate 在 classpath 上；在 Hibernate 实体上直接使用；
- 不管理 `Session`、`EntityManager`、事务、脏检查、级联与懒加载；
- Hibernate 代理类（子类）会沿父类找到 `@Entity` 元数据，按实体字段映射；但访问代理的
  未初始化字段可能触发 `LazyInitializationException`，请先初始化需要的字段再调用 `toNode`；
- 集合属性建议用已加载的 `ArrayList`/`LinkedHashSet` 等普通集合（Hibernate 的
  `PersistentBag` 等实现也按 `Collection` 处理，但写出顺序/身份受其实现影响）；
- 实体需要无参构造器（属性和字段都可以是 `private`，反射会打开访问权限）。

## 限制与注意事项

- 单向/双向关联不会自动保持一致，也不做级联加载：写出的是当前内存中的对象图；
- 不处理 `@Convert`、自定义类型、嵌入式集合表结构等数据库细节，只映射 Java 属性值；
- 不映射多态继承（`@Inheritance`）与 `@DiscriminatorColumn`；关联目标按声明类型或
  `targetEntity` 实例化；
- `@ElementCollection` 元素类型为标量或普通对象；Map 类型的关联/元素集合不支持；
- 不支持 `@Temporal` 之外的日期格式化（如 `@JsonFormat` 等），需要自定义格式请先用
  serialization 模块的 `DocumentDateTimeFormat` 或先转换成字符串；
- `fromNode` 不做 Bean Validation（需要时先用 `validation` 模块校验）；
- 循环引用直接抛异常，不写 `$ref`；需要引用语法请使用 serialization 的 Mapper 配置；
- 映射结果只保证与当前 `JpaConfig` 对称：改变 `associationDepth`/`nullPolicy` 后，
  旧文档中超出深度或缺失的字段会被忽略/保持默认值，不会报错。

## 构建

```bash
# 主代码按 JDK 8 目标编译；测试 JVM 使用 JDK 17（jakarta.persistence-api 3.1.0
# 的 class 文件版本为 55 / Java 11+，JDK 8 无法在运行期加载其注解类）
gradle :jpa:ktlintFormat :jpa:test
```

测试不连接任何数据库，实体只用于注解/反射映射。

文档：[根 README](../README.md) · [serialization](../serialization/README.md) · [core](../core/README.md) · [贡献指南](../CONTRIBUTING.md)
