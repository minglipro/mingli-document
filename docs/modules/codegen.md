# codegen 模块

`codegen` 是 mingli-document 的**编译期 KSP 代码生成**模块。它在 Kotlin 编译阶段扫描被
`@DocumentEnumCodec` 标注的枚举，生成对应的枚举转换器，并在处理结束时生成一个注册全部
转换器的 `DocumentModule`。

- 坐标：`com.mingliqiye.document:codegen`
- 当前版本：`2.3.0`
- 包名：`com.mingliqiye.document.codegen`
- 目标字节码：JDK 8
- 实现依赖：`com.google.devtools.ksp:symbol-processing-api:2.3.0-1.0.21`
- 使用方式：作为 KSP 处理器依赖（`ksp(...)`），**不进入运行时类路径**

> 注意区分：`serialization` 模块内也有三个「代码生成」辅助类
> （`DocumentAccessorCodegen`、`DocumentRuntimeCodecFactory`、`DocumentCodecSourceGenerator`），
> 它们是**运行期/源码字符串生成**，与本文的 KSP 处理器不是同一层能力，见下文
> [运行期与源码生成](#运行期与源码生成serialization-模块)。

## 职责

| 输入 | 输出 |
|---|---|
| 源码中被 `@DocumentEnumCodec` 标注的枚举 | `com.mingliqiye.document.generated.<枚举名>DocumentConverter` |
| 同一次编译中的全部标注枚举 | `com.mingliqiye.document.generated.GeneratedDocumentModule` |

生成的转换器语义（固定模板）：

- 序列化：写出枚举 `name`（`StringNode`）；
- 反序列化：按 `valueOf` 还原；非法名称返回 `null`（Mapper 会按该字段的目标类型处理
  `null`，不会抛异常）；
- 生成的模块在 `setupModule` 中 `context.addConverter(...)` 注册全部转换器。

## 依赖关系

```text
KSP symbol-processing-api 2.3.0-1.0.21（implementation）
                 ▲
                 │
             codegen ──（仅 KSP 处理阶段使用）
                 │ 扫描全限定名：
                 │ com.mingliqiye.document.annotation.DocumentEnumCodec
                 ▼
业务模块编译时：api(annotation)、api(serialization)
```

- `codegen` 主代码**不依赖**本库的 `annotation` / `serialization`：处理器按全限定名扫描
  注解，生成的源码通过 import 引用这些模块的类型；
- 因此业务模块必须让自己的编译类路径上出现 `annotation` 与 `serialization`（通常是
  `api(project(":annotation"))` + `api(project(":serialization"))`，或声明格式模块）；
- `codegen` 在 `bom/build.gradle.kts` 的 `constraints` 中，通过 BOM 可统一版本；
- `codegen-test` 是应用 KSP 的集成测试工程（`ksp(project(":codegen"))`），不对外提供能力。

## 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| Kotlin | 2.3.0 | 根 `build.gradle.kts` 固定 |
| KSP | `2.3.0-1.0.21` | **必须与 Kotlin 版本匹配** |
| JDK | 8+ | 与库一致 |
| Gradle | 8.x | 使用 KSP Gradle 插件 |

## 使用方式

### 1）根工程声明插件

```kotlin
// 根 build.gradle.kts
plugins {
    kotlin("jvm") version "2.3.0" apply false
    id("com.google.devtools.ksp") version "2.3.0-1.0.21" apply false
}
```

### 2）业务模块应用插件并声明处理器

```kotlin
// 业务模块 build.gradle.kts
plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

dependencies {
    api(project(":annotation"))
    api(project(":serialization"))
    ksp(project(":codegen"))
}
```

发布到 Maven 后等价写法：

```kotlin
dependencies {
    implementation("com.mingliqiye.document:annotation:2.3.0")
    implementation("com.mingliqiye.document:serialization:2.3.0")
    ksp("com.mingliqiye.document:codegen:2.3.0")
}
```

> `ksp(...)` 只在编译阶段生效，不会打入产物，也不会传递为运行时依赖。

### 3）给枚举加注解

```kotlin
import com.mingliqiye.document.annotation.DocumentEnumCodec

@DocumentEnumCodec
public enum class Pet {
    CAT,
    DOG,
}
```

编译后生成两个类：

```kotlin
// com.mingliqiye.document.generated.PetDocumentConverter
public class PetDocumentConverter : DocumentBaseConverter<com.example.Pet> {
    override fun getType(): DocumentTypeReference<com.example.Pet> =
        DocumentTypeReference.of(com.example.Pet::class.java)

    override fun serialize(value: com.example.Pet, context: SerializationContext): Node =
        StringNode.of(value.name)

    override fun deserialize(node: Node, context: DeserializationContext): com.example.Pet? =
        try {
            com.example.Pet.valueOf(node.asString())
        } catch (_: Exception) {
            null
        }
}
```

```kotlin
// com.mingliqiye.document.generated.GeneratedDocumentModule
public class GeneratedDocumentModule : DocumentModule {
    override val moduleName: String = "GeneratedDocumentModule"

    override fun setupModule(context: ModuleSetupContext) {
        context.addConverter(PetDocumentConverter())
        // ... 按枚举全限定名排序注册全部转换器
    }
}
```

### 4）注册模块

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.generated.GeneratedDocumentModule

val mapper = JsonMapper.builder()
    .addModule(GeneratedDocumentModule())
    .build()

println(mapper.format(Pet.CAT))      // "CAT"
println(mapper.parse("\"DOG\"", Pet::class.java)) // DOG
```

## 注解参数

```kotlin
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class DocumentEnumCodec(
    val converterPackage: String = "",
    val converterName: String = "",
    val moduleName: String = "",
)
```

| 参数 | 默认值（空串语义） | 说明 |
|---|---|---|
| `converterPackage` | `com.mingliqiye.document.generated` | 生成转换器的包名；必须符合 Kotlin 包名规则 |
| `converterName` | `<枚举简单名>DocumentConverter` | 生成转换器的类名；必须符合 Kotlin 标识符规则 |
| `moduleName` | `GeneratedDocumentModule` | 生成模块的类名；模块包名取自定义该参数的枚举的 `converterPackage` |

模块参数解析规则（源码 `resolveDocumentModuleSpec`）：

- 所有枚举都未指定 `moduleName` → 模块固定在默认包
  `com.mingliqiye.document.generated`，类名 `GeneratedDocumentModule`；
- 任一枚举指定了 `moduleName` → 使用「该枚举的 `converterPackage` + `moduleName`」作为
  模块包名与类名；若多个枚举指定了不同的包名/类名组合，KSP 报错；
- 模块注册**全部**转换器（包括自定义包名/类名的转换器，模块内用全限定名引用），
  注册顺序按枚举全限定名排序。

示例：

```kotlin
@DocumentEnumCodec(
    converterPackage = "com.example.generated",
    converterName = "ColorCodec",
    moduleName = "ExampleGeneratedModule",
)
public enum class Color { RED, GREEN }
```

## 生成代码的校验与错误

KSP 处理器（`DocumentEnumCodecProcessor`）在以下情况通过 `logger.error` 使编译失败：

| 情况 | 错误 |
|---|---|
| 注解标在非枚举类型上 | `@DocumentEnumCodec can only be applied to enum classes.` |
| 枚举或外层声明为 private / internal / protected | `@DocumentEnumCodec requires a public enum: ...` |
| `converterPackage` 非法 | `Invalid converterPackage '...' on @DocumentEnumCodec for ...` |
| `converterName` 非法 | `Invalid converterName '...' ...` |
| `moduleName` 非法（非空时） | `Invalid moduleName '...' ...` |
| 生成的转换器全限定名重复 | `Duplicate generated converter '...' ...` |
| 多个枚举的 moduleName/包名不一致 | `Conflicting @DocumentEnumCodec moduleName values: ...` |
| 写文件失败 | `Failed to generate ...` |

名称校验规则（`DocumentCodegenNames`）：

```text
标识符： [A-Za-z_][A-Za-z0-9_]*
包名：   identifier(\\.identifier)*
```

不支持反引号标识符、不支持 `$` 嵌套类名写法（枚举的 `$` 会在生成代码中替换为 `.`）。

## 运行期与源码生成（serialization 模块）

`serialization` 的 `codegen` 子包提供三种运行期/源码生成能力，面向不同场景：

### DocumentAccessorCodegen（运行期属性访问）

```kotlin
import com.mingliqiye.document.serialization.codegen.DocumentAccessorCodegen

val codegen = DocumentAccessorCodegen()
val getter = codegen.getter(User::class.java, "name")   // Function<Any, Any?>?
val setter = codegen.setter(User::class.java, "name")   // BiConsumer<Any, Any?>?
```

- 通过 `LambdaMetafactory` 生成直接调用代码；
- 优先 getter/setter 方法，其次字段；
- 生成失败返回 `null`，由调用方回退反射；
- 内部缓存，线程安全。

### DocumentRuntimeCodecFactory（运行期 Bean 编解码器）

```kotlin
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.codegen.DocumentRuntimeCodecFactory
import com.mingliqiye.document.format.json.JsonMapper

val factory = DocumentRuntimeCodecFactory(
    namingStrategy = PropertyNamingStrategy.SNAKE_CASE,
    writeNulls = false,
)

val mapper = JsonMapper.builder()
    .addConverter(factory.converter(User::class.java))
    .build()
```

支持：`@DocumentName`、`@DocumentIgnore`、命名策略、无参构造器 + setter、`writeNulls`。

限制：不支持构造器参数注入、Builder、不可变对象、多态子类型；转换器构造时会扫描字段
（包括父类字段），跳过 static/transient/synthetic 字段。

### DocumentCodecSourceGenerator（编译期源码字符串）

在自定义 Gradle/Maven task 中生成 Kotlin 源码，写入源码目录后由编译器编译：

```kotlin
import com.mingliqiye.document.serialization.codegen.DocumentCodecSourceGenerator

val generator = DocumentCodecSourceGenerator()

// 1) 枚举转换器源码
val converterSource = generator.enumConverter(
    enumType = Status::class.java,
    packageName = "com.example.generated",
    converterName = "StatusDocumentConverter", // 默认 <枚举名>DocumentConverter
)

// 2) 注册模块源码
val moduleSource = generator.module(
    packageName = "com.example.generated",
    moduleName = "GeneratedDocumentModule",
    converterTypes = listOf("com.example.generated.StatusDocumentConverter"),
)
```

该生成器不依赖编译器插件，适合在构建脚本里调用；生成的代码与 KSP 版本语义一致
（枚举按 `name` 写出、`valueOf` 读入、非法返回 `null`）。

## 示例：多枚举 + 自定义包

```kotlin
// src/main/kotlin/com/example/model/Status.kt
@DocumentEnumCodec(converterPackage = "com.example.generated")
public enum class Status { ACTIVE, DISABLED }

// src/main/kotlin/com/example/model/Level.kt
@DocumentEnumCodec(
    converterPackage = "com.example.generated",
    converterName = "LevelCodec",
)
public enum class Level { LOW, HIGH }
```

生成结果：

```text
com.example.generated.StatusDocumentConverter
com.example.generated.LevelCodec
com.example.generated.GeneratedDocumentModule
```

业务代码只需 `addModule(GeneratedDocumentModule())`。

## 扩展点

1. **自定义处理器**：`codegen` 暴露 `DocumentEnumCodecProcessorProvider`（KSP 注册入口）
   与 `DocumentEnumCodecSourceGenerator` 接口，可替换源码生成实现（例如生成 Java 或
   接入其他模板），但当前发布物中只提供默认实现。
2. **源码生成器复用**：`DocumentCodecSourceGenerator` 可脱离 KSP 在自定义构建任务中使用。
3. **运行期转换器**：`DocumentRuntimeCodecFactory` 与 `addConverter` 组合，可对热点 Bean
   避免反射查找。
4. **KSP 多轮处理**：处理器实现 `process` 返回延迟符号列表（`deferred`），支持 KSP
   多轮生成场景。

## 限制

- 只支持 **public 的 Kotlin 顶层或嵌套枚举**；private/internal/protected 在 KSP 阶段报错
  （生成的是 public 转换器）；
- 同一编译单元内生成的转换器全限定类名必须唯一，重复时 KSP 报错；
- 每次编译**最多生成一个模块**，模块名/包名配置必须在所有标注枚举间保持一致；
- 注解保留策略为 `SOURCE`，处理器只读源码元数据；不进入运行时字节码；
- 不支持 Java 注解处理（`javax.annotation.processing`），只支持 KSP；
- 生成代码由 KSP 加入编译；若工程开启 ktlint `max_line_length`，需要把
  `build/generated` 排除在 lint 之外（本仓库默认未限制行宽）；
- 生成的模块只注册枚举转换器，不处理 data class / value class / sealed（这些由
  `serialization-kotlin` 负责）；
- 枚举转换器固定按 `name` 读写，不支持 `@SerializedName` 之类自定义枚举值映射；
  需要自定义映射时请手写 `DocumentBaseConverter`；
- KSP 版本必须与 Kotlin 版本匹配，否则编译失败。

## 构建与测试

`codegen` 自身的测试覆盖源码生成器与模块参数解析：

```bash
gradle :codegen:test
gradle :codegen:ktlintCheck
```

`codegen-test` 应用 KSP 插件、声明多个枚举，验证：

- 转换器/模块生成；
- `DocumentModule` setup 注册；
- JSON 枚举 parse/format round-trip；
- 非法名称返回 `null`。

```bash
gradle :codegen-test:test
```

## 坐标与 BOM

```kotlin
// 作为 KSP 处理器
ksp("com.mingliqiye.document:codegen:2.3.0")

// 使用 BOM 统一版本
dependencies {
    implementation(platform("com.mingliqiye.document:mingli-document-bom:2.3.0"))
    ksp("com.mingliqiye.document:codegen")
    api("com.mingliqiye.document:annotation")
    api("com.mingliqiye.document:serialization")
}
```

`codegen` 已被纳入 `bom/build.gradle.kts` 的 `constraints`；`codegen-test` 不对外发布。

相关文档：

- [annotation](./annotation.md)：`@DocumentEnumCodec` 的完整定义
- [serialization](./serialization.md)：`DocumentModule`、`addConverter`、转换器优先级
- [API 参考](../reference/api.md)：`DocumentMapperBuilder` 索引
