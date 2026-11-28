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
YamlConfigBuilder
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

## 自定义 tag

默认使用 SnakeYAML `SafeConstructor`，未知 tag（如 `!foo`、`!env`）会抛出
`YamlParseException`。通过 `YamlConfig.builder().allowCustomTags(true)` 可以按节点
种类还原未知 tag：

- 标量 -> `StringNode`（保留原始标量文本，不做 `!!int` / `!!bool` 隐式解析）
- 序列 -> `ArrayNode`
- 映射 -> `ObjectNode`（与普通 YAML 映射一致，先执行重复 key 校验与 merge key 展开）

```kotlin
import com.mingliqiye.document.format.yaml.YamlConfig
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.format.yaml.YamlMultiDocumentReader
import com.mingliqiye.document.format.yaml.YamlReader

// 单文档 / 多文档 reader 直接传参
val node = YamlReader("value: !env HOME\n", allowCustomTags = true).read()
val documents = YamlMultiDocumentReader("a: !env HOME\n---\nb: !list [1, 2]\n", allowCustomTags = true).readAll()

// Mapper 通过 YamlConfig 同时作用于 Token 路径与 Node 回退路径
val mapper = YamlMapper(YamlConfig.builder().allowCustomTags(true).build())
val bean = mapper.parse("name: !env APP_NAME\n", User::class.java)
val all = mapper.parseAllToNodes("a: !env HOME\n---\nb: !list [1]\n")
```

支持本地 tag（`!foo`）与全局自定义 tag（`!!foo`、`!<tag:example.com,2024:foo>`）；
构造阶段只还原基础类型，不会实例化任意 Java 类型。anchor/alias/merge/深度限制
仍然生效，但以自定义 tag 节点为目标的递归 alias 会被拒绝（抛
`YamlParseException`）。

## 特性

- 单文档与多文档
- `!!binary` -> `BinaryNode`
- Token 直读 Bean
- SnakeYAML SafeConstructor
- 可选自定义 tag（`allowCustomTags`，默认关闭）
- 解析深度限制
- alias 扩张限制
- 原生 YAML Token Reader
- 文件/流读写

## 限制

- 非字符串 mapping key 支持有限
- 自定义 tag 只在读取侧生效：写出只根据 Node 类型输出标准 YAML，tag 信息不保留
- 自定义 tag 节点作为递归 alias 目标时不支持（抛 `YamlParseException`，不构建环）
- YAML 规范不支持跨文档 anchor/alias：`---` 分隔的多个文档中，后一文档不能引用
  前一文档的 anchor（会抛 `YamlParseException`），anchor 只在单个文档内有效
- timestamp 映射为字符串

## 构建

```bash
gradle :format-yaml:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
