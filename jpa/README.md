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
implementation("com.mingliqiye.document:jpa:2.5.0")
```

`jakarta.persistence-api:3.1.0` 会作为传递依赖引入；实际运行时 Hibernate 等 JPA
实现自行提供 `EntityManager`，本模块只需要注解类。

## 主要 API

```text
JpaDocumentMapper       实体 ⇄ ObjectNode（toNode / fromNode / toNodes / fromNodes）
                        额外查询 API：isEntity / entityName / tableName /
                        discriminatorValue / discriminatorFieldName；
                        实体 Map 键文本：entityKeyText / entityKeyFromText
JpaConfig               命名策略、transient、关联深度、null 策略、未知属性策略、访问策略、
                        autoApply 转换器注册、多态子类注册
JpaConfigBuilder        配置构建器
JpaNullPolicy           INCLUDE / OMIT
JpaUnknownPropertyPolicy FAIL / IGNORE
JpaAccessType           AUTO / FIELD / PROPERTY
JpaMapKeys              Map 键 ⇄ 文档字段名的转换规则（与 Mapper 内部一致，供扩展模块复用）
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
    .autoApplyConverter(MoneyConverter::class.java)             // 注册 autoApply 转换器（不扫描 classpath）
    .entityType(CatEntity::class.java)                          // 注册多态子类（按 discriminator 读入）
    .entityType(DogEntity::class.java)
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
| `autoApplyConverters` | 空 | 注册为 autoApply 的 `AttributeConverter` 类；按属性 Java 类型匹配 |
| `entityTypes` | 空 | 多态读入时的候选子类；写出方向不需要注册 |

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
| `@Convert` / `@Converts` | 属性级 `AttributeConverter`，文档中为**数据库列值**；null 也会传入转换器；`attributeName` 支持 `@Embedded`/`@EmbeddedId` 内部点分路径；关系属性上的显式 `@Convert` 表示整个关联值按基本属性转换 |
| autoApply `AttributeConverter` | 通过 `JpaConfig.builder().autoApplyConverter(...)` 注册后按属性类型匹配 |
| `@OneToOne` / `@ManyToOne` | 单值关联，生成嵌套对象；读取 `optional` 决定 null 校验 |
| `@OneToMany` / `@ManyToMany` | 集合关联生成数组；`Map<K, V>` 关联生成对象（见“Map 关联”） |
| `@ElementCollection` | 标量/嵌入对象/Map 元素集合，生成数组或对象 |
| `@MapKey` / `@MapKeyClass` / `@MapKeyColumn` | Map 关联的键语义/键类型；`@MapKeyEnumerated`、`@MapKeyTemporal` 控制键文本 |
| `@MapKeyJoinColumn` / `@MapKeyJoinColumns` | Map 键为实体引用：文档键为键实体标识符文本（见“Map 关联”）；支持 `@EmbeddedId`/`@IdClass` 复合主键 |
| `@Inheritance` / `@DiscriminatorColumn` / `@DiscriminatorValue` | discriminator 字段写出与多态读入（见“继承与多态”） |
| `@Access` | 类级访问策略 |

不支持（会被忽略或按普通值处理）：`@JoinColumn` 的列名语义（关联节点使用属性名）、
`@AttributeOverride`、`@OrderBy`、`@Version` 的乐观锁语义、`@Formula`、
`@Convert(attributeName = ...)` 指向关联/嵌入对象内部非基本属性等写法。

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

`@Convert`/autoApply 转换器不存在时属性值按上表映射；存在转换器时表中描述的是
**数据库列类型 Y**，`X` 与 `Y` 的转换由转换器负责（见“属性转换器”）。

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

## Map 关联与 Map 元素集合

`@OneToMany` / `@ManyToMany` 的 `Map<K, V>`，以及 `Map` 形式的 `@ElementCollection`，
在文档中表示为对象节点：**字段名是字符串化的 Map 键，值是关联/元素值**。

```kotlin
@Entity
class Team {
    @Id
    var id: Long? = null

    var name: String? = null

    // @MapKey(name)：键取自目标实体的持久属性
    @OneToMany(mappedBy = "team")
    @MapKey(name = "name")
    var membersByName: MutableMap<String, Employee> = mutableMapOf()

    // 基本类型键：@MapKeyColumn 只影响 JPA 列名，不改变文档键格式
    @ManyToMany
    @MapKeyColumn(name = "member_id")
    var membersById: MutableMap<Long, Employee> = mutableMapOf()

    // Map 形式的 @ElementCollection：标量/嵌入对象值
    @ElementCollection
    @MapKeyColumn(name = "attr_key")
    var attributes: MutableMap<String, String> = mutableMapOf()

    @ElementCollection
    @MapKeyEnumerated(EnumType.STRING)
    var statusNotes: MutableMap<Status, String> = mutableMapOf()
}
```

```jsonc
{
  "membersByName": {
    "alice": { "id": 1, "name": "alice" },
    "bob": { "id": 2, "name": "bob" }
  },
  "membersById": {
    "10": { "id": 10, "name": null },
    "20": { "id": 20, "name": null }
  },
  "attributes": { "color": "red" },
  "statusNotes": { "ACTIVE": "活跃" }
}
```

规则：

- Map 键与字段名互转规则由 `JpaMapKeys` 公开（与 Mapper 内部完全一致）：
  - `String`/`CharSequence`/`Char` 原样；`boolean` 为 `true`/`false`；
  - 数字为精确十进制文本（`NumberNode.raw`）；
  - 枚举默认 `ORDINAL`（`@MapKeyEnumerated` 可改为 `STRING`）；
  - `Date`/`Calendar` 按 `@MapKeyTemporal`（未标注按目标类型的自然精度），
    `java.time` 类型为 ISO-8601，`UUID` 为 `toString()`；
  - `byte[]`、嵌入对象作为 Map 键不支持（抛 `JpaConfigException`）；
  - 实体作为 Map 键使用 `@MapKeyJoinColumn`/`@MapKeyJoinColumns`（见下文）。
- 键类型解析顺序：`Map<K, V>` 的泛型 `K` → `@MapKeyClass` → `@MapKey`（且泛型键为
  `Object`/`Any`）时按目标实体的 `@Id` 或 `@MapKey(name)` 指定的属性类型；
  仍无法确定时抛 `JpaConfigException`。
- 写出时两个键字符串化后相同（例如 `Map<Any, ...>` 中的 `Long` 键 `1` 与
  `String` 键 `"1"`）会抛 `JpaMappingException`；读入时两个文档字段名解析成同一个
  键同样抛异常；非法键文本（如 `Long` 键的 `"abc"`）抛 `JpaMappingException`。
- 空 Map 写出为空对象；null Map 值按 `nullPolicy`；Map 值中的 null 元素写出
  `NullNode`、读回 null。
- 深度/环检测与集合关联一致：Map 关联值按 `associationDepth` 展开，Map 元素集合
  不消耗关联深度。
- `@MapKey(name = "...")` 指定的目标实体属性仍按普通属性写在值对象中；读入时
  **不会**根据 Map 键回填值对象里缺失的该属性（文档值对象自身内容优先）。

### 实体作为 Map 键（@MapKeyJoinColumn）

`Map<K, V>` 的 `K` 是实体时（JPA 用 `@MapKeyJoinColumn`/`@MapKeyJoinColumns` 映射
连接列），文档字段名是**键实体的标识符文本**，读入时按文本重建 detach 键实体：

```kotlin
@Entity
class Workload {
    @Id
    var id: Long? = null

    // 单主键实体键：文档键 "5"、"6" 即成员主键
    @ManyToMany
    @MapKeyJoinColumn(name = "member_id")
    var projectByMember: MutableMap<Member, Project> = mutableMapOf()

    // @EmbeddedId 复合主键实体键：文档键 "orderNo=A-1&region=EU"
    @ManyToMany
    @MapKeyJoinColumns(
        value = [
            MapKeyJoinColumn(name = "order_no", referencedColumnName = "order_no"),
            MapKeyJoinColumn(name = "region_code", referencedColumnName = "region_code"),
        ],
    )
    var projectByOrder: MutableMap<Order, Project> = mutableMapOf()
}
```

- 单基本主键：字段名就是该主键值的 `JpaMapKeys` 文本（String 原样、数字精确十进制、
  UUID/日期时间 ISO 等）；
- `@EmbeddedId`/`@IdClass` 复合主键：字段名为 `属性名=转义值` 以 `&` 连接的规范文本，
  按主键属性的声明顺序输出；值中的 `%`、`&`、`=` 分别转义为 `%25`、`%26`、`%3D`；
- 读入时只还原标识符属性（detach 键实体），不读取其他字段；复合键组件必须齐全，
  未知/重复/缺失组件或键文本无法解析时抛 `JpaMappingException`；
- 读入后的键按键文本的**规范形式**查重（例如 `"01"` 与 `"1"` 都规范为 `"1"` 时抛
  键冲突）；
- 键实体类型必须有 `@Id`/`@EmbeddedId`、可实例化的无参构造器，主键属性可写；
  键实体为抽象类/接口或复合主键组件不是基本属性时抛 `JpaConfigException`；
- 实体键文本互转也作为公共 API 暴露：`mapper.entityKeyText(keyEntity, keyType)` /
  `mapper.entityKeyFromText(text, keyType)`（供 `jpa-hibernate` 等扩展复用）。

## 属性转换器（@Convert / autoApply）

`@Convert` 显式指定的转换器，以及通过配置注册的 autoApply 转换器都会参与映射；
**文档中出现的是数据库列值**（`convertToDatabaseColumn` 的结果），读入时先按
数据库列类型解析再 `convertToEntityAttribute`。

```kotlin
class Money(val amount: BigDecimal, val currency: String)

class MoneyConverter : AttributeConverter<Money, String> {
    // 与 JPA 提供者一致：null 也会传入转换器，转换器必须能处理 null
    override fun convertToDatabaseColumn(attribute: Money?): String? =
        attribute?.let { "${it.amount.toPlainString()} ${it.currency}" }

    override fun convertToEntityAttribute(dbData: String?): Money? =
        dbData?.let { Money(BigDecimal(it.substringBefore(' ')), it.substringAfter(' ')) }
}

@Entity
class Order {
    @Id
    var id: Long? = null

    @Convert(converter = MoneyConverter::class)
    var price: Money? = null
}
```

```kotlin
val node = mapper.toNode(order)
node["price"]!!.asString()          // "12.50 USD"（数据库值，而不是 Money 对象）
val copy = mapper.fromNode(node, Order::class.java)
```

autoApply（JPA 默认规则）需要显式注册转换器类——本模块不做 classpath 扫描：

```kotlin
val mapper = JpaDocumentMapper(
    JpaConfig.builder()
        .autoApplyConverter(EmailConverter::class.java)   // AttributeConverter<Email, String>
        .build(),
)
```

- 按 `AttributeConverter<X, Y>` 的属性类型 X 匹配（基本类型与包装类型等价）；
  `@Converter(autoApply = true)` 同样需要注册；
- `@Convert` 显式转换器优先于 autoApply；`@Convert(disableConversion = true)` 关闭；
- 按 JPA 规范，`@Id`/`@EmbeddedId`、关联属性、显式 `@Enumerated`/`@Temporal`/
  `@Version` 属性不参与 autoApply；
- 同一属性类型注册多个 autoApply 转换器会抛 `JpaConfigException`；
- `@Convert` 也可以标注在 `@ElementCollection`（基本元素）上，对每个元素生效；
- 转换器类必须实现 `AttributeConverter`、有可访问的无参构造器、泛型参数可解析，
  否则 `JpaDocumentMapper` 构造时抛 `JpaConfigException`；
- 转换器运行时抛出的异常包装为 `JpaMappingException`（cause 保留）；
  转换器返回 null 时写出 `NullNode`；null 值在两个方向都会传入转换器；
- 数据库类型 Y 上的 `@Enumerated`/`@Temporal` 仍生效（例如转换器输出枚举/日期）。

### 嵌套属性路径：@Convert(attributeName = "...")

`@Embedded`/`@EmbeddedId` 属性内部的**基本属性**可用点分路径覆盖转换器：

```kotlin
@Embeddable
class Address {
    var city: String? = null

    var zipCode: String? = null
}

@Entity
class Customer {
    @Id
    var id: Long? = null

    @Embedded
    @Convert(attributeName = "zipCode", converter = ZipConverter::class)   // 相对该 @Embedded
    var address: Address? = null
}

@Entity
@Convert(attributeName = "address.city", converter = CityConverter::class)  // 类级：相对实体根
class ClassLevelCustomer {
    @Id
    var id: Long? = null

    @Embedded
    var address: Address? = null
}
```

- 路径每段是 Java/Kotlin **属性名**（不是列名/节点名）；中间段必须是 `@Embedded`，
  最后一段必须是基本属性（不能是关联/嵌入对象本身）；
- 支持多级点分路径，例如 `@Convert(attributeName = "geo.lat", ...)` 标注在 `@Embedded`
  属性上，或类级 `@Convert(attributeName = "address.geo.lat", ...)`；
- 优先级（高 → 低）：路径覆盖 > 嵌入类属性自身的 `@Convert` > autoApply；
  外层路径覆盖优先于嵌入类自身的类级/属性级转换器声明；
- `@Convert(attributeName = "...", disableConversion = true)` 可对指定路径关闭转换
  （包括嵌入类属性自身的转换器与 autoApply）；
- null 语义与普通转换器一致：转换器收到 null、可返回 null（写出 `NullNode`）；
- 失效路径/中间段不是 `@Embedded`/末段不是基本属性/转换器类型与目标属性不匹配
  都会在元数据解析时抛 `JpaConfigException`；
- 暂不支持指向 `@ElementCollection` 元素内部、关联对象内部的 `attributeName` 路径
  （会抛 `JpaConfigException`；请把转换器标注在嵌入类/元素类的基本属性上）。

### 关系属性上的 @Convert

显式 `@Convert` 标注在关系属性（`@ManyToOne`/`@OneToOne`/`@OneToMany`/`@ManyToMany`）
上时，语义是：**整个关联值按基本属性转换，不展开关联**（显式转换优先于关联展开）：

```kotlin
class EmployeeRefConverter : AttributeConverter<Employee, Long> {
    override fun convertToDatabaseColumn(attribute: Employee?): Long? = attribute?.id

    override fun convertToEntityAttribute(dbData: Long?): Employee? =
        dbData?.let { Employee().apply { id = it } }
}

@Entity
class Review {
    @Id
    var id: Long? = null

    @ManyToOne
    @Convert(converter = EmployeeRefConverter::class)
    var reviewer: Employee? = null        // 文档节点：{"reviewer": 5}（数字，而不是嵌套对象）
}
```

- 写出时 `convertToDatabaseColumn` 的结果按数据库类型写为标量节点；读入时反向转换后
  赋给关联属性（通常是只带标识符的 detach 引用，由调用方按需加载）；
- 转换器泛型 `AttributeConverter<关联属性声明类型, Y>` 必须与属性声明类型一致，否则
  抛 `JpaConfigException`；
- 关系转换不受 `associationDepth` 限制（它不展开关联）；null 关联同样会传入转换器；
- autoApply 不作用于关系属性（JPA 规范），只认显式 `@Convert`；
- 集合/Map 关系属性上的显式 `@Convert` 同样把整个集合/Map 值作为一个基本值转换
  （转换器的实体属性类型需与集合/Map 的声明类型一致），不会逐个元素展开；
- 关系转换**只影响文档树表示**：JPA 规范不转换关系属性，数据库侧仍由 JPA 提供者按
  普通关联/外键映射处理，因此持久化时转换器的数据库类型需与关联列兼容。

## 为什么不做 classpath 扫描

**本模块不做任何 classpath/package 扫描**：autoApply 转换器与多态子类始终通过
`JpaConfig.builder().autoApplyConverter(...)` / `.entityType(...)` 显式注册。这是
刻意选择，原因：

1. 零第三方依赖 + JDK 8 主字节码：`ClassLoader.getResources("pkg")` 只返回
   目录/jar 根 URL，需要自行处理目录列举、jar 条目、`jar:`/`file:` 等 URL 协议、
   fat jar / 自定义 ClassLoader；JDK 8 没有模块系统 API 可枚举模块内类。
2. JPMS：本模块有显式 `module-info`；module path 上 `ClassLoader#getResources`
   不保证能看到命名模块内的类，需要 JDK 9+ 的 `ModuleReader`，与 JDK 8 兼容目标冲突。
3. 确定性与成本：扫描需要额外定义类初始化副作用、重复类、fail-fast/容错、启动耗时
   等策略；而 Hibernate/JPA 提供者在引导期已有自己的 `@Converter(autoApply=true)`
   / `@Entity` 发现机制，本模块再扫描一遍容易与提供者不一致。
4. 线程安全/关闭语义：注册配置当前是构造期不可变快照；若引入延迟扫描，需要处理
   类可见性、URLClassLoader 关闭后缓存失效、并发注册等额外状态。

推荐在应用/框架侧完成扫描后显式注册（本模块无需新增依赖）：

```kotlin
// 示例：用 Spring 的类路径扫描收集转换器，再交给 JpaConfig
val scanner = ClassPathScanningCandidateComponentProvider(false)
scanner.addIncludeFilter(AssignableTypeFilter(AttributeConverter::class.java))
val converters = scanner.findCandidateComponents("com.example")
    .mapNotNull { it.beanClassName }
    .map { Class.forName(it) }
val config = JpaConfig.builder()
    .apply { converters.forEach { autoApplyConverter(it) } }
    .build()
```

ClassGraph、Reflections 或框架自己的扫描结果同理；扫描职责由调用方承担，本模块
保持确定性与 JDK 8/JPMS 兼容。持久化到数据库时，Hibernate `SessionFactory` 仍需
注册同一批转换器（`MetadataBuilder.applyAttributeConverter`），扫描无法替代持久化提供方的注册。

## 继承与多态（@Inheritance）

类层次声明 `@Inheritance` / `@DiscriminatorColumn` 时，文档对象额外写出
discriminator 字段，读入时按它选择具体子类：

```kotlin
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "animal_kind")
abstract class Animal {
    @Id
    var id: Long? = null

    var name: String? = null
}

@Entity
@DiscriminatorValue("cat")
class Cat : Animal() {
    var lives: Int = 9
}

@Entity
@DiscriminatorValue("dog")
class Dog : Animal() {
    var barkVolume: Int = 0
}

@Entity
class Zoo {
    @Id
    var id: Long? = null

    @OneToMany
    var animals: MutableList<Animal> = mutableListOf()   // 关联中同样多态
}

val mapper = JpaDocumentMapper(
    JpaConfig.builder()
        .entityType(Cat::class.java)   // 读入 Animal 文档时必须能解析的子类
        .entityType(Dog::class.java)
        .build(),
)

val node = mapper.toNode(Cat().apply { id = 1; name = "tom"; lives = 7 })
// { "animal_kind": "cat", "id": 1, "name": "tom", "lives": 7 }

val restored = mapper.fromNode(node, Animal::class.java)   // Cat 实例
```

- 写出：按**实际类型**写出字段（父类字段在前、子类字段在后）与 discriminator；
  discriminator 字段位于对象最前，字段名为列名经命名策略转换（默认 `DTYPE`）；
- discriminator 值：`@DiscriminatorValue` 优先；STRING/CHAR 未标注时使用实体名
  （`@Entity(name)` 或简单类名）；INTEGER 必须显式标注，写出为数字节点，
  缺失时抛 `JpaConfigException`；
- 读取：`fromNode(node, 父类)` 按 discriminator 查找已注册子类；未知值抛
  `JpaMappingException`（消息列出已知值；没有任何注册子类时提示使用
  `JpaConfig.builder().entityType(...)`）；
- 没有 discriminator 字段时回退声明类型；声明类型是抽象类/接口时抛
  `JpaMappingException`（提示缺少 discriminator 字段）；
- 读入同时接受 discriminator 的节点字段名与原始列名；两者同时出现视为重复别名抛异常；
- `@Inheritance` 的 `SINGLE_TABLE`/`JOINED`/`TABLE_PER_CLASS` 只影响 JPA 持久化，
  **文档树表示完全一致**；`JpaDocumentMapper.discriminatorValue` /
  `discriminatorFieldName` 可用于查询元数据；
- 写方向不需要注册子类；只有按父类读入时需要。

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
    // 节点类型不匹配、未知属性、非空校验、枚举/日期/转换器转换失败、
    // Map 键冲突/非法、未知 discriminator、循环引用、无法实例化等
} catch (e: JpaConfigException) {
    // 元数据/配置非法：重复字段名、不支持的关联类型、集合关联缺少元素类型、
    // 转换器类型不匹配、autoApply 转换器重复、非法 Map 键类型/注解等
}
```

两者都继承 `JpaException`（继承 `DocumentException`），可以统一按 `DocumentException` 捕获。
反射/构造函数抛出的底层异常作为原因保留。

## Hibernate 兼容说明

- 只读取 JPA 注解，不需要 Hibernate 在 classpath 上；在 Hibernate 实体上直接使用；
- 不管理 `Session`、`EntityManager`、事务、脏检查、级联与懒加载；
- Hibernate 代理类（子类）会沿父类找到 `@Entity` 元数据，按实体字段映射；但访问代理的
  未初始化字段可能触发 `LazyInitializationException`，请先初始化需要的字段再调用 `toNode`；
- 集合属性建议用已加载的 `ArrayList`/`LinkedHashSet` 等普通集合（Hibernate 的
  `PersistentBag`/`PersistentMap` 等实现也按 `Collection`/`Map` 处理，但写出顺序/身份
  受其实现影响）；
- 实体需要无参构造器（属性和字段都可以是 `private`，反射会打开访问权限）；
- 多态读入需要在 `JpaConfig` 中注册子类；autoApply 转换器同样需要注册（与 Hibernate
  自身需要在 `SessionFactory` 注册转换器的要求一致）。

## 限制与注意事项

- 单向/双向关联不会自动保持一致，也不做级联加载：写出的是当前内存中的对象图；
- 不处理数据库表结构/级联/懒加载等数据库细节，只映射 JPA 注解描述的 Java 属性值；
- autoApply 转换器、多态子类必须显式注册（模块不做 classpath 扫描，见“为什么不做
  classpath 扫描”）；discriminator 重复值/INTEGER 缺省值会抛配置异常；
- `@Convert(attributeName = ...)` 只支持 `@Embedded`/`@EmbeddedId` 内部基本属性的
  点分路径；不支持指向关联对象/`@ElementCollection` 元素内部的路径（抛
  `JpaConfigException`）；关系属性上的显式 `@Convert` 表示整个关联值按基本属性转换；
- Map 键支持基本类型与 `@MapKeyJoinColumn` 实体键；键文本冲突/无法解析会抛异常；
  `@MapKey(name)` 指定属性不会根据键回填；实体键类型必须是可实例化的具体实体，
  复合主键组件必须是基本属性；
- `@ElementCollection` 元素类型为标量或普通对象；
- 不支持 `@Temporal` 之外的日期格式化（如 `@JsonFormat` 等），需要自定义格式请先用
  serialization 模块的 `DocumentDateTimeFormat` 或先转换成字符串；
- `fromNode` 不做 Bean Validation（需要时先用 `validation` 模块校验）；
- 循环引用直接抛异常，不写 `$ref`；需要引用语法请使用 serialization 的 Mapper 配置；
- 映射结果只保证与当前 `JpaConfig` 对称：改变 `associationDepth`/`nullPolicy` 后，
  旧文档中超出深度或缺失的字段会被忽略/保持默认值，不会报错。
- 转换器在 null 上也会被调用（与 Hibernate 一致）：转换器实现必须自行处理 null；
  Kotlin 非空参数签名会在 null 时抛出并被包装为 `JpaMappingException`。

## 构建

```bash
# 主代码按 JDK 8 目标编译；测试 JVM 使用 JDK 17（jakarta.persistence-api 3.1.0
# 的 class 文件版本为 55 / Java 11+，JDK 8 无法在运行期加载其注解类）
gradle :jpa:ktlintFormat :jpa:test
```

测试不连接任何数据库，实体只用于注解/反射映射。

文档：[根 README](../README.md) · [serialization](../serialization/README.md) · [core](../core/README.md) · [贡献指南](../CONTRIBUTING.md)
