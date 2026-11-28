# format-properties

Java Properties 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:properties:2.1.0")
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

## 特性

- `.` 表示对象路径
- `[index]` 表示数组下标
- 扁平文本与 Node 树互转
- 文件/流读取与保存
- Token 直读 Bean
- 原生 Properties Token Reader

## 构建

```bash
gradle :format-properties:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
