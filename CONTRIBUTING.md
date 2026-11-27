# 贡献指南

感谢参与 `mingli-document`。

本文说明开发环境、代码规范、测试要求和提交流程。面向使用者的文档见 [README.md](README.md)。

## 1. 开始之前

需要：

- JDK 8 或更高版本（构建使用 JDK 8 toolchain）
- Gradle 8.x
- Git
- 发布相关开发需要本机 GPG（`gpg` 命令）

建议先跑一次完整构建：

```bash
gradle clean ktlintCheck build
```

## 2. 项目结构

```text
annotation              格式无关注解
exception               异常基类
core                    Node 树、类型引用
schema                  格式无关 Schema
serialization           DocumentMapper、Bean 映射、Token
serialization-kotlin    Kotlin data class 支持
format-json             JSON / JSONC
format-yaml             YAML
format-xml              XML
format-properties       Properties
format-toml             TOML
format-csv              CSV
format-ion              Ion 文本 + 二进制
format-cbor             CBOR
format-smile            Smile
format-avro             Avro Schema + wire
format-protobuf         Protobuf Schema + wire
benchmarks              JMH 基准，不发布
fuzz                    模糊测试与边界语料，不发布
```

依赖方向：

```text
format-* -> serialization -> core -> exception
serialization-kotlin -> serialization
format-avro / format-protobuf -> schema
```

`core` 不依赖具体格式。

## 3. 构建与测试

```bash
# 全量：ktlint + 编译 + 测试 + Dokka 文档
gradle clean ktlintCheck build

# 指定模块
gradle :format-json:test
gradle :serialization:test
gradle :serialization-kotlin:test

# 自动格式化
gradle ktlintFormat

# 模糊测试
gradle :fuzz:test

# JMH
gradle :benchmarks:jmh
```

要求：

- 提交前本地 `clean ktlintCheck build` 必须通过
- 新增功能必须带测试
- 修 bug 优先补回归测试
- 测试不要依赖网络、时钟、机器路径或执行顺序

## 4. 代码规范

### 4.1 显式 API

所有模块启用 `explicitApi()`：

- public API 必须显式写 `public`
- 实现类使用 `internal` 或 `private`
- 不把内部 Codec 暴露到公共 API

### 4.2 单文件单类型

生产代码一个文件一个类/接口/对象（companion 可以存在）。

### 4.3 接口与实现

需要可扩展或跨模块使用的能力必须有接口：

- 格式读写入口使用 Mapper
- 转换器实现 `JsonSerializer` / `JsonDeserializer`
- Schema 实现 `DocumentSchema`
- 自定义实例化实现 `ObjectInstantiator`

实现类保持 internal，通过工厂函数或 Builder 创建。

### 4.4 不可变性

- `Node` 不可变
- `BinaryNode` 输入/输出复制字节数组
- 公共集合返回值不要暴露内部可变集合

### 4.5 异常

- 模块专属异常放在该模块的 `.exception` 包
- 异常继承项目异常体系
- 错误信息包含字段/路径等可定位信息
- 不要把 `StackOverflowError`、`OutOfMemoryError` 当正常错误处理

### 4.6 空安全

公共 API 优先使用非空类型；确实可能为空时在 KDoc 中写清楚。

### 4.7 KDoc

public API 必须有 KDoc：

```kotlin
/**
 * 将文本解析为目标类型。
 *
 * @param source 格式文本
 * @param targetType 目标类型
 * @return 解析结果
 * @throws XxxParseException 格式非法时抛出
 */
```

## 5. 新增格式模块

新增 `format-xxx` 时至少完成：

- [ ] `settings.gradle.kts` 注册模块
- [ ] `build.gradle.kts`：JDK 8 toolchain、ktlint、explicitApi
- [ ] `XxxMapper` / `XxxMapperBuilder`
- [ ] `XxxReader` / `XxxWriter`
- [ ] `.exception` 包
- [ ] Node 映射：对象、数组、标量、null、二进制（如支持）
- [ ] Bean / 集合 / Map 映射测试
- [ ] 坏输入与边界测试
- [ ] README 格式表更新
- [ ] NOTICE 依赖许可证更新
- [ ] 若支持 Token，实现 `DocumentTokenReader`

Mapper 的文本/二进制能力按格式选择：

```text
文本格式：parse(String) / format
二进制格式：parse(ByteArray) / formatBytes
```

## 6. Token 与二进制

### Token Reader

实现 `DocumentTokenReader` 时：

- `currentToken` 与 `nextToken()` 语义一致
- 标量、对象、数组、字段名、null、二进制值都要覆盖
- Token 值在 `nextToken()` 后保持到下一次读取
- 优先避免每个 Token 创建对象；高频路径可直接维护字段

### BinaryNode

- 文本格式无法原生表达二进制时，明确使用 Base64 或格式自带机制
- 读取时保证二进制字段类型正确
- 写回时保持 round-trip

## 7. 测试规范

- 单元测试放 `src/test/kotlin`
- 测试类按模块/对象命名
- 覆盖正常路径、边界、异常、兼容行为
- 涉及深度、随机输入、解析器时加超时
- 新增边界样本可放入 `fuzz/src/test/resources/corpus/<format>/`

性能相关改动：

- 先跑 `:benchmarks:jmh`
- 记录改动前后同轮数据
- 不要用单次结果下结论

## 8. 提交规范

使用原子提交：

```text
feat(format-json): 增加 xxx
fix(serialization): 修复 xxx
perf(json): 优化 xxx
test: 增加 xxx
docs: 更新 xxx
build: 调整 xxx
```

要求：

- 一个提交只做一件事
- 代码、测试、文档尽量在同一功能提交内
- 不提交构建产物、IDE 文件、凭据
- 提交前检查：

```bash
git status
gradle clean ktlintCheck build
```

## 9. 公共 API 兼容

- 不随意删除/修改 public API
- 新增可选能力优先用默认参数、重载或新方法
- 弃用 API 先标记并写替代方案
- 修改行为要有迁移说明和测试

## 10. 文档

两类文档分工：

- `README.md`：面向使用者，只保留安装、示例、能力、限制
- `CONTRIBUTING.md`：开发规范、构建、测试、发布、评审

修改行为时同步更新：

- README 示例/能力表
- KDoc
- NOTICE（新增依赖）
- TODO（未完成边界）

## 11. 发布

Maven 坐标：

- 普通模块：`com.mingliqiye.document:<module>`
- 格式模块：`com.mingliqiye.document.format:<module>`

版本与分组在根 `gradle.properties`：

```properties
projectVersion=1.0-SNAPSHOT
projectGroup=com.mingliqiye.document
formatProjectGroup=com.mingliqiye.document.format
jvmVersion=8
```

发布：

```bash
# 本地验证
gradle publishToMavenLocal

# 远程
gradle publish
```

说明：

- 远程仓库地址和凭据由 Gradle `init.gradle` 提供，不提交到仓库
- `-SNAPSHOT` 走 snapshot 仓库，正式版本走 release 仓库
- 发布物包含 jar、pom、Gradle module metadata、javadoc、kotlindoc
- 发布物使用 GPG 签名；本机需配置可用 GPG key
- `benchmarks`、`fuzz` 不发布

## 12. Review 清单

- [ ] 设计符合现有模块边界
- [ ] public API 有 KDoc
- [ ] 实现类 internal
- [ ] 有正常/边界/异常测试
- [ ] `clean ktlintCheck build` 通过
- [ ] 无无关格式化改动
- [ ] 文档/许可证同步
- [ ] 提交信息清晰、原子

## 13. Definition of Done

一个功能完成需要：

1. 实现
2. 单元测试与边界测试
3. 全量构建通过
4. README / KDoc 更新
5. 依赖许可证更新
6. 原子提交
7. 工作区干净

## 14. 当前已知边界

- XML 混合内容不保留文本/子元素相对顺序
- CSV 只支持平坦表格
- TOML 日期时间映射为字符串
- Ion timestamp/symbol 为字符串，注解忽略
- Protobuf 不支持 extensions / group / MessageSet
- Avro OCF 不支持非默认 codec 与 appendTo
- YAML/XML/Properties/TOML/CSV 的 Token 读取通过 `NodeTokenReader` 适配

这些边界在相关 KDoc 和测试中有说明。

## 15. 许可证

贡献代码即表示同意以项目许可证（Apache License 2.0）发布。

新增第三方依赖必须：

- 兼容 Apache License 2.0
- 在 `NOTICE` 补充版权与许可证
- 不使用 copyleft 依赖
