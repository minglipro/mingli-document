# record-support

`record-support` 是 Java record 支持的独立模块（JDK 17+）。它把 record 反射访问从
`serialization` 中拆出，让主库继续保持 JDK 8 字节码目标，同时让 JDK 17+ 用户可以按需启用
record 映射。

## 职责

- 实现 `com.mingliqiye.document.serialization.JavaRecordSupport` SPI：
  - `isRecord(type)`：判断类型是否为 record；
  - `components(type)`：读取 record 组件（名称、泛型类型、访问器方法、`RecordComponent`
    元素）；
- 直接使用 JDK 16+ 的 `Class.isRecord()` / `Class.getRecordComponents()` /
  `RecordComponent`，不再通过反射探测；
- 通过 `META-INF/services` 注册 provider，`serialization` 运行期用 `ServiceLoader` 加载。

## 坐标与依赖

```kotlin
implementation("com.mingliqiye.document:record-support:2.5.0") // 2.5.0 起提供（未发布）
```

- group：`com.mingliqiye.document`；artifactId：`record-support`；
- `api` 依赖 `serialization`，因此引入后无需重复声明 `serialization`；
- 自动模块名：`com.mingliqiye.document.record.support`（无显式 `module-info`，通过
  `META-INF/services` 提供服务）。

## 环境要求

| 项目 | 要求 | 说明 |
|---|---|---|
| 运行 JDK | **JDK 17+** | 主代码与测试均按 JDK 17 编译（class file 61） |
| 库其它模块 | JDK 8+ | `serialization` / `core` / `format-*` 等仍为 JDK 8 字节码 |
| 显式注册 | 不需要 | JDK 17+ 且在 classpath / module path 上即自动生效 |

## 工作原理

```text
record-support (JDK 17)
  └─ Jdk17RecordSupport : JavaRecordSupport
       META-INF/services/com.mingliqiye.document.serialization.JavaRecordSupport
                    │ ServiceLoader（运行期）
                    ▼
serialization (JDK 8)
  └─ JavaRecordSupportLoader
       ├─ JDK 16+ 且找到 provider → 使用 SPI
       └─ JDK 8-15 / 无 provider / LinkageError / ServiceConfigurationError → 安全降级
```

安全降级时：`isRecord` 恒为 `false`、`components` 恒为空列表，record 类型按普通 Bean
处理，不抛异常。加载器只在首次使用时执行一次，失败不缓存异常。

## 映射语义

引入本模块后，record 的映射行为与此前 `serialization` 内嵌实现一致：

- 序列化：组件访问器作为可读属性写出，支持 `@DocumentName`、`@DocumentIgnore`、
  `@DocumentNaming` / 全局命名策略、`@DocumentInclude` 写出策略；
- 反序列化：优先 `@DocumentCreator` 构造器；没有时绑定 canonical constructor，
  按组件名取值（可用 `@DocumentName` 覆盖）；支持嵌套 record、泛型集合 / Map、可空组件；
- 缺失字段按 `MissingFieldPolicy`；`FAIL_ON_NULL_FOR_PRIMITIVES` 对 record 组件生效；
- merge：record 是只读类型，返回新实例（原实例不变）。

## JPMS

模块路径上使用时，业务模块需要 require 本自动模块（否则 record-support 不会被解析，
record 映射自动降级）：

```java
requires com.mingliqiye.document.record.support;
```

`serialization` 的 `module-info` 已声明
`uses com.mingliqiye.document.serialization.JavaRecordSupport`，本模块的
`META-INF/services` 会被模块系统作为 `provides` 识别。

## GraalVM Native Image

- provider 由 Native Image 的 ServiceLoader 支持自动注册；
- record 类本身仍需按 Native Image 要求注册 record 组件
  （`{"name":"...","allRecordComponents":true}`），详见 [GraalVM](../guide/graalvm.md)；
- `serialization` jar 内嵌的 `--initialize-at-run-time=...JavaRecordSupportLoader`
  已覆盖 SPI 加载器的初始化时机。

## 限制

- 仅支持 JDK 17+ 运行时（JDK 16 无法加载本模块字节码）；
- 不引入本模块时 record 没有特殊映射，按普通 Bean 处理；
- SPI 仅做「识别 + 组件元数据读取」，映射规则仍全部由 `serialization` 实现，本模块不
  提供额外配置项；
- 与 `serialization-kotlin` 的 Kotlin data class 支持互不影响。

## 构建

```bash
gradle :record-support:test         # JDK 17 工具链
gradle :record-support:ktlintFormat
```

相关文档：[serialization](./serialization.md) ·
[serialization README](../../serialization/README.md) ·
[record-support README](../../record-support/README.md)
