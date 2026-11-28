# format-yaml

YAML 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:yaml:2.0.0")
```

## 主要 API

```text
YamlMapper
YamlReader
YamlMultiDocumentReader
YamlConfig
```

## 示例

```kotlin
import com.mingliqiye.document.format.yaml.YamlMapper

val mapper = YamlMapper()
val yaml = """
    name: 小于
    age: 18
""".trimIndent()

val user = mapper.parse(yaml, User::class.java)
val text = mapper.format(user)
```

## 多文档

```kotlin
val nodes = YamlMapper().parseAllToNodes(text)
val result = YamlMapper().formatAll(nodes)
```

## 特性

- 单文档与多文档
- `!!binary` -> `BinaryNode`
- Token 直读 Bean
- SnakeYAML SafeConstructor
- 解析深度限制
- alias 扩张限制
- 原生 YAML Token Reader
- 文件/流读写

## 限制

- 非字符串 mapping key 支持有限
- 跨文档 anchor/alias 不保留
- timestamp 映射为字符串

## 构建

```bash
gradle :format-yaml:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
