---
title: JPMS 与自动模块名
---

# JPMS 与自动模块名

> 本页说明 `mingli-document` 在 Java 平台模块系统（JPMS / Project Jigsaw）下的当前支持方式。
> 结论先行：**所有可发布模块的主 jar 都带有稳定的 `Automatic-Module-Name`，可以在 JDK 9+
> 上作为「自动模块」放到 module path 使用；但当前字节码目标仍是 JDK 8，因此暂不提供真正的
> `module-info.class`**，后续再以 Multi-Release jar 的方式补充。
>
> 版本基线：`gradle.properties` 的 `projectVersion=2.2.0`、`jvmVersion=8`。

## 目录

- [当前支持范围](#当前支持范围)
- [模块名清单](#模块名清单)
- [为什么不是 module-info.java](#为什么不是-module-info-java)
- [在 module path 上使用](#在-module-path-上使用)
- [验证方式](#验证方式)
- [限制](#限制)
- [后续计划：Multi-Release module-info](#后续计划multi-release-module-info)

## 当前支持范围

构建脚本（根 `build.gradle.kts`）统一为**可发布模块的主 jar** 写入清单属性
`Automatic-Module-Name`，不逐模块重复配置：

| 产物 | 是否写自动模块名 | 说明 |
|---|---|---|
| 可发布模块的 `jar`（主 jar） | ✅ | 例如 `core-2.2.0.jar`、`json-2.2.0.jar` |
| `javadoc` / `kotlindoc` classifier jar | ❌ | 文档 jar 不参与 module path |
| `bom`（`mingli-document-bom`） | ❌ | `java-platform`，没有 jar |
| `benchmarks` / `fuzz` | ❌ | 非发布模块（不在 `publishableProjects` 中） |
| 根项目 `mingli-document` | ❌ | 只做聚合构建，jar 仅含 `META-INF` |

在 JDK 8 上运行时，`Automatic-Module-Name` 会被直接忽略，行为与本页出现之前完全一致；
只有把 jar 放到 **module path** 上、且运行在 JDK 9+ 时才会生效。

> 注意：本库 jar 的模块名是稳定的，但完整依赖链里第三方 jar 能否放到 module path 由上游
> 决定（例如 `alias-for` 需要先补模块名），详见[限制](#限制)。

## 模块名清单

模块名规则：

- `format-*`：`com.mingliqiye.document.format.<格式名>`
- 其他模块：`com.mingliqiye.document.<模块名>`（模块名中的短横线 `-` 转成点 `.`，因为
  JPMS 模块名不允许 `-`）

| 模块 | Maven 坐标 | Automatic-Module-Name |
|---|---|---|
| `annotation` | `com.mingliqiye.document:annotation` | `com.mingliqiye.document.annotation` |
| `exception` | `com.mingliqiye.document:exception` | `com.mingliqiye.document.exception` |
| `core` | `com.mingliqiye.document:core` | `com.mingliqiye.document.core` |
| `schema` | `com.mingliqiye.document:schema` | `com.mingliqiye.document.schema` |
| `schema-registry` | `com.mingliqiye.document:schema-registry` | `com.mingliqiye.document.schema.registry` |
| `validation` | `com.mingliqiye.document:validation` | `com.mingliqiye.document.validation` |
| `jdbc` | `com.mingliqiye.document:jdbc` | `com.mingliqiye.document.jdbc` |
| `serialization` | `com.mingliqiye.document:serialization` | `com.mingliqiye.document.serialization` |
| `serialization-kotlin` | `com.mingliqiye.document:serialization-kotlin` | `com.mingliqiye.document.serialization.kotlin` |
| `codegen` | `com.mingliqiye.document:codegen` | `com.mingliqiye.document.codegen` |
| `codegen-test` | —（测试支持模块） | `com.mingliqiye.document.codegen.test` |
| `openapi` | `com.mingliqiye.document:openapi` | `com.mingliqiye.document.openapi` |
| `format-json` | `com.mingliqiye.document.format:json` | `com.mingliqiye.document.format.json` |
| `format-yaml` | `com.mingliqiye.document.format:yaml` | `com.mingliqiye.document.format.yaml` |
| `format-xml` | `com.mingliqiye.document.format:xml` | `com.mingliqiye.document.format.xml` |
| `format-properties` | `com.mingliqiye.document.format:properties` | `com.mingliqiye.document.format.properties` |
| `format-toml` | `com.mingliqiye.document.format:toml` | `com.mingliqiye.document.format.toml` |
| `format-csv` | `com.mingliqiye.document.format:csv` | `com.mingliqiye.document.format.csv` |
| `format-ion` | `com.mingliqiye.document.format:ion` | `com.mingliqiye.document.format.ion` |
| `format-cbor` | `com.mingliqiye.document.format:cbor` | `com.mingliqiye.document.format.cbor` |
| `format-smile` | `com.mingliqiye.document.format:smile` | `com.mingliqiye.document.format.smile` |
| `format-avro` | `com.mingliqiye.document.format:avro` | `com.mingliqiye.document.format.avro` |
| `format-protobuf` | `com.mingliqiye.document.format:protobuf` | `com.mingliqiye.document.format.protobuf` |

::: tip 模块名是发布契约
自动模块名和 `module-info` 里的模块名一样，是使用方的编译期/运行期契约；一旦发布就从
`Automatic-Module-Name` 变成正式模块名，**不应该再改**。因此这里按
`com.mingliqiye.document[.format].<模块>` 统一规划，格式模块单独使用 `format` 命名空间。
:::

## 为什么不是 module-info.java

本库对运行环境的承诺是 **JDK 8+**（见 [安装与依赖](./installation.md#环境要求) 与根
`gradle.properties` 的 `jvmVersion=8`）。在这个前提下直接加 `module-info.java` 不可行：

| 障碍 | 说明 |
|---|---|
| class file 版本 | `module-info.class` 是 class file 53（JDK 9+）格式，`javac --release 8` / `kotlinc -jvm-target 1.8` 无法产出 |
| JDK 8 兼容 | JDK 8 的类加载器不认识 `module-info.class`；把它直接放进普通 jar 虽然 JVM 通常会忽略，但部分旧版字节码工具/扫描器会报错，因此需要 Multi-Release jar 让 JDK 8 完全看不到它 |
| 编译工具链 | 单个 `module-info.java` 需要 JDK 9+ 工具链，且 Kotlin 源码本身不参与模块描述（Kotlin 编译器不编译 `module-info.kt`） |
| 依赖模块名 | `requires` 需要写出 Kotlin stdlib、Jackson、tomlj、protobuf-java 等第三方依赖的模块名，而它们大多是自动模块，名称稳定性不由本库控制 |

业界常见的兼容做法是 **Multi-Release jar**：

```text
core-2.2.0.jar
├── META-INF/MANIFEST.MF          (Multi-Release: true)
├── com/mingliqiye/document/...   (JDK 8 字节码)
└── META-INF/versions/9/
    └── module-info.class         (JDK 9+ 才会读取)
```

这需要额外的 JDK 9+ 编译步骤与逐模块 `module-info.java` 维护，所以当前先提供
`Automatic-Module-Name` 作为**稳定、零风险、立刻可用**的过渡方案：JDK 8 完全不受影响，
JDK 9+ 又能拿到确定的模块名，而不是由 jar 文件名推导出的临时名字。

::: warning 自动模块名推导的对照
如果没有 `Automatic-Module-Name`，JDK 会从 jar 文件名推导模块名，例如
`format-json-2.2.0.jar` 会被推导成 `format.json`。这种名字既不稳定也不好看，所以本库
主动声明固定名字。
:::

## 在 module path 上使用

### 命令行的直接使用

把需要的 jar 放入一个目录（module path），用 `--add-modules` 解析需要的模块：

```bash
# main 方法的类在 classpath 上
javac --module-path lib \
      --add-modules com.mingliqiye.document.format.json \
      -d out Main.java

java --module-path lib \
     --add-modules com.mingliqiye.document.format.json \
     -cp out Main
```

`lib/` 中除了 `mingli-document` 的 jar，还要放入它的全部传递依赖（例如 `kotlin-stdlib`、
`jackson-core`、`tomlj`、`protobuf-java` 等）。自动模块之间会互相解析，但**依赖 jar 必须
放在 module path 上**：命名模块（含自动模块）默认读不到 classpath（unnamed module）里的类。

### 在模块化的消费方项目里使用

消费方自己有 `module-info.java` 时，直接 `requires` 需要的模块：

```java
module my.app {
    requires com.mingliqiye.document.serialization;
    requires com.mingliqiye.document.format.json;
}
```

- Gradle / Maven：消费方项目自身声明了 `module-info.java` 时，构建工具会把依赖放到
  module path；纯 classpath 项目不受影响。Gradle 还提供
  `java { modularity.inferModulePath = true }` 用于控制 module path 的推断。
- Kotlin 消费方：Kotlin 编译器不编译 `module-info.kt`；混合项目中的模块描述需要由 Java 侧
  `module-info.java` 承担，Kotlin 源码与 Java 字节码在同一个模块里。

### 建议的 requires 组合

| 场景 | 需要的模块名 |
|---|---|
| 只用 Node 树 | `com.mingliqiye.document.core` |
| Bean 映射 | `com.mingliqiye.document.serialization` |
| Kotlin data class | `com.mingliqiye.document.serialization.kotlin` |
| JSON / YAML / ... | `com.mingliqiye.document.format.json` 等 |
| KSP 代码生成 | `com.mingliqiye.document.codegen`（annotation processor 场景一般走 classpath，而不是模块路径） |

::: tip 自动模块之间会一起进入模块图
本库的模块都是自动模块。JDK 17 上的实际解析行为（`--show-module-resolution`）是：
`requires` 其中一个自动模块时，module path 上的其它自动模块（`core`、`serialization`、
`annotation`、`exception` 等）会一起进入模块图，因此不需要为它们逐个补 `requires`。

但**显式模块**不受这条规则影响：`kotlin.stdlib`、`com.fasterxml.jackson.core` 这类自带
`module-info.class` 的依赖不会被自动带上。消费方要么在 `module-info.java` 里显式
`requires`，要么在启动参数里声明，例如 `--add-modules ALL-MODULE-PATH`。
:::

### 端到端示例

在 JDK 17 上，一个模块化的消费方项目（`lib/` 为 module path，里面有本库 jar 与传递依赖）：

```java
// src/smoke.app/module-info.java
module smoke.app {
    requires com.mingliqiye.document.core;
    requires com.mingliqiye.document.format.json;
    requires kotlin.stdlib;                  // 显式模块：必须补 requires
    requires com.fasterxml.jackson.core;     // 显式模块：必须补 requires
}
```

```java
// src/smoke.app/smoke/Main.java
package smoke;

import com.mingliqiye.document.core.Node;
import com.mingliqiye.document.format.json.JsonMapper;

public final class Main {
    public static void main(String[] args) {
        JsonMapper mapper = new JsonMapper();
        Node node = mapper.parse("{\"hello\":\"jpms\"}", Node.class);
        System.out.println("JPMS smoke OK: " + mapper.format(node));
    }
}
```

```bash
javac --module-path lib -d out src/smoke.app/module-info.java src/smoke.app/smoke/Main.java
java --module-path "lib:out" -m smoke.app/smoke.Main
# Windows 的 module path 分隔符是 ';'：java --module-path "lib;out" -m smoke.app/smoke.Main
# JPMS smoke OK: {"hello":"jpms"}
```

::: warning alias-for 需要先补模块名
上面的示例能跑通，前提是 `alias-for` 已补过 `Automatic-Module-Name`（见[限制](#限制)第 6
条）。原版 `alias-for-1.0.3.jar` 会直接让 module path 初始化失败，原因见后文。
:::

## 验证方式

本页结论可以通过 JDK 9+ 的 `jar` 工具直接验证（构建 jar 不必跑测试）：

```bash
# 构建一个模块 jar（示例：core）
gradle :core:jar

# 查看模块描述；输出中的 automatic 表示这是自动模块
jar --describe-module --file core/build/libs/core-2.2.0.jar
```

JDK 17 上的实际输出（`core`）：

```text
com.mingliqiye.document.core@2.2.0 automatic
requires java.base mandated
contains com.mingliqiye.document.core
```

Windows PowerShell 写法：

```powershell
& "C:\Program Files\Zulu\zulu-17\bin\jar.exe" `
  --describe-module --file core\build\libs\core-2.2.0.jar
```

也可以直接检查清单：

```bash
# JDK 9+ 自带 jar 工具
unzip -p core/build/libs/core-2.2.0.jar META-INF/MANIFEST.MF | grep Automatic-Module-Name

# Windows
Expand-Archive core\build\libs\core-2.2.0.jar -DestinationPath tmp
Select-String -Path tmp\META-INF\MANIFEST.MF -Pattern Automatic-Module-Name
```

输出应包含：

```text
Automatic-Module-Name: com.mingliqiye.document.core
```

如果需要一次构建出全部可发布 jar 再逐个核对（仍然不跑测试）：

```bash
gradle assemble
```

## 限制

1. **没有显式导出**：自动模块会导出全部包，`internal` 实现包也对外可见，无法用 JPMS
   隐藏实现；也没法用 `opens` 精确控制反射访问。
2. **没有 `uses` / `provides`**：无法在模块描述里声明服务；依赖 `ServiceLoader` 的能力
   （例如 KSP 注解处理器）在模块路径场景需要消费方自行声明 `provides` / `--add-modules`。
   本库的 KSP 处理器日常仍建议走 classpath。
3. **不能用于 `jlink`**：`jlink` 只接受显式模块（有 `module-info.class` 的模块），自动模块
   不能进入自定义运行时镜像。
4. **split package 风险**：自动模块导出全部包，若消费方或其它依赖声明了同名包，模块解析
   会报错或出现可见性冲突。本库各模块之间不存在同名包（已按模块包名核对）。
5. **传递依赖也要在模块路径上**：自动模块之间会一起解析，但显式模块（`kotlin.stdlib`、
   `com.fasterxml.jackson.core`）需要消费方显式 `requires` 或用 `--add-modules` 声明；
   任何依赖缺失都会在解析阶段或运行期失败。
6. **第三方依赖并非都已模块化**：`serialization` 的运行时依赖
   `com.mingliqiye.utils.aliasfor:alias-for:1.0.3` 没有 `Automatic-Module-Name`，而按文件名
   推导出的模块名是 `alias.for` —— `for` 是 Java 关键字。JDK 17 在初始化 module path 时会
   直接失败：

   ```text
   java.lang.module.FindException: Unable to derive module descriptor for alias-for-1.0.3.jar
   Caused by: java.lang.IllegalArgumentException:
       alias.for: Invalid module name: 'for' is not a Java identifier
   ```

   因此把 `serialization` / `format-*` 的完整依赖链放进 module path 之前，需要先给该 jar
   补一个合法的模块名：

   ```bash
   echo "Automatic-Module-Name: com.mingliqiye.utils.aliasfor" > alias-for.module.txt
   jar --update --file alias-for-1.0.3.jar --manifest alias-for.module.txt
   ```

   （`mingli-exception:1.1.1-Final` 同样没有自动模块名，会被推导为 `mingli.exception`，
   可以解析，但名字不受本库控制。）其余格式依赖（`jackson-dataformat-*`、`tomlj`、
   `commons-csv`、`avro`、`protobuf-java`、`ion-java` 等）的模块名由各自上游决定，使用对应
   格式前需要确认它们能否放到 module path 上。
7. **JDK 8 上没有模块化收益**：`Automatic-Module-Name` 在 JDK 8 被忽略，JDK 8 仍然按
   classpath 使用。
8. **不是正式模块的承诺**：`jar --describe-module` 会显示 `automatic`；在补充
   `module-info.class` 之前，`jdeps --check`、`jlink`、模块级封装能力都不可用。

## 后续计划：Multi-Release module-info

路线图（`ROADMAP.md` 阶段 6）把「真正的 `module-info`」列为后续项，计划如下：

1. 为每个可发布模块新增仅在 JDK 9+ 编译的 `module-info.java`（独立 source set，
   `--release 9` 编译），内容包含：
   - `exports`：只导出公开 API 包（`com.mingliqiye.document.*` 的公共包）；
   - `requires transitive`：`core`、`exception` 等需要传递的模块依赖；
   - `provides`：`codegen` 的注解处理器（`javax.annotation.processing.Processor`）；
   - `requires static`：仅编译期需要的依赖（如 KSP API）。
2. 通过 Multi-Release 打包进 `META-INF/versions/9/module-info.class`，同时在清单声明
   `Multi-Release: true`；JDK 8 端仍只看到原 jar 内容，不受影响。
3. 补齐后 `jar --describe-module` 将显示显式模块（不再有 `automatic` 标记），届时才能
   用于 `jlink`，并可用 `jdeps --check` 做依赖收敛检查。

在此之前，`Automatic-Module-Name` 就是本库对 JPMS 的正式支持方式。
