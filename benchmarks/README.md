# JMH 性能基准

本模块只用于性能测试，不参与发布。

## 运行

```bash
gradle :benchmarks:jmh
```

环境：

- JDK8 toolchain
- JMH 1.37
- 1000 个 Java Bean，JSON 约 128 KB
- 3 轮 warmup + 5 轮测量，单 fork

## 当前结果

```text
Benchmark                             Mode  Cnt  Score   Error  Units
JsonMapperBenchmark.jacksonFormat     avgt    5  0.200 ± 0.038  ms/op
JsonMapperBenchmark.jacksonParseData  avgt    5  0.290 ± 0.051  ms/op
JsonMapperBenchmark.jacksonParseTree  avgt    5  0.328 ± 0.054  ms/op
JsonMapperBenchmark.ourFormat         avgt    5  0.747 ± 0.095  ms/op
JsonMapperBenchmark.ourParseData      avgt    5  1.155 ± 0.241  ms/op
JsonMapperBenchmark.ourParseTree      avgt    5  0.467 ± 0.087  ms/op
```

说明：

- `ourFormat` 使用流式 JSON 写出，不构建完整 Node 树
- 该结果是单机普通 JVM 数据，不代表所有硬件/负载
- 与 Jackson 对比时注意：本库输出统一的不可变 Node 树，Jackson 直接绑定 Bean