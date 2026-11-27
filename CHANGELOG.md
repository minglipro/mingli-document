# Changelog

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
