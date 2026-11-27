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
JsonMapperBenchmark.jacksonFormat                avgt    5  0.201 ± 0.010  ms/op
JsonMapperBenchmark.jacksonParseData             avgt    5  0.290 ± 0.019  ms/op
JsonMapperBenchmark.jacksonParseTree             avgt    5  0.307 ± 0.015  ms/op
JsonMapperBenchmark.ourFormat                    avgt    5  0.728 ± 0.139  ms/op
JsonMapperBenchmark.ourParseData                 avgt    5  0.702 ± 0.016  ms/op
JsonMapperBenchmark.ourParseKotlinData           avgt    5  0.750 ± 0.028  ms/op
JsonMapperBenchmark.ourParseTree                 avgt    5  0.487 ± 0.016  ms/op
TokenPathBenchmark.jsonTokenScan                 avgt    5  0.294 ± 0.011  ms/op
TokenPathBenchmark.jsonParseBeanToken            avgt    5  0.807 ± 0.019  ms/op
TokenPathBenchmark.jsonParseBeanValueReader      avgt    5  0.870 ± 0.023  ms/op
TokenPathBenchmark.jsonParseJavaBeanToken        avgt    5  0.745 ± 0.020  ms/op
TokenPathBenchmark.jsonParseJavaBeanValueReader  avgt    5  0.910 ± 0.102  ms/op
TokenPathBenchmark.cborParseBeanToken            avgt    5  0.918 ± 0.059  ms/op
TokenPathBenchmark.cborParseBeanNode             avgt    5  1.300 ± 0.106  ms/op
TokenPathBenchmark.cborFormatBeanToken           avgt    5  0.701 ± 0.022  ms/op
TokenPathBenchmark.cborFormatBeanNode            avgt    5  1.381 ± 0.094  ms/op
```

说明：

- `JsonTokenReader` 已做到 Token 对象零分配，`int/long` 数字直接按源码区间解析
- 解码/写出路径使用懒路径构建，不再为每个字段分配路径字符串
- JSON 字符串写出增加无转义快速路径
- JSON Token 路径已快于旧 `JsonValueReader` 直读：Java Bean 约 18%，Kotlin data class 约 7%
- `cborParseBeanToken`/`cborFormatBeanToken` 对比 Node 路径分别约快 29% / 49%
- 该结果是单机普通 JVM 数据，不代表所有硬件/负载