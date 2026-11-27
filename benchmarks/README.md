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
JsonMapperBenchmark.jacksonFormat                avgt    5  0.207 ± 0.043  ms/op
JsonMapperBenchmark.jacksonParseData             avgt    5  0.305 ± 0.094  ms/op
JsonMapperBenchmark.jacksonParseTree             avgt    5  0.311 ± 0.109  ms/op
JsonMapperBenchmark.ourFormat                    avgt    5  0.669 ± 0.127  ms/op
JsonMapperBenchmark.ourParseData                 avgt    5  0.722 ± 0.236  ms/op
JsonMapperBenchmark.ourParseKotlinData           avgt    5  0.754 ± 0.242  ms/op
JsonMapperBenchmark.ourParseTree                 avgt    5  0.499 ± 0.184  ms/op
TokenPathBenchmark.jsonTokenScan                 avgt    5  0.295 ± 0.077  ms/op
TokenPathBenchmark.jsonParseBeanToken            avgt    5  0.894 ± 0.257  ms/op
TokenPathBenchmark.jsonParseBeanValueReader      avgt    5  0.901 ± 0.282  ms/op
TokenPathBenchmark.jsonParseJavaBeanToken        avgt    5  0.739 ± 0.221  ms/op
TokenPathBenchmark.jsonParseJavaBeanValueReader  avgt    5  0.943 ± 0.310  ms/op
TokenPathBenchmark.cborParseBeanToken            avgt    5  0.891 ± 0.231  ms/op
TokenPathBenchmark.cborParseBeanNode             avgt    5  1.362 ± 0.421  ms/op
TokenPathBenchmark.cborFormatBeanToken           avgt    5  0.695 ± 0.150  ms/op
TokenPathBenchmark.cborFormatBeanNode            avgt    5  1.433 ± 0.381  ms/op
```

说明：

- `JsonTokenReader` 已做到 Token 对象零分配，`int/long` 数字直接按源码区间解析
- 解码/写出路径使用懒路径构建，不再为每个字段分配路径字符串
- JSON 写出使用预分配缓冲、无转义字符串快速路径
- 可读属性在 BeanMetadata 中预编译；无字段转换器时跳过转换器查找
- JSON Token 路径已快于旧 `JsonValueReader` 直读：Java Bean 约 22%
- `cborParseBeanToken`/`cborFormatBeanToken` 对比 Node 路径分别约快 35% / 52%
- 该结果是单机普通 JVM 数据，不代表所有硬件/负载