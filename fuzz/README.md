# fuzz

解析器模糊测试与边界语料模块。

本模块只用于测试，不发布。

## 覆盖

```text
JSON
YAML
XML
Properties
TOML
CSV
Ion
CBOR
Smile
Avro
Protobuf
```

## 测试内容

- malformed 文本/字节语料
- 随机畸形输入
- 超时保护
- 不崩溃、不无限循环、不 StackOverflow
- 解析器受控抛异常

## 运行

```bash
gradle :fuzz:test
```

## 语料目录

```text
fuzz/src/test/resources/corpus/<format>/
```

新增边界样本时按格式放入对应目录。

## 构建

```bash
gradle :fuzz:build
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
