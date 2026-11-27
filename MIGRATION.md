# 从 1.0.0 迁移到 1.1.0

## Maven 坐标

请统一升级：

```kotlin
implementation(platform("com.mingliqiye.document:mingli-document-bom:1.1.0"))
```

或者手动把版本从 `1.0.0` 改为 `1.1.0`。

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
