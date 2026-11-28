# Bean Validation

## 坐标

```kotlin
implementation("com.mingliqiye.document:validation:2.4.0")
```

需要运行期提供 Jakarta Validation Provider，例如 Hibernate Validator。

## 能力

```text
Jakarta Bean Validation 集成
根对象序列化/反序列化校验
字段级/嵌套/集合元素约束
与 DocumentMapperConfig 集成
```

## 快速开始

```kotlin
val validator = JakartaDocumentBeanValidator.create()

val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .beanValidator(validator)
        .build(),
)

val user = mapper.parse(json, User::class.java)
```

`parse` 返回前校验结果，`format`/`formatBytes` 写出前校验根对象。

## 手动校验

```kotlin
try {
    validator.validate(user)
} catch (e: DocumentSerializationException) {
    println(e.message)
}
```

## 消息格式

```text
Bean validation failed for com.example.User
name: must not be null; tags[0]: size must be between 1 and 10
```

## 约束示例

```kotlin
data class Address(
    @field:NotBlank val street: String,
)

data class User(
    @field:NotNull val id: Long?,
    @field:Size(min = 1, max = 32) val name: String,
    @field:Valid val address: Address,
    val tags: List<@Size(min = 1) String>,
)
```

## 自定义校验器

```kotlin
val custom = DocumentBeanValidator { value ->
    if (value is User && value.name.isEmpty()) {
        throw DocumentSerializationException("name is empty")
    }
}
```

通过 `DocumentMapperConfig.builder().beanValidator(custom)` 接入，不依赖 Jakarta。

## 限制

```text
默认只校验默认分组
嵌套级联依赖 @Valid
merge 不自动校验
convert 分别校验源和目标
根对象只校验一次
```
