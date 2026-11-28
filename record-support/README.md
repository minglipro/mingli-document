# record-support

Java record 支持模块（JDK 17+）。

`serialization` 模块以 JDK 8 为字节码目标，不能直接引用 Java 16 新增的
`Class.isRecord()` / `Class.getRecordComponents()` / `RecordComponent`。本模块用 JDK 17
编译这部分反射访问，并通过 `META-INF/services` 注册为
`com.mingliqiye.document.serialization.JavaRecordSupport` SPI 实现：

- JDK 17+ 运行且本模块在 classpath / module path 上时，`JsonMapper`、`YamlMapper` 等
  所有格式 Mapper 自动获得 record 映射能力，无需注册代码；
- 不引入本模块（或运行在 JDK 8-15）时，`serialization` 对 record 类型安全降级为
  普通 Bean 行为，不抛异常。

## Maven

```kotlin
// JDK 17+
implementation("com.mingliqiye.document:record-support:2.5.1")
```

`record-support` 依赖 `serialization`，因此引入本模块后不需要再单独声明
`serialization`；格式模块与其它功能依赖保持不变。

## 快速开始

```java
// record 模型
public record Address(String city, int zipCode) {}

public record User(
        @DocumentName("display_name") String name,
        int age,
        Address address) {}
```

```kotlin
import com.mingliqiye.document.format.json.JsonMapper

// JDK 17+，classpath 上有 record-support 即可，无需注册
val mapper = JsonMapper()

val json = mapper.format(User("小于", 18, Address("杭州", 310000)))
// {"display_name":"小于","age":18,"address":{"city":"杭州","zipCode":310000}}

val user = mapper.parse(json, User::class.java)
```

## 环境要求

| 项目 | 要求 | 说明 |
|---|---|---|
| 运行 JDK | **JDK 17+** | 本模块主代码与测试均按 JDK 17 编译（class file 61），JDK 16 及以下无法加载 |
| 库其它模块 | JDK 8+ | `serialization` / `core` / `format-*` 等仍以 JDK 8 为字节码目标 |
| JPMS | 显式模块 `com.mingliqiye.document.record.support`（Multi-Release，JDK 9+） | `module-info` 用 `provides JavaRecordSupport`；classpath 仍用 `META-INF/services` |

## SPI 契约

`serialization` 定义的公共 SPI：

```kotlin
public interface JavaRecordSupport {
    fun isRecord(type: Class<*>): Boolean
    fun components(type: Class<*>): List<JavaRecordComponentInfo>
}

public class JavaRecordComponentInfo(
    val name: String,
    val type: Type,
    val accessor: Method,
    val element: AnnotatedElement,
)
```

本模块的实现 `Jdk17RecordSupport` 直接调用 JDK 17 API：

```text
isRecord(type)   -> type.isRecord()
components(type) -> type.getRecordComponents()
```

注册文件：

```text
META-INF/services/com.mingliqiye.document.serialization.JavaRecordSupport
  -> com.mingliqiye.document.serialization.record.Jdk17RecordSupport
```

`serialization` 侧使用 `ServiceLoader` 加载，并在以下情况安全返回「无 record 支持」：
JDK 8-15、找不到 provider、provider 类版本不兼容（`LinkageError`）或服务配置损坏
（`ServiceConfigurationError`）。

## 支持的 record 映射

引入本模块后，record 的映射语义与普通 Bean 一致：

- **序列化**：record 组件访问器（`name()`）作为可读属性写出，支持 `@DocumentName`、
  `@DocumentIgnore`、`@DocumentNaming` / 全局命名策略与 `@DocumentInclude` 写出策略；
- **反序列化**：优先使用 `@DocumentCreator` 构造器；没有标注时自动绑定 canonical
  constructor，按组件名（可用 `@DocumentName` 覆盖）取值；支持泛型组件、嵌套 record、
  集合 / Map / 可空与基本类型；
- **缺失字段**：按 `MissingFieldPolicy` 处理；`@DocumentIgnore` 组件既不写出也不要求；
- **只读语义**：record 组件按构造参数注入；merge 返回新实例（原实例不变）；
- **`FAIL_ON_NULL_FOR_PRIMITIVES`**：开启后 `null` 赋给基本类型组件会抛
  `DocumentSerializationException`。

## JPMS

本模块主 jar 是 Multi-Release 显式模块（主 classes 为 JDK 17 字节码，`module-info.class`
位于 `META-INF/versions/9/`）。模块路径上使用 record 支持时，业务模块需要 `requires` 本模块
（让模块被解析，`provides` 才会被 `ServiceLoader` 发现）：

```java
requires com.mingliqiye.document.record.support;
```

`serialization` 的 `module-info` 已声明
`uses com.mingliqiye.document.serialization.JavaRecordSupport`；本模块通过
`provides com.mingliqiye.document.serialization.JavaRecordSupport with
com.mingliqiye.document.serialization.record.Jdk17RecordSupport` 提供服务实现，jar 内同时
保留 `META-INF/services` 文件供 classpath 使用。未 require 本模块时 record 映射自动降级。

## GraalVM Native Image

- 本模块的 provider 由 Native Image 的 ServiceLoader 支持自动注册；
- **record 类本身**仍需要按 Native Image 要求注册 record 组件，例如
  `{"name":"com.example.User","allRecordComponents":true}`，详见
  [GraalVM 指南](../docs/guide/graalvm.md)；
- `serialization` jar 内嵌的 `--initialize-at-run-time` 参数已覆盖 SPI 加载器。

## 限制

- 仅支持 JDK 17+ 运行时；JDK 16 虽然已有 record 反射 API，但本模块字节码为 17，
  请升级到 JDK 17；
- 不引入本模块时，record 类型仍按普通 Bean 处理（可能写出空对象或无法实例化），
  这是刻意的安全降级，不是 bug；
- SPI 只负责「识别 record + 读取组件元数据」，record 的序列化/反序列化规则仍由
  `serialization` 统一实现，本模块不提供额外的映射配置；
- 与 Kotlin data class 支持（`serialization-kotlin`）互不影响。

## 构建

```bash
gradle :record-support:test        # JDK 17 工具链
gradle :record-support:ktlintFormat
```

文档：[serialization](../serialization/README.md) · [根 README](../README.md) ·
[GraalVM](../docs/guide/graalvm.md)
