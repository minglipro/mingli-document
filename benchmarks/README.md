# JMH 性能基准

本模块只用于性能测试，不参与发布。

## 运行

```bash
gradle :benchmarks:jmh
```

环境：

- JDK8 toolchain
- JMH 1.37
- 1000 个 Java Bean / Kotlin data class，JSON 约 128 KB
- 3 轮 warmup + 5 轮测量，单 fork

## 当前结果

```text
Benchmark                             Mode  Cnt  Score   Error  Units
JsonMapperBenchmark.jacksonFormat       avgt    5  0.207 ± 0.017  ms/op
JsonMapperBenchmark.jacksonParseData    avgt    5  0.298 ± 0.012  ms/op
JsonMapperBenchmark.jacksonParseTree    avgt    5  0.329 ± 0.018  ms/op
JsonMapperBenchmark.ourFormat           avgt    5  0.727 ± 0.013  ms/op
JsonMapperBenchmark.ourParseData        avgt    5  0.724 ± 0.055  ms/op
JsonMapperBenchmark.ourParseKotlinData  avgt    5  0.819 ± 0.029  ms/op
JsonMapperBenchmark.ourParseTree        avgt    5  0.492 ± 0.031  ms/op
```

说明：

- `ourFormat` 使用流式 JSON 写出，不构建完整 Node 树
- 该结果是单机普通 JVM 数据，不代表所有硬件/负载
- 与 Jackson 对比时注意：本库输出统一的不可变 Node 树，Jackson 直接绑定 Bean