# mingli-document-codegen

`mingli-document-codegen` 是 mingli-document 的编译期 KSP 代码生成模块。它在 Kotlin
编译阶段扫描被 `@DocumentEnumCodec` 标注的枚举，生成对应的枚举转换器与一个注册全部
转换器的 `DocumentModule`。

## 使用方式

根工程声明 KSP Gradle 插件（版本必须与 Kotlin 2.5.1 匹配）：

```kotlin
plugins {
    kotlin("jvm") version "2.5.1" apply false
    id("com.google.devtools.ksp") version "2.5.1-1.0.21" apply false
}
```

业务模块应用插件并声明处理器依赖，`annotation` 与 `serialization` 需要出现在编译类路径上：

```kotlin
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

为枚举添加注解即可：

```kotlin
@DocumentEnumCodec
public enum class Pet {
    CAT,
    DOG,
}
```

编译后生成：

- `com.mingliqiye.document.generated.PetDocumentConverter : DocumentBaseConverter<Pet>`：
  序列化写出枚举 `name`，反序列化按 `valueOf` 还原，非法名称返回 `null`；
- `com.mingliqiye.document.generated.GeneratedDocumentModule : DocumentModule`：
  `setupModule` 中注册全部生成的转换器。

业务代码直接注册模块：

```kotlin
val mapper = JsonMapper.builder()
    .addModule(GeneratedDocumentModule())
    .build()
```

## 注解参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `converterPackage` | `com.mingliqiye.document.generated` | 生成转换器的包名 |
| `converterName` | `<枚举名>DocumentConverter` | 生成转换器的类名 |
| `moduleName` | `GeneratedDocumentModule` | 生成模块的类名；模块包名取自定义该参数的枚举的 `converterPackage` |

`moduleName` 由多个枚举同时指定时必须完全一致（包名与类名都相同），否则 KSP 报错；
未指定时模块固定生成在默认包 `com.mingliqiye.document.generated`，并注册**全部**转换器
（包括自定义包名的转换器，模块内使用全限定名引用）。

## 限制

- 只支持 public 的 Kotlin 顶层或嵌套枚举；private/internal/protected 枚举会在 KSP
  阶段报错，因为生成的是 public 转换器；
- 同一编译单元内生成的转换器全限定类名必须唯一，重复时 KSP 报错；
- 每次编译最多生成一个模块，模块名/包名的配置必须在所有标注枚举间保持一致；
- 注解保留策略为 `SOURCE`，处理器只读取源码元数据；不支持 Java 注解处理之外的反向生成；
- 生成的源码由 KSP 加入编译，使用 ktlint 等工具的工程若开启 `max_line_length`，需要把
  `build/generated` 排除在 lint 之外（本仓库默认未限制行宽，生成代码可直接通过 ktlint）。

## 测试

- `codegen`：源码生成器、模块参数解析的单元测试；
- `codegen-test`：应用 KSP 插件并声明多个枚举，验证转换器/模块生成、`DocumentModule`
  setup 注册、JSON 枚举 parse/format round-trip 与非法名称返回 `null`。
