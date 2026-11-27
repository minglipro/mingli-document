# benchmarks

`mingli-document` 的 JMH 性能基准模块。

只用于开发测试，不发布，也不在 BOM 中。

## 运行

```bash
gradle :benchmarks:jmh
```

只跑部分基准：

```bash
gradle :benchmarks:jmh "-PjmhArgs=-rf text -wi 3 -i 5 -f 1 JsonMapperBenchmark.ourFormat"
```

## 覆盖

- JSON 树解析
- JSON → Bean
- JSON → Kotlin data class
- Bean → JSON
- CBOR Token / Node 解析
- CBOR Token / Node 写出
- Token 扫描

## 性能结果

详细数据见根 [README 性能章节](../README.md#性能)。

## 构建

```bash
gradle :benchmarks:build
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
