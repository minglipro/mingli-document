# ROADMAP

本文件记录 `mingli-document` 后续能力补齐路线。

按批次推进；每批完成后必须：

```text
实现 -> 模块测试 -> clean ktlintCheck build -> 文档 -> 原子提交
```

---

## 阶段 0：发布基线

- [x] dev -> master
- [x] 版本升级
- [x] 全量构建
- [x] Maven 私服发布
- [x] GitHub 推送

---

## 阶段 1：高级数据绑定

- [x] 字段命名策略（SNAKE_CASE / KEBAB_CASE / UPPER_CAMEL）
- [x] 多态类型（type 字段 + 类型注册）
- [x] 构造器注入
- [x] Builder 模式实例化
- [x] 输入本地 `$ref` 解析
- [x] 对象身份引用序列化
- [x] Java record（2.0.0 内嵌支持；2.5.0 起独立为 `record-support` 模块，JDK 17+）
- [x] Kotlin value class / sealed class
- [x] 只读属性
- [x] 字段 merge / update

---

## 阶段 2：格式协议完整性

- [x] Avro：非默认 codec、appendTo
- [x] Avro：完整 logical type
- [x] Protobuf：extensions、group、MessageSet、proto3 语义
- [x] XML：混合内容顺序保留
- [x] CBOR sequence
- [x] CBOR：canonical map key 排序
- [x] CBOR：可选 tag 保真
- [x] Ion：timestamp / symbol / clob / blob 类型保真
- [x] YAML：自定义 tag（跨文档 anchor 规范不支持）
- [x] TOML：注释保留（独立注释模型）
- [x] CSV：嵌套结构 / 多级 Schema
- [x] Smile：多消息流

---

## 阶段 3：性能与流式

- [x] CSV 原生 Token Reader
- [x] Properties 原生 Token Reader
- [x] TOML 原生 Token Reader
- [x] YAML 原生 Token Reader
- [x] XML 原生 Token Reader（默认配置）
- [x] JSON 紧凑模式纯字节写出
- [x] Schubfach Double 最短格式化
- [x] 运行期属性访问代码生成
- [x] 编译期枚举转换器源码生成
- [x] 运行期 Bean 编解码器生成
- [x] 编译期注解处理器 / KSP
- [x] 大文件流式 Bean 映射
- [x] 对象池 / 零拷贝

---

## 阶段 4：安全边界

- [x] Mapper 层 maxNodeDepth / maxNodeCount
- [x] CBOR / Smile 解析器 maxDepth
- [x] Protobuf 解析器 maxDepth
- [x] Avro 解析器 maxDepth
- [x] Ion 解析器 maxDepth
- [x] YAML 解析器 maxDepth
- [x] XML / Properties / TOML / CSV maxDepth
- [x] 全格式 maxInputSize
- [x] Node 树 maxStringLength
- [x] YAML alias 扩张限制
- [x] 持续 fuzz harness（fuzzContinuous）
- [x] Jazzer 覆盖率引导 fuzz
- [x] JQF 集成
- [x] 安全测试基线

---

## 阶段 5：Schema 与生态

- [x] JSON Schema（常用 draft-07 子集）
- [x] Schema Registry 适配
- [x] OpenAPI 3.0 生成
- [x] Bean Validation 集成
- [x] JDBC 数据库集成
- [x] JPA 实体注解映射（jpa 模块）
- [x] Hibernate Session / EntityManager 集成

---

## 阶段 6：构建与发布

- [x] 自定义 Charset
- [x] 原子文件写入
- [x] 自动创建父目录
- [x] CI/CD（GitHub Actions）
- [x] Maven Central 发布配置（已放弃，不计划发布）
- [x] SHA-256 校验和
- [x] CycloneDX JSON SBOM
- [x] JPMS 自动模块名（`Automatic-Module-Name`，所有可发布 jar）
- [x] JPMS `module-info`（Multi-Release jar：全部 26 个可发布模块；`record-support` 为 JDK 17 字节码并提供 `provides JavaRecordSupport`，`codegen` 通过 `provides` 声明 KSP 处理器，见 docs/guide/jpms.md）
- [x] GraalVM Native Image 实测通过（GraalVM JDK21 + VS BuildTools + Windows SDK）

> 已放弃：Gitea CI、Maven Central。

---

## 批次计划

| 批次 | 内容 | 状态 |
|---|---|---|
| 第一批 | 字段命名策略、XML 混合内容顺序、Avro codec/append | 完成 |
| 第二批 | JSON Schema、安全边界、文件写入增强 | 待开始 |
| 第三批 | 多态、构造器/Builder、CBOR tag、Ion 保真 | 待开始 |
| 第四批 | 原生 Token、JSON byte writer、Ryu | 待开始 |
| 第五批 | CI/CD、SBOM、发布收口 | 待开始 |

---

## 1.1.0 交付状态

已验证：

- master / dev / v1.1.0 已推送 GitHub
- 私服 1.1.0 已发布
- `clean ktlintCheck build generateChecksums generateSbom` 成功
- 648 tests / 0 failures / 0 errors
- SHA-256 清单 53 个 jar
- SBOM 46 个组件

以下能力延后到 1.2.0：

- CBOR tag 保留
- Ion 类型保真

- 原生 Token Reader
- Ryu / Schubfach
- Jazzer / JQF 持续 fuzz
- 运行期 / 编译期代码生成
- 远程 `$ref`
- Maven Central 自动发布（已放弃）
---

## 1.2.0 交付状态

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
- Maven Central 自动发布（已放弃）
---

## 1.3.0 交付状态

- master / v1.3.0 已推送 GitHub；dev 保持了本地未推送
- 私服 1.3.0 全模块发布成功
- 全量构建与 691 tests 通过
- 新增 Ion 类型保真、CBOR tag 保真、CSV 原生 Token Reader、maxStringLength、YAML alias 限制

仍延后到 1.4.0：

- YAML / XML / Properties / TOML 原生 Token Reader
- Ryu / Schubfach
- Jazzer / JQF 持续 fuzz
- 运行期 / 编译期代码生成
---

## 1.4.0 交付状态

- master / v1.4.0 已推送 GitHub
- 私服 1.4.0 全模块发布成功
- 全量构建与 749 tests 通过
- 原生 Token Reader 覆盖 JSON/CBOR/Smile/Ion/CSV/Properties/TOML/YAML/XML
- JSON Double 使用 Schubfach
- 新增 fuzzContinuous 持续随机 fuzz
- dev 本地保持未推送

仍延后：

- Jazzer / JQF 覆盖率引导 fuzz
- 运行期 / 编译期代码生成
---

## 1.5.0 交付状态

- master / v1.5.0 已推送 GitHub
- 私服 1.5.0 全模块发布成功
- 全量构建与 749 tests 通过
- 新增 11 个 Jazzer 目标与 fuzzJazzer 任务
- fuzzContinuous 持续随机 fuzz
- dev 本地保持未推送

仍延后：

- JQF 集成
- 运行期 / 编译期代码生成
---

## 2.0.0 交付状态

- master / v2.0.0 已发布
- 私服 2.0.0 全模块发布成功
- 全量构建通过，1029 tests
- 新增 codegen / openapi 模块
- 运行期/编译期代码生成、JQF/Jazzer、原生 Token Reader、Schubfach、类型保真、record/只读/merge、CBOR sequence、Smile 多消息、Protobuf 高级语义、CSV 嵌套、YAML 自定义 tag、安全基线
- dev 本地保持未推送

仍延后：

- TOML 注释保留
- 大文件流式 Bean 映射
- 对象池 / 零拷贝
- Schema Registry 适配
- Bean Validation 集成
- 数据库 / ORM 集成
- JPMS module-info
- GraalVM Native Image
- Maven Central 实际发布（已放弃）
---

## 2.1.0 交付状态

- master / v2.1.0 发布
- 私服 2.1.0 全模块发布成功
- 全量构建通过，1191 tests
- 新增 schema-registry / validation 模块
- P1 全部完成：流式 Bean、缓冲池/零拷贝、Schema Registry、Bean Validation
- 文档站同步新增页面
---

## 2.2.0 交付状态

- master / v2.2.0 发布
- 私服 2.2.0 全模块发布
- P2 完成：JDBC、TOML 注释保留、JPMS Automatic-Module-Name、GraalVM 配置
- Maven Central 实际发布（已放弃）
- 显式 module-info / native-image 构建仍需后续 CI 验证

---

## dev 开发状态（未发布）

- JPMS：全部 26 个可发布模块（含本批 `serialization-kotlin` / `record-support` / `codegen` /
  `codegen-test` / `openapi`）为 Multi-Release JAR，JDK 9+ 使用显式 `module-info`，不再有
  本库自动模块；JDK 8 字节码、classpath 用法与自动模块名保持不变。`record-support` 为
  JDK 17 字节码并通过 `provides JavaRecordSupport` 提供 record 支持；`codegen` 通过
  `provides SymbolProcessorProvider` 声明 KSP 处理器（依赖自动模块
  `symbol.processing.api`）。`format-json` 写 `Double` 使用 JDK `Double.toString`，
  module path 与 classpath 都不需要 `--add-exports`。

---

## 2.3.0 交付状态

- master / v2.3.0 发布
- 私服 2.3.0 全模块发布
- jpa 模块：JPA / Hibernate 实体注解映射
- 显式 module-info + Multi-Release JAR（5 个模块）
- GraalVM Native Image 实测通过（GraalVM JDK21 + Windows SDK）
- 全量构建 1342 tests 通过
- Maven Central 发布（已放弃）

---

## 2.4.0 交付状态

- master / v2.4.0 发布
- jpa-hibernate：Hibernate Session/EntityManager、懒加载、代理、Map 关联、多态
- jpa 高级映射：@Convert、autoApply、@Inheritance
- Avro 完整 logical type
- XML / Properties / TOML / CSV maxDepth
- TOML parseCommented / formatCommented
- 显式 MR-JAR module-info 扩展到 21 个模块
- 对象映射便捷 API：readTree / writeTree / readValue / writeValueAs* / readerFor / writer
- Kotlin reified 扩展迁移到 serialization-kotlin
- DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
- @DocumentNaming / @DocumentInclude 类级与字段级注解
- DocumentModule 的 converter/serializer/deserializer 注册完善
- 全量构建 1566 tests 通过
- Gitea CI / Maven Central：已放弃
- 私服 / GitHub Release 已发布

## 2.5.0 交付状态（开发中）

- Java record 支持独立为 `record-support` 模块（JDK 17+ 字节码）：`serialization` 改为
  公共 `JavaRecordSupport` SPI + `ServiceLoader` 加载，JDK 8-15 或无 provider 时安全降级；
  `module-info` 增加 `uses`
- 删除 `:serialization:recordTest`；record 映射测试迁移到 `:record-support:test`
  （JDK 17 工具链），serialization 测试保持 JDK 8 工具链
- BOM 纳入 `record-support`；GraalVM 运行期初始化参数改指 `JavaRecordSupportLoader`
- 已验证：`:serialization:ktlintFormat :serialization:test :record-support:ktlintFormat
  :record-support:test` 通过；全量构建 1588 tests 通过