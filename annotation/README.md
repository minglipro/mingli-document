# annotation

格式无关的文档注解模块。

注解位于：

```text
com.mingliqiye.document.annotation
```

## 注解

| 注解 | 作用 |
|---|---|
| `@DocumentName("name")` | 指定文档字段名 |
| `@DocumentIgnore` | 忽略字段 |
| `@DocumentNaming(DocumentNamingStrategy.SnakeCaseStrategy)` | 类级/字段级命名策略覆盖 |
| `@DocumentInclude(DocumentIncludePolicy.NON_NULL)` | 类级/字段级 null/空值写出策略 |
| `@DocumentConverter(XxxConverter::class)` | 字段专用双向转换器 |
| `@DocumentSerializer(XxxSerializer::class)` | 字段专用序列化器 |
| `@DocumentDeserializer(XxxDeserializer::class)` | 字段专用反序列化器 |
| `@DocumentTypeInfo(property)` | 多态基类/接口的类型属性名 |
| `@DocumentTypeName("name")` | 多态子类的类型名 |
| `@DocumentCreator` | 标记反序列化构造器 |
| `@DocumentBuilder` | 标记 Builder 静态工厂方法 |
| `@DocumentBuild` | 标记 Builder 的 build 方法 |
| `@DocumentDateTimeFormat(pattern, timezone)` | 日期时间格式 |
| `@DocumentNumberFormat(pattern, locale)` | 数字格式 |
| `@DocumentRootName("root")` | XML 根元素名 |

## 命名策略：@DocumentNaming

```kotlin
import com.mingliqiye.document.annotation.DocumentNaming
import com.mingliqiye.document.annotation.DocumentNamingStrategy.KebabCaseStrategy
import com.mingliqiye.document.annotation.DocumentNamingStrategy.SnakeCaseStrategy

@DocumentNaming(SnakeCaseStrategy)   // 类级：该类所有未显式命名的属性
class User {
    var userName: String = ""        // 写出 user_name

    @field:DocumentNaming(KebabCaseStrategy)  // 字段级覆盖类级
    var displayName: String = ""     // 写出 display-name

    @field:DocumentName("custom")    // @DocumentName 最高优先级
    var explicit: String = ""
}
```

`DocumentNamingStrategy` 取值（Kotlin 可导入枚举项后直接写，Java 写
`DocumentNamingStrategy.SnakeCaseStrategy`）：

```text
AsIsStrategy        原样
SnakeCaseStrategy   user_name
KebabCaseStrategy   user-name
UpperCamelStrategy  UserName
LowerCamelStrategy  userName
```

优先级：`@DocumentName` > 字段级 `@DocumentNaming` > 类级 `@DocumentNaming` >
全局 `DocumentMapperConfig.namingStrategy`。

## 写出策略：@DocumentInclude

```kotlin
import com.mingliqiye.document.annotation.DocumentInclude
import com.mingliqiye.document.annotation.DocumentIncludePolicy
import com.mingliqiye.document.annotation.DocumentIncludePolicy.NON_EMPTY

@DocumentInclude(NON_EMPTY)          // 类级：null/空字符串/空集合/空 Map/空数组不写出
class Order {
    var remark: String = ""

    @field:DocumentInclude(DocumentIncludePolicy.ALWAYS)  // 总是写出，即使为 null
    var note: String? = null
}
```

`DocumentIncludePolicy` 取值：

```text
ALWAYS        总是写出
NON_NULL      null 不写出
NON_EMPTY     null/空字符串/空集合/空 Map/空数组不写出
USE_DEFAULTS  回退到下一优先级，最终使用全局 writeNulls
```

优先级：字段级 `@DocumentInclude` > 类级 `@DocumentInclude` > 全局
`DocumentMapperConfig.writeNulls`。只影响序列化，反序列化不受影响。

## Maven

```kotlin
implementation("com.mingliqiye.document:annotation:2.4.0")
```

## 示例

```kotlin
import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName

class User {
    @field:DocumentName("display_name")
    var name: String = ""

    @field:DocumentIgnore
    var internalId: Long = 0
}
```

注解不依赖任何具体格式；由 `serialization` 和各 `format-*` 模块读取。

`annotation` 中的 `DocumentBaseConverter` / `DocumentBaseSerializer` /
`DocumentBaseDeserializer` 是注解参数使用的标记接口；真正的功能接口位于
`serialization` 模块并继承这些标记。

## Kotlin 使用位置

Kotlin 属性上的注解默认落在**字段**上；需要注解 getter / setter 时使用 use-site
target（`@get:` / `@set:`），例如：

```kotlin
class Bean {
    @field:DocumentNaming(SnakeCaseStrategy)
    var userName: String = ""

    @get:DocumentInclude(DocumentIncludePolicy.NON_NULL)
    var nickname: String? = null
}
```

`@DocumentNaming` / `@DocumentInclude` 也支持标注在 Java getter（`FUNCTION` target）与
`@DocumentCreator` 构造参数（`VALUE_PARAMETER` target）上。

## 构建

```bash
gradle :annotation:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
