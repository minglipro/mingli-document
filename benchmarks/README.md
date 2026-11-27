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
JsonMapperBenchmark.jacksonFormat       avgt    5  0.203 ± 0.017  ms/op
JsonMapperBenchmark.jacksonParseData    avgt    5  0.309 ± 0.015  ms/op
JsonMapperBenchmark.jacksonParseTree    avgt    5  0.304 ± 0.017  ms/op
JsonMapperBenchmark.ourFormat           avgt    5  0.724 ± 0.019  ms/op
JsonMapperBenchmark.ourParseData        avgt    5  1.036 ± 0.054  ms/op
JsonMapperBenchmark.ourParseKotlinData  avgt    5  1.089 ± 0.111  ms/op
JsonMapperBenchmark.ourParseTree        avgt    5  0.671 ± 0.070  ms/op
TokenPathBenchmark.cborParseBeanToken   avgt    5  1.028 ± 0.044  ms/op
TokenPathBenchmark.cborParseBeanNode    avgt    5  1.328 ± 0.321  ms/op
TokenPathBenchmark.cborFormatBeanToken  avgt    5  0.701 ± 0.016  ms/op
TokenPathBenchmark.cborFormatBeanNode   avgt    5  1.432 ± 0.112  ms/op
TokenPathBenchmark.jsonParseBeanToken   avgt    5  1.162 ± 0.173  ms/op
```

说明：

- `ourParseData` / `ourParseKotlinData` 已走 JSON Token 直读
- `ourFormat` 使用 JSON Token 写出，不构建完整 Node 树
- `cborParseBeanToken`/`cborFormatBeanToken` 对比 `cborParseBeanNode`/`cborFormatBeanNode`：Token 路径分别约快 23% / 51%
- 该结果是单机普通 JVM 数据，不代表所有硬件/负载