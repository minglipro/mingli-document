# 从 1.x 迁移到 2.0.0

## 2.0.0

2.0.0 汇总 1.3.0 之后的能力，API 总体兼容：

```text
新增 codegen 模块（KSP）
新增 DocumentRuntimeCodecFactory
新增 DocumentAccessorCodegen
新增 DocumentCodecSourceGenerator
新增 JQF / Jazzer / fuzzContinuous
新增原生 Token Reader 与 Schubfach
```

需要 Kotlin 编译期代码生成时，额外引入 `codegen` 并应用 KSP 插件。

## 1.5.0

1.5.0 保持 1.4.0 API 兼容，新增 fuzz 工具链：

```text
Jazzer 目标
fuzzJazzer Gradle 任务
fuzzContinuous Gradle 任务
```

无需迁移。

## 1.4.0

1.4.0 保持 1.3.0 API 兼容，新增能力默认关闭或内部生效：

```text
CSV / Properties / TOML / YAML / XML 原生 Token Reader
JSON Schubfach Double 格式化
fuzzContinuous
YAML maxAliases
```

无需迁移。

## 1.3.0

1.3.0 保持 1.2.0 API 兼容，新增能力默认关闭：

```text
IonConfig.preserveTypes
CborConfig.preserveTags
maxStringLength
YAML maxAliases
CSV 原生 Token Reader（内部路径）
```

无需迁移；需要时显式开启即可。

## 1.2.0

1.2.0 保持 1.1.0 API 兼容，新增能力默认关闭：

```text
Protobuf maxDepth
Avro maxDepth
Ion maxDepth
YAML maxDepth
XML maxDepth
remoteReferenceResolver
```

无需迁移；需要时显式开启即可。

## Maven 坐标

请统一升级：

```kotlin
implementation(platform("com.mingliqiye.document:mingli-document-bom:2.0.0"))
```

或者手动把版本从 `1.0.0` 改为 `2.0.0`。

## 转换器接口改名

```text
JsonConverter      -> DocumentBaseConverter
JsonSerializer     -> DocumentBaseSerializer
JsonDeserializer   -> DocumentBaseDeserializer
```

旧代码：

```kotlin
import com.mingliqiye.document.serialization.JsonConverter

class MoneyConverter : JsonConverter<Money> {
    // ...
}
```

新代码：

```kotlin
import com.mingliqiye.document.serialization.DocumentBaseConverter

class MoneyConverter : DocumentBaseConverter<Money> {
    // ...
}
```

## @DocumentConverter 泛型收紧

旧版本：

```kotlin
@DocumentConverter(MySerializer::class)
```

如果 `MySerializer` 只实现单向序列化或反序列化，新版本需要改用：

```kotlin
@DocumentSerializer(MySerializer::class)
```

或：

```kotlin
@DocumentDeserializer(MyDeserializer::class)
```

`@DocumentConverter` 只接受双向 `DocumentBaseConverter`。

## 注解模块依赖

`serialization` 现在通过 `api` 暴露 `annotation`：

```text
annotation 标记接口是 DocumentBaseSerializer/Deserializer 的父类型
```

使用 Gradle/Maven 正常传递依赖即可，不需要额外声明。

## 新增能力（不需要迁移）

以下能力默认关闭或保持兼容，不迁移也不影响原行为：

```text
writeReferences       默认 false
resolveReferences     默认 false
maxInputSize          默认 0（不限制）
maxNodeDepth          默认 0（不限制）
maxNodeCount          默认 0（不限制）
atomicFileWrite       默认 false
createParentDirectories 默认 false
```

字段命名策略默认仍是 `AS_IS`。

## 文件/流 API

1.1.0 新增：

```kotlin
mapper.parse(file, User::class.java)
mapper.format(user, file)
mapper.parse(inputStream, User::class.java)
mapper.format(user, outputStream)
```

旧代码继续可用。

## 参考

- [CHANGELOG](CHANGELOG.md)
- [README](README.md)
- [模块文档](README.md#项目结构)
