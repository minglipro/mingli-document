# TODO

当前分支：`dev`

本文件记录下一阶段待办。按顺序完成；每一项都必须：

1. 先补设计/验收标准
2. 实现后跑通模块测试
3. 跑 `gradle clean ktlintCheck build`
4. 更新 README / NOTICE（如新增依赖）
5. 独立原子提交

---

## 总览

| 序号 | 事项 | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| 1 | Avro / Protobuf wire parser | 高 | 待开始 | 无 |
| 2 | XML attributes + namespaces | 中高 | 待开始 | 无 |
| 3 | JSON byte API + 重复 key 策略 + 深度限制 | 高 | 待开始 | 无 |
| 4 | YAML 多文档 / typed binary | 中 | 待开始 | 无 |
| 5 | 所有文本格式接入 Token Reader | 中 | 待开始 | 2、4 |
| 6 | 模糊测试 + 边界语料 | 高 | 待开始 | 1~5 |

建议顺序：`1 → 2 → 3 → 4 → 5 → 6`

---

## 1. Avro / Protobuf wire parser

### 目标

把现有 Schema 层补成真正的 wire 编解码：

```text
AvroSchema      -> AvroMapper
ProtobufSchema  -> ProtobufMapper
```

### 范围

- `format-avro`
  - 引入 Apache Avro 依赖
  - `AvroMapper`：byte[] 读写，基于 schema
  - `Node` ↔ Avro `GenericRecord` / 标量 / 数组 / map
  - schema 由 `AvroSchema` 或外部 `org.apache.avro.Schema` 提供
  - 支持 `BinaryEncoder` / `BinaryDecoder`
  - 单根记录校验
- `format-protobuf`
  - 引入 protobuf-java
  - `ProtobufMapper`：按 `Descriptor` 读写 `DynamicMessage`
  - `Node` ↔ protobuf message / repeated / map
  - `ProtobufSchema` 能生成或接收 `Descriptor`
- 两个模块都补 `DocumentMapper` API、异常、测试与 NOTICE

### 验收标准

- [ ] 二进制 `formatBytes` -> `parse(ByteArray)` 往返一致
- [ ] 嵌套对象、数组、map、枚举、bytes 至少各一个测试
- [ ] 未知字段、缺字段、schema 不匹配有明确异常
- [ ] Avro/Protobuf 各自测试通过
- [ ] 全量构建通过

### 风险

- Avro 二进制本身不自带 schema，API 必须要求调用方显式传 schema
- Protobuf 动态消息依赖 `Descriptor`，现有简化 Schema 需要扩展
- 新增依赖需要许可证审查

---

## 2. XML attributes + namespaces

### 目标

让 XML 不再只支持 `type` / `nil` 自定义协议，支持真实 XML 结构。

### 范围

- 属性映射
  - 设计字段级标记，例如 `@DocumentAttribute`
  - 或提供 `XmlConfig.attributePrefix`（如 `@`）
  - 写出：标量属性直接写 XML attribute
  - 读取：attribute 映射回 Node 字段
- 命名空间
  - `XmlConfig.namespaceAware`
  - 读取时保留 `xmlns` / 前缀限定名
  - 写出时支持命名空间声明与 QName
  - 明确 Node 模型中命名空间的表示方式
- 普通 XML 兼容
  - 无 `type` / `nil` 时按元素结构推断
  - 混合内容、文本节点边界策略
- 安全配置保持：禁用 DOCTYPE、外部实体

### 验收标准

- [ ] 属性 round-trip
- [ ] 带命名空间 XML 可读取且不丢信息
- [ ] 写出结果可被标准 XML 解析器读取
- [ ] 原有 `type` / `nil` 协议测试不回归
- [ ] XXE 安全测试仍通过

### 需要先定决策

- 属性在 Node 里用前缀表示，还是引入 AttributeNode？
- 命名空间用前缀 + `xmlns` 字段，还是扩展 Node 元数据？
- 混合内容是否支持，还是明确拒绝

---

## 3. JSON byte API + 重复 key 策略 + 深度限制

### 目标

补齐 JSON 作为生产级解析器需要的三个边界能力。

### 范围

- byte API
  - `JsonMapper` 覆盖 `parse(ByteArray, ...)` / `formatBytes(...)`
  - UTF-8 编解码
  - 非法 UTF-8 明确报错
- 重复 key 策略
  - 新增 `DuplicateKeyPolicy`
  - 建议值：
    - `LAST_WINS`（默认，保持兼容）
    - `FIRST_WINS`
    - `FAIL`
  - 树、Token、Bean 三条路径一致
- 深度限制
  - `JsonConfig.maxDepth`
  - 默认值需安全（例如 512）
  - 对象/数组嵌套统一计数
  - 超限抛 `JsonParseException`，不能 StackOverflow
- 测试覆盖 tree / token / Bean / data class

### 验收标准

- [ ] `parse(bytes)` / `formatBytes` 往返
- [ ] 三种重复 key 策略各有测试
- [ ] 超深 JSON 抛出受控异常
- [ ] 既有 JSON 测试全部通过
- [ ] 性能不明显回退

---

## 4. YAML 多文档 / typed binary

### 目标

补齐 YAML 常用但当前缺失的两个语义。

### 范围

- 多文档
  - 提供 `YamlMultiDocumentReader` 或 `YamlMapper.parseAll`
  - 返回 `List<Node>`
  - 写出多文档 API（`---` 分隔）
  - 单文档 API 保持兼容
- typed binary
  - `!!binary` 解析为 `BinaryNode`，不再转 Base64 字符串
  - `YamlWriter` 把 `BinaryNode` 写成 `!!binary`
  - 与 JSON/XML 的二进制兼容策略保持一致
- 多文档中空文档、`---` / `...` 边界行为明确

### 验收标准

- [ ] 多文档读取按顺序返回
- [ ] 多文档写出可再读回
- [ ] `!!binary` round-trip 保持字节
- [ ] 原有 YAML 测试不回归

---

## 5. 所有文本格式接入 Token Reader

### 目标

让文本格式和二进制格式一样有统一 Token 路径。

### 范围

为以下模块提供 `DocumentTokenReader`：

| 格式 | 建议实现 |
|---|---|
| YAML | SnakeYAML event API 直接产 Token |
| XML | StAX 事件流产 Token |
| Properties | 基于路径键值产 Token |
| TOML | Tomlj 解析结果转 Token |
| CSV | 行/列转 Token |

- 对应 Mapper 的 `parse(String)` 优先 Token 直读 Bean
- 保留 Node 路径和 `fromNode`
- 转换器 / data class 继续走局部 Node 回退
- 补 Token 序列测试和“确实走 Token 路径”的探针测试

### 验收标准

- [ ] 每个文本格式至少有一个 Token Reader
- [ ] Bean 解析走 Token 路径
- [ ] 原有 parse/format/fromNode 行为不变
- [ ] 各格式 Token 序列测试通过
- [ ] 全量构建通过

### 风险

- Properties / CSV 不是树形格式，Token 模型需要适配
- XML 混合内容和属性会让 Token 化复杂
- 如果收益不明显，先保证契约完整，不强行替换热路径

---

## 6. 模糊测试 + 边界语料

### 目标

用自动化手段确认所有解析器在畸形输入下：

- 不崩溃进程
- 不无限循环
- 不 OOM
- 不 StackOverflow
- 异常可预期

### 范围

- 随机字节/文本 fuzz
  - JSON、YAML、XML、Properties、TOML、CSV、Ion、CBOR、Smile
  - 每个输入设置超时与最大长度
- 边界语料
  - 空输入
  - 截断输入
  - 超深嵌套
  - 超长字符串/数字
  - 非法 UTF-8
  - 重复 key / 重复属性
  - XML 外部实体 / DOCTYPE
  - YAML 多文档边界
  - CBOR/Smile 非法头
- 语料目录
  - `src/test/resources/corpus/<format>/...`
- 建议先做轻量随机 fuzz + JUnit 超时，不引入过重依赖
- 如需专业 fuzz，再评估 Jazzer/JQF（JDK8 兼容性与 CI 成本）

### 验收标准

- [ ] 每个解析器至少 1 组 malformed corpus 测试
- [ ] 深嵌套输入抛受控异常而非 StackOverflow
- [ ] 测试有超时限制
- [ ] CI/Gradle 全量测试时间可控
- [ ] 发现的问题先修复再收口

---

## 完成标记

全部完成后：

- [ ] `gradle clean ktlintCheck build` 通过
- [ ] README / NOTICE / 模块文档同步
- [ ] 所有项独立提交
- [ ] 工作区干净

---

## 暂不做

- 运行期代码生成
- Ryu/Schubfach 数字格式化
- JSON 纯 byte 写出器（与当前 String API 一起评估）
- 发布到 Maven 仓库
