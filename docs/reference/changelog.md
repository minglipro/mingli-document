# 变更记录

本页是 `CHANGELOG.md` 的站点版，记录 `mingli-document` 各版本的公开能力变化。
当前文档对应 `2.4.0`（`2.5.0` 开发中）；最新条目之后的 `dev` 分支提交见文末
[未发布的 dev 变更](#未发布的-dev-变更)。

## 版本速览

| 版本 | 主题 | 关键能力 |
|---|---|---|
| `2.5.0`（未发布） | record 支持独立模块 | `record-support` 模块、`JavaRecordSupport` SPI / `JavaRecordComponentInfo`、record 测试迁移到 JDK 17 |
| `2.4.0` | ORM、Avro、深度限制 | `jpa-hibernate`、Avro logical type、XML/Properties/TOML/CSV `maxDepth`、更多显式 module-info、对象映射便捷 API |
| `2.3.0` | ORM 与 JPMS | `jpa` 模块、5 个显式 MR-JAR `module-info`、GraalVM Native Image 实测 |
| `2.2.0` | 数据库与扩展 | `jdbc`、`schema-registry`、`validation`、TOML 注释保留、JPMS 自动模块名 |
| `2.1.0` | 流式与安全 | JSON Lines/数组、CSV 流式、`ByteArrayPool`、零拷贝、流式写出 |
| `2.0.0` | 代码生成与类型系统 | KSP/运行期代码生成、Java record、只读属性、merge、Kotlin value/sealed、CBOR sequence、Smile 多消息、CSV 嵌套、Protobuf 高级语义、OpenAPI、安全基线 |
| `1.5.0` | fuzz 工具链 | 11 个 Jazzer 目标、`fuzzJazzer`、`fuzzContinuous` |
| `1.4.0` | 原生 Token + 性能 | CSV/Properties/TOML/YAML/XML 原生 Token Reader、Schubfach Double、YAML `maxAliases` |
| `1.3.0` | 类型保真 | Ion `preserveTypes`、CBOR `preserveTags`、CSV Token、`maxStringLength`、YAML alias 限制 |
| `1.2.0` | 深度限制与远程引用 | 各格式 `maxDepth`、远程 `$ref`（`RemoteReferenceResolver`） |
| `1.1.0` | 高级数据绑定 | 文件/流 API、命名策略、多态、构造器注入、Builder、`$ref`、JSON Schema、安全限制 |
| `1.0.0` | 初始发布 | core/annotation/exception/schema/serialization/serialization-kotlin + 11 格式 + BOM |

发布标签：`1.0.0`、`v1.1.0`、`v1.2.0`、`v1.3.0`、`v1.4.0`、`v1.5.0`、`v2.0.0`、`v2.1.0`、`v2.2.0`、`v2.3.0`、`v2.4.0`。

## 2.5.0（未发布）

### 新增

- `record-support` 模块（JDK 17+）：Java record 反射访问独立成模块，提供
  `JavaRecordSupport` SPI 实现 `Jdk17RecordSupport`，经 `META-INF/services` +
  `ServiceLoader` 接入
- `serialization` 新增公共 SPI：`JavaRecordSupport` / `JavaRecordComponentInfo`
- JPMS：26 个可发布模块全部为显式 MR-JAR module-info
  （serialization-kotlin / record-support / codegen / codegen-test / openapi 补齐）
  内部 `JavaRecordSupportLoader` 在 JDK 8-15 或找不到 provider 时安全降级
  （`ServiceConfigurationError` / `LinkageError`）
- BOM 纳入 `record-support`
- `format-json` 的 `Double` 写出改用 JDK `Double.toString`，移除 jackson-core 内部包依赖

### 变更

- **Java record 支持改为可选模块**：`serialization` 不再内嵌 record 反射探测；JDK 17+
  应用需额外引入 `com.mingliqiye.document:record-support` 才能映射 record；
  未引入或运行在 JDK 8-15 时按普通 Bean 处理
- `serialization` 的 `module-info` 增加
  `uses com.mingliqiye.document.serialization.JavaRecordSupport`
- GraalVM 运行期初始化参数改为 `JavaRecordSupportLoader`

### 移除

- 删除 `:serialization:recordTest` 任务；record 映射测试迁移到 `:record-support:test`
  （JDK 17 工具链）
- 删除 `serialization` 内嵌的 record 反射实现
## 2.4.0

### 新增

- `jpa-hibernate` 模块：Hibernate 代理解包、懒加载初始化、Session/EntityManager 集成
- JPA 高级映射：`@Convert` / autoApply、Map 关联、`@Inheritance` 多态
- Hibernate Map 关联与多态实体支持
- Avro 完整 logical type：date/time/timestamp/decimal/uuid/duration
- XML / Properties / TOML / CSV `maxDepth`
- TOML 注释便捷 API：`TomlMapper.parseCommented` / `formatCommented`
- `serialization` / `jdbc` / `validation` / `jpa` 显式 MR-JAR `module-info`
- 11 个 `format-*` 与 `jpa-hibernate` 显式 MR-JAR `module-info`（显式模块累计 21 个）
- `alias-for` 1.0.4
- `DeserializationFeature` 反序列化特性开关：`FAIL_ON_NULL_FOR_PRIMITIVES`（null → 基本类型可抛异常，默认关闭）
- `@DocumentNaming` + `DocumentNamingStrategy`：类级/字段级命名策略覆盖
- `@DocumentInclude` + `DocumentIncludePolicy`：类级/字段级写出策略覆盖（ALWAYS / NON_NULL / NON_EMPTY / USE_DEFAULTS）
- `DocumentMapperBuilder.propertyMetadataResolver(...)`：Builder 直接注册属性元数据解析器
- `DocumentModule` 注册优先级文档化：模块覆盖直接注册、后注册模块优先、同名模块去重
- 对象映射便捷 API（format-agnostic）：
  - 树 API：`readTree`（String / ByteArray / File / Path / InputStream / Reader）、
    `writeTree` / `writeTreeAsBytes`、`valueToTree`、`treeToValue`
  - 值 API：`readValue`（六种来源 × `Class` / `DocumentTypeReference`）、
    `writeValueAsString` / `writeValueAsBytes`、`updateValue`、`convertValue`
  - Reader / Writer：`readerFor`、`writer()`、`writerWithDefaultPrettyPrinter()`，
    返回轻量包装 `DocumentObjectReader` / `DocumentObjectWriter`
  - 基于既有 `parse` / `format` / `toNode` / `fromNode` / `merge` 实现，不改变原行为；
    `DocumentMapper` 显式覆写，Java 可直接调用

## 2.3.0

### 新增

- `jpa` 模块：JPA / Hibernate 实体注解映射（`JpaDocumentMapper`、`JpaConfig`）
- `annotation` / `exception` / `core` / `schema` / `schema-registry` 的显式 `module-info` + Multi-Release JAR
- GraalVM Native Image 配置与实测（GraalVM JDK 21 + VS BuildTools + Windows SDK）

### 验证

- 1342 tests
- JDK 17 / JDK 11 module path smoke
- JDK 8 classpath 兼容
- native-smoke 输出 `SMOKE OK`

## 2.2.0

### 新增

- `jdbc` 模块：ResultSet / PreparedStatement / 命名参数 / RowMapper
- TOML 注释保留：`TomlCommentedDocument`
- JPMS `Automatic-Module-Name`（全部可发布 jar）
- GraalVM Native Image 配置模板与 JVM smoke

## 2.1.0

### 新增

- 大文件流式 Bean 映射：JSON Lines / NDJSON、JSON 顶层数组、CSV 逐行
- `ByteArrayPool` 缓冲池与 `BinaryNode.ofShared` 零拷贝
- CBOR 可配置缓冲池
- `schema-registry` 模块
- `validation` 模块：Jakarta Bean Validation 集成
- `DocumentBeanValidator` 扩展点

## 2.0.0

### 新增

- KSP 编译期代码生成：`@DocumentEnumCodec`、枚举转换器、`DocumentModule`
- 运行期 Bean 编解码器：`DocumentRuntimeCodecFactory`
- Java record 支持（JDK 16+ 运行时）
- 只读属性与字段 merge/update
- CBOR sequence、Smile 多消息流
- Kotlin value class / sealed class
- CSV 嵌套结构 / 多级 Schema
- Protobuf extensions / group / MessageSet / proto3 语义
- Security baseline 与 `SECURITY.md`
- YAML 自定义 tag
- OpenAPI 3.0 生成模块
- Maven Central 发布配置
- 运行期属性访问器生成：`DocumentAccessorCodegen`
- 编译期源码生成器：`DocumentCodecSourceGenerator`
- JQF 9 格式覆盖率引导 fuzz + `fuzzJqf` 任务
- Jazzer 13 个覆盖率引导目标
- `fuzzContinuous` 持续随机 fuzz
- 全格式原生 Token Reader（XML 默认配置，其余配置回退）
- JSON Double Schubfach 最短格式化
- Ion 类型保真、CBOR tag 保真
- `maxStringLength`、YAML alias 限制
- Properties/TOML/CSV Token 深度校验

### 说明

- 2.0.0 汇总 1.3.0 之后本地 dev 能力
- 新增 `codegen` 模块；`codegen-test` 不参与发布
- 运行期/KSP 代码生成能力为本次大版本核心

### 迁移提示

- 新增 `codegen` 模块（KSP）；需要用编译期代码生成时，额外引入 `codegen` 并应用
  KSP 插件（Kotlin 2.0.0 对应 KSP `2.0.0-1.0.21`）；
- 新增 `DocumentRuntimeCodecFactory`、`DocumentAccessorCodegen`、
  `DocumentCodecSourceGenerator`；
- 1.5.0 保持 1.4.0 API 兼容，2.0.0 汇总后新增能力默认关闭或保持兼容：
  `writeReferences`、`resolveReferences`、`maxInputSize`、`maxNodeDepth`、
  `maxNodeCount`、`atomicFileWrite`、`createParentDirectories` 均默认关闭/不限；
- 字段命名策略默认仍是 `AS_IS`；
- 详细迁移见 [MIGRATION.md](https://github.com/minglipro/mingli-document/blob/master/MIGRATION.md)
  与站点指南的「迁移」章节。

## 1.5.0

### 新增

- 11 个 Jazzer 覆盖率引导 fuzz 目标
- Gradle `fuzzJazzer` 任务
- `fuzzContinuous` 持续随机 fuzz
- 本地 dev 能力统一发布

### 说明

- 1.5.0 与 1.4.0 API 兼容
- Jazzer 任务不在常规 `build`/`test` 中运行

## 1.4.0

### 新增

- 原生 Token Reader：CSV / Properties / TOML / YAML / XML（默认配置）
- JSON Double 使用 Schubfach 最短表示
- `fuzzContinuous` 持续随机 fuzz harness
- YAML `maxAliases` 配置
- XML 解析错误静默处理

### 说明

- 1.4.0 与 1.3.0 API 兼容
- 原生 Token 路径在非默认配置或 Node 限制存在时自动回退
- 本地 dev 能力统一发布为 1.4.0

## 1.3.0

### 新增

- Ion 可选类型保真：`IonConfig.preserveTypes`（timestamp / symbol / clob / blob）
- CBOR 可选 tag 保真：`CborConfig.preserveTags`
- Properties / TOML / CSV Token 路径深度校验
- CSV 原生 Token Reader
- `maxStringLength` Node 字符串长度限制
- YAML alias 扩张限制
- CBOR tag / Ion 类型保真畸形输入 fuzz 覆盖

### 说明

- 1.3.0 与 1.2.0 API 兼容
- 保真能力默认关闭
- 本地 dev 提交统一发布为 1.3.0

## 1.2.0

### 新增

- Protobuf 解析深度限制：`ProtobufMapper(..., maxDepth)`
- Avro 解析深度限制：`AvroWireReader(..., maxDepth)` / `config.maxNodeDepth`
- Ion 文本/二进制解析深度限制：`IonReader` / `IonBinaryReader`
- YAML 解析深度限制：SnakeYAML `nestingDepthLimit`
- XML DOM 解析深度限制
- 可插拔远程 `$ref`：`RemoteReferenceResolver`
- GitHub Actions CI 修复 `gradlew` 可执行权限

### 说明

- 远程 `$ref` 默认关闭，需要显式配置 resolver
- 所有深度限制默认 `0`，表示不限制
- 1.2.0 保持 1.1.0 API 兼容

## 1.1.0

### 新增

- File / Path / InputStream / OutputStream / Reader / Writer 快速读写 API
- 文本文件与流自定义 Charset
- 原子文件写入、自动创建父目录
- `maxInputSize`、`maxNodeDepth`、`maxNodeCount` 输入与 Node 树限制
- 字段命名策略：`SNAKE_CASE` / `KEBAB_CASE` / `UPPER_CAMEL` / `LOWER_CAMEL`
- 多态类型：`@DocumentTypeInfo`、`@DocumentTypeName`、子类型 registry
- 单向转换注解：`@DocumentSerializer`、`@DocumentDeserializer`
- 构造器注入：`@DocumentCreator`
- Builder 模式实例化：`@DocumentBuilder`、`@DocumentBuild`
- 对象引用：`writeReferences` 输出 `$ref`，`resolveReferences` 读取本地 `$ref`
- JSON Schema 常用 draft-07 子集与高级关键字
- XML `PRESERVE_ORDERED` 混合内容顺序保留
- Avro OCF codec：null / deflate / snappy，`appendContainer`
- CBOR canonical map key 排序
- CBOR / Smile 解析器深度限制
- JSON 紧凑模式纯字节写出
- 发布校验和、CycloneDX JSON SBOM、GitHub Actions release workflow

### 变更

- `JsonConverter` / `JsonSerializer` / `JsonDeserializer` 改名为
  `DocumentBaseConverter` / `DocumentBaseSerializer` / `DocumentBaseDeserializer`
- `DocumentConverter.value` 类型收紧为 `KClass<out DocumentBaseConverter<*>>`
- `annotation` 作为 `serialization` 的公共 API 依赖暴露

### 文档

- 根 README 重写
- CONTRIBUTING 更新
- 每个模块增加 README
- ROADMAP 与变更记录

## 1.0.0

- 初始发布
- core / annotation / exception / schema / serialization / serialization-kotlin
- JSON / YAML / XML / Properties / TOML / CSV / Ion / CBOR / Smile / Avro / Protobuf
- Maven BOM
- GPG 签名发布

## 未发布的 dev 变更

`2.4.0` 发布之后 `dev` 分支的未发布变更统一记入上方的「2.5.0（未发布）」小节
（核心是 `record-support` 模块与 `JavaRecordSupport` SPI）。

`2.4.0` 之前 dev 分支的流式写出、Protobuf Descriptor 等提交已随 2.4.0 发布归档，
不再单列；历史细节可从 `git log` 核对。

## 升级指引

### 1.0.0 → 2.0.0

1. **坐标升级**：统一改为 `2.0.0`，或导入 BOM：

   ```kotlin
   implementation(platform("com.mingliqiye.document:mingli-document-bom:2.0.0"))
   ```

2. **转换器接口改名**：

   | 旧名 | 新名 |
   |---|---|
   | `JsonConverter` | `DocumentBaseConverter` |
   | `JsonSerializer` | `DocumentBaseSerializer` |
   | `JsonDeserializer` | `DocumentBaseDeserializer` |

3. **`@DocumentConverter` 泛型收紧**：只接受双向 `DocumentBaseConverter`；单向实现改用
   `@DocumentSerializer` / `@DocumentDeserializer`。

4. **注解依赖**：`serialization` 通过 `api` 暴露 `annotation`，正常传递依赖即可。

5. **默认行为不变**：`writeReferences` / `resolveReferences` / 各类限制 / 原子写 /
   自动建目录默认关闭；命名策略默认 `AS_IS`。

6. **需要代码生成时**：额外引入 `codegen` 并应用 KSP 插件（见
   [codegen 模块](../modules/codegen.md)）。

### 1.x → 1.5.0

`1.5.0` 保持 `1.4.0` API 兼容，只新增 fuzz 工具链，无需迁移。

### 1.x → 1.4.0 / 1.3.0 / 1.2.0

均为「新增能力默认关闭/内部生效」，无需迁移；需要时显式开启对应配置项即可。

## 兼容性说明

| 项目 | 承诺 |
|---|---|
| 版本号 | 语义化版本；2.x 内保持向后兼容 |
| 字节码 | JDK 8 目标（`record-support` 为 JDK 17）；Java record 支持需要 JDK 17+ 运行时 + `record-support` 模块 |
| 破坏性变更 | 仅在大版本（如 1.x → 2.0.0）出现，并配套迁移说明 |
| 新增配置 | 默认值保持旧行为（关闭/不限制） |
| 异常 message | 不作为稳定契约 |
| 生成代码 | KSP/源码生成器的类名与包名在 2.x 内保持兼容 |

## 文档来源

- 本页内容整理自仓库根目录 `CHANGELOG.md`；
- `dev` 未发布变更整理自 `git log`（2.4.0 发布后进入 2.5.0 开发）；
- 迁移细节整理自根目录 `MIGRATION.md`；
- 版本号以根 `gradle.properties` 的 `projectVersion` 为准。

相关页面：

- [路线图](./roadmap.md)
- [模块总览](../modules/index.md)
