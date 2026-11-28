# Changelog

## 2.4.0（未发布）

### 新增

- `jpa-hibernate` 模块：Hibernate 代理、懒加载、Session/EntityManager 集成
- Avro 完整 logical type：date/time/timestamp/decimal/uuid/duration
- XML / Properties / TOML / CSV `maxDepth` 安全深度限制
- TOML 注释保留便捷 API：`TomlMapper.parseCommented` / `formatCommented`
- `serialization` / `jdbc` / `validation` / `jpa` 显式 MR-JAR `module-info`
- `alias-for` 升级 1.0.4（增加 `Automatic-Module-Name`）

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
