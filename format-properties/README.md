# format-properties

Java Properties 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:properties:2.3.0")
```

## 主要 API

```text
PropertiesMapper
PropertiesReader
PropertiesWriter
```

## 示例

```kotlin
import com.mingliqiye.document.format.properties.PropertiesMapper

val mapper = PropertiesMapper()
val text = """
    name=小于
    age=18
""".trimIndent()

val user = mapper.parse(text, User::class.java)
val output = mapper.format(user)
```

## 路径规则

```text
user.name=小于
user.tags[0]=a
```

表示：

```json
{
  "user": {
    "name": "小于",
    "tags": ["a"]
  }
}
```

## 深度限制（maxDepth）

key 路径的段数决定对象/数组嵌套深度（根对象深度为 `1`，`a.b.c=1` 最深层级为 `4`）。
默认安全上限为 512，避免恶意超深路径导致递归构建 StackOverflow：

```kotlin
import com.mingliqiye.document.format.properties.PropertiesMapper

// 通过 MapperBuilder（默认 PropertiesMapper.DEFAULT_MAX_DEPTH = 512）
val mapper = PropertiesMapper.builder().maxDepth(64).build()

// 通过构造器
val mapper2 = PropertiesMapper(config, maxDepth = 64)

// 独立读取器
val node = com.mingliqiye.document.format.properties.PropertiesReader(source, maxDepth = 64).read()
```

- 超限时 Node / Token 路径都抛出
  `com.mingliqiye.document.format.properties.exception.PropertiesParseException`；
- 通用 `DocumentMapperConfig.maxNodeDepth` 保持 Node 限制语义：超限抛
  `com.mingliqiye.document.serialization.exception.DocumentSerializationException`；
- `PropertiesReader` / `PropertiesTokenReader` 的 `maxDepth` 显式传 `0` 或负数表示关闭保护。

## 特性

- `.` 表示对象路径
- `[index]` 表示数组下标
- 扁平文本与 Node 树互转
- 文件/流读取与保存
- Token 直读 Bean
- 原生 Properties Token Reader
- 解析深度限制（默认 512，可配置）

## 构建

```bash
gradle :format-properties:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
