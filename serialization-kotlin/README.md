# serialization-kotlin

Kotlin data class 支持模块。

提供 `kotlinModule()`，让 Mapper 在没有无参构造器时仍能实例化 data class。

## Maven

```kotlin
implementation("com.mingliqiye.document:serialization-kotlin:1.1.0")
```

## 示例

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.kotlinModule

data class User(val name: String, val age: Int)

val mapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .build()

val json = mapper.format(User("小于", 18))
val user = mapper.parse(json, User::class.java)
```

## 支持

- data class 主构造器
- 默认参数
- 可空参数
- 嵌套 data class
- 泛型集合
- 字段注解
- Token 路径直接实例化

## 构建

```bash
gradle :serialization-kotlin:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
