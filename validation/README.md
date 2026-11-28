# validation

Jakarta Bean Validation（JSR 380）与 `serialization` 模块的集成。

通过 `DocumentMapperConfig.beanValidator` 把 Bean Validation 接入序列化/反序列化流程，
在根对象出入口统一执行校验；具体校验器实现由本模块提供。

## 主要能力

- `JakartaDocumentBeanValidator`：基于 `jakarta.validation.Validator` 的
  `DocumentBeanValidator` 实现
- `JakartaDocumentBeanValidator.create()`：使用默认 Jakarta Bean Validation Provider
  创建校验器
- 约束违规转换为 `DocumentSerializationException`，消息包含对象类型、字段路径与
  约束消息
- 支持 `ConstraintViolationException` / `Set<ConstraintViolation<*>>` 的异常转换
- 与 `serialization` 的根对象校验扩展点配合：`toNode`/`writeTo` 入口、
  `fromNode`/`fromTokens`/`readFrom` 出口各校验一次，嵌套对象不重复校验

## Maven

```kotlin
implementation("com.mingliqiye.document:validation:2.5.0")
```

模块只依赖 `jakarta.validation:jakarta.validation-api`，运行期需要自行提供
Jakarta Bean Validation Provider：

```kotlin
// Hibernate Validator（独立运行/Runtime 需要）
implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
implementation("org.glassfish:jakarta.el:4.0.2") // 消息插值需要 EL 实现
```

在 Jakarta EE 容器中通常由容器提供 Provider 与 EL 实现。

`create()` 每次调用都会创建独立的默认 `ValidatorFactory`，建议应用启动时创建一次
校验器并复用，而不是每次序列化时重新创建。

## 使用

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import com.mingliqiye.document.validation.JakartaDocumentBeanValidator

val config = DocumentMapperConfig.builder()
    .beanValidator(JakartaDocumentBeanValidator.create())
    .build()
val mapper = JsonMapper(config)

try {
    mapper.format(user)
} catch (e: DocumentSerializationException) {
    // Bean validation failed for com.example.User: name: must not be null; tags: size must be between 1 and 3
    println(e.message)
}
```

也可以包装已有的 `jakarta.validation.Validator`（例如自行配置 Provider、消息插值器）：

```kotlin
val validator = Validation.buildDefaultValidatorFactory().validator
val beanValidator = JakartaDocumentBeanValidator(validator)
```

自定义分组等场景可直接使用底层 `Validator` 校验，再转换异常：

```kotlin
val violations = validator.validate(user, MyGroup::class.java)
if (violations.isNotEmpty()) {
    throw JakartaDocumentBeanValidator.toDocumentSerializationException(violations, user)
}
```

## 异常消息

违规消息按“字段路径: 约束消息”拼接，多条违规按文本排序后用 `; ` 连接，
消息前缀为被校验对象的类型名。例如：

```text
Bean validation failed for com.example.User: address.city: must not be null; name: must not be null
```

集合元素路径由 Jakarta Bean Validation 生成，例如 `tags[0].<list element>`；
类级别约束没有字段路径，只输出约束消息。

## 校验时机与限制

- 校验发生在 Mapper 的根对象出入口：序列化根入口 `toNode`/`writeTo`，反序列化
  根出口 `fromNode`/`fromTokens`/`readFrom`；`format`/`parse` 等便捷 API 经过这些
  根路径，因此同样生效
- 每次根操作只校验一次：生成式编解码器、值类转换器等内部回调 Mapper 的嵌套调用
  不会重复校验
- 嵌套对象的级联校验由 Bean Validation 自身的 `@Valid` 语义决定，本模块不做递归
  遍历
- 默认只校验默认分组（`Default`）；需要分组时使用上面的底层 `Validator` 方案
- 只支持可产生约束违规的 Bean Validation；`merge` 等不经过根出入口的 API 不自动
  校验，需要时可在调用后手动校验
- 校验失败抛出的 `DocumentSerializationException` 由 Mapper 原样传播；校验器抛出
  的其他异常由 Mapper 包装并以 cause 保留原异常

## 构建

```bash
# 主代码按 JDK 8 目标编译；测试使用 JDK 17 运行时（Hibernate Validator 8 需要 Java 11+）
gradle :validation:test
```

文档：[根 README](../README.md) · [serialization](../serialization/README.md) · [贡献指南](../CONTRIBUTING.md)
