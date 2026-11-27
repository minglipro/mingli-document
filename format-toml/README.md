# format-toml

TOML 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:toml:1.4.0")
```

## 主要 API

```text
TomlMapper
TomlReader
TomlWriter
TomlConfig
```

## 示例

```kotlin
import com.mingliqiye.document.format.toml.TomlMapper

val mapper = TomlMapper()
val toml = """
    name = "小于"
    age = 18
""".trimIndent()

val user = mapper.parse(toml, User::class.java)
val text = mapper.format(user)
```

## 特性

- 标准 TOML 表与数组
- 嵌套表
- 内联表写出
- 文件/流读写
- Token 直读 Bean
- 原生 TOML Token Reader

## 限制

- TOML 日期时间类型映射为 ISO-8601 字符串
- TOML 没有 null 类型
- 根节点固定为对象

## 构建

```bash
gradle :format-toml:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
