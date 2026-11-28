# jdbc

JDBC 数据库集成模块：`ResultSet` ⇄ `Node`/Bean，Bean/Map/Node → `PreparedStatement` 参数绑定。

模块复用 `core` 的 [Node](../core/README.md) 与 `serialization` 的 `DocumentMapper`：
读取时把查询结果转换为与格式无关的文档树（再交给任意格式 Mapper 反序列化为 Bean），
写入时把 Bean/Map/Node 展开为 `INSERT`/`UPDATE` 的顺序参数或命名参数，并完成 JDBC
类型转换。

不绑定任何数据库驱动；测试也完全不需要数据库（用 `java.lang.reflect.Proxy` 模拟
`ResultSet`/`PreparedStatement`）。

## Maven

```kotlin
implementation("com.mingliqiye.document:jdbc:2.3.0")
```

通常还需要一个格式 Mapper 用于 Bean 转换，例如：

```kotlin
implementation("com.mingliqiye.document.format:json:2.3.0")
```

## 主要 API

```text
JdbcDocumentReader       ResultSet -> List<ObjectNode> / List<Bean>
JdbcDocumentWriter       Bean/Map/Node -> PreparedStatement 参数绑定
DocumentRowMapper        行映射器（Node -> Bean / Map / 自定义类型）
JdbcConfig               列名策略、NULL 策略、类型映射、自定义列转换器
JdbcConfigBuilder        配置构建器
ColumnConverter          自定义列转换器（ResultSet 原始值 -> Node）
JdbcColumn               列元数据
JdbcStatement            SQL 文本 + 参数顺序
JdbcNullPolicy           INCLUDE / OMIT
```

异常位于 `com.mingliqiye.document.jdbc.exception`：
`JdbcException`（继承 `DocumentException`）、`JdbcReadException`、`JdbcWriteException`。

## 读取

### 读取为文档树

```kotlin
import com.mingliqiye.document.jdbc.JdbcDocumentReader

val reader = JdbcDocumentReader()
connection.createStatement().use { statement ->
    statement.executeQuery("SELECT id, user_name, active, created FROM users").use { resultSet ->
        val rows = reader.readNodes(resultSet) // List<ObjectNode>
        for (row in rows) {
            println(row["id"]?.asNumber()?.toInt())
            println(row["user_name"]?.asString())
        }
    }
}
```

### 读取为 Bean

`DocumentMapper` 可以是 `JsonMapper`、`YamlMapper` 等任意实现；`jdbc` 模块只依赖
`serialization`，不依赖具体格式。

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.jdbc.JdbcDocumentReader
import com.mingliqiye.document.jdbc.JdbcConfig
import com.mingliqiye.document.serialization.PropertyNamingStrategy

val config = JdbcConfig.builder()
    .namingStrategy(PropertyNamingStrategy.SNAKE_CASE) // USER_NAME -> userName
    .build()
val reader = JdbcDocumentReader(config)
val mapper = JsonMapper()

statement.executeQuery("SELECT id, user_name, active FROM users").use { resultSet ->
    val users: List<User> = reader.read(resultSet, mapper, User::class.java)
}
```

也支持泛型类型引用与单行读取：

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference

val users = reader.read(resultSet, mapper, DocumentTypeReference.listType(User::class.java))

resultSet.next()
val row = reader.readRow(resultSet)          // ObjectNode，当前行
val user = reader.mapRow(resultSet, mapper, User::class.java) // 当前行转 Bean
```

### 自定义行映射

`DocumentRowMapper` 是函数式接口，可以完全控制单行转换：

```kotlin
import com.mingliqiye.document.jdbc.DocumentRowMapper

val mapper = DocumentRowMapper<User> { row ->
    User(
        id = row["id"]!!.asNumber().toInt(),
        name = row["name"]?.asString(),
    )
}
val users = reader.read(resultSet, mapper)
```

内置工厂：

```kotlin
DocumentRowMapper.nodes()                          // ObjectNode -> ObjectNode
DocumentRowMapper.maps()                           // ObjectNode -> Map<String, Any?>
DocumentRowMapper.of(User::class.java, jsonMapper) // ObjectNode -> Bean
```

### 自定义列转换器

列类型推断不满足需求时，可按列标签或列下标注册 `ColumnConverter`（列下标优先）：

```kotlin
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jdbc.ColumnConverter
import com.mingliqiye.document.jdbc.JdbcConfig

// 例如把 JSON 文本列转换为对象节点
val jsonColumn = ColumnConverter { value, column ->
    when (column.label) {
        "payload" -> value?.let { ObjectNode.of("raw" to StringNode.of(it.toString())) }
        else -> null // 返回 null 表示回退内置转换
    }
}
val config = JdbcConfig.builder()
    .columnConverter("payload", jsonColumn)
    .build()
```

转换器返回 `null` 时回退内置转换；SQL NULL 需要自定义结果时返回目标节点（如
`NullNode.INSTANCE`）。转换器抛出 `DocumentException` 时原样传播，其它异常包装为
`JdbcReadException`。

## 写入

写入器把对象展开为对象节点，再按参数名绑定 `PreparedStatement`：

- 顺序参数：调用方给出与 `?` 一致的参数列表；
- 命名参数：用 `namedStatement` 解析 SQL 中的 `:name` 占位符；
- `insertStatement` / `updateStatement`：直接生成带 `?` 的 SQL 与参数顺序。

写入器不执行 SQL、不关闭 `PreparedStatement`，由调用方负责。

### 顺序参数

```kotlin
import com.mingliqiye.document.jdbc.JdbcDocumentWriter

val writer = JdbcDocumentWriter() // 写 Map / ObjectNode 时不需要 DocumentMapper
val value = mapOf("id" to 1, "name" to "alice", "active" to true)

val plan = writer.insertStatement("users", value)
// INSERT INTO users (id, name, active) VALUES (?, ?, ?)
connection.prepareStatement(plan.sql).use { statement ->
    writer.bind(statement, plan, value)
    statement.executeUpdate()
}
```

`bind(statement, parameters, value)` 可以按任意参数顺序绑定：

```kotlin
writer.bind(statement, listOf("name", "id"), value) // 对应 SQL: ... (?, ?)
```

### 命名参数

```kotlin
val sql = "INSERT INTO users (id, name) VALUES (:id, :name)"
val plan = writer.namedStatement(sql) // parameters == ["id", "name"]
connection.prepareStatement(sql).use { statement ->
    writer.bindNamed(statement, sql, mapOf("id" to 1, "name" to "alice"))
    statement.executeUpdate()
}
```

参数解析会跳过单引号字符串、双引号/反引号标识符、`--`/`/* */` 注释与 PostgreSQL
的 `::` 类型转换；不支持 PostgreSQL 的 dollar-quoted 字符串，也不支持 `:name` 与 `?`
混用。

### 写入 Bean

构造时传入 `DocumentMapper` 即可写 Bean：

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.jdbc.JdbcDocumentWriter

val writer = JdbcDocumentWriter(JsonMapper())
val plan = writer.insertStatement("users", user)
connection.prepareStatement(plan.sql).use { statement ->
    writer.bind(statement, plan, user)
    statement.executeUpdate()
}
```

也可以传入自己的配置：`JdbcDocumentWriter(config)` 或
`JdbcDocumentWriter(jsonMapper, config)`（需要 Bean 时传 Mapper）。

### UPDATE

```kotlin
// SET 列默认取“对象字段 - WHERE 列”，WHERE 列参数排在 SET 列之后
val plan = writer.updateStatement("users", user, whereColumns = listOf("id"))
// UPDATE users SET name = ?, active = ? WHERE id = ?

// 也可显式指定 SET 列
val partial = writer.updateStatement("users", user, listOf("id"), setColumns = listOf("name"))
```

### 写入的输入类型

| 输入 | 要求 |
|---|---|
| `ObjectNode` | 直接按文档字段名取值 |
| `Map` | key 必须是 `String`；标量、日期/时间（ISO 字符串）、二进制、枚举、UUID、Map、集合、数组直接转换；其它对象需要 `DocumentMapper` |
| 其它对象（Bean） | 需要构造写入器时传入 `DocumentMapper`，Bean 被序列化为对象节点 |

## 配置

```kotlin
import com.mingliqiye.document.jdbc.JdbcConfig
import com.mingliqiye.document.jdbc.JdbcNullPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import java.sql.Types

val config = JdbcConfig.builder()
    .namingStrategy(PropertyNamingStrategy.SNAKE_CASE) // 双向：USER_NAME ⇄ userName
    .nullPolicy(JdbcNullPolicy.INCLUDE)                // 默认；OMIT 见下
    .columnMapping("ID_COL", "id")                     // 显式列名映射，优先于命名策略
    .columnConverter("payload", myConverter)           // 按列标签注册转换器
    .columnConverter(1, myConverter)                   // 按列下标注册（优先）
    .typeMapping(String::class.java, Types.CLOB)       // 写出时的 setObject 目标类型
    .defaultNullSqlType(Types.VARCHAR)                 // 驱动不支持 ParameterMetaData 时
    .build()
```

### 命名策略

`namingStrategy` 同时作用于两个方向：

- 读取：列名 → 文档字段名，按“拆分单词后转 lowerCamelCase”反向解析，`USER_NAME`、
  `user-name`、`UserName` 都得到 `userName`；`AS_IS` 时保持列名原样；
- 生成 `INSERT`/`UPDATE`：文档字段名 → 列名，按策略正向转换，`userName` 得到
  `user_name`。

显式 `columnMapping(列名, 字段名)` 在两个方向上都优先。列名与字段名完全一致时保持默认
`AS_IS` 即可。

### NULL 策略

- `JdbcNullPolicy.INCLUDE`（默认）：读取时 SQL NULL 列生成 `NullNode`；写出时以
  `setNull` 绑定 SQL NULL。
- `JdbcNullPolicy.OMIT`：读取时跳过 SQL NULL 列（对象中不出现该字段）；生成
  `INSERT`/`UPDATE` 时跳过值为 null 的字段。显式参数（`WHERE` 列或 `bind` 传入的
  参数）仍然必须绑定，遇到 null 抛 `JdbcWriteException`，因此显式绑定场景请使用
  `INCLUDE`。

### 类型映射

`typeMapping(Java 类型, java.sql.Types)` 用于写出非 null 参数：参数对象的 Java 类型命中
映射（含父类/接口）时，以映射类型调用 `setObject`，适合把 `String` 显式写成 `CLOB`
等场景。默认不配置。

`defaultNullSqlType` 是 null 参数在驱动不支持 `ParameterMetaData` 时的兜底 SQL 类型
（默认 `Types.NULL`）；多数驱动支持参数元数据，此时会使用真实的目标列类型。

## 类型推断

读取时按 `ResultSetMetaData` 的列类型与驱动返回的 Java 值推断：

| 列类型 / 运行时值 | Node |
|---|---|
| `BOOLEAN`、`BIT` | `BooleanNode`（驱动返回数字时按非 0 视为 true） |
| 数字类型（`TINYINT` … `DECIMAL`、`BigDecimal`、`BigInteger` 等） | `NumberNode`（精确 `BigDecimal`） |
| `DATE`、`TIME`、`TIMESTAMP`、`TIME/TIMESTAMP_WITH_TIMEZONE`、`java.time`、`java.util.Date` | `StringNode`（ISO-8601 文本） |
| `BINARY`、`VARBINARY`、`LONGVARBINARY`、`BLOB`、`byte[]`、`Blob` | `BinaryNode` |
| `CLOB`、`NCLOB`、`SQLXML`、`LONGVARCHAR`、`Clob` | `StringNode` |
| 字符串、枚举、`UUID`、其它可 `toString()` 的值 | `StringNode` |

SQL NULL 按 `JdbcNullPolicy` 生成 `NullNode` 或跳过；无法识别的 Java 类型回退为
`toString()` 字符串节点，需要特殊语义时注册 `ColumnConverter`。

写入时：

- `NullNode` → `setNull`（SQL 类型优先取 `ParameterMetaData`，否则
  `defaultNullSqlType`）；
- `BooleanNode` → `Boolean`；
- `NumberNode` → `BigDecimal`；目标参数为整数类型（`TINYINT`/`SMALLINT`/`INTEGER`/
  `BIGINT`）且可精确收窄时转换为对应 Java 整数；
- `StringNode` → `String`；目标参数为 `DATE`/`TIME`/`TIMESTAMP`/
  `TIMESTAMP_WITH_TIMEZONE` 时按 ISO-8601 解析为对应 JDBC/`java.time` 值；
- `BinaryNode` → `setBytes`；
- 对象/数组节点不能直接绑定，抛 `JdbcWriteException`。

## 异常

```kotlin
import com.mingliqiye.document.jdbc.exception.JdbcReadException
import com.mingliqiye.document.jdbc.exception.JdbcWriteException

try {
    reader.readNodes(resultSet)
} catch (e: JdbcReadException) {
    // ResultSet 访问失败、列转换失败、行映射失败（非 DocumentException）
}
try {
    writer.bind(statement, plan, value)
} catch (e: JdbcWriteException) {
    // 缺字段、复杂节点、nullPolicy 冲突、PreparedStatement 调用失败
}
```

- `JdbcReadException` / `JdbcWriteException` 继承 `JdbcException`，`JdbcException` 继承
  `DocumentException`，可用 `DocumentException` 统一捕获；
- `ResultSet`/`PreparedStatement` 的 `SQLException` 作为 cause 保留；
- 自定义转换器/行映射器抛出的 `DocumentException` 原样传播，其它异常包装后抛出。

## 限制与注意事项

- 不管理 `Connection`、事务、`ResultSet`/`PreparedStatement` 的关闭与游标滚动；调用方负责。
  读取只支持前向遍历，单次 `readNodes`/`read` 会消费剩余全部行。
- 生成的 SQL 中表名、列名来自调用方配置，不参与参数化；对不可信输入必须自行校验标识符。
- 日期/时间读取为 ISO-8601 字符串节点，Bean 属性请用 `String`，或为属性配置转换器
  （如 `@DocumentConverter`）后自行解析为 `java.time` 类型；写入 Bean 时 `java.time`
  属性会按普通 Bean 序列化为对象节点，无法绑定，建议 DTO 使用 `String`（`Map` 输入中的
  `java.time`/`java.util.Date` 值会转换为 ISO 字符串后再绑定）。
- 嵌套对象/数组节点不能作为单个 JDBC 参数绑定；需要存储 JSON 时先把值转换为字符串节点，
  或自定义读取端 `ColumnConverter`。
- 命名参数只支持 `:name` 形式，忽略引号、标识符、注释与 `::`；不支持 dollar-quoted
  字符串、`?`/`:name` 混用、同一 SQL 中重复占位符之外的复杂绑定。
- null 参数的目标 SQL 类型依赖驱动 `ParameterMetaData`；不支持时使用
  `defaultNullSqlType`（默认 `Types.NULL`），部分驱动要求具体类型（如
  `Types.VARCHAR`），请按驱动配置。
- 数字绑定在驱动不支持参数元数据时以 `BigDecimal` 传递；严格类型驱动可通过
  `typeMapping` 指定目标 SQL 类型。
- 与数据库无关的 SQL 数组、结构体等复杂 JDBC 类型不做结构化转换：默认回退为
  `toString()` 字符串节点，需要时注册 `ColumnConverter`。
- 读取器按列标签（无标签时退回列名）映射字段；同名列后出现者覆盖前者。

## 构建

```bash
# 主代码按 JDK 8 目标编译；测试不依赖任何数据库
gradle :jdbc:test
```

文档：[根 README](../README.md) · [serialization](../serialization/README.md) · [core](../core/README.md) · [贡献指南](../CONTRIBUTING.md)
