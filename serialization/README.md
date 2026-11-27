# serialization

Java Bean / Kotlin 实体与文档树之间的通用映射模块。

格式无关，具体格式由 `format-*` 模块注入。

## 主要能力

- `DocumentMapper` / `DocumentMapperApi`
- `DocumentMapperBuilder`
- `DocumentMapperConfig`
- 字段转换器、日期格式、数字格式
- 多态类型注册与类型属性
- Bean 元数据缓存与访问器
- `DocumentTokenReader` / `DocumentTokenWriter`
- `BinaryDocumentMapper`
- `NodeTokenReader`

## Maven

```kotlin
implementation("com.mingliqiye.document:serialization:1.0.0")
```

## 主要 API

```kotlin
mapper.toNode(value)
mapper.fromNode(node, User::class.java)
mapper.parse(text, User::class.java)
mapper.parse(bytes, User::class.java)
mapper.format(value)
mapper.formatBytes(value)
mapper.parse(file, User::class.java)
mapper.format(value, outputStream)
```

## 配置示例

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy

val config = DocumentMapperConfig.builder()
    .missingFieldPolicy(MissingFieldPolicy.FAIL)
    .failOnUnknownFields(true)
    .writeNulls(false)
    .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .fileCharset(Charsets.UTF_8)
    .atomicFileWrite(true)
    .createParentDirectories(true)
    .maxInputSize(16L * 1024 * 1024)
    .maxNodeDepth(64)
    .maxNodeCount(100_000)
    .build()
```

`PropertyNamingStrategy` 支持：

```text
AS_IS（默认）
SNAKE_CASE
KEBAB_CASE
UPPER_CAMEL
LOWER_CAMEL
```

`@DocumentName` 显式注解优先于命名策略。

文件读写选项：

```text
fileCharset             文本文件/流字符集，默认 UTF-8
atomicFileWrite         临时文件 + 原子替换
createParentDirectories 自动创建父目录
maxInputSize            单次输入最大字节数，0 表示不限制
maxNodeDepth            Node 树最大深度，0 表示不限制
maxNodeCount            Node 树最大节点数，0 表示不限制
resolveReferences       反序列化前解析本地 $ref，默认 false
writeReferences         序列化时输出对象身份 $ref，默认 false
```

本地 `$ref`：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .resolveReferences(true)
        .build(),
)

val node = mapper.fromNode(JsonReader.parse(document), Map::class.java)
```

支持 `#/...` JSON Pointer、循环检测；只支持本地引用。

对象身份引用：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .writeReferences(true)
        .build(),
)
```

同一个可变对象第二次出现时写成：

```json
{"$ref": "#/first"}
```

仅 `toNode` / Node 路径支持；流式 `format` 仍保持原行为。

## 转换器

实现 `DocumentBaseSerializer` 与/或 `DocumentBaseDeserializer`，通过：

- `DocumentMapperBuilder.addSerializer(...)`
- `DocumentMapperBuilder.addDeserializer(...)`
- `@DocumentConverter`
- 字段级配置

注册。

## 多态类型

```kotlin
@DocumentTypeInfo("kind")
interface Animal

@DocumentTypeName("cat")
class Cat : Animal

val config = DocumentMapperConfig.builder()
    .subtype(Animal::class.java, "cat", Cat::class.java)
    .build()

val mapper = JsonMapper(config)
```

字段声明为 `Animal` 时自动写入：

```json
{
  "kind": "cat"
}
```

反序列化按 `kind` 查找已注册子类；缺失或未知类型会抛 `DocumentSerializationException`。

多态只使用显式注册的 registry，不扫描子类。

## 构造器注入

不可变 Java Bean 可以用 `@DocumentCreator` 指定反序列化构造器：

```java
class User {
    private final String name;
    private final int age;

    @DocumentCreator
    User(@DocumentName("name") String name, @DocumentName("age") int age) {
        this.name = name;
        this.age = age;
    }
}
```

- 构造参数用 `@DocumentName` 指定文档字段名
- 没有注解时回退到反射参数名（需要 `-parameters`）
- 缺字段按 `MissingFieldPolicy` 处理
- 未标注构造器继续走原有无参构造逻辑

## Builder 模式

不可变类也可以通过 Builder 反序列化：

```java
class User {
    @DocumentBuilder
    static UserBuilder builder() {
        return new UserBuilder();
    }
}

class UserBuilder {
    private String name;

    UserBuilder setName(String name) {
        this.name = name;
        return this;
    }

    @DocumentBuild
    User build() {
        return new User(name);
    }
}
```

启用条件：

```text
没有 @DocumentCreator
没有无参构造器
目标类存在 @DocumentBuilder 静态工厂
Builder 存在 @DocumentBuild 或无参 build()
```

## 构建

```bash
gradle :serialization:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
