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

持续随机 fuzz：

```bash
gradle :fuzz:fuzzContinuous -PfuzzSeconds=60
```

Jazzer 覆盖率引导 fuzz：

```bash
gradle :fuzz:fuzzJazzer -PfuzzSeconds=30 -PjazzerTarget=com.mingliqiye.document.fuzz.JsonJazzerTarget
```

JQF 覆盖率引导 fuzz：

```bash
gradle :fuzz:fuzzJqf -PfuzzSeconds=30
```

`fuzzJqf` 使用 JQF JUnit 5 适配器与 `jqf-instrument` agent，覆盖：

```text
json / yaml / xml / properties / toml / csv / ion / cbor / smile
```

普通 `test` 任务会排除 `*JqfTest*`，不影响常规构建。

可用目标：

```text
JsonJazzerTarget
YamlJazzerTarget
XmlJazzerTarget
PropertiesJazzerTarget
TomlJazzerTarget
CsvJazzerTarget
IonJazzerTarget
CborJazzerTarget
SmileJazzerTarget
AvroJazzerTarget
ProtobufJazzerTarget
CborTagJazzerTarget
IonFidelityJazzerTarget
```

`fuzzContinuous` 按时间持续生成文本/二进制畸形输入，遇到 StackOverflow 或 OOM
会以非零退出码终止；普通异常视为被解析器拒绝。

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
