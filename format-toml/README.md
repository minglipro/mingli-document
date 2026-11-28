# format-toml

TOML 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:toml:2.2.0")
```

## 主要 API

```text
TomlMapper / TomlMapperBuilder        Bean <-> TOML 映射
TomlReader / TomlWriter               文档树 <-> TOML 文本
TomlTokenReader                       原生 Token 读取

TomlCommentPreservingReader           保留注释读取
TomlCommentPreservingWriter           保留注释写出
TomlCommentedDocument                 文档树 + 注释 + 空行 + 表结构
TomlComment / TomlCommentKind         注释模型
TomlBlankLine                         空行锚点
TomlCommentConfig / TomlCommentConfigBuilder / TomlOrphanCommentPolicy
                                      注释写出配置
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

## 注释保留

tomlj 的解析结果只有表/值、不含注释，因此本模块用独立的行扫描器读取注释与空行，
再用独立的写出器把注释按关联位置放回。该能力与现有的 `TomlReader` / `TomlWriter` /
`TomlMapper` 完全独立，不改变它们的行为。

```kotlin
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.toml.TomlCommentKind
import com.mingliqiye.document.format.toml.TomlCommentPreservingReader
import com.mingliqiye.document.format.toml.TomlCommentPreservingWriter
import com.mingliqiye.document.format.toml.toTomlCommentedDocument

val source = """
    # 数据库配置
    #
    # 第二行文件头注释

    title = "demo" # 行尾注释

    # db 表
    [db]
    # 主机
    host = "localhost"
    port = 5432 # 端口
""".trimIndent()

// 方式一：Reader / Writer
val document = TomlCommentPreservingReader.parse(source)
val written = TomlCommentPreservingWriter().write(document)
check(written == "$source\n") // 示例文本可逐字节 round-trip

// 方式二：扩展函数
val viaExtension = source.toTomlCommentedDocument()
check(viaExtension.root == document.root)

// 按类型、路径和行号读取注释
val leading = document.comments.first { it.kind == TomlCommentKind.LEADING }
println("${leading.tablePath} / ${leading.key} @ line ${leading.line}")

// 修改节点树后继续保留注释（注释按路径重新关联）
val db = document.root["db"]!!.asObject()
val updated = document.withRoot(
    ObjectNode.of(
        "title" to document.root["title"]!!,
        "db" to ObjectNode.of(
            "host" to db["host"]!!,
            // 删除 port 后，它上方的注释按孤立注释策略处理
        ),
    ),
)
println(updated.toTomlString())
```

### 注释模型

`TomlCommentedDocument` 由三部分组成：

- `root`：与 `TomlReader` 值一致的文档树（对象字段按源文本语句顺序排列）；
- `comments`：按源文本顺序排列的 `TomlComment`；
- `blankLines`：`TomlBlankLine`，记录空行位于哪条语句（或文件末尾）之前。

`TomlComment` 的字段：

| 字段 | 说明 |
| --- | --- |
| `text` | `#` 之后的原始文本（保留空格，写出时按 `"#" + text` 原样还原） |
| `content` | 去掉首尾空白的注释内容 |
| `kind` | 布局类型，见下表 |
| `line` | 源文本行号（从 `1` 开始；手工构造的注释可为 `0`） |
| `tablePath` | 关联的表路径，根表为空列表 |
| `keyPath` | 关联的键路径（相对 `tablePath`），表头注释为空列表 |
| `tableIndex` | 数组表元素下标，`-1` 表示不在数组表中 |
| `path` / `key` | 完整路径与键名（只读派生属性） |

`TomlCommentKind`：

| 类型 | 含义 | 写出位置 |
| --- | --- | --- |
| `FILE_HEADER` | 文件第一组注释 | 文件开头 |
| `LEADING` | 直接位于表头/键值行上方的注释 | 对应语句上方 |
| `INLINE` | 与表头/键值行同一行的注释 | 对应语句行尾 |
| `INTERIOR` | 多行值内部的注释 | 所属键值行上方（独立注释行） |
| `FILE_TRAILER` | 最后一条语句之后的注释 | 文件末尾 |
| `ORPHAN` | 没有直接关联语句的游离注释 | 按孤立注释策略处理 |

### 写出配置

```kotlin
import com.mingliqiye.document.format.toml.TomlCommentConfig
import com.mingliqiye.document.format.toml.TomlOrphanCommentPolicy

val config = TomlCommentConfig.builder()
    .orphanCommentPolicy(TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_TABLE) // 默认
    .preserveBlankLines(true) // 默认输出空行
    .trailingNewline(true) // 默认以换行结尾
    .build()

val text = document.toTomlString(config)
```

孤立注释策略（同时作用于游离注释、以及 `withRoot` 后关联键/表已不存在的注释）：

| 策略 | 行为 |
| --- | --- |
| `PRESERVE_AT_END_OF_TABLE` | 默认；保留在关联表末尾，关联表也不存在时放到文件末尾 |
| `PRESERVE_AT_END_OF_FILE` | 统一保留在文件末尾（文件尾注释之前） |
| `DROP` | 丢弃 |

### 注释保留的实现方式

- 先用 tomlj 解析文档树并完成语法校验，再用独立的行扫描器识别注释、空行与表/数组表结构；
- 扫描器只理解“结构”（表头、键值行、行尾注释、跨行数组/内联 table、多行字符串），
  值仍由 tomlj 解析，不做重复解析；
- tomlj 的 `TomlTable` 使用 `HashMap`，字段顺序与源文本无关，因此读取时会按源语句顺序重排
  对象字段（`ObjectNode` 的相等性与顺序无关，不影响与 `TomlReader` 结果的比较）；
- 写出时按读取时记录的表结构输出 `[table]` / `[[array-table]]`，未记录为表节的对象写成内联 table，
  注释按路径挂回对应语句。

## 特性

- 标准 TOML 表与数组
- 嵌套表、数组表
- 内联表写出
- 文件/流读写
- Token 直读 Bean
- 原生 TOML Token Reader
- 注释/空行保留 round-trip（独立 API）

## 限制

基础读写：

- TOML 日期时间类型映射为 ISO-8601 字符串
- TOML 没有 null 类型
- 根节点固定为对象
- tomlj 1.0.0 只支持 TOML 0.5：数组元素必须同类型；且无法回读内联 table/数组中的空数组
- `TomlMapper` 的 Bean 映射不保留注释：注释保留 API 独立于 Mapper 使用

注释保留：

- 本功能基于独立的注释扫描/模型，因为 tomlj 不保留注释；注释扫描依赖输入是合法 TOML，
  非法文本先由 `TomlReader` 抛出 `TomlParseException`
- 注释内容（`#` 之后）原样保留；但注释前的空格、行尾注释的 `#` 与值之间的空白会规范化为一个空格；
  输出统一使用 LF 换行
- 文件开头/结尾的空行规范化：文件开头空行不输出；文件末尾空行保留数量并输出在文件尾注释之前
- 同一语句前的多个空行会保留数量，但空行与行前注释块的内部相对顺序会规范化（空行提前到注释块之前），
  被空行与后续语句隔开的注释按游离注释处理
- 多行值（多行数组、多行内联 table、多行字符串）内部的注释类型为 `INTERIOR`，写出时会提升到
  所属键值行上方，不再保留值内部的原始位置与缩进
- 节点树无法区分 `[table]` 表节与内联 table：读取时记录表/数组表结构来还原；通过
  `withRoot` 新增的对象字段默认写成内联 table，除非它包含已记录的子表
- 内联 table 内部的键顺序无法从语句顺序推导，保持 tomlj 的 `HashMap` 顺序
- 表节内总是先输出键值行、再输出子表头；嵌套数组表元素内的注释按最内层元素下标匹配，
  多层嵌套数组表（如 `[[a]]` 内的 `[[a.b]]`）的注释可能错位，属已知限制
- 数组表元素重排后，元素内注释按元素下标（而非元素身份）跟随
- 注释保留 API 只处理文本，不涉及二进制/流式 API；`TomlMapper`/Token 路径不携带注释

## 构建

```bash
gradle :format-toml:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
