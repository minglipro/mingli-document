# Changelog

## 2.5.1（未发布）

### 修复

- 把 YAML / JSON 等 Token 读取路径中的 `NUMBER` / `BOOLEAN` 标量映射到
  `String` 属性时按文本转换，不再抛 `Expected string ... but was NUMBER`
  （例如 `name: 666` 映射到 `name: String` 得到 `"666"`）

## 2.5.0

### 新增

- `record-support` 模块（JDK 17+）：Java record 反射访问独立成模块，提供
  `JavaRecordSupport` SPI 实现 `Jdk17RecordSupport`（直接使用 `Class.isRecord()` /
  `Class.getRecordComponents()` / `RecordComponent`），经 `META-INF/services` +
  `ServiceLoader` 接入
- `serialization` 新增公共 SPI：`JavaRecordSupport` / `JavaRecordComponentInfo`；内部
  `JavaRecordSupportLoader` 在 JDK 8-15 或找不到 provider 时安全降级（捕获
  `ServiceConfigurationError` / `LinkageError`）
- BOM 纳入 `record-support`
- `alias-for` 1.0.5 / `mingli-exception` 1.1.2-Final 提供显式 Module-Info
- native-image CI：`.github/workflows/native-image.yml`（GraalVM JDK 21 / Linux）
- 新增 `:format-json:jlinkImage`，`format-json` 依赖链可生成 JDK 17 jlink 镜像
- `format-json` 不再依赖 jackson-core 内部包：`Double` 写出改用 JDK
  `Double.toString`，module path 上不再需要 `--add-exports`
- JPMS：26 个可发布模块全部为显式 MR-JAR module-info（补齐
  serialization-kotlin / record-support / codegen / codegen-test / openapi）
- `jpa`：`@Convert(attributeName = "...")` 支持 `@Embedded`/`@EmbeddedId` 内部基本
  属性的点分路径覆盖（类级/属性级标注、`disableConversion` 覆盖、外层路径优先于
  嵌入类自身转换器与 autoApply；失效路径/类型不匹配抛 `JpaConfigException`）
- `jpa`：关系属性（`@ManyToOne`/`@OneToOne`/`@OneToMany`/`@ManyToMany`）上的显式
  `@Convert`：整个关联值按基本属性写/读（不展开关联、不受 `associationDepth` 限制）
- `jpa`：`@MapKeyJoinColumn`/`@MapKeyJoinColumns` 实体 Map 键：单主键与
  `@EmbeddedId`/`@IdClass` 复合主键的标识符文本（转义与规范文本、键冲突检测），
  公共 API `JpaDocumentMapper.entityKeyText` / `entityKeyFromText`
- `jpa-hibernate`：关系 `@Convert`、实体 Map 键解析为受管实例（`EntityManager.find`，
  找不到回退 detach）；`fromNode` 关联引用解析支持 `@EmbeddedId`/`@IdClass` 复合主键
  （`EntityManager.find` 使用复合 id 对象，单主键行为不变）
- 评估结论：`jpa` 不引入 classpath/package 扫描：autoApply 转换器与多态子类保持显式
  注册；README 说明原因（JDK 8/JPMS/确定性）与 Spring/ClassGraph 等替代接入方式

### 变更

- **Java record 支持改为可选模块**：`serialization` 不再内嵌 record 反射探测；JDK 17+
  应用需额外引入 `com.mingliqiye.document:record-support` 才能映射 record。未引入该模块
  或运行在 JDK 8-15 时按普通 Bean 处理（不再自动识别 record，行为与 2.0.0 之前一致）
- `serialization` 的 `module-info` 增加
  `uses com.mingliqiye.document.serialization.JavaRecordSupport`
- `serialization` 的 GraalVM 运行期初始化参数改为
  `JavaRecordSupportLoader`，并移除不再需要的 JDK record 反射条目
- `jpa` 元数据缓存由 `ConcurrentHashMap.computeIfAbsent` 改为可重入同步缓存，支持
  元数据构建期间的嵌套解析（嵌入类/实体 Map 键），避免 `Recursive update`

### 移除

- 删除 `:serialization:recordTest` 任务；record 映射测试迁移到 `:record-support:test`
  （JDK 17 工具链）
- 删除 `serialization` 内嵌的 record 反射实现（`JavaRecordSupport` 改为公共 SPI）
## 2.4.0

### 新增

- `jpa-hibernate` 模块：Hibernate 代理、懒加载、Session/EntityManager 集成
- JPA 高级映射：`@Convert` / autoApply 转换器、Map 关联、`@Inheritance` 多态
- Hibernate Map 关联与多态实体支持
- Avro 完整 logical type：date/time/timestamp/decimal/uuid/duration
- XML / Properties / TOML / CSV `maxDepth` 安全深度限制
- TOML 注释保留便捷 API：`TomlMapper.parseCommented` / `formatCommented`
- `serialization` / `jdbc` / `validation` / `jpa` 显式 MR-JAR `module-info`
- 11 个 `format-*` 与 `jpa-hibernate` 显式 MR-JAR `module-info`（显式模块累计 21 个）
- `alias-for` 升级 1.0.4（增加 `Automatic-Module-Name`）

### 反序列化与注解

- `DeserializationFeature` 开关：`FAIL_ON_NULL_FOR_PRIMITIVES`（默认关闭）
  - 开启后 `null` -> 基本类型（字段/构造参数/Builder/record/基本类型数组元素）
    抛 `DocumentSerializationException`
  - 关闭时保持基本类型默认值行为
- `@DocumentNaming` + `DocumentNamingStrategy`：类级/字段级命名策略覆盖
  - 优先级：`@DocumentName` > 字段级 > 类级 > 全局 `namingStrategy`
- `@DocumentInclude` + `DocumentIncludePolicy`：
  `ALWAYS` / `NON_NULL` / `NON_EMPTY` / `USE_DEFAULTS`
  - 优先级：字段级 > 类级 > 全局 `writeNulls`
- `DocumentMapperBuilder.propertyMetadataResolver(...)` 补齐 Builder 直接注册
- `DocumentModule` / `ModuleSetupContext` 注册优先级明确：
  模块注册覆盖 Builder 直接注册；后注册模块覆盖先注册模块；同名模块按
  `moduleName` 去重

### 对象映射 API

- 树 API：`readTree`（String / ByteArray / File / Path / InputStream / Reader）、
  `writeTree` / `writeTreeAsBytes`、`valueToTree`、`treeToValue`
- 值 API：`readValue`（六种来源 × `Class` / `DocumentTypeReference`）、
  `writeValueAsString` / `writeValueAsBytes`、`updateValue`、`convertValue`
- Reader / Writer：`readerFor`、`writer()`、`writerWithDefaultPrettyPrinter()`，
  返回 `DocumentObjectReader` / `DocumentObjectWriter`
- Kotlin reified 扩展迁移到 `serialization-kotlin`：
  `parse<T>` / `fromNode<T>` / `convert<T>` / `readerFor<T>`
  - `serialization` 保持纯 Java 可用，不包含 reified API

## 2.3.0

### 新增

- `jpa` 模块：JPA / Hibernate 实体注解映射，实体 ↔ Node
- 显式 `module-info` + Multi-Release JAR：annotation / exception / core / schema / schema-registry
- GraalVM Native Image 实测通过（GraalVM JDK21 + Windows SDK）

### JPMS

- JDK 8 继续使用 classpath
- JDK 9+ 使用 `META-INF/versions/9/module-info.class`
- 其余模块依赖 alias-for，仍为自动模块

## 2.2.0

### 新增

- `jdbc` 模块：ResultSet / PreparedStatement / 命名参数 / RowMapper
- `schema-registry` 模块
- `validation` 模块：Jakarta Bean Validation 集成
- TOML 注释保留：`TomlCommentedDocument` / 注释读写
- JPMS `Automatic-Module-Name`（全部可发布 jar）
- GraalVM Native Image 配置模板与 JVM smoke

### 说明

- Maven Central 实际发布暂缓（额度原因）
- GraalVM Native Image 构建需要 GraalVM JDK 17+

## 2.1.0

### 新增

- 大文件流式 Bean 映射：JSON Lines / NDJSON、JSON 顶层数组流式、CSV 逐行流式
- `ByteArrayPool` 缓冲池与 `BinaryNode.ofShared` 零拷贝
- CBOR 可配置缓冲池
- `schema-registry` 模块：内存 Schema Registry、版本/兼容性策略、远程 SPI
- `validation` 模块：Jakarta Bean Validation 集成
- `DocumentBeanValidator` 校验扩展点

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
- Security baseline 与 SECURITY.md
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

## 1.5.0

### 新增

- 11 个 Jazzer 覆盖率引导 fuzz 目标
- Gradle `fuzzJazzer` 任务
- `fuzzContinuous` 持续随机 fuzz
- 本地 dev 能力统一发布

### 说明

- 1.5.0 与 1.4.0 API 兼容
- Jazzer 任务不在常规 build/test 中运行

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

- `JsonConverter` / `JsonSerializer` / `JsonDeserializer` 改名为 `DocumentBaseConverter` / `DocumentBaseSerializer` / `DocumentBaseDeserializer`
- `DocumentConverter.value` 收紧为 `KClass<out DocumentBaseConverter<*>>`
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
