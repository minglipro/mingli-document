---
title: 文件与流
---

# 文件与流

> `DocumentMapper` 对 IO 做了统一封装：文本来源（String / ByteArray / File / Path /
> InputStream / Reader）和文本目标（String / ByteArray / File / Path / OutputStream /
> Writer）都支持，字符集、原子写、父目录、输入大小限制集中在配置里。

## 目录

- [API 总览](#api-总览)
- [文本格式与二进制格式的规则](#文本格式与二进制格式的规则)
- [字符串 API](#字符串-api)
- [字节数组 API](#字节数组-api)
- [文件与路径](#文件与路径)
- [输入流与输出流](#输入流与输出流)
- [字符流 Reader 与 Writer](#字符流-reader-与-writer)
- [字符集](#字符集)
- [最大输入大小](#最大输入大小)
- [流式写出覆盖范围](#流式写出覆盖范围)
- [错误处理](#错误处理)
- [完整示例](#完整示例)
- [性能与使用建议](#性能与使用建议)

## API 总览

`DocumentMapperApi` 提供的 IO 方法：

| 方向 | 来源/目标 | 方法 |
|---|---|---|
| 读 | `String` | `parse(source, Class)` / `parse(source, DocumentTypeReference)` |
| 读 | `ByteArray` | `parse(bytes, Class)` / `parse(bytes, DocumentTypeReference)` |
| 读 | `File` | `parse(file, Class)` / `parse(file, DocumentTypeReference)` |
| 读 | `Path` | `parse(path, Class)` / `parse(path, DocumentTypeReference)` |
| 读 | `InputStream` | `parse(inputStream, Class)` / `parse(inputStream, DocumentTypeReference)` |
| 读 | `Reader` | `parse(reader, Class)` / `parse(reader, DocumentTypeReference)` |
| 写 | `String` | `format(value)` / `formatPretty(value)` |
| 写 | `ByteArray` | `formatBytes(value)` |
| 写 | `File` | `format(value, file)` / `formatPretty(value, file)` |
| 写 | `Path` | `format(value, path)` / `formatPretty(value, path)` |
| 写 | `OutputStream` | `format(value, output)` / `formatPretty(value, output)` |
| 写 | `Writer` | `format(value, writer)` / `formatPretty(value, writer)` |

Kotlin 还有 reified 扩展（`parse<T>(file)` 等），声明在
`DocumentMapperApi.Companion` 中，需要时导入
`com.mingliqiye.document.serialization.DocumentMapperApi.Companion.parse`。

## 文本格式与二进制格式的规则

| | 文本格式 | 二进制格式 |
|---|---|---|
| JSON / YAML / XML / Properties / TOML / CSV / Ion 文本 | ✅ 全部文本 API | — |
| CBOR / Smile / Avro / Protobuf / Ion 二进制 | 文件与字节流可用 | `parse(String)`、`format()`、`Reader`、`Writer` 会抛异常 |

具体规则：

- **二进制格式**（`BinaryDocumentMapper` 子类）：`parse(String)` / `format` /
  `formatPretty` / `parse(Reader)` / `format(value, Writer)` 抛
  `DocumentSerializationException("... does not support ...")`；
  文件、`Path`、`InputStream`、`OutputStream`、`ByteArray` 走字节语义。
- **混合格式 Ion**：`parse(String)`、`format()` 处理文本 Ion；
  `parse(ByteArray)`、`formatBytes()` 处理二进制 Ion；文件/流按方法语义选择。
- **文本格式**：`parse(ByteArray)` 会用 `fileCharset` 把字节解码成文本再解析；
  `formatBytes` 会用 `fileCharset` 把文本编码成字节（JSON 例外，见下）。

## 字符串 API

最简单的一组：

```kotlin
import com.mingliqiye.document.format.json.JsonMapper

val mapper = JsonMapper()
val user = mapper.parse("""{"name":"小于","age":18}""", User::class.java)

val compact = mapper.format(user)       // 紧凑 JSON
val pretty = mapper.formatPretty(user)  // 缩进 JSON
```

`format` 与 `formatPretty` 的区别：

- `format` 使用格式的默认风格（JSON 是否缩进由 `JsonConfig.pretty` 决定）；
- `formatPretty` **强制**格式化输出（缩进、块样式 YAML、可读 XML 等）。

## 字节数组 API

```kotlin
val bytes: ByteArray = mapper.formatBytes(user)
val restored: User = mapper.parse(bytes, User::class.java)
```

编码规则：

| Mapper | `formatBytes` | `parse(ByteArray)` |
|---|---|---|
| 文本格式（通用） | `format(value)` 文本按 `fileCharset` 编码 | 字节按 `fileCharset` 解码后走 `parse(String)` |
| JSON | UTF-8；`JsonConfig.pretty` 开启时输出缩进 UTF-8 | **严格 UTF-8** 解码，非法字节抛 `JsonParseException` |
| 二进制格式 | 原始二进制 | 原始二进制 |

::: warning JSON 的字节 API 固定 UTF-8
JSON 规范规定编码为 UTF-8，因此 `JsonMapper.parse(ByteArray)` 不受 `fileCharset`
影响，并且会拒绝非法 UTF-8。文本形式 `parse(String)` 当然不受影响。
:::

## 文件与路径

```kotlin
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

val file = File("out/user.json")
val path: Path = Paths.get("out/user.json")

// 写
mapper.format(user, file)
mapper.formatPretty(user, path)

// 读
val fromFile = mapper.parse(file, User::class.java)
val fromPath = mapper.parse(path, User::class.java)
```

### 文件由谁打开、谁关闭

- 文件 API 内部使用 `Files.readAllBytes` / `Files.write`（或原子写），
  **自己打开、自己关闭**；
- 调用方只传 `File` / `Path`，不需要也不应该自行打开文件流；
- 读取时一次性读入内存，受 `maxInputSize` 保护（见下文）。

### 原子写

默认 `atomicFileWrite = false`，直接覆盖目标文件。开启后：

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.format.json.JsonMapper

val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .atomicFileWrite(true)
        .createParentDirectories(true)
        .build(),
)
mapper.format(user, File("out/user.json"))
```

实现细节：

1. 解析目标文件的**绝对路径**，取父目录；
2. 若 `createParentDirectories = true`，先 `Files.createDirectories(parent)`；
3. 在父目录创建临时文件（名字形如 `.user.json.<随机>.tmp`）；
4. 写入临时文件；
5. `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`；文件系统不支持原子移动时
   回退到 `REPLACE_EXISTING`；
6. 无论成功失败，`finally` 中删除残留临时文件。

因此：**原子写要求父目录可解析且可写**；目标路径没有父目录时抛
`DocumentSerializationException`。

### 自动创建父目录

默认 `createParentDirectories = false`，父目录不存在时写文件抛 `NoSuchFileException`。
开启后自动 `mkdir -p`：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .createParentDirectories(true)
        .build(),
)
mapper.format(user, File("a/b/c/user.json")) // a/b/c 会被创建
```

::: tip 两个开关经常一起用
`atomicFileWrite(true)` + `createParentDirectories(true)` 是写生产配置文件的推荐组合：
目录自动创建、写入要么成功要么保留旧文件。
:::

## 输入流与输出流

### 谁负责关闭

| 流 | 谁关闭 | 本库行为 |
|---|---|---|
| 调用方传入的 `InputStream` / `OutputStream` / `Reader` / `Writer` | **调用方** | 只读取/写入并 `flush()`，不调用 `close()` |
| 文件 API 内部打开的流 | 本库 | `Files.*` 或 try-with-resources 内部处理 |

因此推荐的写法是调用方用 `use { }` / try-with-resources 包住：

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// 写
val buffer = ByteArrayOutputStream()
mapper.format(user, buffer) // 只 flush，不 close
val bytes = buffer.toByteArray()

// 读
val restored = ByteArrayInputStream(bytes).use { input ->
    mapper.parse(input, User::class.java)
}
```

### 流式写出

`format(value, outputStream)` 会优先尝试**直接流式写出**：Mapper 内部创建一个
`DocumentTokenWriter`（需要格式支持），边遍历实体边写字节，不构建完整
`String`/`ByteArray`。格式不支持时回退为「先构造完整字节再写」。

```kotlin
import java.io.FileOutputStream

FileOutputStream("out/user.json").use { out ->
    mapper.format(user, out)       // 紧凑风格
    // mapper.formatPretty(user, out) // 格式化风格
}
```

`formatPretty(value, outputStream)` 总是以格式化模式走流式路径（若可用）。

### 读取流仍是整段读入

`parse(InputStream)` / `parse(Reader)` / `parse(File)` / `parse(Path)` 当前实现都是
**读取全部内容后解析**：

```text
InputStream -> readBytes() -> parse(ByteArray)
Reader      -> readText()  -> parse(String)
File/Path   -> Files.readAllBytes() -> parse(...)
```

也就是说，读取侧没有增量/流式解析 API。对超大文件请：

- 用 `maxInputSize` 控制上限；
- 在业务层按记录拆分文件（例如 JSON Lines 逐行自己解析，或用格式提供的多值 API，
  如 CBOR sequence / Smile 多消息流）；
- 或者使用 Token API 自行控制来源（见 [Token 流式 API](/guide/token-stream.html)）。

## 字符流 Reader 与 Writer

只支持文本格式：

```kotlin
import java.io.StringReader
import java.io.StringWriter

val writer = StringWriter()
mapper.format(user, writer)       // 只 flush，不关闭
val text = writer.toString()

val restored = mapper.parse(StringReader(text), User::class.java)
```

- 二进制格式调用 `Reader` / `Writer` 重载会抛 `DocumentSerializationException`；
- `Writer` 路径直接写入文本，**不经过 `fileCharset`**（Writer 自带编码语义）。

## 字符集

`fileCharset` 默认 UTF-8，作用于文本格式的**文件、字节数组、输入输出流**：

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.format.properties.PropertiesMapper

val mapper = PropertiesMapper(
    DocumentMapperConfig.builder()
        .fileCharset(Charsets.ISO_8859_1) // 典型 Java Properties 场景
        .build(),
)

val text = mapper.parse(bytes, User::class.java)   // 按 ISO-8859-1 解码
mapper.format(user, output)                       // 按 ISO-8859-1 编码
```

注意：

- `format(file)` / `format(path)` / `format(outputStream)` 用 `fileCharset` 编码；
- `format(writer)` 不涉及字符集（Writer 语义）；
- JSON 的 `parse(ByteArray)` / `formatBytes` 固定 UTF-8；
- 二进制格式完全不使用 `fileCharset`。

## 最大输入大小

`maxInputSize`（字节数，`0` = 不限制）在解析入口检查：

| 入口 | 检查对象 |
|---|---|
| `parse(String)` | `source.length`（**字符数**） |
| `parse(ByteArray)` | 字节数组长度 |
| `parse(File)` / `parse(Path)` | `Files.size(path)`，读入后再次校验 |
| `parse(InputStream)` | 读入后的字节数 |
| `parse(Reader)` | 读入后的字符数 |

超限抛：

```text
DocumentSerializationException: Input size 12345 exceeds maxInputSize 1024
```

::: warning 单位是字节，但 String/Reader 按字符数
文本入口用 `String.length` 近似，多字节字符（中文、emoji）会低估实际字节数。
对不可信输入建议同时设置 `maxNodeDepth` / `maxNodeCount`，不要只依赖 `maxInputSize`。
:::

## 流式写出覆盖范围

以当前源码行为为准（`createStreamingTokenWriter` 与 `format(value, OutputStream)` 的
覆盖情况）：

| 格式 | `format(value, OutputStream)` | 说明 |
|---|---|---|
| JSON | ✅ Token 流式写出 | `JsonUtf8StreamWriter`，紧凑模式纯 UTF-8 字节写出 |
| Ion | ✅ 文本 Token 流式写出 | `IonTextTokenWriter`，按 `fileCharset` 编码 |
| CBOR | ✅ Token 流式写出 | 默认模式；`pretty` 或 `preserveTags` 时回退 |
| Smile | ✅ Token 流式写出 | 默认模式；`formatPretty` 回退 |
| YAML | ✅ 直接写 `Writer` | 仍先构建 Node 树，但直接写入输出流、不构造完整 String |
| XML | ✅ 直接写 `Writer` | 同上 |
| Properties | ⚠️ 回退 | 先构造完整 String 再编码写出 |
| TOML | ⚠️ 回退 | 同上 |
| CSV | ⚠️ 回退 | 同上 |
| Avro / Protobuf | 不适用 | 二进制格式，使用 `formatBytes` / 文件 / `OutputStream` 的字节路径 |

即使回退，接口语义不变（`OutputStream` 由调用方关闭，本库只 flush），只是多了一次
中间 String 分配。需要极致写性能时优先 JSON/CBOR/Smile/Ion 的 Token 路径。

## 错误处理

| 情况 | 抛出的异常 |
|---|---|
| 文件不存在 / 无权限 | JDK `IOException`（`NoSuchFileException` / `AccessDeniedException`）原样抛出 |
| 父目录不存在且未开启自动创建 | `NoSuchFileException` |
| 原子写时无法解析父目录 | `DocumentSerializationException` |
| 输入超过 `maxInputSize` | `DocumentSerializationException` |
| 二进制格式用字符流 | `DocumentSerializationException` |
| 格式语法错误 | 各格式 `*ParseException`（继承 `DocumentException`） |
| 实体映射失败 | `DocumentSerializationException` |

IO 异常不会被包装成库异常，方便调用方区分「IO 出问题」与「数据格式/映射有问题」。

## 完整示例

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import java.io.File

data class AppConfig(
    val name: String,
    val port: Int,
    val debug: Boolean = false,
)

fun main() {
    val yaml = YamlMapper(
        DocumentMapperConfig.builder()
            .atomicFileWrite(true)
            .createParentDirectories(true)
            .maxInputSize(4L * 1024 * 1024)
            .build(),
    )

    val file = File("config/app.yaml")

    // 写配置：自动建目录 + 原子替换
    yaml.formatPretty(
        AppConfig(name = "demo", port = 8080, debug = true),
        file,
    )

    // 读配置：超过 4MB 直接拒绝
    val config = yaml.parse(file, AppConfig::class.java)
    println(config)

    // 同一份数据转成 JSON 从标准输出流写出
    val json = JsonMapper()
    json.format(config, System.out) // 不关闭 System.out，只 flush
}
```

二进制格式的同一套调用：

```kotlin
import com.mingliqiye.document.format.cbor.CborMapper
import java.io.ByteArrayOutputStream
import java.io.File

val cbor = CborMapper()

// 文件
cbor.format(config, File("config/app.cbor"))
val fromCbor = cbor.parse(File("config/app.cbor"), AppConfig::class.java)

// 字节流
val output = ByteArrayOutputStream()
cbor.format(config, output)
val restored = cbor.parse(output.toByteArray(), AppConfig::class.java)
```

## 性能与使用建议

1. **复用 Mapper**：Mapper 内部缓存元数据，构建一次、全局复用。
2. **写多读少时优先 Token 流式格式**：JSON / CBOR / Smile / Ion。
3. **文件读写是一次性全量**，不要用它处理几个 GB 的文件；用 `maxInputSize` 兜底。
4. **批量转换**时先 `toNode` 一次，再多次 `format` 到不同格式，避免重复反射。
5. **不要关闭调用方传入的流**，也不要在库调用后再复用已 `flush` 的流做其他写入
   （除非你明确知道格式尾部语义）。
6. **字符集显式化**：跨平台写文件建议显式 `fileCharset`，不要依赖平台默认。

下一步：[配置](/guide/configuration.html) 了解所有读写开关；
[Token 流式 API](/guide/token-stream.html) 了解更低层的读写方式。
