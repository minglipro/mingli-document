# JDBC 集成

## 坐标

```kotlin
implementation("com.mingliqiye.document:jdbc:2.3.0")
```

## 读取 ResultSet

```kotlin
val reader = JdbcDocumentReader()
val nodes = reader.read(resultSet)
```

映射为 Bean：

```kotlin
val users = reader.read(resultSet, JsonMapper(), User::class.java)
```

## 自定义行映射

```kotlin
val mapper = DocumentRowMapper.of { row ->
    User(
        id = row["id"] as Long,
        name = row["name"] as String,
    )
}
```

## 写入 PreparedStatement

```kotlin
val writer = JdbcDocumentWriter()

writer.bind(statement, user)
writer.bindNamed(statement, user)
```

生成 SQL：

```kotlin
val insertSql = writer.insertStatement("users", listOf("id", "name"))
val updateSql = writer.updateStatement("users", listOf("id"), listOf("name"))
```

## 命名参数

```sql
INSERT INTO users (id, name) VALUES (:id, :name)
```

```kotlin
writer.bindNamed(statement, mapOf("id" to 1, "name" to "小于"))
```

## 类型映射

| JDBC | Node |
|---|---|
| 数值 | NumberNode |
| 字符串 | StringNode |
| 布尔 | BooleanNode |
| 日期/时间 | ISO-8601 StringNode |
| byte[] / BLOB | BinaryNode |
| NULL | NullNode |

## 配置

```kotlin
JdbcConfig.builder()
    .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .nullPolicy(JdbcNullPolicy.OMIT)
    .build()
```

## 限制

```text
不管理 Connection / 事务 / 结果集关闭
只支持前向 ResultSet
表名/列名不做参数化
嵌套对象不能作为单个 JDBC 参数
复杂 JDBC 类型需要 ColumnConverter
```
