# 安全测试基线

本文件记录 `mingli-document` 的安全边界与自动化基线测试。

## 已有防护

```text
XML: 禁用 DOCTYPE、外部通用/参数实体、XInclude、实体展开
YAML: SafeConstructor、maxAliases、单文档
JSON/YAML/XML/Properties/TOML/CSV/Ion: maxInputSize / maxNodeDepth / maxNodeCount / maxStringLength
CBOR/Smile/Protobuf/Avro/Ion: 解析深度限制
$ref: 本地/远程解析器可插拔，远程默认禁用
```

## 基线测试

```text
fuzz/SecurityBaselineTest
```

覆盖：

- XXE 外部实体拒绝
- YAML alias 膨胀拒绝
- Node 深度/字符串长度限制
- 输入大小限制
- CBOR 深度限制

## 非目标

以下不在当前安全基线内：

- 恶意 schema 导致的业务逻辑绕过
- 不受信任的 参考实现/第三方库漏洞
- 侧信道、DoS 级别的 CPU/内存竞争
- 远程 `$ref` 解析器的 SSRF 防护（由使用方实现）

## 使用建议

- 解析不可信输入时显式设置 `maxInputSize`、`maxNodeDepth`、`maxStringLength`
- 不要给不可信输入启用远程 `$ref`
- YAML 设置 `maxAliases`
- XML 使用默认安全解析器，不要放开 DOCTYPE
