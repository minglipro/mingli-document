---
title: JPMS、显式 module-info 与 Multi-Release JAR
---

# JPMS、显式 module-info 与 Multi-Release JAR

> 本页说明 `mingli-document` 在 Java 平台模块系统（JPMS / Project Jigsaw）下的支持方式。
>
> 结论先行：**`annotation` / `exception` / `core` / `schema` / `schema-registry` 五个模块的
> 主 jar 已经是 Multi-Release JAR：JDK 9+ 会读到真正的显式 `module-info.class`
> （`jar --describe-module --release 9` 不再显示 `automatic`），JDK 8 仍然只看到原来的
> JDK 8 字节码（class file 52），完全按 classpath 使用。其余可发布模块（`serialization`、
> `format-*` 等）依赖的第三方 jar 模块名不合法，暂时仍以「自动模块」方式提供，原因见
> [限制](#限制)。**
>
> 版本基线：`gradle.properties` 的 `projectVersion=2.2.0`、`jvmVersion=8`。主 classes 仍由
> JDK 8 工具链编译；`module-info` 使用 **JDK 17 工具链 + `javac --release 9`** 单独编译。

## 目录

- [当前支持范围](#当前支持范围)
- [模块名与形态清单](#模块名与形态清单)
- [Multi-Release JAR 的结构与构建方式](#multi-release-jar-的结构与构建方式)
- [各模块的 exports / requires](#各模块的-exports--requires)
- [在 module path 上使用](#在-module-path-上使用)
- [验证方式](#验证方式)
- [限制](#限制)
- [后续计划](#后续计划)

## 当前支持范围

| 产物 | 形态 | 说明 |
|---|---|---|
| `annotation` / `exception` / `core` / `schema` / `schema-registry` 的主 jar | ✅ 显式模块（Multi-Release） | JDK 9+ 读取 `META-INF/versions/9/module-info.class`；JDK 8 看不到该目录 |
| 其余可发布模块的主 jar（`serialization`、`serialization-kotlin`、`format-*`、`codegen`、`openapi`、`jdbc`、`validation` 等） | ⚠️ 自动模块 | 只有 `Automatic-Module-Name`，`jar --describe-module` 会显示 `automatic` |
| `javadoc` / `kotlindoc` classifier jar | ❌ 不参与 module path | 文档 jar 不参与模块解析 |
| `bom`（`mingli-document-bom`） | ❌ | `java-platform`，没有 jar |
| 根项目 `mingli-document`、`benchmarks`、`fuzz` | ❌ | 聚合/非发布模块，不写自动模块名 |

两类产物共同点：

- 所有可发布模块的主 jar 清单**仍然保留** `Automatic-Module-Name`：JDK 8、纯 classpath
  消费方完全不受影响，JDK 9+ 若因依赖链问题无法形成显式模块，也有稳定兜底名字；
- 主 classes 目标字节码仍是 JDK 8（class file 52）；`module-info.class` 是 class file 53，
  只存在于 `META-INF/versions/9/` 下。

> 显式模块只是在原有自动模块名的基础上「升级形态」，**模块名不变**。模块名是编译期/运行期
> 契约，一旦发布不应再改。

## 模块名与形态清单

命名规则：

- `format-*`：`com.mingliqiye.document.format.<格式名>`
- 其他模块：`com.mingliqiye.document.<模块名>`（模块名中的短横线 `-` 转成点 `.`，因为
  JPMS 模块名不允许 `-`）

### 已提供显式 `module-info` 的模块

| 模块 | Maven 坐标 | 模块名 |
|---|---|---|
| `annotation` | `com.mingliqiye.document:annotation` | `com.mingliqiye.document.annotation` |
| `exception` | `com.mingliqiye.document:exception` | `com.mingliqiye.document.exception` |
| `core` | `com.mingliqiye.document:core` | `com.mingliqiye.document.core` |
| `schema` | `com.mingliqiye.document:schema` | `com.mingliqiye.document.schema` |
| `schema-registry` | `com.mingliqiye.document:schema-registry` | `com.mingliqiye.document.schema.registry` |

### 仍为自动模块的模块

以下模块的主 jar 只带 `Automatic-Module-Name`（名称与上表规则一致）：

| 模块 | 模块名 |
|---|---|
| `serialization` | `com.mingliqiye.document.serialization` |
| `serialization-kotlin` | `com.mingliqiye.document.serialization.kotlin` |
| `codegen` | `com.mingliqiye.document.codegen` |
| `codegen-test` | `com.mingliqiye.document.codegen.test` |
| `openapi` | `com.mingliqiye.document.openapi` |
| `jdbc` | `com.mingliqiye.document.jdbc` |
| `validation` | `com.mingliqiye.document.validation` |
| `format-json` | `com.mingliqiye.document.format.json` |
| `format-yaml` | `com.mingliqiye.document.format.yaml` |
| `format-xml` | `com.mingliqiye.document.format.xml` |
| `format-properties` | `com.mingliqiye.document.format.properties` |
| `format-toml` | `com.mingliqiye.document.format.toml` |
| `format-csv` | `com.mingliqiye.document.format.csv` |
| `format-ion` | `com.mingliqiye.document.format.ion` |
| `format-cbor` | `com.mingliqiye.document.format.cbor` |
| `format-smile` | `com.mingliqiye.document.format.smile` |
| `format-avro` | `com.mingliqiye.document.format.avro` |
| `format-protobuf` | `com.mingliqiye.document.format.protobuf` |

::: tip 模块名是发布契约
自动模块名和 `module-info` 里的模块名一样，是使用方的编译期/运行期契约。本库按
`com.mingliqiye.document[.format].<模块>` 统一规划，格式模块单独使用 `format` 命名空间；
补显式 `module-info` 时只升级形态、不改名字。
:::

## Multi-Release JAR 的结构与构建方式

### 结构

```text
core-2.2.0.jar
├── META-INF/MANIFEST.MF
│   ├── Automatic-Module-Name: com.mingliqiye.document.core
│   └── Multi-Release: true
├── com/mingliqiye/document/core/*.class      (class file 52，JDK 8 使用)
└── META-INF/versions/9/
    └── module-info.class                     (class file 53，仅 JDK 9+ 读取)
```

JDK 8 的 `JarFile` 不认识 `Multi-Release` 清单属性，会把
`META-INF/versions/9/module-info.class` 当作普通条目，永远不会加载；JDK 9+ 的 `JarFile`
依据 `Multi-Release: true` 优先读取 `META-INF/versions/9/` 下的条目，模块系统因此拿到显式
模块描述。

### 构建方式（根 `build.gradle.kts`）

1. 模块描述放在 `src/main/java9/module-info.java`，**不加入主 source set**。主源码仍由
   JDK 8 工具链编译（各模块 `kotlin { jvmToolchain(8) }`），所以 JDK 8 编译、测试、发布、
   Dokka、ktlint 都不需要改动。
2. 根构建脚本检测到 `src/main/java9/module-info.java` 后注册 `compileJava9ModuleInfo` 任务：
   - 使用 JDK 17 工具链的 `javac`（`JavaToolchainService.compilerFor { 17 }`）；
   - `--release 9`：按 JDK 9 API/语言级别编译模块描述；
   - `--patch-module <模块名>=<主 classes 目录>`：把已编译的主 classes 挂到待编译模块上，
     否则单独编译 `module-info.java` 时 javac 会报 `package ... is empty or does not exist`；
   - `--module-path`：由 `compileClasspath` 用 `artifactView` 重新选择 `LibraryElements=jar`
     变体得到——同构建内的依赖模块使用打好的 Multi-Release jar（各自已带显式描述），第三方
     依赖使用普通 jar（`kotlin.stdlib` 是显式模块，`mingli.exception` 是自动模块）；
   - `-encoding UTF-8`、`-proc:none`。
3. 模块的 `jar` 任务把编译结果放进 `META-INF/versions/9/module-info.class`，并在清单写入
   `Multi-Release: true`；同时校验 `module-info.java` 中声明的模块名必须与
   `Automatic-Module-Name` 一致，不一致直接构建失败。
4. 只有包含 `src/main/java9/module-info.java` 的模块走这套逻辑；其余模块保持原行为。

::: tip 工具链要求
编译 `module-info` 需要 JDK 9+（本仓库固定 JDK 17）的 `javac`。Gradle 会自动探测本机已安装
的 JDK，或通过 settings 中的 `foojay-resolver-convention` 下载；其余源码仍是 JDK 8 工具链。
:::

## 各模块的 exports / requires

| 模块 | `exports` | `requires` |
|---|---|---|
| `com.mingliqiye.document.annotation` | `com.mingliqiye.document.annotation` | `transitive kotlin.stdlib` |
| `com.mingliqiye.document.exception` | `com.mingliqiye.document.exception` | `transitive kotlin.stdlib`、`mingli.exception`（自动模块） |
| `com.mingliqiye.document.core` | `com.mingliqiye.document.core` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.exception` |
| `com.mingliqiye.document.schema` | `com.mingliqiye.document.schema` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core` |
| `com.mingliqiye.document.schema.registry` | `com.mingliqiye.document.registry`、`com.mingliqiye.document.registry.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.schema` |

说明：

- `kotlin.stdlib` 是**显式模块**（`kotlin-stdlib-2.0.0.jar` 自带 Multi-Release
  `module-info.class`）。Kotlin 公共 API 的类型来自它，所以用 `requires transitive`，
  消费方 `requires` 本库模块即可获得 stdlib 可读性。
- `mingli.exception` 没有显式 `module-info`，模块名由文件名推导
  （`mingli-exception-1.1.1-Final.jar` → `mingli.exception`）。`exception` 模块只做普通
  `requires`；如果消费方直接使用 `MingLiBaseException` 的 API，需要自行
  `requires mingli.exception`。
- `core` 的公开 API 会抛出 `DocumentTypeException`，`schema-registry` 的公开 API 会抛出
  `DocumentException` 家族异常，因此对 `exception` 使用 `requires transitive`，与 Gradle 的
  `api(project(":exception"))` 语义一致。
- `schema` 的当前实现没有直接引用 `core` 类型，但构建声明了 `api(project(":core"))`；
  `module-info` 保留 `requires transitive com.mingliqiye.document.core`，避免消费方从
  classpath 迁移到 module path 时行为变化。
- 每个模块只导出公共 API 包。`Default*` 实现类与公共 API 在同一个包里（Kotlin `internal`
  在字节码层仍是 public，这一点与自动模块相同）；真正隐藏实现需要拆分 internal 包，属于后续
  工作。

## 在 module path 上使用

### 端到端示例（JDK 17）

一个模块化的消费方项目（`lib/` 为 module path，包含本库 jar 与全部传递依赖）：

```java
// src/smoke.app/module-info.java
module smoke.app {
    requires com.mingliqiye.document.core;
    requires com.mingliqiye.document.schema;
}
```

```java
// src/smoke.app/smoke/Main.java
package smoke;

import com.mingliqiye.document.core.Node;
import com.mingliqiye.document.core.StringNode;
import com.mingliqiye.document.schema.DocumentSchema;
import com.mingliqiye.document.schema.SchemaField;
import com.mingliqiye.document.schema.SchemaType;

public final class Main {
    public static void main(String[] args) {
        Node node = StringNode.of("jpms");
        DocumentSchema schema = DocumentSchema.of(
                "smoke",
                SchemaField.of("name", SchemaType.STRING, true, false));
        System.out.println("JPMS-MR smoke OK: node=" + node.asString()
                + ", schema=" + schema.getName()
                + ", fields=" + schema.getFields().size());
    }
}
```

`lib/` 至少需要：`exception-2.2.0.jar`、`core-2.2.0.jar`、`schema-2.2.0.jar`，以及依赖
`kotlin-stdlib-2.0.0.jar`、`mingli-exception-1.1.1-Final.jar`（只有直接使用注解时才需要
`annotation-2.2.0.jar`）。

```bash
javac --module-path lib -d out src/smoke.app/module-info.java src/smoke.app/smoke/Main.java
java --module-path "lib:out" -m smoke.app/smoke.Main
# Windows 的 module path 分隔符是 ';'：
# java --module-path "lib;out" -m smoke.app/smoke.Main
# JPMS-MR smoke OK: node=jpms, schema=smoke, fields=1
```

JDK 17 实际运行（`--show-module-resolution` 节选）可以看到显式模块逐个解析：

```text
root smoke.app file:///.../out/
smoke.app requires com.mingliqiye.document.schema file:///.../lib/schema-2.2.0.jar
smoke.app requires com.mingliqiye.document.core file:///.../lib/core-2.2.0.jar
com.mingliqiye.document.schema requires kotlin.stdlib file:///.../lib/kotlin-stdlib-2.0.0.jar
com.mingliqiye.document.schema requires com.mingliqiye.document.core file:///.../lib/core-2.2.0.jar
com.mingliqiye.document.core requires com.mingliqiye.document.exception file:///.../lib/exception-2.2.0.jar
com.mingliqiye.document.core requires kotlin.stdlib file:///.../lib/kotlin-stdlib-2.0.0.jar
com.mingliqiye.document.exception requires kotlin.stdlib file:///.../lib/kotlin-stdlib-2.0.0.jar
com.mingliqiye.document.exception requires mingli.exception file:///.../lib/mingli-exception-1.1.1-Final.jar automatic
```

### 显式模块与自动模块的解析差异

- 自动模块会「抱团」进入模块图：`requires` 一个自动模块时，module path 上其它自动模块会一起
  解析。**显式模块不会**：只按 `requires` / `requires transitive` 逐个解析。
- 五个模块变成显式模块后，module path 上必须同时提供它们声明的依赖：
  `com.mingliqiye.document.exception`（`core` / `schema-registry` 需要）、
  `mingli.exception`（`exception` 需要）、`kotlin.stdlib`（全部需要）。缺任何一个都会在
  编译或启动解析阶段失败。
- `requires transitive` 让消费方无需重复声明：`requires com.mingliqiye.document.core` 时，
  `com.mingliqiye.document.exception`（以及 `kotlin.stdlib`）自动可读。

### JDK 8 行为不变

JDK 8 不认识模块系统，也不会读 `META-INF/versions/9`：它按普通 classpath 加载主 classes，
`Automatic-Module-Name`、`Multi-Release` 清单属性与 versioned `module-info.class` 都会被忽略。
实测（Zulu JDK 8 + classpath）：

```bash
javac -cp "lib/*" -d out8 src8/smoke/Main.java
java -cp "out8;lib/*" smoke.Main
# JDK8 classpath smoke OK: node=jpms, schema=smoke, fields=1
```

### 建议的 requires 组合

| 场景 | 需要的模块名 | 形态 |
|---|---|---|
| 只用 Node 树 | `com.mingliqiye.document.core` | 显式模块 |
| Schema / Schema Registry | `com.mingliqiye.document.schema`、`.schema.registry` | 显式模块 |
| 注解（`@DocumentName` 等） | `com.mingliqiye.document.annotation` | 显式模块 |
| Bean 映射 | `com.mingliqiye.document.serialization` | 自动模块 |
| Kotlin data class | `com.mingliqiye.document.serialization.kotlin` | 自动模块 |
| JSON / YAML / ... | `com.mingliqiye.document.format.json` 等 | 自动模块 |
| KSP 代码生成 | `com.mingliqiye.document.codegen`（注解处理器场景一般走 classpath） | 自动模块 |

::: warning 混合使用注意
`serialization` / `format-*` 仍是自动模块。自动模块会导出全部包，并且 `requires` 它们时会
把 module path 上其它自动模块一并拉入模块图；与本库的显式模块混用时，显式模块的依赖仍需
逐个提供（见[限制](#限制)）。
:::

## 验证方式

下面命令不需要跑全量测试。构建五个模块 jar：

```bash
gradle :annotation:jar :exception:jar :core:jar :schema:jar :schema-registry:jar
# 需要连同测试/ktlint/dokka 一起验证时：
gradle :annotation:build :exception:build :core:build :schema:build :schema-registry:build
```

### 1. 查看模块描述（确认是显式模块）

```bash
jar --describe-module --file core/build/libs/core-2.2.0.jar --release 9
```

JDK 17 实际输出：

```text
releases: 9

com.mingliqiye.document.core jar:file:///.../core-2.2.0.jar!/META-INF/versions/9/module-info.class
exports com.mingliqiye.document.core
requires com.mingliqiye.document.exception transitive
requires java.base mandated
requires kotlin.stdlib transitive
```

- `releases: 9` 表示这是 Multi-Release jar；
- 输出中**没有 `automatic` 标记**，说明读取的是显式模块描述；
- 若省略 `--release 9`，JDK 17 的 `jar` 会提示「没有模块描述符，请指定 --release」
  （基版本本来就没有 `module-info.class`）。

### 2. 校验 Multi-Release jar

```bash
jar --validate --file core/build/libs/core-2.2.0.jar
```

命令无输出且退出码为 0 表示 jar 结构合法。

### 3. 检查 jar 内容与清单

```bash
jar tf core/build/libs/core-2.2.0.jar | grep module-info
# META-INF/versions/9/module-info.class
# （基版本根目录没有 module-info.class）
```

Windows PowerShell：

```powershell
Expand-Archive core\build\libs\core-2.2.0.jar -DestinationPath tmp
Get-Content tmp\META-INF\MANIFEST.MF | Select-String 'Multi-Release|Automatic-Module-Name'
# Automatic-Module-Name: com.mingliqiye.document.core
# Multi-Release: true
```

### 4. 主 classes 仍是 JDK 8 字节码

```bash
javap -verbose -cp core/build/libs/core-2.2.0.jar com.mingliqiye.document.core.StringNode \
  | grep 'major version'
# major version: 52
```

`META-INF/versions/9/module-info.class` 的 class file 版本是 53，只对 JDK 9+ 生效。

### 5. JDK 17 module path smoke

见上文[端到端示例](#端到端示例jdk-17)：用 `javac --module-path lib` 编译、`java -m` 运行，
输出 `JPMS-MR smoke OK: ...`。

### 6. jlink（仅对没有自动模块依赖的模块可用）

```bash
jlink --module-path lib --add-modules com.mingliqiye.document.annotation --output image
# 成功：annotation 只依赖 kotlin.stdlib（显式模块），可以进入自定义运行时镜像
```

`core` / `schema` / `schema-registry` 因为依赖自动模块 `mingli.exception`，`jlink` 仍会失败
（见[限制](#限制)）。

## 限制

1. **只有五个模块是显式模块**：`serialization` / `format-*` / `codegen` / `openapi` / `jdbc` /
   `validation` 等仍为自动模块。原因是它们的运行时依赖
   `com.mingliqiye.utils.aliasfor:alias-for:1.0.3` 没有 `Automatic-Module-Name`，按文件名
   推导出的模块名是 `alias.for`——`for` 是 Java 关键字。JDK 17 初始化 module path 时直接失败：

   ```text
   java.lang.module.FindException: Unable to derive module descriptor for alias-for-1.0.3.jar
   Caused by: java.lang.IllegalArgumentException:
       alias.for: Invalid module name: 'for' is not a Java identifier
   ```

   需要先给该 jar 补一个合法的模块名（或等上游发布新版本），才能给这些模块补显式
   `module-info`。
2. **`mingli.exception` 是自动模块**：模块名由文件名推导，不受本库控制；`exception` 模块对它只做
   普通 `requires`（不使用 `requires transitive`），消费方直接使用 `MingLiBaseException` 时需
   自行 `requires mingli.exception`。
3. **`jlink` 仅 `annotation` 可用**：`core` / `schema` / `schema-registry` 的依赖链里有自动模块
   `mingli.exception`，`jlink` 只接受显式模块，因此仍不能生成自定义运行时镜像。
4. **没有 `uses` / `provides`**：本批五个模块没有服务提供者。`codegen` 的注解处理器日常仍建议
   走 classpath，而不是 module path。
5. **Kotlin 消费方的 JPMS 支持有限**：Kotlin 编译器不编译 `module-info.kt`，Kotlin + Java
   混合模块需要由 Java 侧 `module-info.java` 承担模块描述；Kotlin 2.0 对 module path 上消费
   Kotlin 库的支持仍在演进。
6. **依赖必须同时放在 module path 上**：显式模块不会自动带上兄弟模块，`kotlin-stdlib` 与
   `mingli-exception` 必须提供；命名模块默认读不到 classpath（unnamed module）里的类。
7. **split package 仍需注意**：五个模块已改为显式 `exports`，但自动模块（其余模块）导出全部包；
   混用时若消费方或其它依赖声明了同名包，模块解析会报错或出现可见性冲突。新增模块时应保证包
   名不重叠（本库现有模块已按包名核对）。
8. **JDK 8 没有模块化收益**：JDK 8 忽略 `Multi-Release` 与模块描述，仍按 classpath 使用；
   新增的 `module-info.class` 对 JDK 8 不可见。
9. **模块名不可随意变更**：显式模块名与 `Automatic-Module-Name` 必须一致（构建脚本会校验），
   改名属于破坏性变更。
10. **构建工具链要求**：编译 module-info 需要 JDK 9+（本仓库固定 JDK 17）工具链。只装 JDK 8
    的环境需要让 Gradle 自动下载/指定 JDK 17，否则五个模块的 `jar` 任务会失败。

## 后续计划

1. **`alias-for` 模块名**：向上游反馈或在本库测试/打包流程中提供补好
   `Automatic-Module-Name` 的版本，之后给 `serialization` / `format-*` 等模块补
   `src/main/java9/module-info.java`。
2. **`mingli-exception` 显式模块化**：建议上游提供 `module-info` 或合法自动模块名，解除
   `jlink` 与 `requires transitive` 限制。
3. **服务声明**：为 `codegen` 的注解处理器补 `provides javax.annotation.processing.Processor`，
   并评估各格式模块的 `uses`。
4. **内部包拆分**：把 `internal` 实现移到独立包并由 `exports` 真正隐藏，而不是依赖 Kotlin
   `internal` 的字节码可见性。
5. **CI 校验**：在 CI 中增加 JDK 17 module path smoke、`jar --validate` 与
   `jar --describe-module --release 9` 的断言，防止 Multi-Release 打包回归。
