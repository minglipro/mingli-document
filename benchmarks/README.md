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
Benchmark                                        Mode  Cnt  Score   Error  Units
JsonMapperBenchmark.jacksonFormat                avgt    5  0.217 ± 0.019  ms/op
JsonMapperBenchmark.jacksonParseData             avgt    5  0.316 ± 0.055  ms/op
JsonMapperBenchmark.jacksonParseTree             avgt    5  0.328 ± 0.095  ms/op
JsonMapperBenchmark.ourFormat                    avgt    5  0.774 ± 0.233  ms/op
JsonMapperBenchmark.ourParseData                 avgt    5  0.785 ± 0.377  ms/op
JsonMapperBenchmark.ourParseKotlinData           avgt    5  0.816 ± 0.319  ms/op
JsonMapperBenchmark.ourParseTree                 avgt    5  0.475 ± 0.054  ms/op
TokenPathBenchmark.jsonTokenScan                 avgt    5  0.289 ± 0.031  ms/op
TokenPathBenchmark.jsonParseBeanToken            avgt    5  0.806 ± 0.089  ms/op
TokenPathBenchmark.jsonParseBeanValueReader      avgt    5  0.839 ± 0.068  ms/op
TokenPathBenchmark.jsonParseJavaBeanToken        avgt    5  0.812 ± 0.099  ms/op
TokenPathBenchmark.jsonParseJavaBeanValueReader  avgt    5  0.907 ± 0.109  ms/op
TokenPathBenchmark.cborParseBeanToken            avgt    5  0.917 ± 0.116  ms/op
TokenPathBenchmark.cborParseBeanNode             avgt    5  1.175 ± 0.358  ms/op
TokenPathBenchmark.cborFormatBeanToken           avgt    5  0.716 ± 0.210  ms/op
TokenPathBenchmark.cborFormatBeanNode            avgt    5  1.316 ± 0.090  ms/op
```

说明：

- `JsonTokenReader` 已做到 Token 对象零分配，`int/long` 数字直接按源码区间解析，不生成中间字符串
- JSON Token 路径与旧 `JsonValueReader` 直读基本持平，Java Bean 场景约快 10%
- `cborParseBeanToken`/`cborFormatBeanToken` 对比 Node 路径分别约快 22% / 46%
- 该结果是单机普通 JVM 数据，不代表所有硬件/负载