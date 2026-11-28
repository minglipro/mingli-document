# JPA / Hibernate ORM

## 坐标

```kotlin
implementation("com.mingliqiye.document:jpa:2.3.0")
```

## 能力

```text
JPA 实体 <-> Node 树
@Column / @Id / @Embedded / @Enumerated / @Temporal
@OneToOne / @ManyToOne / @OneToMany / @ManyToMany（含 Map<K, V>）
@ElementCollection（含 Map）/ @Transient / @MappedSuperclass
@Convert / AttributeConverter / autoApply 转换器
@Inheritance / @DiscriminatorColumn / @DiscriminatorValue 多态
关联深度限制、环检测
```

不依赖 Hibernate 运行期，只读取 `jakarta.persistence` 注解。

## 快速开始

```kotlin
@Entity
@Table(name = "users")
data class User(
    @Id
    @Column(name = "user_id")
    val id: Long = 0,
    @Column(name = "user_name", nullable = false)
    val name: String = "",
    @Embedded
    val address: Address? = null,
)
```

```kotlin
val mapper = JpaDocumentMapper()

val node = mapper.toNode(user)
val restored = mapper.fromNode(node, User::class.java)
```

配合格式 Mapper：

```kotlin
val json = JsonMapper().format(mapper.toNode(user))
```

## 配置

```kotlin
JpaDocumentMapper(
    JpaConfig.builder()
        .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .associationDepth(2)
        .nullPolicy(JpaNullPolicy.OMIT)
        .unknownPropertyPolicy(JpaUnknownPropertyPolicy.IGNORE)
        .build(),
)
```

## 注解支持

| 注解 | 支持 |
|---|---|
| `@Entity` / `@Table` | 是 |
| `@Id` / `@EmbeddedId` | 是 |
| `@Column` | 列名 / nullable |
| `@Embedded` | 是 |
| `@Enumerated` | STRING / ORDINAL |
| `@Temporal` | DATE / TIME / TIMESTAMP |
| `@Transient` | 默认排除 |
| `@OneToOne` / `@ManyToOne` | 展开 |
| `@OneToMany` / `@ManyToMany` | 数组；`Map<K, V>` 为对象（字符串化键） |
| `@ElementCollection` | 数组；Map 形式为对象 |
| `@MapKey` / `@MapKeyColumn` / `@MapKeyEnumerated` / `@MapKeyTemporal` | Map 键语义/键文本 |
| `@Convert` / `@Converts` / autoApply `AttributeConverter` | 文档为数据库列值 |
| `@Inheritance` / `@DiscriminatorColumn` / `@DiscriminatorValue` | discriminator + 多态读入 |
| `@Access` | FIELD / PROPERTY |
| `@MappedSuperclass` | 是 |

## Hibernate 兼容

```text
读取 JPA 注解
不管理 Session / EntityManager / 事务 / 懒加载
Hibernate 代理按父类 @Entity 元数据展开
建议在事务内先初始化需要序列化的关联
```

## 高级映射

```kotlin
val mapper = JpaDocumentMapper(
    JpaConfig.builder()
        .autoApplyConverter(EmailConverter::class.java)  // autoApply 转换器需显式注册
        .entityType(Cat::class.java)                     // 多态读入候选子类
        .entityType(Dog::class.java)
        .build(),
)
```

- `@Convert`：文档中出现数据库列值；null 也会传入转换器（与 Hibernate 一致）；
- Map 关联：文档中为对象，键规则由 `JpaMapKeys` 统一（枚举/数字/日期等）；
- 多态：写出实际子类字段 + discriminator；读入按 discriminator 选择已注册子类，
  未知值抛异常、无 discriminator 时回退声明类型（抽象类报错）。

## 限制

```text
不扫描 classpath：autoApply 转换器与多态子类需注册
@Convert(attributeName=...) / 关系属性上的 @Convert 不支持
@MapKeyJoinColumn（实体作为 Map 键）不支持
@JoinColumn 列名不参与节点键
循环引用抛异常而非 $ref
日期时间输出 ISO 字符串
```
