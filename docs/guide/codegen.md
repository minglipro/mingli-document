---
title: 代码生成
---

# 代码生成

> `mingli-document` 提供四种「把映射工作交给生成的代码」的手段。本页说明它们各自的
> 坐标、用法、产物与限制，帮你判断该用哪一种。

## 目录

- [四种手段对比](#四种手段对比)
- [KSP 编译期枚举转换器](#ksp-编译期枚举转换器)
- [生成产物说明](#生成产物说明)
- [注解参数](#注解参数)
- [KSP 环境配置](#ksp-环境配置)
- [KSP 限制](#ksp-限制)
- [运行期属性访问器](#运行期属性访问器)
- [运行期 Bean 编解码器](#运行期-bean-编解码器)
- [编译期源码生成器](#编译期源码生成器)
- [如何选择](#如何选择)
- [性能与注意事项](#性能与注意事项)

## 四种手段对比

| 手段 | 触发时机 | 产物 | 适用场景 |
|---|---|---|---|
| `@DocumentEnumCodec`（KSP） | Kotlin 编译期 | 枚举转换器 + `DocumentModule` 源码 | 大量枚举需要统一注册 |
| `DocumentAccessorCodegen` | 运行期首次访问 | LambdaMetafactory 访问器（缓存） | 反射热路径优化 |
| `DocumentRuntimeCodecFactory` | 运行期按需构建 | 单个 Bean 的 `DocumentBaseConverter` | 不想写转换器、又想要稳定路径 |
| `DocumentCodecSourceGenerator` | 构建脚本手动调用 | Kotlin 源码字符串 | 不想引入 KSP、只想生成源码 |

四者都**不是必需的**：不配置时使用通用反射映射，功能完整。

## KSP 编译期枚举转换器

`codegen` 模块是一个 KSP 处理器。它扫描被 `@DocumentEnumCodec` 标注的枚举，
在编译阶段生成：

1. `<枚举名>DocumentConverter : DocumentBaseConverter<枚举>`；
2. 一个 `DocumentModule`，在 `setupModule` 中注册全部生成的转换器。

Maven 坐标（已在 BOM 中管理）：

```text
com.mingliqiye.document:codegen
```

注解位于 `annotation` 模块：

```kotlin
package com.mingliqiye.document.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class DocumentEnumCodec(
    val converterPackage: String = "",
    val converterName: String = "",
    val moduleName: String = "",
)
```

使用：

```kotlin
import com.mingliqiye.document.annotation.DocumentEnumCodec

@DocumentEnumCodec
public enum class Pet {
    CAT,
    DOG,
}
```

编译后可以在业务代码中直接使用生成的模块：

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.generated.GeneratedDocumentModule

val mapper = JsonMapper.builder()
    .addModule(GeneratedDocumentModule())
    .build()

mapper.format(Pet.DOG)                        // "DOG"
mapper.parse("\"CAT\"", Pet::class.java)      // Pet.CAT
mapper.parse("\"UNKNOWN\"", Pet::class.java)  // null
```

## 生成产物说明

默认生成在 `com.mingliqiye.document.generated` 包下（`DocumentCodegenDefaults`）：

```text
com.mingliqiye.document.generated.PetDocumentConverter
com.mingliqiye.document.generated.GeneratedDocumentModule
```

### PetDocumentConverter

```kotlin
public class PetDocumentConverter : DocumentBaseConverter<Pet> {
    override fun getType(): DocumentTypeReference<Pet> =
        DocumentTypeReference.of(Pet::class.java)

    override fun serialize(value: Pet, context: SerializationContext): Node =
        StringNode.of(value.name)

    override fun deserialize(node: Node, context: DeserializationContext): Pet? =
        try {
            Pet.valueOf(node.asString())
        } catch (_: Exception) {
            null
        }
}
```

行为要点：

- 序列化写出枚举 `name`（`StringNode`）；
- 反序列化按 `valueOf` 还原；
- 非法名称返回 `null`（而不是抛异常）；
- 生成的转换器是 `DocumentBaseConverter`，既可用于序列化也可用于反序列化。

### GeneratedDocumentModule

```kotlin
public class GeneratedDocumentModule : DocumentModule {
    override val moduleName: String = "GeneratedDocumentModule"

    override fun setupModule(context: ModuleSetupContext) {
        context.addConverter(PetDocumentConverter())
        context.addConverter(PriorityDocumentConverter())
        // ...
    }
}
```

- `moduleName` 固定为 `GeneratedDocumentModule`（或注解里自定义的名字）；
- 未指定 `moduleName` 时，模块固定生成在默认包，并注册**全部**生成的转换器
  （包括自定义包名的转换器，模块内使用全限定名引用）；
- 同一个 Mapper 可以同时 `addModule(...)` 多个模块；
- 生成的转换器也可以单独使用（`new PetDocumentConverter()`）。

### 自定义包名与类名

```kotlin
@DocumentEnumCodec(converterPackage = "com.example.generated")
public enum class Status { ON, OFF }
```

生成 `com.example.generated.StatusDocumentConverter`。

```kotlin
@DocumentEnumCodec(
    converterPackage = "com.example.generated",
    converterName = "StatusCodec",
)
public enum class Status { ON, OFF }
```

生成 `com.example.generated.StatusCodec`。

## 注解参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| `converterPackage` | `""`（即 `com.mingliqiye.document.generated`） | 生成转换器的包名 |
| `converterName` | `""`（即 `<枚举名>DocumentConverter`） | 生成转换器的类名 |
| `moduleName` | `""`（即 `GeneratedDocumentModule`） | 生成模块的类名；模块包名取自定义该参数的枚举的 `converterPackage` |

`moduleName` 由多个枚举同时指定时，包名与类名必须完全一致，否则 KSP 报错。
未指定时模块固定生成在默认包 `com.mingliqiye.document.generated`，并注册全部转换器。

## KSP 环境配置

根工程声明插件（KSP 版本必须与 Kotlin 2.0.0 匹配）：

```kotlin
plugins {
    kotlin("jvm") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
}
```

业务模块应用插件并声明处理器依赖，`annotation` 与 `serialization` 需要在编译类路径上：

```kotlin
plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

dependencies {
    api("com.mingliqiye.document:annotation")
    api("com.mingliqiye.document:serialization")
    ksp("com.mingliqiye.document:codegen")
}
```

构建后生成的源码位于 `build/generated/ksp/<variant>/kotlin`，由 KSP 自动加入编译：

```bash
gradle build
# 或单独触发
gradle kspKotlin
```

本仓库内 `codegen-test` 模块就是集成示例（不发布）：

```bash
gradle :codegen-test:test
```

它验证：转换器/模块生成、`DocumentModule` setup 注册、JSON 枚举 parse/format
round-trip、非法名称返回 `null`。

## KSP 限制

- **只支持 public 的 Kotlin 顶层或嵌套枚举**。`private` / `internal` / `protected`
  枚举会在 KSP 阶段报错，因为生成的是 public 转换器；
- **同一编译单元内生成的转换器全限定类名必须唯一**，重复时 KSP 报错；
- **每次编译最多生成一个模块**，模块名/包名配置必须在所有标注枚举间保持一致；
- 注解保留策略为 `SOURCE`，处理器只读取源码元数据，注解不会进入运行时字节码；
- 生成的源码由 KSP 加入编译。若工程开启 ktlint `max_line_length`，需要把
  `build/generated` 排除在 lint 之外（本仓库默认未限制行宽）；
- 只生成枚举转换器，不生成 Bean 映射代码。

## 运行期属性访问器

`DocumentAccessorCodegen`（`com.mingliqiye.document.serialization.codegen`）为属性
getter/setter 生成直接调用代码，避免热路径上的反射查找：

```kotlin
import com.mingliqiye.document.serialization.codegen.DocumentAccessorCodegen

val codegen = DocumentAccessorCodegen()

val getter: Function<Any, Any?>? = codegen.getter(User::class.java, "name")
val setter: BiConsumer<Any, Any?>? = codegen.setter(User::class.java, "name")

val user = User()
setter?.accept(user, "小于")
println(getter?.apply(user)) // 小于
```

实现要点：

- 通过 LambdaMetafactory 生成方法句柄调用（`BeanAccessorFactory`）；失败时回退普通
  反射 `method.invoke` / `field.get`；
- 找不到可用访问器时返回 `null`，由调用方决定回退策略；
- 结果按 `(类型, 属性名)` 缓存在 `ConcurrentHashMap`；
- 属性解析顺序：`getXxx()` / `isXxx()` 方法 → 字段；
  setter：`setXxx(...)` 方法 → 非 `final` 字段。

主要用途：作为 `DocumentRuntimeCodecFactory` 与 Mapper 内部访问器的加速层；
普通业务代码一般不需要直接调用。

## 运行期 Bean 编解码器

`DocumentRuntimeCodecFactory` 在运行期为一个 Bean 类型构建
`DocumentBaseConverter`，注册后该类型走预构建的字段访问路径：

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.codegen.DocumentRuntimeCodecFactory

val factory = DocumentRuntimeCodecFactory(
    namingStrategy = PropertyNamingStrategy.SNAKE_CASE,
    writeNulls = false,
)

val mapper = JsonMapper.builder()
    .addConverter(factory.converter(User::class.java))
    .build()

mapper.format(User().apply { userName = "小于" }) // {"user_name":"小于"}
```

构造参数：

| 参数 | 默认 | 说明 |
|---|---|---|
| `namingStrategy` | `AS_IS` | 字段名策略 |
| `writeNulls` | `false` | null 字段是否写出（注意与 `DocumentMapperConfig.writeNulls` 默认 `true` 不同） |

支持：

- `@DocumentName`（字段 / getter / Kotlin 属性注解）；
- `@DocumentIgnore`（字段 / getter）；
- 命名策略；
- 无参构造器 + setter 反序列化；
- 父类字段（逐级向上收集 `declaredFields`，跳过 static / transient / synthetic /
  `$` 前缀字段）；
- 相同类型复用缓存的转换器实例（`ConcurrentHashMap`）。

限制（源码注释明确列出）：

- 不支持构造器参数注入（`@DocumentCreator`）；
- 不支持 Builder；
- 不支持不可变对象（没有 setter 的字段反序列化会被跳过）；
- 不支持多态子类型；
- 反序列化需要无参构造器（反射创建，不存在时抛 `DocumentSerializationException`）。

也就是说：它适合**简单可变 POJO** 的批量加速；复杂对象请用通用映射或
`DocumentCodecSourceGenerator`。

## 编译期源码生成器

`DocumentCodecSourceGenerator` 不依赖 KSP：你在 Gradle 任务里调用它，得到源码字符串，
写入源码目录后由编译器编译。

```kotlin
import com.mingliqiye.document.serialization.codegen.DocumentCodecSourceGenerator

val generator = DocumentCodecSourceGenerator()

// 1) 枚举转换器源码
val converterSource: String = generator.enumConverter(
    Status::class.java,
    "com.example.generated",
)
// 可选自定义类名：
val namedSource: String = generator.enumConverter(
    Status::class.java,
    "com.example.generated",
    converterName = "StatusCodec",
)

// 2) 注册全部转换器的模块源码
val moduleSource: String = generator.module(
    packageName = "com.example.generated",
    moduleName = "GeneratedDocumentModule",
    converterTypes = listOf("com.example.generated.StatusDocumentConverter"),
)
```

典型 Gradle 用法（自定义 task，把源码写到 `build/generated/sources` 并加入 sourceSet）：

```kotlin
val generateDocumentCodecs by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/document-codecs").get().asFile
    outputs.dir(outputDir)
    doLast {
        val generator = com.mingliqiye.document.serialization.codegen.DocumentCodecSourceGenerator()
        val name = generator.enumConverter(
            Class.forName("com.example.Status"),
            "com.example.generated",
        )
        val dir = outputDir.resolve("com/example/generated")
        dir.mkdirs()
        dir.resolve("StatusDocumentConverter.kt").writeText(name)
    }
}
```

生成代码与 KSP 产物结构一致：转换器实现 `DocumentBaseConverter`，模块实现
`DocumentModule` 并在 `setupModule` 中 `context.addConverter(...)`。

## 如何选择

| 需求 | 推荐 |
|---|---|
| 项目里枚举很多，想让枚举统一以 name 序列化并注册 | KSP `@DocumentEnumCodec` |
| 不想引入 KSP / 构建链简单 | `DocumentCodecSourceGenerator` 手动生成 |
| 大量简单 POJO，反射是热点 | `DocumentRuntimeCodecFactory` |
| 只要加速属性访问 | `DocumentAccessorCodegen` |
| 复杂对象（构造器注入、Builder、record、多态） | 通用映射，或手写转换器 |

## 性能与注意事项

- 代码生成不是银弹：先跑 `gradle :benchmarks:jmh` 定位瓶颈；
- `DocumentRuntimeCodecFactory` 构建的转换器按类型缓存，但**每个 Factory 实例独立
  缓存**，建议全局复用一个 Factory；
- KSP 生成代码会增加编译时间，枚举数量少时收益有限；
- 运行期转换器的 `writeNulls` 与 Mapper 配置独立，注意不要配错导致 null 行为不一致；
- 生成的 `DocumentModule` 与 `kotlinModule()` 可以同时注册，互不冲突；
- 生成的源码要纳入版本管理时注意与 `@DocumentEnumCodec` 的注解参数保持一致。

下一步：[Fuzz 与安全](/guide/fuzz-security.html) 或 [常见问题](/guide/faq.html)。
