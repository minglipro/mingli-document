# 贡献与代码规范

本文件定义 `com.mingliqiye.document` 项目的代码、模块、接口、提交和发布规范。

目标：

- 公共 API 先定义契约，再提供实现；
- 模块职责清晰，依赖方向不能反向；
- 每个改动都能独立提交、独立构建、独立测试；
- 代码格式、文档、测试保持统一。

---

## 1. 基本原则

1. 一个提交只做一件事。
2. 每个提交必须能通过构建和 ktlint。
3. 公共行为必须有稳定接口；实现类不能泄漏到公共 API。
4. 模块之间只能按依赖方向引用，不允许循环依赖。
5. 先设计 API，再写实现；不要先写一个万能类再拆。
6. 不为“看起来高级”而增加抽象。

---

## 2. 模块与依赖

当前模块：

| 模块 | 职责 | 对外依赖 |
|---|---|---|
| `annotation` | 格式无关注解 | 无 |
| `exception` | 文档异常基类 | `mingli-exception` |
| `core` | 文档树、类型引用契约 | `exception` |
| `format-json` | JSON Mapper | `serialization`、`core` |
| `format-yaml` | YAML Mapper | `serialization`、`core`、SnakeYAML |
| `serialization` | Java 实体与树之间的映射、DocumentMapper 基类 | `core`、`annotation` |
| `serialization-kotlin` | Kotlin data class 模块 `KotlinModule` | `serialization`、`annotation`、`kotlin-reflect` |

允许的依赖方向：

```text
annotation       独立

exception
    ↑
core
 └── serialization
      ├── format-json
      ├── format-yaml
      └── serialization-kotlin
```

规则：

- `core` 不能依赖任何具体格式模块。
- `annotation` 不能依赖 `core`、`format-json`、`format-yaml`、`serialization`。
- `format-*` 依赖 `serialization` 与 `core`，通过继承 `DocumentMapper` 暴露格式 Mapper。
- `serialization-kotlin` 提供 `KotlinModule` 与 kotlin-reflect，不绑定具体格式。
- `serialization` 主代码不依赖具体格式。
- 测试可以临时依赖其他模块做集成验证，但不能把测试依赖带入主代码 API。
- 传递暴露给使用方的类型使用 `api`；纯实现依赖使用 `implementation`。

---

## 3. 接口与实现规范

### 3.1 必须接口化的内容

以下内容必须“接口 + 默认实现”：

- 服务、编解码器、解析器、序列化器
- 可替换策略、扩展点、注册表
- 树节点等需要被调用方类型判断的契约
- 需要多实现或未来会多实现的组件

示例：

```text
DocumentMapperApi          -> DefaultDocumentMapper
DocumentMapper             -> JsonMapper / YamlMapper
Node                       -> DefaultObjectNode 等
JsonConverter              -> 用户自定义实现
```

### 3.2 不强制接口化的内容

以下内容保留 Kotlin 原生形式，不强行制造无意义的 `XxxApi`：

- `enum class`
- 注解 `annotation class`
- 不可变值对象
- 异常类层次
- 纯数据 DTO
- 模块内部的具体实现类（`internal` 或 `private`）

判断标准：

> 如果这个类型没有可替换行为，不要为了“全部接口化”增加空接口。

内部实现类只是实现细节，不需要为了拆分而暴露成 SPI。只有当调用方确实需要替换该行为时，才把它提升为公开接口。

### 3.3 命名

| 类型 | 命名 |
|---|---|
| 行为 API | `XxxApi` |
| 角色接口 | `Xxx`，例如 `Node`、`JsonSerializer` |
| 默认实现 | `DefaultXxx` |
| 仅内部实现 | `internal class XxxImpl` 或 `internal class DefaultXxx` |
| 异常 | `XxxException` |
| 注解 | `Xxx` |
| 测试 | `XxxTest` |

禁止：

- `IUserService` 这种匈牙利命名
- 公共 API 直接出现 `Default` 实现类
- 没有实际替换价值的 `XxxInterface`

### 3.4 实现类可见性

- 只给内部用的实现类必须是 `internal`。
- 需要让使用方自行实例化的默认实现才 `public`，并命名为 `DefaultXxx`。
- 接口方法只暴露接口类型，不返回实现类型。
- 实现类的构造函数优先 `internal`，通过工厂或 Builder 创建。

### 3.5 默认方法

接口默认方法只用于：

- 稳定的便捷重载
- 不依赖可变状态的组合逻辑

不要：

- 在接口默认方法里写大量业务逻辑
- 在接口中持有全局状态

### 3.6 文档树规划

按接口 + 默认实现重构：

```text
Node
├── ObjectNode          -> DefaultObjectNode
├── ArrayNode           -> DefaultArrayNode
├── NumberNode          -> DefaultNumberNode
├── StringNode          -> DefaultStringNode
├── BooleanNode         -> DefaultBooleanNode
└── NullNode            -> DefaultNullNode
```

工厂统一放在对应接口的伴生对象或 `Nodes` 工具中，调用方不直接 new 实现类。

### 3.7 Mapper 规划

```text
DocumentMapperApi
    └── DocumentMapper（serialization 基类）
          ├── JsonMapper（format-json）
          └── YamlMapper（format-yaml）
```

`DocumentMapper` 负责编排与实体映射，内部通过 `private` 方法或 `internal` 具体类组织反射、类型转换和集合处理。
格式模块只负责提供文本解析/写出函数，并继承 `DocumentMapper`。

规则：

- 不新增内部 SPI 接口，除非确认存在第二个可替换实现；
- 不引入 Java `ServiceLoader` / `META-INF/services` 自动发现；
- 内部格式 Codec 不对外暴露；
- 对外扩展点只保留：`JsonSerializer`、`JsonDeserializer`、`JsonConverter`；
- 未来 XML 模块继承 `DocumentMapper`，例如 `XmlMapper`。
- 跨格式能力通过 `DocumentModule` 注册，使用 `JsonMapper.builder().addModule(...)`。

---

## 4. Kotlin 编码规范

### 4.1 显式 API

所有模块启用 Kotlin Explicit API Mode：

- public 声明显式写 `public`
- public 函数显式写返回类型
- 不为内部实现补无意义的 `public`

### 4.2 可见性

- 默认使用 `private`。
- 同模块使用 `internal`。
- 只有真正对外 API 才 `public`。
- 不用 `protected` 暴露内部实现。

### 4.3 空安全

- 禁止无说明的 `!!`。
- 优先使用 `require`、`check`、`requireNotNull`、`checkNotNull`。
- 公共 API 的 null 语义必须在 KDoc 写清楚。
- 不使用平台类型直接穿过公共 API。

### 4.4 不可变性

- 默认 `val`。
- 集合返回只读视图或不可变集合。
- 公共数据对象优先不可变。
- 可变状态必须在 KDoc 说明线程安全策略。

### 4.5 异常

- 统一使用 `exception` 模块的异常层次。
- 不给控制流使用异常；可预期失败优先返回结果或可空值。
- 不允许吞异常后返回默认值，除非方法名和 KDoc 明确表达。
- 包装底层异常时必须保留 `cause`。
- 禁止在公共 API 抛出无信息的 `RuntimeException`。

### 4.6 反射

- 反射只允许出现在 `serialization` 模块。
- 反射入口必须有缓存。
- 反射失败必须转换为 `DocumentSerializationException`，不能抛裸 `ReflectiveOperationException`。
- 反射实现不能进入 `core` 公共 API。

---

## 5. Java 实体规范

`serialization` 模块支持 Java Bean：

- 提供 public 无参构造器；
- 使用 public getter/setter；
- 字段默认 `private`；
- 不依赖 Lombok；
- 不依赖运行时代理；
- Java 测试实体使用 `src/test/java`；
- Java 源码保持 Java 8 兼容。

---

## 6. KDoc 规范

所有 public API 必须有 KDoc：

- 类、接口：职责、使用场景、限制
- 函数：`@param`、`@return`、`@throws`
- 属性：含义、是否可空
- 泛型：`@param T`
- 复杂实现：说明为什么这样做

要求：

- 不写“设置 xxx”这种同义反复；
- 说明边界条件；
- 说明线程安全；
- 说明异常条件；
- 接口和实现不要复制两份互相矛盾的文档。

---

## 7. 代码格式与静态检查

以 ktlint 为准：

```bash
gradle ktlintFormat
gradle ktlintCheck
```

规则：

- 4 空格缩进，禁止 Tab；
- UTF-8；
- 按 ktlint 自动排序 import；
- 禁止无意义通配符 import；
- 不手写与 ktlint 冲突的格式；
- 格式化改动和逻辑改动分开提交。

每个模块都必须应用：

```kotlin
id("org.jlleitschuh.gradle.ktlint")
```

---

## 8. 测试规范

- 每个模块独立测试。
- 使用 `kotlin.test` / JUnit Platform。
- 接口必须有契约测试。
- 默认实现必须有行为测试。
- 反序列化必须有异常路径测试。
- 不依赖外网、外部数据库、真实文件系统路径。
- 测试方法名表达“场景 + 预期”。

最低验证：

```bash
gradle ktlintCheck
gradle build
```

涉及公共 API 的改动，必须同时补测试或说明为什么不需要。

---

## 9. Git 提交规范

### 9.1 提交粒度

**每完成一个可编译的小功能就提交。**

禁止把以下内容放在同一个提交：

- 格式化 + 逻辑
- 模块重构 + 新功能
- 多个无关模块
- 测试修复 + 生产代码修复

一次提交应当能用一句话说清楚。

### 9.2 提交格式

```text
<type>(<scope>): <subject>

<body>
```

type：

- `feat`：新增功能
- `fix`：修复缺陷
- `refactor`：重构，不改变外部行为
- `test`：测试
- `docs`：文档
- `build`：构建、依赖、Gradle
- `chore`：杂项
- `style`：纯格式
- `perf`：性能

scope：

```text
annotation
exception
core
format-json
serialization
build
docs
```

示例：

```text
feat(core): 增加 ObjectNode 接口

refactor(core): 使用 DefaultObjectNode 实现 ObjectNode

test(core): 覆盖 ObjectNode 类型转换

docs: 定义贡献与代码规范

build(serialization): 补充 format-json 测试依赖
```

### 9.3 提交前检查

- [ ] 只包含本次提交相关文件
- [ ] 编译通过
- [ ] ktlint 通过
- [ ] 测试通过
- [ ] 没有提交 `build/`、`.gradle/`、IDE 临时文件
- [ ] 没有密钥、密码、token、私服凭据

### 9.4 分支与推送

- `dev`：日常集成分支
- `feat/<topic>`：功能分支
- `fix/<topic>`：修复分支
- 不直接 force-push 已共享分支
- 推送前先同步远程
- tag 使用语义化版本，例如 `1.0.0`

---

## 10. 公共 API 变更

- 新增 API：补 KDoc、测试和对应模块说明。
- 破坏性变更：必须升级 minor/major 版本并写 CHANGELOG。
- 废弃 API：先标 `@Deprecated`，说明替代方案。
- 删除 API：至少跨一个发布周期，或明确说明是实验 API。

---

## 11. Review 清单

每个 PR / 提交审查：

- [ ] 是否先定义接口，再写实现？
- [ ] 实现类是否被错误暴露到公共 API？
- [ ] 模块依赖方向是否正确？
- [ ] 是否有无意义的抽象？
- [ ] 异常是否保留 cause？
- [ ] 公共 API 是否有 KDoc？
- [ ] 是否有契约测试和异常测试？
- [ ] ktlint / build 是否通过？
- [ ] 提交粒度是否单一？
- [ ] 是否更新 CHANGELOG / 文档？

---

## 12. Definition of Done

一个任务完成必须同时满足：

- 接口契约已定义；
- 默认实现已提供；
- 公共 API 有 KDoc；
- 正常路径和异常路径有测试；
- `gradle ktlintCheck` 通过；
- `gradle build` 通过；
- 代码已按单一职责拆分；
- 已提交，提交信息符合规范。

---

## 13. 当前待重构清单

- [x] 当前工作区按模块拆成原子提交
- [x] `core` 树节点改为接口 + Default 实现
- [x] `DocumentTypeReference` 改为接口 + 默认实现
- [x] `format-json` 的 parser/writer 收起实现细节
- [x] `DefaultDocumentMapper` 收起为内部实现
- [x] `annotation` 模块定义首批注解
- [x] 所有模块启用 Explicit API Mode
- [x] 接口契约测试补齐
- [x] YAML 适配器接入（`format-yaml`）
- [x] Kotlin data class 实例化模块（`serialization-kotlin`）
- [ ] XML 适配器在 core API 稳定后接入
- [ ] 补充 CI 门禁
- [ ] document 私服发布配置
