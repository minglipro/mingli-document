# JPA / Hibernate ORM

## 坐标

```kotlin
implementation("com.mingliqiye.document:jpa:2.3.0")
```

## 能力

```text
JPA 实体 <-> Node 树
@Column / @Id / @Embedded / @Enumerated / @Temporal
@OneToOne / @ManyToOne / @OneToMany / @ManyToMany
@ElementCollection / @Transient / @MappedSuperclass
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
| `@OneToMany` / `@ManyToMany` | 展开 + 深度/环检测 |
| `@ElementCollection` | 是 |
| `@Access` | FIELD / PROPERTY |
| `@MappedSuperclass` | 是 |

## Hibernate 兼容

```text
读取 JPA 注解
不管理 Session / EntityManager / 事务 / 懒加载
Hibernate 代理按父类 @Entity 元数据展开
建议在事务内先初始化需要序列化的关联
```

## 限制

```text
不支持 @Inheritance / @DiscriminatorColumn 多态
不支持 @Convert / AttributeConverter
不支持 Map 关联、@AttributeOverride、@Formula
@JoinColumn 列名不参与节点键
循环引用抛异常而非 $ref
日期时间输出 ISO 字符串
```
