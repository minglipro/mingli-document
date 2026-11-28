# 路线图

本页是仓库根目录 `ROADMAP.md` 的站点版，记录 `mingli-document` 的能力补齐路线、阶段
计划与各版本交付状态。勾选状态与仓库中的 ROADMAP 保持一致；个别条目在 CHANGELOG 中
有更细的实现说明，见文末「状态说明」。

## 推进方式

路线图按批次推进，每批完成后必须走完固定流程：

```text
实现 -> 模块测试 -> clean ktlintCheck build -> 文档 -> 原子提交
```

版本发布时统一从根 `gradle.properties` 的 `projectVersion` 取版本号，发布模块包括
`annotation` / `exception` / `core` / `schema` / `serialization` /
`serialization-kotlin` / `record-support` / `codegen` / `openapi` / `format-*` / `bom`；
`benchmarks`、`fuzz`、`codegen-test` 不发布。

## 阶段 0：发布基线

- [x] dev → master
- [x] 版本升级
- [x] 全量构建
- [x] Maven 私服发布
- [x] GitHub 推送

## 阶段 1：高级数据绑定

- [x] 字段命名策略（`SNAKE_CASE` / `KEBAB_CASE` / `UPPER_CAMEL` / `LOWER_CAMEL`）
- [x] 多态类型（type 字段 + 类型注册）
- [x] 构造器注入（`@DocumentCreator`）
- [x] Builder 模式实例化（`@DocumentBuilder` / `@DocumentBuild`）
- [x] 输入本地 `$ref` 解析
- [x] 对象身份引用序列化（`writeReferences`）
- [x] Java record（2.0.0 内嵌；2.5.0 起独立为 `record-support` 模块，JDK 17+）
- [x] Kotlin value class / sealed class
- [x] 只读属性
- [x] 字段 merge / update（`MergePolicy`）

> 对应 1.1.0 与 2.0.0 的能力；详见
> [serialization](../modules/serialization.md) 与
> [serialization-kotlin](../modules/serialization-kotlin.md)。

## 阶段 2：格式协议完整性

- [x] Avro：非默认 codec、`appendTo`
- [x] Avro：完整 logical type
- [x] Protobuf：extensions、group、MessageSet、proto3 语义
- [x] XML：混合内容顺序保留
- [x] CBOR sequence
- [x] CBOR：canonical map key 排序
- [x] CBOR：可选 tag 保真（`CborConfig.preserveTags`）
- [x] Ion：timestamp / symbol / clob / blob 类型保真（`IonConfig.preserveTypes`）
- [x] YAML：自定义 tag（跨文档 anchor 规范不支持）
- [x] TOML：注释保留
- [x] CSV：嵌套结构 / 多级 Schema
- [x] Smile：多消息流

> Avro logical type 与 TOML 注释保留均已完成。

## 阶段 3：性能与流式

- [x] CSV 原生 Token Reader
- [x] Properties 原生 Token Reader
- [x] TOML 原生 Token Reader
- [x] YAML 原生 Token Reader
- [x] XML 原生 Token Reader（默认配置）
- [x] JSON 紧凑模式纯字节写出
- [x] Schubfach Double 最短格式化
- [x] 运行期属性访问代码生成（`DocumentAccessorCodegen`）
- [x] 编译期枚举转换器源码生成（`DocumentCodecSourceGenerator`）
- [x] 运行期 Bean 编解码器生成（`DocumentRuntimeCodecFactory`）
- [x] 编译期注解处理器 / KSP（`codegen` 模块）
- [x] 大文件流式 Bean 映射
- [x] 对象池 / 零拷贝

> 未完成项：大文件流式 Bean 映射、对象池/零拷贝。

## 阶段 4：安全边界

- [x] Mapper 层 `maxNodeDepth` / `maxNodeCount`
- [x] CBOR / Smile 解析器 `maxDepth`
- [x] Protobuf 解析器 `maxDepth`
- [x] Avro 解析器 `maxDepth`
- [x] Ion 解析器 `maxDepth`
- [x] YAML 解析器 `maxDepth`
- [x] XML / Properties / TOML / CSV `maxDepth`
- [x] 全格式 `maxInputSize`
- [x] Node 树 `maxStringLength`
- [x] YAML alias 扩张限制
- [x] 持续 fuzz harness（`fuzzContinuous`）
- [x] Jazzer 覆盖率引导 fuzz
- [x] JQF 集成
- [x] 安全测试基线

> 状态说明：XML / Properties / TOML / CSV 的解析器 `maxDepth` 已在 2.4.0 补齐；此前
> ROADMAP 中「Token 深度校验」与「解析器深度」是两条不同路径，现在两者都有受控限制。
> `SECURITY.md` 记录安全边界基线。

## 阶段 5：Schema 与生态

- [x] JSON Schema（常用 draft-07 子集）
- [x] Schema Registry 适配
- [x] OpenAPI 3.0 生成
- [x] Bean Validation 集成
- [x] JPA 实体注解映射（`jpa` 模块）
- [x] Hibernate Session / EntityManager 运行期集成

> Schema Registry、Bean Validation、JPA 注解映射、Hibernate 运行期集成均已完成。

## 阶段 6：构建与发布

- [x] 自定义 Charset
- [x] 原子文件写入
- [x] 自动创建父目录
- [x] CI/CD（GitHub Actions）
- [x] Gitea CI（已放弃）
- [x] Maven Central 发布配置（已放弃）
- [x] SHA-256 校验和
- [x] CycloneDX JSON SBOM
- [x] JPMS 自动模块名（`Automatic-Module-Name`）
- [x] JPMS `module-info`（Multi-Release jar：全部 26 个可发布模块；`record-support` 为
  JDK 17 字节码并提供 `provides JavaRecordSupport`，`codegen` 通过 `provides` 声明 KSP
  处理器，见 [JPMS 指南](../guide/jpms.md)）
- [x] GraalVM Native Image（GraalVM JDK 21 + VS BuildTools + Windows SDK 实测）
- [x] native-image CI（GitHub Actions，GraalVM JDK 21 / Linux）

> 已放弃：Gitea CI、Maven Central 发布。
> 未完成项：第三方自动模块的显式模块化（Avro / Ion / TOML / Protobuf / commons-csv /
> hibernate-core / KSP API 等仍限制 jlink 范围）。

## 批次计划

| 批次 | 版本 | 内容 | 状态 |
|---|---|---|---|
| 第一批 | 1.x | Node/Bean 映射、11 种格式、Token、安全边界 | 完成 |
| 第二批 | 2.0.0 | 代码生成、类型系统、CBOR sequence、Smile 多消息、OpenAPI | 完成 |
| 第三批 | 2.1.0 | 流式 Bean、对象池、Schema Registry、Bean Validation | 完成 |
| 第四批 | 2.2.0 | JDBC、TOML 注释、JPMS 自动模块、GraalVM 配置 | 完成 |
| 第五批 | 2.3.0 | JPA、显式 MR-JAR、Native Image 实测 | 完成 |
| 第六批 | 2.4.0 | 对象映射 API、反序列化/命名/包含注解、JPMS 全量 | 完成 |
| 第七批 | 2.5.0 | record-support、jlink、Hibernate 细节、native-image CI | 完成 |
| 后续 | 2.6.0 | 第三方依赖显式模块化、扩大 jlink 范围 | 规划中 |

> 原 1.x 的「第二批～第五批」已经通过后续版本完成，不再按原表单独收口；
> 上表按实际发布版本重新组织。

## 历史交付状态

### 1.1.0

已验证：

- master / dev / v1.1.0 已推送 GitHub
- 私服 1.1.0 已发布
- `clean ktlintCheck build generateChecksums generateSbom` 成功
- 648 tests / 0 failures / 0 errors
- SHA-256 清单 53 个 jar
- SBOM 46 个组件

当时延后到 1.2.0：

- CBOR tag 保留
- Ion 类型保真
- 原生 Token Reader
- Ryu / Schubfach
- Jazzer / JQF 持续 fuzz
- 运行期 / 编译期代码生成
- 远程 `$ref`
- Maven Central 自动发布

### 1.2.0

- master / dev / v1.2.0 已推送 GitHub
- 私服 1.2.0 已发布（annotation/exception 409 表示已存在且内容一致）
- 全量构建通过，657 tests / 0 failures
- 新增 Protobuf/Avro/Ion/YAML/XML 解析深度
- 新增可插拔远程 `$ref`
- 修复 GitHub Actions `gradlew` 执行权限

仍延后到 1.3.0：

- CBOR tag 保留
- Ion 类型保真
- 原生 Token Reader
- Ryu / Schubfach
- Jazzer / JQF 持续 fuzz
- 运行期 / 编译期代码生成
- Maven Central 自动发布

### 1.3.0

- master / v1.3.0 已推送 GitHub；dev 保持本地未推送
- 私服 1.3.0 全模块发布成功
- 全量构建与 691 tests 通过
- 新增 Ion 类型保真、CBOR tag 保真、CSV 原生 Token Reader、`maxStringLength`、
  YAML alias 限制

仍延后到 1.4.0：

- YAML / XML / Properties / TOML 原生 Token Reader
- Ryu / Schubfach
- Jazzer / JQF 持续 fuzz
- 运行期 / 编译期代码生成

### 1.4.0

- master / v1.4.0 已推送 GitHub
- 私服 1.4.0 全模块发布成功
- 全量构建与 749 tests 通过
- 原生 Token Reader 覆盖 JSON/CBOR/Smile/Ion/CSV/Properties/TOML/YAML/XML
- JSON Double 使用 Schubfach
- 新增 `fuzzContinuous` 持续随机 fuzz
- dev 本地保持未推送

仍延后：

- Jazzer / JQF 覆盖率引导 fuzz
- 运行期 / 编译期代码生成

### 1.5.0

- master / v1.5.0 已推送 GitHub
- 私服 1.5.0 全模块发布成功
- 全量构建与 749 tests 通过
- 新增 11 个 Jazzer 目标与 `fuzzJazzer` 任务
- `fuzzContinuous` 持续随机 fuzz
- dev 本地保持未推送

仍延后：

- JQF 集成
- 运行期 / 编译期代码生成

### 2.0.0

- master / v2.0.0 已发布
- 私服 2.0.0 全模块发布成功
- 全量构建通过，1029 tests
- 新增 `codegen` / `openapi` 模块
- 运行期/编译期代码生成、JQF/Jazzer、原生 Token Reader、Schubfach、类型保真、
  record/只读/merge、CBOR sequence、Smile 多消息、Protobuf 高级语义、CSV 嵌套、
  YAML 自定义 tag、安全基线
- dev 本地保持未推送

仍延后：

- Gitea CI、Maven Central 发布（已放弃）

## 2.4.0 历史状态

### 已完成的核心目标

| 方向 | 状态 |
|---|---|
| 多格式编解码（11 种） | ✅ |
| Node 树 + Bean 映射 | ✅ |
| 高级绑定（多态、构造器、Builder、record、只读、merge） | ✅ |
| Kotlin 类型（data class、value class、sealed） | ✅ |
| 代码生成（KSP + 运行期） | ✅ |
| 原生 Token + 流式写出 | ✅ |
| 大文件流式 Bean 映射 | ✅ |
| 对象池 / 零拷贝 | ✅ |
| Schema Registry / Bean Validation | ✅ |
| JDBC / JPA 注解映射 | ✅ |
| Hibernate Session / EntityManager / @Convert / Map / 多态 | ✅ |
| JPMS 自动模块名 + 全部 26 个显式 MR-JAR 模块 | ✅ |
| GraalVM Native Image | ✅ 实测 |
| 安全边界、fuzz、OpenAPI、SBOM/校验和 | ✅ |
| Gitea CI、Maven Central | ❌ 已放弃 |
| Avro logical type / maxDepth / jpa-hibernate 高级映射 | ✅ 2.4.0 已发布 |
| DeserializationFeature / @DocumentNaming / @DocumentInclude | ✅ 2.4.0 已发布 |
| 对象映射便捷 API / reified 迁移到 serialization-kotlin | ✅ 2.4.0 已发布 |

### 待办清单（2.4.0 之后）

| 序号 | 待办 | 阶段 |
|---|---|---|
| 2 | `jlink` 支持：`format-json` 链已实测，其余受第三方自动模块限制 | 阶段 6 |

> 2.4.0 已发布；`2.5.0` 已发布。

> 仓库根的 `TODO.md` 记录了更细粒度的待办项，本表只列路线图级目标。


## 当前状态（2.5.0）

- **Java record 支持独立为 `record-support` 模块**（JDK 17+ 字节码）：`serialization`
  不再内嵌 record 反射探测，改为公共 `JavaRecordSupport` SPI + `ServiceLoader`；
  JDK 17+ 应用需额外引入 `com.mingliqiye.document:record-support`，未引入或运行在
  JDK 8-15 时安全降级为普通 Bean 行为
- 删除 `:serialization:recordTest`；record 映射测试迁移到 `:record-support:test`
  （JDK 17 工具链）
- BOM 纳入 `record-support`；GraalVM 运行期初始化参数改指 `JavaRecordSupportLoader`
- **JPMS 全部 26 个可发布模块显式化**：补齐 `serialization-kotlin` / `record-support` /
  `codegen` / `codegen-test` / `openapi` 的 Multi-Release `module-info`；`record-support`
  提供 `provides JavaRecordSupport`，`codegen` 提供 KSP `provides SymbolProcessorProvider`
- JPA 剩余映射细节：`@Convert(attributeName)` 嵌套路径、关系属性 `@Convert`、
  `@MapKeyJoinColumn` 实体 Map 键（`@EmbeddedId`/`@IdClass` 复合主键文本）、
  jpa-hibernate 复合主键关联引用解析；classpath 扫描评估为不实现（保持显式注册）
- 已验证：`:serialization:ktlintFormat :serialization:test :record-support:ktlintFormat
  :record-support:test` 通过；`:jpa:build :jpa-hibernate:build` 通过

## 状态说明

- **勾选来源**：本页勾选状态整理自根 `ROADMAP.md`；
- **交付状态**：整理自 ROADMAP 中各版本小节，测试数量与发布状态保持原文；
- **与 CHANGELOG 的差异**：`CHANGELOG.md` 有更细的能力描述（例如 2.0.0 的流式写出、
  Protobuf extensions 等），两者如有不一致，以最终发布说明/源码为准；
- **dev 变更**：见 [变更记录](./changelog.md#后续-dev-变更)；
- 路线图会随版本更新调整，不构成对交付时间的承诺。

## 相关页面

- [变更记录](./changelog.md)：版本级变化
- [模块总览](../modules/index.md)：当前模块能力
- [安全说明](https://github.com/minglipro/mingli-document/blob/master/SECURITY.md)
- [贡献指南](https://github.com/minglipro/mingli-document/blob/master/CONTRIBUTING.md)
