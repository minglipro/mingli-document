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
- Java record、只读属性与构造器注入
- `merge` 合并到已有对象（`MergePolicy`）
- `DocumentTokenReader` / `DocumentTokenWriter`
- `BinaryDocumentMapper`
- `NodeTokenReader`

## Maven

```kotlin
implementation("com.mingliqiye.document:serialization:2.0.0")
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
mapper.formatBytes(value)                   // 文本格式返回 charset 编码字节；二进制返回原始字节
mapper.formatPretty(value, file)            // 格式化文本写入 File
mapper.formatPretty(value, path)            // 格式化文本写入 Path
mapper.formatPretty(value, outputStream)    // 格式化文本写入 OutputStream
mapper.formatPretty(value, writer)          // 格式化文本写入 Writer
mapper.merge(node, target)                    // 合并到已有对象，未出现字段保留
mapper.merge(node, target, MergePolicy.DEEP)  // 嵌套 Bean 深合并
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
maxStringLength         Node 树单个字符串最大长度，0 表示不限制
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

远程 `$ref` 需要显式提供解析器：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .resolveReferences(true)
        .remoteReferenceResolver { uri ->
            loadDocument(uri) // 返回外部文档 Node
        }
        .build(),
)
```

`remoteReferenceResolver` 默认 `null`，此时远程引用直接报错；URI 校验、IO 与缓存由调用方负责。

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

## Java record

record 组件访问器是 `name()` 而不是 `getName()`，Mapper 会自动识别 record 组件：

```java
public record Address(String city, int zipCode) {}

public record User(
        @DocumentName("display_name") String name,
        int age,
        Address address) {}
```

```kotlin
val node = mapper.toNode(User("小于", 18, Address("杭州", 310000)))
// {"display_name":"小于","age":18,"address":{"city":"杭州","zipCode":310000}}

val user = mapper.fromNode(node, User::class.java)
```

- 序列化：record 组件访问器作为可读属性写出，支持 `@DocumentName`、`@DocumentIgnore`
  与命名策略
- 反序列化：优先使用 `@DocumentCreator` 构造器；没有标注时自动绑定 canonical
  constructor，按组件名（可用 `@DocumentName` 覆盖）取字段
- record 组件都是只读的，按构造参数注入；缺失字段按 `MissingFieldPolicy` 处理
- `@DocumentIgnore` 标记的组件既不写出，也不从文档读取
- 只读属性见下一节

record 反射 API（`Class.isRecord()` / `RecordComponent`）从 JDK 16 开始提供。
Mapper 通过反射按名称探测，**主代码仍按 JDK 8 目标编译**：运行在 JDK 8 时该分支
不会执行，普通 Bean 行为不变；运行在 JDK 16+ 时自动启用 record 支持。

## 只读属性

没有 setter、也不是可写构造参数的属性（Java `final` 字段、只有 getter 的属性）：

- 序列化照常写出
- 反序列化直接跳过：文档中出现该字段时不会被当作未知字段，即使开启
  `failOnUnknownFields` 也不报错
- `MissingFieldPolicy.FAIL` 不会因为缺少只读字段而报错
- record / `@DocumentCreator` 构造器 Bean 中，只读属性是构造参数，按参数注入

```java
public final class ReadOnlyUser {
    public final String tag = "fixed";   // 无 setter，反序列化跳过

    private final String name;

    @DocumentCreator
    public ReadOnlyUser(@DocumentName("name") String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @DocumentName("label")
    public String getLabel() {           // 只读属性：写出 label，读入时跳过
        return name.toUpperCase();
    }
}
```

## 合并已有对象

`merge(node, target)` 把文档树合并到已有对象：文档中出现的字段覆盖，未出现的字段
保留原值。

```kotlin
val user = mapper.parse(
    """{"name":"a","age":18,"address":{"city":"杭州","zipCode":310000}}""",
    User::class.java,
)

// 浅合并：address 整体替换为文档中的对象
mapper.merge(ObjectNode.of("age" to NumberNode.of(20)), user)
// user.age == 20；name、address 保留

// 深合并：嵌套 Bean 递归合并，保留文档中没有出现的嵌套字段
mapper.merge(
    ObjectNode.of("address" to ObjectNode.of("city" to StringNode.of("北京"))),
    user,
    MergePolicy.DEEP,
)
// user.address.city == "北京"，user.address.zipCode == 310000
```

`MergePolicy`：

```text
REPLACE           文档字段整体替换（默认）
DEEP              嵌套 Bean 递归合并，保留嵌套对象未出现字段
SKIP_NULLS        文档中的 null 不覆盖已有值
DEEP_SKIP_NULLS   深合并 + 忽略 null
MergePolicy.of(deepMerge, skipNulls)  自定义组合
```

语义与限制：

- 只支持 Node 到 Bean 的合并；流式 `writeTo` 没有合并 API
- 可变 Bean 就地修改并返回同一实例；record / `@DocumentCreator` / `@DocumentBuilder`
  Bean 返回新实例（原实例不变），通过“已有对象序列化结果 + 覆盖字段”重建
- 集合、Map、数组与多态声明字段始终整体替换，不做元素级合并
- 只读属性即使在文档中出现也会跳过
- 文档中的未知字段默认忽略，开启 `failOnUnknownFields` 时报错
- `MissingFieldPolicy` 不参与 merge：未出现的字段一律保留原值
- `node` 必须是对象节点；目标为 `null` 或非 Bean 类型时抛
  `DocumentSerializationException`

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

## 代码生成

### 运行期属性访问代码生成

```kotlin
val codegen = DocumentAccessorCodegen()
val getter = codegen.getter(User::class.java, "name")
val setter = codegen.setter(User::class.java, "name")
```

优先使用 LambdaMetafactory 生成直接调用代码，失败时回退反射。

### 运行期 Bean 编解码器生成

```kotlin
val factory = DocumentRuntimeCodecFactory(
    namingStrategy = PropertyNamingStrategy.SNAKE_CASE,
    writeNulls = false,
)
val mapper = JsonMapper.builder()
    .addConverter(factory.converter(User::class.java))
    .build()
```

生成器按字段元数据与 [DocumentAccessorCodegen] 访问器构建运行期转换器，支持
`@DocumentName`、`@DocumentIgnore` 与无参构造器 + setter。

### 编译期源码生成

```kotlin
val source = DocumentCodecSourceGenerator().enumConverter(
    Status::class.java,
    "com.example.generated",
)
```

生成的源码可直接写入源码目录，由编译器编译；也可以生成模块：

```kotlin
val module = DocumentCodecSourceGenerator().module(
    packageName = "com.example.generated",
    moduleName = "GeneratedDocumentModule",
    converterTypes = listOf("com.example.generated.StatusDocumentConverter"),
)
```

## 构建

```bash
# 默认测试（JDK 8 工具链，Java record 测试自动跳过）
gradle :serialization:test

# Java record 映射测试：需要 JDK 16+ 运行时，测试用 Java 17 工具链执行
gradle :serialization:recordTest
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
'

流式写出：

```text
format(value, OutputStream) 会优先通过 DocumentTokenWriter 直接写出
当前实现：JSON、Ion 文本、XML、YAML、CSV、Properties、TOML、CBOR（默认模式）、Smile
其他格式或暂不支持的组合会回退到先构造 String/ByteArray
OutputStream/Writer 由调用方关闭；本库只 flush
```