---
title: JPMS、显式 module-info 与 Multi-Release JAR
---

# JPMS、显式 module-info 与 Multi-Release JAR

> 本页说明 `mingli-document` 在 Java 平台模块系统（JPMS / Project Jigsaw）下的支持方式。
>
> 结论先行：**全部 26 个可发布模块的主 jar 都已经是 Multi-Release JAR：JDK 9+ 会读到真正的
> 显式 `module-info.class`（`jar --describe-module --release 9` 不再显示 `automatic`），
> JDK 8 仍然只看到原来的 JDK 8 字节码（class file 52），完全按 classpath 使用。
> 其中 `record-support` 是 JDK 17 字节码（class file 61）的可选增强模块，只在 JDK 17+
> 运行期使用（record 映射依赖它）；`record-support` / `codegen` 还通过 `provides` 声明了
> `JavaRecordSupport` 服务与 KSP 处理器。`format-json` 已改用 JDK `Double.toString`，
> module path 上写 `Double` 不再需要 `--add-exports`。**

>
> 版本基线：`gradle.properties` 的 `projectVersion=2.5.1`、`jvmVersion=8`。主 classes 仍由
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
| 全部 26 个可发布模块的主 jar（`annotation` / `exception` / `core` / `schema` / `schema-registry` / `serialization` / `jdbc` / `validation` / `jpa` / `jpa-hibernate` / 11 个 `format-*` / `serialization-kotlin` / `record-support` / `codegen` / `codegen-test` / `openapi`） | ✅ 显式模块（Multi-Release） | JDK 9+ 读取 `META-INF/versions/9/module-info.class`；JDK 8 看不到该目录。本批新增：`serialization-kotlin` / `record-support` / `codegen` / `codegen-test` / `openapi` |
| `javadoc` / `kotlindoc` classifier jar | ❌ 不参与 module path | 文档 jar 不参与模块解析 |
| `bom`（`mingli-document-bom`） | ❌ | `java-platform`，没有 jar |
| 根项目 `mingli-document`、`benchmarks`、`fuzz` | ❌ | 聚合/非发布模块，不写自动模块名 |

共同点：

- 所有可发布模块的主 jar 清单**仍然保留** `Automatic-Module-Name`：JDK 8、纯 classpath
  消费方完全不受影响，JDK 9+ 若因依赖链问题无法形成显式模块，也有稳定兜底名字；
- 主 classes 目标字节码仍是 JDK 8（class file 52）；`module-info.class` 是 class file 53，
  只存在于 `META-INF/versions/9/` 下。唯一例外是 `record-support`：它是 JDK 17 字节码
  （class file 61）的可选模块，只在 JDK 17+ 运行期使用。

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
| `serialization` | `com.mingliqiye.document:serialization` | `com.mingliqiye.document.serialization` |
| `jdbc` | `com.mingliqiye.document:jdbc` | `com.mingliqiye.document.jdbc` |
| `validation` | `com.mingliqiye.document:validation` | `com.mingliqiye.document.validation` |
| `jpa` | `com.mingliqiye.document:jpa` | `com.mingliqiye.document.jpa` |
| `format-avro` | `com.mingliqiye.document.format:avro` | `com.mingliqiye.document.format.avro` |
| `format-cbor` | `com.mingliqiye.document.format:cbor` | `com.mingliqiye.document.format.cbor` |
| `format-csv` | `com.mingliqiye.document.format:csv` | `com.mingliqiye.document.format.csv` |
| `format-ion` | `com.mingliqiye.document.format:ion` | `com.mingliqiye.document.format.ion` |
| `format-json` | `com.mingliqiye.document.format:json` | `com.mingliqiye.document.format.json` |
| `format-properties` | `com.mingliqiye.document.format:properties` | `com.mingliqiye.document.format.properties` |
| `format-protobuf` | `com.mingliqiye.document.format:protobuf` | `com.mingliqiye.document.format.protobuf` |
| `format-smile` | `com.mingliqiye.document.format:smile` | `com.mingliqiye.document.format.smile` |
| `format-toml` | `com.mingliqiye.document.format:toml` | `com.mingliqiye.document.format.toml` |
| `format-xml` | `com.mingliqiye.document.format:xml` | `com.mingliqiye.document.format.xml` |
| `format-yaml` | `com.mingliqiye.document.format:yaml` | `com.mingliqiye.document.format.yaml` |
| `jpa-hibernate` | `com.mingliqiye.document:jpa-hibernate` | `com.mingliqiye.document.jpa.hibernate` |
| `serialization-kotlin` | `com.mingliqiye.document:serialization-kotlin` | `com.mingliqiye.document.serialization.kotlin` |
| `record-support` | `com.mingliqiye.document:record-support` | `com.mingliqiye.document.record.support` |
| `codegen` | `com.mingliqiye.document:codegen` | `com.mingliqiye.document.codegen` |
| `codegen-test` | `com.mingliqiye.document:codegen-test` | `com.mingliqiye.document.codegen.test` |
| `openapi` | `com.mingliqiye.document:openapi` | `com.mingliqiye.document.openapi` |

### 模块形态说明

全部可发布模块都已提供 `src/main/java9/module-info.java`，**没有剩余的「可发布自动模块」**：
`benchmarks` / `fuzz` 不是可发布模块，`bom` 是 `java-platform`（没有 jar），根项目只做聚合，
它们都不需要模块描述。第三方依赖中 `mingli.exception` / `alias-for` 已显式；
仍有自动模块（Avro / Ion / TOML / commons-csv / protobuf-java、`hibernate-core` 等），
见[限制](#限制)。

::: tip 模块名是发布契约
自动模块名和 `module-info` 里的模块名一样，是使用方的编译期/运行期契约。本库按
`com.mingliqiye.document[.format].<模块>` 统一规划，格式模块单独使用 `format` 命名空间；
补显式 `module-info` 时只升级形态、不改名字。
:::

## Multi-Release JAR 的结构与构建方式

### 结构

```text
core-2.5.1.jar
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
     变体得到——同构建内的依赖模块使用打好的 Multi-Release jar（各自已带显式描述）；第三方
     依赖使用普通 jar：`kotlin.stdlib`、`jakarta.validation`、`jakarta.persistence`、
     `JSON Token 底层库` / `数据绑定底层库` / `CBOR 底层库` /
     `Smile 底层库`、`snakeyaml`、`jakarta.xml.bind` 等是显式模块；
     `org.apache.avro`、`com.amazon.ion`、`tomlj`、
     `org.apache.commons.csv`、`com.google.protobuf`、`org.hibernate.orm.core` 等是自动模块；
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
| `com.mingliqiye.document.serialization` | `com.mingliqiye.document.serialization`、`.serialization.codegen`、`.serialization.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.annotation`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`com.mingliqiye.utils.aliasfor`（自动模块）、`mingli.exception`（自动模块）、`java.desktop`、`java.sql`；`uses com.mingliqiye.document.serialization.JavaRecordSupport` |
| `com.mingliqiye.document.jdbc` | `com.mingliqiye.document.jdbc`、`com.mingliqiye.document.jdbc.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.serialization`、`com.mingliqiye.document.exception`、`transitive java.sql` |
| `com.mingliqiye.document.validation` | `com.mingliqiye.document.validation` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.serialization`、`transitive jakarta.validation` |
| `com.mingliqiye.document.jpa` | `com.mingliqiye.document.jpa`、`com.mingliqiye.document.jpa.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.serialization`、`com.mingliqiye.document.exception`、`transitive jakarta.persistence`、`java.desktop`、`java.sql` |
| `com.mingliqiye.document.format.avro` | `.format.avro`、`.format.avro.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.schema`、`transitive com.mingliqiye.document.serialization`、`org.apache.avro`（自动模块） |
| `com.mingliqiye.document.format.cbor` | `.format.cbor`、`.format.cbor.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`第三方底层库坐标.core`、`第三方底层库坐标.databind`、`第三方底层库坐标.dataformat.cbor` |
| `com.mingliqiye.document.format.csv` | `.format.csv`、`.format.csv.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.schema`、`transitive com.mingliqiye.document.serialization`、`org.apache.commons.csv`（自动模块） |
| `com.mingliqiye.document.format.ion` | `.format.ion`、`.format.ion.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`com.amazon.ion`（自动模块） |
| `com.mingliqiye.document.format.json` | `.format.json`、`.format.json.exception`、`.format.json.schema` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`（无第三方依赖） |
| `com.mingliqiye.document.format.properties` | `.format.properties`、`.format.properties.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`（无第三方依赖） |
| `com.mingliqiye.document.format.protobuf` | `.format.protobuf`、`.format.protobuf.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.schema`、`transitive com.mingliqiye.document.serialization`、`com.google.protobuf`（自动模块） |
| `com.mingliqiye.document.format.smile` | `.format.smile`、`.format.smile.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`第三方底层库坐标.core`、`第三方底层库坐标.dataformat.smile` |
| `com.mingliqiye.document.format.toml` | `.format.toml`、`.format.toml.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`tomlj`（自动模块） |
| `com.mingliqiye.document.format.xml` | `.format.xml`、`.format.xml.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`com.mingliqiye.document.annotation`、`java.xml` |
| `com.mingliqiye.document.format.yaml` | `.format.yaml`、`.format.yaml.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`org.yaml.snakeyaml` |
| `com.mingliqiye.document.jpa.hibernate` | `.jpa.hibernate`、`.jpa.hibernate.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.jpa`、`transitive com.mingliqiye.document.core`、`com.mingliqiye.document.exception`、`transitive jakarta.persistence`、`org.hibernate.orm.core`（自动模块） |
| `com.mingliqiye.document.serialization.kotlin` | `.serialization.kotlin` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.serialization`、`transitive com.mingliqiye.document.core`、`com.mingliqiye.document.annotation`、`com.mingliqiye.document.exception`、`kotlin.reflect`、`java.desktop` |
| `com.mingliqiye.document.record.support` | `.serialization.record` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.serialization`；`provides com.mingliqiye.document.serialization.JavaRecordSupport with com.mingliqiye.document.serialization.record.Jdk17RecordSupport`（主 classes 为 JDK 17 / class file 61） |
| `com.mingliqiye.document.codegen` | `.codegen` | `transitive kotlin.stdlib`、`symbol.processing.api`（自动模块）；`provides com.google.devtools.ksp.processing.SymbolProcessorProvider with com.mingliqiye.document.codegen.DocumentEnumCodecProcessorProvider` |
| `com.mingliqiye.document.codegen.test` | `.codegen.test`、`.generated`、`.generated.custom` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.annotation`、`transitive com.mingliqiye.document.serialization` |
| `com.mingliqiye.document.openapi` | `.openapi`、`.openapi.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.schema`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception` |

说明：

- `kotlin.stdlib` 是**显式模块**（`kotlin-stdlib-2.0.0.jar` 自带 Multi-Release
  `module-info.class`）。Kotlin 公共 API 的类型来自它，所以用 `requires transitive`，
  消费方 `requires` 本库模块即可获得 stdlib 可读性。
- `mingli.exception` 没有显式 `module-info`，模块名由文件名推导
  （`mingli-exception-1.1.2-Final.jar` → `mingli.exception`）。`exception` 模块只做普通
  `requires`；如果消费方直接使用 `MingLiBaseException` 的 API，需要自行
  `requires mingli.exception`。`serialization` 也保留一条普通 `requires mingli.exception`
  （与 `exception` 模块保持一致；普通 `requires` 只影响本模块可读性，消费方仍需自行声明）。
- `alias-for` 1.0.5 起提供显式 `module-info`（模块名 `com.mingliqiye.utils.aliasfor`），可被
  `requires`（仍是自动模块）。`serialization` 只在模块内部用 `AnnotationGetter` 读取注解，
  消费方一般不需要直接读取它，所以用普通 `requires`。
- `core` 的公开 API 会抛出 `DocumentTypeException`，`schema-registry` 的公开 API 会抛出
  `DocumentException` 家族异常，因此对 `exception` 使用 `requires transitive`，与 Gradle 的
  `api(project(":exception"))` 语义一致。
- `schema` 的当前实现没有直接引用 `core` 类型，但构建声明了 `api(project(":core"))`；
  `module-info` 保留 `requires transitive com.mingliqiye.document.core`，避免消费方从
  classpath 迁移到 module path 时行为变化。
- `serialization` / `jdbc` / `jpa` 对 Gradle 里声明为 `api(...)` 的依赖使用
  `requires transitive`（`core`、`exception`、`annotation`、`serialization`、`java.sql`、
  `jakarta.persistence`），这样消费方 `requires` 这些模块后就能直接使用公开 API 里出现的
  `Node`、`DocumentMapper`、`ResultSet`、JPA 注解等类型；自动模块（`aliasfor`、
  `mingli.exception`）按上面的约定只用普通 `requires`。
- `serialization` 与 `jpa` 需要 `java.desktop`：无参 Bean/实体属性读取使用
  `java.beans.Introspector`（`java.beans` 在 `java.desktop` 模块里）。`serialization` 的日期
  时间转换器还直接引用 `java.sql.Date` / `java.sql.Timestamp`，因此需要 `java.sql`；`jpa` 的
  `@Temporal` 转换也使用 `java.sql.Date/Time/Timestamp`。`jdbc` 的公开 API 直接以
  `java.sql.ResultSet` / `java.sql.Types` 为参数，因此对 `java.sql` 使用 `requires transitive`。
- `jakarta.validation-api-3.0.2.jar` 与 `jakarta.persistence-api-3.1.0.jar` 都是**显式模块**，
  模块名分别为 `jakarta.validation`、`jakarta.persistence`。`validation` 的公开构造器直接接收
  `jakarta.validation.Validator`，因此对 `jakarta.validation` 用 `requires transitive`；
  `jpa` 只在映射内部读取 JPA 注解，但构建把它声明为 `api(...)`，所以对
  `jakarta.persistence` 也用 `requires transitive`。
- 11 个 `format-*` 模块和 `jpa-hibernate` 采用同一套映射规则：Gradle 里声明为
  `api(project(...))` 的同构建模块（`core` / `exception` / `schema` / `serialization` /
  `annotation` / `jpa`）用 `requires transitive`，消费方 `requires` 一个格式模块即可直接使用
  公开 API 里的 `Node`、`DocumentMapper`、`DocumentSchema` 等类型；第三方依赖按模块形态区分：
  `JSON Token 底层库` / `数据绑定底层库` / `CBOR 底层库` /
  `Smile 底层库` / `snakeyaml` 自带显式 `module-info`（Multi-Release），
  其余 `org.apache.avro`、`com.amazon.ion`、`tomlj`、`org.apache.commons.csv`、
  `com.google.protobuf`、`org.hibernate.orm.core` 是自动模块，按本库约定只用普通
  `requires`（自动模块导出全部包，但没有 `requires transitive` 语义，消费方直接使用这些
  第三方类型时建议自行 `requires`）。
- 第三方模块名均经 `jar --describe-module` 实测核对：
  `JSON Token 底层库` → `第三方底层库坐标.core`，`CBOR 底层库` →
  `第三方底层库坐标.dataformat.cbor`（`module-info` 还 requires
  `第三方底层库坐标.databind`，运行时 module path 上必须提供 databind 与
  `第三方底层库坐标.annotation`），`Smile 底层库` →
  `第三方底层库坐标.dataformat.smile`（同理需要 databind），`snakeyaml` →
  `org.yaml.snakeyaml`，`avro` → `org.apache.avro`（自动模块），`ion-java` →
  `com.amazon.ion`（自动模块），`tomlj` → `tomlj`（自动模块，其 antlr4-runtime 依赖也是
  自动模块），`commons-csv` → `org.apache.commons.csv`（自动模块），`protobuf-java` →
  `com.google.protobuf`（自动模块），`hibernate-core` → `org.hibernate.orm.core`
  （自动模块）。
- `format-xml` 的 `java.xml` 必须显式声明：`javax.xml.parsers.DocumentBuilderFactory`、
  `org.w3c.dom`、`org.xml.sax` 都在 JDK 的 `java.xml` 模块里（JDK 9+ 不再默认导出给具名
  模块）。它通过 `implementation(project(":annotation"))` 使用 `annotation` 模块，因此只有
  普通 `requires`。
- `format-json` 的 `Double` 输出使用 JDK `Double.toString`，module path 与 classpath
  行为一致，不需要 `--add-exports`。
- `jpa-hibernate` 的公开 API 暴露 `JpaConfig` / `JpaDocumentMapper` / `Node` /
  `jakarta.persistence.EntityManager` / `org.hibernate.Session`，因此 `jpa`、`core`、
  `jakarta.persistence`、`kotlin.stdlib` 用 `requires transitive`；`exception`（公开异常
  继承 `DocumentException`）与自动模块 `org.hibernate.orm.core` 用普通 `requires`。
  它的 `@Temporal` 等映射复用 `jpa` 模块，`java.sql` / `java.desktop` 由 `jpa` 模块声明、
  `jakarta.persistence` 也 `requires transitive java.sql`，本模块自身不直接引用这两个
  JDK 模块的类，因此不再重复声明。
- 每个模块只导出公共 API 包。`jdbc.internal`、`jpa.internal` 是内部实现包，**不导出**；
  `serialization` 的 `codegen` 包里有公开类（`DocumentAccessorCodegen` 等），因此导出；
  `format-json` 的 `schema` 包（`JsonSchema`、`JsonSchemaValidationError`、
  `JsonSchemaValidationException`）是公开 API，因此导出。其余格式模块只有
  `format.<name>` 与 `format.<name>.exception` 两个公共包。
  Kotlin `internal` 类在字节码层仍是 public，这一点与自动模块相同；真正隐藏实现需要继续
  拆分 internal 包，属于后续工作。
- `serialization-kotlin` 的 `kotlin.reflect` 来自 `kotlin-reflect-2.0.0.jar`：该 jar 自带
  Multi-Release 显式 `module-info`，模块名为 `kotlin.reflect`，只导出 `kotlin.reflect.full` /
  `kotlin.reflect.jvm`（`KClass` / `KType` 等基础接口在 `kotlin.stdlib` 的 `kotlin.reflect`
  包里，所以同时需要 `kotlin.stdlib`）。`java.desktop` 来自 `java.beans.Introspector`；
  `annotation` / `exception` 只在实现内部使用，因此用普通 `requires`。
- `record-support` 的 `provides` 与 `serialization` 的 `uses` 配对：module path 上消费方
  只需 `requires com.mingliqiye.document.record.support`（或 `--add-modules`）让本模块进入
  模块图，`ServiceLoader` 就能在 `serialization` 侧发现 provider；未解析本模块时 record
  映射安全降级为普通 Bean。classpath 使用方式继续走 `META-INF/services` 文件（jar 内保留）。
  本模块主 classes 是 class file 61，JDK 8–16 不能加载。
- `codegen` 依赖的 `symbol-processing-api-2.0.0-1.0.21.jar` 没有显式 `module-info`、也没有
  `Automatic-Module-Name`，按文件名推导出的自动模块名是 `symbol.processing.api`。模块通过
  `provides com.google.devtools.ksp.processing.SymbolProcessorProvider` 声明 KSP 处理器；
  KSP 构建插件日常以 classpath 方式加载处理器（继续读 `META-INF/services`），不受影响；
  若在 module path 上使用，则必须同时提供 KSP API jar 与 `kotlin-stdlib`。
- `codegen-test` 是 codegen 的示例/测试支持模块：编译期由 KSP 生成
  `com.mingliqiye.document.generated`（及 `.generated.custom`）包，生成代码随主 jar 一起
  发布，因此这三个包都 `exports`。它是测试夹具，不建议在正式 module path 上与「同样生成
  `com.mingliqiye.document.generated` 包」的消费方模块同时使用（split package 风险）。
- `openapi` 与 Gradle 的 `api(project(":schema"))` / `api(project(":core"))` /
  `api(project(":exception"))` 一致使用 `requires transitive`，消费方 `requires
  com.mingliqiye.document.openapi` 后可直接使用 `DocumentSchema` / `Node` 等公开类型，并
  捕获继承自 `DocumentException` 的 `OpenApiException`。

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

`lib/` 至少需要：`exception-2.5.1.jar`、`core-2.5.1.jar`、`schema-2.5.1.jar`，以及依赖
`kotlin-stdlib-2.0.0.jar`、`mingli-exception-1.1.2-Final.jar`（只有直接使用注解时才需要
`annotation-2.5.1.jar`）。

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
smoke.app requires com.mingliqiye.document.schema file:///.../lib/schema-2.5.1.jar
smoke.app requires com.mingliqiye.document.core file:///.../lib/core-2.5.1.jar
com.mingliqiye.document.schema requires kotlin.stdlib file:///.../lib/kotlin-stdlib-2.0.0.jar
com.mingliqiye.document.schema requires com.mingliqiye.document.core file:///.../lib/core-2.5.1.jar
com.mingliqiye.document.core requires com.mingliqiye.document.exception file:///.../lib/exception-2.5.1.jar
com.mingliqiye.document.core requires kotlin.stdlib file:///.../lib/kotlin-stdlib-2.0.0.jar
com.mingliqiye.document.exception requires kotlin.stdlib file:///.../lib/kotlin-stdlib-2.0.0.jar
com.mingliqiye.document.exception requires mingli.exception file:///.../lib/mingli-exception-1.1.2-Final.jar
```

### serialization / jdbc / validation / jpa smoke（实测）

`serialization` / `jdbc` / `validation` / `jpa` 四个模块也可以用同样的方式在 JDK 17 module
path 上使用。一个覆盖四个模块的最小消费方：

```java
// src/smoke.app/module-info.java
open module smoke.app {
    requires com.mingliqiye.document.serialization;
    requires com.mingliqiye.document.jdbc;
    requires com.mingliqiye.document.validation;
    requires com.mingliqiye.document.jpa;
}
```

```java
// src/smoke.app/smoke/Main.java（节选：serialization 与 jpa 的简单映射）
package smoke;

import java.sql.Types;
import java.util.Collections;

import com.mingliqiye.document.annotation.DocumentName;
import com.mingliqiye.document.core.Node;
import com.mingliqiye.document.core.ObjectNode;
import com.mingliqiye.document.jdbc.JdbcColumn;
import com.mingliqiye.document.jpa.JpaDocumentMapper;
import com.mingliqiye.document.serialization.DocumentMapper;
import com.mingliqiye.document.validation.JakartaDocumentBeanValidator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.ConstraintViolation;

public final class Main {
    public static final class UserBean {
        @DocumentName("user_name")
        public String userName;
        public int age;
    }

    @Entity
    public static final class UserEntity {
        @Id
        public Long id;
        @Column(name = "user_name")
        public String userName;
    }

    static final class TreeMapper extends DocumentMapper {
        TreeMapper() {
            super(
                    "smoke",
                    (String text) -> { throw new UnsupportedOperationException("no parser"); },
                    (Node node, Boolean pretty) -> "");
        }
    }

    public static void main(String[] args) {
        TreeMapper mapper = new TreeMapper();
        UserBean bean = new UserBean();
        bean.userName = "jpms";
        bean.age = 7;
        ObjectNode node = mapper.toNode(bean).asObject();
        UserBean back = mapper.fromNode(node, UserBean.class);
        System.out.println("serialization: type=" + node.getType()
                + ", fields=" + node.getSize() + ", roundtrip=" + back.userName + "/" + back.age);

        UserEntity entity = new UserEntity();
        entity.id = 1L;
        entity.userName = "jpa";
        JpaDocumentMapper jpa = new JpaDocumentMapper();
        System.out.println("jpa: keys=" + jpa.toNode(entity).getKeys());

        JdbcColumn column = new JdbcColumn(1, "user_name", Types.VARCHAR, "java.lang.String");
        System.out.println("jdbc: " + column);

        System.out.println("validation: " + JakartaDocumentBeanValidator.message(
                Collections.<ConstraintViolation<?>>emptyList(), null));
    }
}
```

`lib/` 需要本库的 `annotation` / `exception` / `core` / `serialization` / `jdbc` /
`validation` / `jpa` jar，以及 `kotlin-stdlib-2.0.0.jar`、
`mingli-exception-1.1.2-Final.jar`、`alias-for-1.0.5.jar`、
`jakarta.validation-api-3.0.2.jar`、`jakarta.persistence-api-3.1.0.jar`。

```bash
javac --module-path lib -d out src/smoke.app/module-info.java src/smoke.app/smoke/Main.java
java --module-path "lib;out" -m smoke.app/smoke.Main
# serialization: type=OBJECT, fields=2, roundtrip=jpms/7
# jpa: keys=[id, user_name, age]
# jdbc: JdbcColumn(index=1, label='user_name', sqlType=VARCHAR, columnClassName=java.lang.String)
# validation: Bean validation failed:
# JPMS smoke OK
```

JDK 17 `--show-module-resolution` 中可以看到四个模块的 `requires` 都被逐个解析：

```text
smoke.app requires com.mingliqiye.document.jpa file:///.../lib/jpa-2.5.1.jar
smoke.app requires com.mingliqiye.document.serialization file:///.../lib/serialization-2.5.1.jar
smoke.app requires com.mingliqiye.document.jdbc file:///.../lib/jdbc-2.5.1.jar
smoke.app requires com.mingliqiye.document.validation file:///.../lib/validation-2.5.1.jar
com.mingliqiye.document.jpa requires jakarta.persistence file:///.../lib/jakarta.persistence-api-3.1.0.jar
com.mingliqiye.document.jpa requires java.desktop jrt:/java.desktop
com.mingliqiye.document.jpa requires java.sql jrt:/java.sql
com.mingliqiye.document.serialization requires com.mingliqiye.utils.aliasfor file:///.../lib/alias-for-1.0.5.jar
com.mingliqiye.document.serialization requires mingli.exception file:///.../lib/mingli-exception-1.1.2-Final.jar
com.mingliqiye.document.validation requires jakarta.validation file:///.../lib/jakarta.validation-api-3.0.2.jar
com.mingliqiye.document.jdbc requires java.sql jrt:/java.sql
```

### format-* smoke（实测：format-json / format-yaml）

11 个 `format-*` 模块的显式 `module-info` 依赖链都能在 JDK 17 module path 上解析。
消费方只需要 `requires` 格式模块本身（`core` / `exception` / `serialization` 已经通过
`requires transitive` 传递可读，不再需要像自动模块时代那样手工补
`requires com.mingliqiye.document.serialization`）：

```java
// src/smoke.json/module-info.java
open module smoke.json {
    requires com.mingliqiye.document.format.json;
}
```

```java
// src/smoke.json/smoke/Main.java（节选）
JsonMapper mapper = new JsonMapper();
Map<String, Object> value = new LinkedHashMap<>();
value.put("format", "json");
value.put("module", "explicit");
System.out.println(mapper.format(value));
// {"format":"json","module":"explicit"}

User user = mapper.parse("{\"name\":\"jpms\",\"age\":7}", User.class);
// json bean roundtrip: name=jpms, age=7
```

实测（Zulu JDK 17.0.19，module path = 本库 jar + 传递依赖，`open module` 便于 Bean 反射）：

```text
json tree format: {"format":"json","module":"explicit"}
json bean roundtrip: name=jpms, age=7
json bean format: {"name":"jpms","age":7}
JPMS json smoke OK

yaml tree format:
{format: yaml, module: explicit}
yaml bean roundtrip: name=jpms, age=7
yaml bean format:
{name: jpms, age: 7}
JPMS yaml smoke OK
```

`lib/` 需要：对应格式模块 jar + `core` / `exception` / `serialization` / `annotation`
jar + `kotlin-stdlib-2.0.0.jar`、`mingli-exception-1.1.2-Final.jar`、
`alias-for-1.0.5.jar`，以及该格式的第三方依赖（JSON 是
`JSON Token 底层库-2.15.3.jar`；YAML 是 `snakeyaml-2.6.jar`）。
`--show-module-resolution` 中可以看到格式模块的 `requires` 逐条解析（节选）：

```text
root smoke.json file:///.../out/
smoke.json requires com.mingliqiye.document.format.json file:///.../lib/format-json-2.5.1.jar
com.mingliqiye.document.format.json requires com.mingliqiye.document.serialization file:///.../lib/serialization-2.5.1.jar
com.mingliqiye.document.serialization requires com.mingliqiye.utils.aliasfor file:///.../lib/alias-for-1.0.5.jar
```

::: tip format-json Double
module path 上写 `Double` 使用 JDK `Double.toString`，不需要 `--add-exports`；
实测输出 `json double format: {"double":1.5}`。
:::

另外用 `java --validate-modules --module-path lib` 校验 31 个 jar（11 个格式模块 +
`core` / `exception` / `schema` / `annotation` / `serialization` + 各格式第三方依赖）
无 split package、重复模块名与非法描述，退出码 0；用 `javac` 编译一个 `requires` 全部
11 个格式模块的 `module-info.java` 也通过（说明 11 个模块声明的传递依赖都能在 module
path 上解析）。

### jpa-hibernate smoke（实测）

`jpa-hibernate` 同样可以在 JDK 17 module path 上使用（Hibernate 6.5 与
`jakarta.persistence-api:3.1.0` 的 class 文件版本是 55，运行期要求 JDK 11+）：

```java
// src/smoke.jpa/module-info.java
open module smoke.jpa {
    requires com.mingliqiye.document.jpa.hibernate;
    // 公开方法签名里出现 org.hibernate.Session，消费方需自行 requires 这个自动模块。
    requires org.hibernate.orm.core;
}
```

```java
// src/smoke.jpa/smoke/Main.java（节选）
@Entity
public static final class UserEntity {
    @Id
    public Long id;

    @Column(name = "user_name")
    public String userName;

    public int age;
}

HibernateDocumentMapper mapper = new HibernateDocumentMapper();
ObjectNode node = mapper.toNode(entity);
UserEntity back = mapper.fromNode(node, UserEntity.class);
```

实测输出：

```text
jpa-hibernate: org.hibernate.Session module = org.hibernate.orm.core
jpa-hibernate toNode: ObjectNode({id=1, user_name=DefaultStringNode(value=jpms), age=7})
jpa-hibernate roundtrip: id=1, userName=jpms, age=7
JPMS jpa-hibernate smoke OK
```

`lib/` 需要本库的 `jpa-hibernate` / `jpa` / `core` / `serialization` / `exception` /
`annotation` jar，`kotlin-stdlib`、`mingli-exception`、`alias-for`、
`jakarta.persistence-api-3.1.0.jar`、`hibernate-core-6.5.3.Final.jar` 以及 Hibernate 的
运行期依赖（`jakarta.transaction-api`、`jboss-logging`、`byte-buddy`、`jandex`、
`classmate`、`hibernate-commons-annotations`、`jaxb`、`antlr4-runtime` 等，多为自动模块；
Gradle 的 `runtimeClasspath` 已包含）。

该 smoke 覆盖「不传 `EntityManager` 的 detach 映射路径 + Hibernate 类型可读性」。完整的
Hibernate bootstrap / `EntityManager` 运行期路径还依赖 `hibernate-core` 自动模块未在
`module-info` 中声明的传递依赖（如 `jakarta.transaction`，而
`jakarta.transaction-api:2.0.1` 的 `module-info` 又 `requires jakarta.cdi` /
`jakarta.interceptor`），在 module path 上需要额外准备 `--add-modules` 与这些模块，本次未
纳入 smoke；这类场景仍建议以 classpath 使用（见[限制](#限制)）。

### serialization-kotlin / record-support / codegen / openapi smoke（实测）

本批 5 个模块都在 JDK 17 module path 上做了消费方 smoke：

```java
// serialization-kotlin：require 后调用 KotlinModuleKt.kotlinModule()
module smoke.serialization.kotlin {
    requires com.mingliqiye.document.serialization.kotlin;
}

// openapi：require 后构建 OpenApiConfig / OpenApiGenerator
module smoke.openapi {
    requires com.mingliqiye.document.openapi;
}

// record-support：open module 便于 record 反射；声明 uses 便于 ServiceLoader 调用
open module smoke.record {
    requires com.mingliqiye.document.record.support;
    requires com.mingliqiye.document.serialization;
    requires com.mingliqiye.document.format.json;

    uses com.mingliqiye.document.serialization.JavaRecordSupport;
}

// codegen：KSP API 目前是自动模块 symbol.processing.api
module smoke.codegen {
    requires com.mingliqiye.document.codegen;
    requires symbol.processing.api;

    uses com.google.devtools.ksp.processing.SymbolProcessorProvider;
}
```

实测输出（Zulu JDK 17.0.19，module path = 本库 jar + 传递依赖）：

```text
serialization-kotlin smoke OK: module=com.mingliqiye.document.serialization.kotlin.KotlinModule, namedModule=true
openapi smoke OK: keys=[openapi, info, paths, components], namedModule=true
record-support smoke OK: provider=com.mingliqiye.document.serialization.record.Jdk17RecordSupport, isRecord=true, components=2, parsed=1,2, json={"x":1,"y":2}
codegen smoke OK: provider=com.mingliqiye.document.codegen.DocumentEnumCodecProcessorProvider
codegen-test smoke OK: pet=CAT, generated=com.mingliqiye.document.generated.GeneratedDocumentModule, generatedModule=com.mingliqiye.document.codegen.test
```

其中 record smoke 定义了一个 `record Point(int x, int y)`，`JsonMapper.parse` 反序列化为
`parsed=1,2`、`format` 输出 `{"x":1,"y":2}`，说明 `provides` + `uses` 的服务发现与 record
映射在 module path 上可用。

`--show-module-resolution` 关键行（record-support / codegen）：

```text
smoke.record requires com.mingliqiye.document.record.support file:///.../lib/record-support-2.5.1.jar
com.mingliqiye.document.record.support requires com.mingliqiye.document.serialization file:///.../lib/serialization-2.5.1.jar
com.mingliqiye.document.serialization binds com.mingliqiye.document.record.support file:///.../lib/record-support-2.5.1.jar
smoke.codegen requires com.mingliqiye.document.codegen file:///.../lib/codegen-2.5.1.jar
com.mingliqiye.document.codegen requires symbol.processing.api file:///.../lib/symbol-processing-api-2.0.0-1.0.21.jar automatic
smoke.codegen binds com.mingliqiye.document.codegen file:///.../lib/codegen-2.5.1.jar
```

`lib/` 需要本批 5 个模块 jar + `annotation` / `exception` / `core` / `schema` /
`serialization` / `format-json` jar，以及 `kotlin-stdlib-2.0.0.jar`、
`kotlin-reflect-2.0.0.jar`（`serialization-kotlin`）、`alias-for-1.0.5.jar`、
`mingli-exception-1.1.2-Final.jar`、`JSON Token 底层库-2.15.3.jar`（record smoke 的 `JsonMapper`）、
`symbol-processing-api-2.0.0-1.0.21.jar`（`codegen` 的 KSP 依赖）。

### 显式模块与自动模块的解析差异

- 自动模块会「抱团」进入模块图：`requires` 一个自动模块时，module path 上其它自动模块会一起
  解析。**显式模块不会**：只按 `requires` / `requires transitive` 逐个解析。
- 26 个可发布模块全部变成显式模块后，module path 上必须同时提供它们声明的依赖：
  `com.mingliqiye.document.exception`（`core` / `schema-registry` 需要）、
  `com.mingliqiye.document.annotation` / `com.mingliqiye.document.core`（`serialization` 需要）、
  `com.mingliqiye.utils.aliasfor`（`serialization` 需要）、
  `mingli.exception`（`exception` / `serialization` 需要）、`kotlin.stdlib`（全部需要）、
  `kotlin.reflect`（`serialization-kotlin` 需要）、`jakarta.validation`（`validation` 需要）、
  `jakarta.persistence`（`jpa` / `jpa-hibernate` 需要）、`JSON Token 底层库` / `数据绑定底层库` /
  `CBOR 底层库` / `Smile 底层库`（`format-cbor` / `format-smile` /
  `format-json` 需要）、`org.yaml.snakeyaml`（`format-yaml` 需要）、
  `symbol.processing.api`（`codegen` 需要，自动模块）、`org.hibernate.orm.core`
  （`jpa-hibernate` 需要）。缺任何一个都会在编译或启动解析阶段失败。其余格式的第三方依赖
  （`org.apache.avro`、`com.amazon.ion`、`tomlj`、`org.apache.commons.csv`、
  `com.google.protobuf`）是自动模块，module path 上也必须提供。
- `requires transitive` 让消费方无需重复声明：`requires com.mingliqiye.document.core` 时，
  `com.mingliqiye.document.exception`（以及 `kotlin.stdlib`）自动可读；`requires
  com.mingliqiye.document.format.json` 时，`core` / `exception` / `serialization` 自动可读
  （11 个格式模块与 `jpa-hibernate` 都按 Gradle 的 `api(...)` 语义声明了 `requires
  transitive`）；本批的 `serialization-kotlin` / `openapi` / `codegen-test` 同样按
  `api(...)` 语义声明了 `requires transitive`（`serialization` / `core` / `schema` /
  `annotation` 等）。
- 本批 5 个模块也全部是显式模块：`requires com.mingliqiye.document.serialization.kotlin`、
  `...openapi`、`...record.support`、`...codegen`、`...codegen.test` 时，它们的
  `requires` 会逐个解析，不再有「自动模块抱团」的兜底。`record-support` 的
  `provides JavaRecordSupport` 与 `codegen` 的 `provides SymbolProcessorProvider` 都要求
  对应模块本身被解析（`requires` 或 `--add-modules`）才会被 `ServiceLoader` 发现。
- 第三方自动模块（`org.apache.avro`、`com.amazon.ion`、`tomlj`、`org.apache.commons.csv`、
  `com.google.protobuf`、`org.hibernate.orm.core`、`symbol.processing.api` 等）被
  `requires` 后仍会「抱团」解析 module path 上的其它自动模块；它们没有 `requires
  transitive`，消费方直接使用其类型时需要自己声明 `requires`。

### JDK 8 行为不变

JDK 8 不认识模块系统，也不会读 `META-INF/versions/9`：它按普通 classpath 加载主 classes，
`Automatic-Module-Name`、`Multi-Release` 清单属性与 versioned `module-info.class` 都会被忽略。
实测（Zulu JDK 8 + classpath）：

```bash
javac -cp "lib/*" -d out8 src8/smoke/Main.java
java -cp "out8;lib/*" smoke.Main
# JDK8 classpath smoke OK: node=jpms, schema=smoke, fields=1
```

`serialization`（含 Bean 映射与注解读取）/ `jdbc` / `validation` 在 JDK 8 classpath 上同样
实测通过（`jpa` 不在 JDK 8 范围，原因见下面的 warning）：

```text
serialization: fields=2, roundtrip=jpms8/8
jdbc: JdbcColumn(index=1, label='user_name', sqlType=VARCHAR, columnClassName=java.lang.String)
validation: Bean validation failed:
JDK8 classpath smoke OK: java=1.8.0_492
```

本批新增的格式模块同样以 JDK 8 classpath 实测通过（Zulu JDK 8.0.492，`-cp "lib/*"`）：

```text
json tree format: {"format":"json","module":"explicit"}
json bean roundtrip: name=jpms, age=7
JPMS json smoke OK

yaml tree format:
{format: yaml, module: explicit}
yaml bean roundtrip: name=jpms, age=7
JPMS yaml smoke OK
```

即 Multi-Release 结构对 JDK 8 完全透明：基版本主 classes 的 class file major version 仍是
52（本次 12 个新 jar 全部实测为 52），`META-INF/versions/9/module-info.class` 是 53，JDK 8
不会读取该目录。

本批 5 个模块中，`serialization-kotlin` / `openapi`（以及 `codegen` / `codegen-test` 的
主 classes）也是 class file 52，已在 JDK 8 classpath 上实测通过：

```text
JDK8 classpath smoke OK: kotlinModule=KotlinModule, openapiKeys=[openapi, info, paths, components], java=1.8.0_492
```

`record-support` 例外：主 classes 是 class file 61，只支持 JDK 17+；它不参与 JDK 8 的
classpath 使用（JDK 8–16 消费者不引入该 jar 即可，`serialization` 会安全降级）。

::: warning jpa 运行期需要 JDK 11+
`jpa` 依赖的 `jakarta.persistence-api-3.1.0.jar` 主 classes 是 class file 55（Java 11），
`serialization` / `jdbc` / `validation` 的依赖仍是 Java 8 字节码。因此 `jpa` 走 module path
（或以 classpath 加载 JPA 注解类）时要求 JDK 11+；JDK 8/9/10 只能使用不含 `jpa` 的模块。
:::

### 建议的 requires 组合

| 场景 | 需要的模块名 | 形态 |
|---|---|---|
| 只用 Node 树 | `com.mingliqiye.document.core` | 显式模块 |
| Schema / Schema Registry | `com.mingliqiye.document.schema`、`.schema.registry` | 显式模块 |
| 注解（`@DocumentName` 等） | `com.mingliqiye.document.annotation` | 显式模块 |
| Bean 映射 | `com.mingliqiye.document.serialization` | 显式模块 |
| JDBC | `com.mingliqiye.document.jdbc` | 显式模块 |
| Bean Validation | `com.mingliqiye.document.validation` | 显式模块 |
| JPA 注解映射 | `com.mingliqiye.document.jpa` | 显式模块 |
| Kotlin data class | `com.mingliqiye.document.serialization.kotlin`（module path 上还需 `kotlin-reflect` 提供的 `kotlin.reflect` 模块） | 显式模块 |
| Java record（JDK 17+） | `com.mingliqiye.document.record-support`（同时需要 `com.mingliqiye.document.serialization`） | 显式模块（JDK 17 字节码；`provides JavaRecordSupport`，模块需被解析才生效） |
| OpenAPI 3.0 生成 | `com.mingliqiye.document.openapi` | 显式模块 |
| JSON / YAML / XML / ... | `com.mingliqiye.document.format.<name>` | 显式模块（`core` / `exception` / `serialization` 等已传递可读） |
| JPA + Hibernate | `com.mingliqiye.document.jpa.hibernate`（或 `com.mingliqiye.document.jpa`） | 显式模块（直接使用 `org.hibernate.Session` 时再加 `requires org.hibernate.orm.core`） |
| KSP 代码生成 | `com.mingliqiye.document.codegen`（module path 上还需自动模块 `symbol.processing.api`；KSP 构建插件场景一般走 classpath） | 显式模块 |
| codegen 示例/测试支持 | `com.mingliqiye.document.codegen.test`（jar 内含 KSP 生成的 `com.mingliqiye.document.generated` 包） | 显式模块 |

### 混合使用注意

::: warning
现在 26 个可发布模块全部是显式模块，没有「本库自动模块」；混用时的自动化规则只和**第三方
自动模块**有关。自动模块会导出全部包，并且 `requires` 一个自动模块时会把 module path 上其它
自动模块一并拉入模块图；但它们不会把显式模块拉入模块图（显式模块本来就只能按
`requires` 解析，这一点与模块形态无关）。

- `record-support` 的 `provides JavaRecordSupport` 只有在它被解析时才会被
  `serialization` 的 `ServiceLoader` 发现：消费方需要 `requires
  com.mingliqiye.document.record.support`（或 `--add-modules`），仅把 jar 放在 module path
  上不够。
- `codegen` 在 module path 上需要同时提供自动模块 `symbol.processing.api`；KSP 构建插件场景
  仍建议走 classpath（处理器通过 `META-INF/services` 发现）。
- `codegen-test` 的 jar 里带有 KSP 生成的 `com.mingliqiye.document.generated` 包；如果消费方
  自己的模块也生成同名包，两个模块同时放到 module path 上会触发 split package 错误。它是
  示例/测试夹具，不建议与真实业务模块一起放在 module path。
- 消费方直接使用第三方自动模块里的类型（如 `MingLiBaseException`、`org.hibernate.Session`、
  格式模块的第三方类型）时需要自行 `requires`；module path 上也必须提供对应 jar。
:::

## 验证方式

下面命令不需要跑全量测试。构建 26 个显式模块 jar（`format-*` 换成 `:format-json:jar`
等对应模块；`jpa-hibernate` 依赖 `jpa`）：

```bash
gradle :annotation:jar :exception:jar :core:jar :schema:jar :schema-registry:jar \
  :serialization:jar :jdbc:jar :validation:jar :jpa:jar \
  :format-avro:jar :format-cbor:jar :format-csv:jar :format-ion:jar :format-json:jar \
  :format-properties:jar :format-protobuf:jar :format-smile:jar :format-toml:jar \
  :format-xml:jar :format-yaml:jar :jpa-hibernate:jar \
  :serialization-kotlin:jar :record-support:jar :codegen:jar :codegen-test:jar :openapi:jar
# 需要连同测试/ktlint/dokka 一起验证时，把 :jar 换成 :build：
gradle :annotation:build :exception:build :core:build :schema:build :schema-registry:build \
  :serialization:build :jdbc:build :validation:build :jpa:build \
  :format-avro:build :format-cbor:build :format-csv:build :format-ion:build \
  :format-json:build :format-properties:build :format-protobuf:build :format-smile:build \
  :format-toml:build :format-xml:build :format-yaml:build :jpa-hibernate:build \
  :serialization-kotlin:build :record-support:build :codegen:build :codegen-test:build \
  :openapi:build
```

> 已实测：21 个存量模块用同一套构建逻辑生成显式 MR jar（历史批次）。本批 5 个模块
> （`serialization-kotlin` / `record-support` / `codegen` / `codegen-test` / `openapi`）的
> `build` 全部通过（Zulu JDK 17.0.19 + Gradle 8.14.7，含 129 个测试、ktlint、Dokka；
> `BUILD SUCCESSFUL in 22s`，134 个 task 中 48 个执行），5 个 `compileJava9ModuleInfo`
> 任务全部执行；生成的主 jar 全部为显式模块。

### 1. 查看模块描述（确认是显式模块）

```bash
jar --describe-module --file core/build/libs/core-2.5.1.jar --release 9
```

JDK 17 实际输出：

```text
releases: 9

com.mingliqiye.document.core jar:file:///.../core-2.5.1.jar!/META-INF/versions/9/module-info.class
exports com.mingliqiye.document.core
requires com.mingliqiye.document.exception transitive
requires java.base mandated
requires kotlin.stdlib transitive
```

- `releases: 9` 表示这是 Multi-Release jar；
- 输出中**没有 `automatic` 标记**，说明读取的是显式模块描述；
- 若省略 `--release 9`，JDK 17 的 `jar` 会提示「没有模块描述符，请指定 --release」
  （基版本本来就没有 `module-info.class`）。

新增四个模块的实际输出（节选）：

```text
# serialization
exports com.mingliqiye.document.serialization
exports com.mingliqiye.document.serialization.codegen
exports com.mingliqiye.document.serialization.exception
requires com.mingliqiye.document.annotation transitive
requires com.mingliqiye.document.core transitive
requires com.mingliqiye.document.exception transitive
requires com.mingliqiye.utils.aliasfor
requires java.desktop
requires java.sql
requires kotlin.stdlib transitive
requires mingli.exception

# jdbc
exports com.mingliqiye.document.jdbc
exports com.mingliqiye.document.jdbc.exception
requires com.mingliqiye.document.core transitive
requires com.mingliqiye.document.exception
requires com.mingliqiye.document.serialization transitive
requires java.sql transitive
requires kotlin.stdlib transitive

# validation
exports com.mingliqiye.document.validation
requires com.mingliqiye.document.serialization transitive
requires jakarta.validation transitive
requires kotlin.stdlib transitive

# jpa
exports com.mingliqiye.document.jpa
exports com.mingliqiye.document.jpa.exception
requires com.mingliqiye.document.core transitive
requires com.mingliqiye.document.exception
requires com.mingliqiye.document.serialization transitive
requires jakarta.persistence transitive
requires java.desktop
requires java.sql
requires kotlin.stdlib transitive
```

本批新增的 12 个模块实际输出（节选，完整清单见[各模块的 exports / requires](#各模块的-exports--requires)）：

```text
# format-json
com.mingliqiye.document.format.json jar:file:///.../format-json-2.5.1.jar!/META-INF/versions/9/module-info.class
exports com.mingliqiye.document.format.json
exports com.mingliqiye.document.format.json.exception
exports com.mingliqiye.document.format.json.schema
requires com.mingliqiye.document.core transitive
requires com.mingliqiye.document.exception transitive
requires com.mingliqiye.document.serialization transitive
requires java.base mandated
requires kotlin.stdlib transitive

# format-xml
exports com.mingliqiye.document.format.xml
exports com.mingliqiye.document.format.xml.exception
requires com.mingliqiye.document.annotation
requires com.mingliqiye.document.core transitive
requires com.mingliqiye.document.exception transitive
requires com.mingliqiye.document.serialization transitive
requires java.base mandated
requires java.xml
requires kotlin.stdlib transitive

# jpa-hibernate
exports com.mingliqiye.document.jpa.hibernate
exports com.mingliqiye.document.jpa.hibernate.exception
requires com.mingliqiye.document.core transitive
requires com.mingliqiye.document.exception
requires com.mingliqiye.document.jpa transitive
requires jakarta.persistence transitive
requires java.base mandated
requires kotlin.stdlib transitive
requires org.hibernate.orm.core
```

12 个新 jar 全部实测 `releases: 9` 且输出中没有 `automatic` 标记。

本批 5 个模块的实际输出（节选）：

```text
# serialization-kotlin
exports com.mingliqiye.document.serialization.kotlin
requires com.mingliqiye.document.annotation
requires com.mingliqiye.document.core transitive
requires com.mingliqiye.document.exception
requires com.mingliqiye.document.serialization transitive
requires java.desktop
requires kotlin.reflect
requires kotlin.stdlib transitive

# record-support（JDK 17 字节码 + provides）
exports com.mingliqiye.document.serialization.record
requires com.mingliqiye.document.serialization transitive
requires kotlin.stdlib transitive
provides com.mingliqiye.document.serialization.JavaRecordSupport
        with com.mingliqiye.document.serialization.record.Jdk17RecordSupport

# codegen
exports com.mingliqiye.document.codegen
requires kotlin.stdlib transitive
requires symbol.processing.api
provides com.google.devtools.ksp.processing.SymbolProcessorProvider
        with com.mingliqiye.document.codegen.DocumentEnumCodecProcessorProvider

# codegen-test
exports com.mingliqiye.document.codegen.test
exports com.mingliqiye.document.generated
exports com.mingliqiye.document.generated.custom
requires com.mingliqiye.document.annotation transitive
requires com.mingliqiye.document.serialization transitive
requires kotlin.stdlib transitive

# openapi
exports com.mingliqiye.document.openapi
exports com.mingliqiye.document.openapi.exception
requires com.mingliqiye.document.core transitive
requires com.mingliqiye.document.exception transitive
requires com.mingliqiye.document.schema transitive
requires kotlin.stdlib transitive
```

本批 5 个 jar 同样实测 `releases: 9` 且输出中没有 `automatic` 标记；全量 26 个显式模块 jar
逐项用 `jar --describe-module --release 9` 核对，均无 `automatic`。

### 2. 校验 Multi-Release jar

```bash
jar --validate --file core/build/libs/core-2.5.1.jar
```

命令无输出且退出码为 0 表示 jar 结构合法。本批 12 个新 jar 全部实测退出码 0
（`META-INF/versions/9/` 下的 `previous-compilation-data.bin` 不影响校验）；本批 5 个新
jar（含 `record-support` 的 class file 61 主 classes）同样全部退出码 0。

### 3. 检查 jar 内容与清单

```bash
jar tf core/build/libs/core-2.5.1.jar | grep module-info
# META-INF/versions/9/module-info.class
# （基版本根目录没有 module-info.class）
```

Windows PowerShell：

```powershell
Expand-Archive core\build\libs\core-2.5.1.jar -DestinationPath tmp
Get-Content tmp\META-INF\MANIFEST.MF | Select-String 'Multi-Release|Automatic-Module-Name'
# Automatic-Module-Name: com.mingliqiye.document.core
# Multi-Release: true
```

本批 12 个新模块的清单同样保留 `Automatic-Module-Name`（模块名与上表一致）并写入
`Multi-Release: true`；本批 5 个模块同样如此。全部 **26 个**显式模块的清单都保留
`Automatic-Module-Name` 作为 JDK 8 / 无法形成显式模块时的兜底。

> 备注：当前 jar 的 `META-INF/versions/9/` 下除 `module-info.class` 外还有一个
> `previous-compilation-data.bin`，它是 Gradle `JavaCompile` 的增量编译缓存文件，会被
> `compileJava9ModuleInfo` 的输出目录带入 jar。JDK 不读取该文件，不影响模块解析与
> `jar --validate`；更干净的 jar 需要后续在根构建里显式排除，不属于模块 `module-info` 的
> 范围。

### 4. 主 classes 仍是 JDK 8 字节码

```bash
javap -verbose -cp core/build/libs/core-2.5.1.jar com.mingliqiye.document.core.StringNode \
  | grep 'major version'
# major version: 52
```

`META-INF/versions/9/module-info.class` 的 class file 版本是 53，只对 JDK 9+ 生效。
本批 12 个新模块实测：主 classes `major version: 52`，`module-info.class`
`major version: 53`（`format-json` / `format-yaml` / `format-xml` / `jpa-hibernate`
逐项用 `javap -verbose` 核对了 module-info 的版本，其余模块同为 52/53）。
本批 5 个模块的实测结果：`serialization-kotlin` / `openapi` / `codegen` / `codegen-test`
主 classes 为 **52**，`module-info.class` 为 **53**；`record-support` 是唯一例外——主 classes
为 **61**（JDK 17 字节码，JDK 8–16 不能加载），`module-info.class` 仍为 53（放在
`META-INF/versions/9/`，由 JDK 9+ 模块系统读取，JDK 17 才真正能用该模块）。

### 5. JDK 17 module path smoke

见上文[端到端示例](#端到端示例jdk-17)、「serialization / jdbc / validation / jpa smoke
（实测）」、「format-* smoke（实测：format-json / format-yaml）」、「jpa-hibernate smoke
（实测）」与「serialization-kotlin / record-support / codegen / openapi smoke（实测）」小节：
用 `javac --module-path lib` 编译、`java -m` 运行，输出 `JPMS smoke OK`。
上一批实测另有 `java --validate-modules`（11 个格式模块 + 依赖共 31 个 jar）退出码 0，
以及一个 `requires` 全部 11 个格式模块的消费方 `module-info` 编译通过；本批实测
`java --validate-modules --module-path lib`（17 个 jar：11 个本库/示例模块 + 6 个第三方
依赖）退出码 0，并额外做一个 `requires` 本批 5 个模块的 `module-info` 编译测试（见上）。

### 6. JDK 8 classpath smoke

用 Zulu JDK 8 编译消费方源码（去掉 `module-info.java` 与 `jpa` 相关代码，
`-cp "lib/*"`）并运行，`serialization` / `jdbc` / `validation` 输出
`JDK8 classpath smoke OK: java=1.8.0_492`，证明 Multi-Release 结构对 JDK 8 完全透明。
本批 `format-json` / `format-yaml` 也用 JDK 8 实测通过（`JPMS json smoke OK` /
`JPMS yaml smoke OK`）；`jpa-hibernate` 依赖 Hibernate 6.5 与
`jakarta.persistence-api:3.1.0`（class file 55），不在 JDK 8 支持范围。

### 7. jdeps 一致性检查（可选）

```bash
jdeps --multi-release 9 --module-path lib --check com.mingliqiye.document.jpa
```

退出码 0，说明 `module-info` 声明的 `requires` 都能在 module path 上解析、没有缺失依赖。
`jdeps` 的「Suggested module descriptor」只把未在公开 API 中直接暴露的模块降为普通
`requires`，本库选择与 Gradle `api(...)` 一致的 `requires transitive`，属于有意的差异。

本批新增模块中，`format-json` / `format-yaml` 等格式模块的 `jdeps --check` 实测退出码 0
（依赖链里的显式模块可正常解析）。

本批 5 个模块的 `jdeps --check` 也全部退出码 0：`serialization-kotlin`（含
`kotlin.reflect`、`java.desktop`）、`record-support`（含 `provides`）、`codegen`（含自动模块
`symbol.processing.api`）、`codegen-test`（含导出生成包）与 `openapi` 的 `requires` 都能在
module path 上解析。

::: warning jpa-hibernate 的 jdeps --check 需要额外模块
`jpa-hibernate` 依赖自动模块 `org.hibernate.orm.core`；`jdeps --check` 会尝试解析
module path 上的全部模块，而 `jakarta.transaction-api:2.0.1` 的 `module-info` 声明了
`requires transitive jakarta.cdi` / `requires jakarta.interceptor`，这两个模块不在
Hibernate 的运行时依赖里。因此对 `jpa-hibernate` 直接跑 `jdeps --check` 会报
`Module jakarta.cdi not found`，需要额外补充 `jakarta.cdi` / `jakarta.interceptor`
jar 才能完成检查；`java --module-path ... -m` 的运行时解析不受影响（`jakarta.transaction`
默认不进入本模块的模块图，jpa-hibernate smoke 已实测通过）。
:::

### 8. jlink（format-json 链已实测）

`format-json` 的依赖链已全部是显式模块：

```text
com.mingliqiye.document.format.json
  -> serialization / core / exception / annotation
  -> kotlin.stdlib / kotlin.reflect
  -> alias-for 1.0.5 / mingli-exception 1.1.2-Final
```

`alias-for` 1.0.5 与 `mingli-exception` 1.1.2-Final 已提供显式 Multi-Release
`module-info`，因此可以直接生成自定义运行时镜像：

```bash
jlink \
  --module-path lib \
  --add-modules com.mingliqiye.document.format.json \
  --output image \
  --strip-debug --no-header-files --no-man-pages
```

Gradle 任务：

```bash
gradle :format-json:jlinkImage
```

产物：

```text
format-json/build/jlink/format-json/
```

镜像中可以看到：

```text
com.mingliqiye.document.annotation
com.mingliqiye.document.core
com.mingliqiye.document.exception
com.mingliqiye.document.format.json
com.mingliqiye.document.serialization
com.mingliqiye.utils.aliasfor
kotlin.reflect
kotlin.stdlib
mingli.exception
```

仍不能直接 `jlink` 的模块：

```text
format-avro / format-ion / format-toml / format-protobuf / format-csv
  第三方自动模块：Avro / Ion / tomlj / protobuf / commons-csv
jpa / jpa-hibernate
  hibernate-core 及其运行期依赖为自动模块
codegen
  symbol.processing.api 自动模块
```

`annotation` 与 `core` / `exception` / `schema` / `serialization` 等只依赖
`kotlin.stdlib`、`alias-for`、`mingli-exception` 的链也已具备 jlink 条件。

## 限制

1. **`record-support` 要求 JDK 17+**：它的主 classes 是 class file 61（JDK 17），JDK 8–16
   无法加载；它只是 record 映射的可选增强模块，未引入（或运行在 JDK 8–15）时 `serialization`
   安全降级为普通 Bean 行为。module path 上需要与 `serialization` 一起提供，并且必须被
   解析（`requires` / `--add-modules`）后 `provides JavaRecordSupport` 才会被
   `ServiceLoader` 发现。除此之外，**所有 26 个可发布模块都已显式化**，不存在「本库的
   自动模块」。
2. **`format-json` 的 `Double` 已使用 JDK `Double.toString`**：module path 与 classpath
   行为一致，不需要 `--add-exports`。
3. **部分第三方依赖仍是自动模块**：`org.apache.avro`、`com.amazon.ion`、
   `tomlj`（以及它依赖的 `antlr4-runtime`）、`org.apache.commons.csv`、
   `com.google.protobuf` 没有显式 `module-info`；`codegen` 依赖的
   `symbol.processing.api` 同样没有显式 `module-info` / `Automatic-Module-Name`，模块名由
   jar 文件名推导。自动模块导出全部包、并且被 `requires` 后
   会把 module path 上的其它自动模块一并拉入模块图；`tomlj` 的实现还需要
   `antlr4-runtime` 在 module path 上（Gradle 传递依赖已包含）。这些依赖不解决，
   `format-avro` / `format-ion` / `format-toml` / `format-csv` / `format-protobuf` 不能
   `jlink`，也无法获得「只导出声明包」的强封装收益；`codegen` 在 module path 上需要
   同时提供 KSP API jar。
4. **`hibernate-core` 是自动模块**：`org.hibernate.orm.core` 由清单
   `Automatic-Module-Name` 提供，`jpa-hibernate` 对它只用普通 `requires`（消费方直接使用
   `org.hibernate.Session` 等类型时需自行 `requires`）。另外 Hibernate 的运行期依赖
   （`jakarta.transaction`、`jboss-logging`、byte-buddy、jandex、classmate、jaxb、antlr4
   等）都是自动模块，`hibernate-core` 的自动模块描述不会声明它们；在 module path 上做
   完整的 Hibernate bootstrap / `EntityManager` 运行期初始化需要额外 `--add-modules` 并
   提供这些 jar（`jakarta.transaction-api:2.0.1` 的 `module-info` 还要求
   `jakarta.cdi` / `jakarta.interceptor`）。因此 `jpa-hibernate` 的 module path smoke 覆盖
   不传 `EntityManager` 的 detach 映射路径；完整 ORM 运行期仍建议 classpath。
5. **`mingli.exception` 已显式化**：`mingli-exception` 1.1.2-Final 提供显式
   `module mingli.exception`，`exception` / `serialization` 仍按普通 `requires` 使用。
6. **`alias-for` 已显式化**：`alias-for` 1.0.5 提供显式
   `module com.mingliqiye.utils.aliasfor`，`serialization` 按普通 `requires` 使用。
7. **`jlink` 支持范围**：`format-json` 链已验证可 `jlink`；其余格式模块与
   `jpa` / `jpa-hibernate` / `codegen` 仍受第三方自动模块阻塞（Avro / Ion / tomlj /
   protobuf-java / commons-csv / hibernate-core / symbol.processing.api）。
8. **`jpa` / `jpa-hibernate` 的 module path 运行期要求 JDK 11+**：
   `jakarta.persistence-api:3.1.0` 主 classes 是 class file 55，Hibernate 6.5 的 class 文件
   也是 55；JDK 9/10 无法加载这些类。`serialization` / `jdbc` / `validation` / 格式模块及其它
   依赖仍是 Java 8 字节码，可在 JDK 9+ 使用。
9. **服务声明**：`serialization` 声明 `uses com.mingliqiye.document.serialization
   .JavaRecordSupport`，`record-support` 用 `provides ... with ...Jdk17RecordSupport` 提供
   实现（classpath 下两个 jar 仍保留 `META-INF/services` 文件）；`codegen` 用
   `provides com.google.devtools.ksp.processing.SymbolProcessorProvider with
   ...DocumentEnumCodecProcessorProvider` 声明 KSP 处理器（KSP 构建插件日常以 classpath
   加载处理器，继续走 `META-INF/services`）。module path 上使用这两个服务时，提供者模块必须
   被解析（`requires` 或 `--add-modules`）；`codegen-test` 没有服务提供者。
10. **Kotlin 消费方的 JPMS 支持有限**：Kotlin 编译器不编译 `module-info.kt`，Kotlin + Java
    混合模块需要由 Java 侧 `module-info.java` 承担模块描述；Kotlin 2.0 对 module path 上消费
    Kotlin 库的支持仍在演进。
11. **依赖必须同时放在 module path 上**：显式模块不会自动带上兄弟模块，`kotlin-stdlib`、
    `kotlin-reflect`、`mingli-exception`、`alias-for`、`jakarta.validation`、
    `jakarta.persistence`、`参考实现-*`、`snakeyaml`、`symbol.processing.api`、
    `hibernate-core` 以及格式模块的第三方依赖都必须提供；命名模块
    默认读不到 classpath（unnamed module）里的类。
12. **split package 仍需注意**：本库 26 个模块已全部改为显式 `exports`，但第三方自动模块
    仍导出全部包；混用时若消费方或其它依赖声明了同名包，模块解析会报错或出现可见性冲突。
    `codegen-test` 的 jar 带有 KSP 生成的 `com.mingliqiye.document.generated` 包，若消费方
    模块也生成同名包，二者同时放到 module path 会触发 split package；它是示例/测试夹具，
    不建议与业务模块一起上 module path。新增模块时应保证包名不重叠（本库现有模块已按包名
    核对，`--validate-modules` 实测通过）。
13. **JDK 8 没有模块化收益**：JDK 8 忽略 `Multi-Release` 与模块描述，仍按 classpath 使用；
    新增的 `module-info.class` 对 JDK 8 不可见。
14. **模块名不可随意变更**：显式模块名与 `Automatic-Module-Name` 必须一致（构建脚本会校验），
    改名属于破坏性变更。
15. **构建工具链要求**：编译 module-info 需要 JDK 9+（本仓库固定 JDK 17）工具链。只装 JDK 8
    的环境需要让 Gradle 自动下载/指定 JDK 17，否则这 26 个模块的 `jar` 任务会失败。

## 后续计划

1. **第三方依赖显式模块化**：推动 `symbol-processing-api` 以及 Avro / Ion / TOML / commons-csv / protobuf-java、
    `hibernate-core` 上游提供（或升级到
   带显式 `module-info` 的）版本，解除 `jlink` 与「自动模块抱团」限制。本库 26 个可发布模块
   已全部显式化；仍阻塞 `jlink` 的只剩这些第三方自动模块。
3. **第三方依赖显式模块化**：推动 `mingli-exception`、`alias-for`、Avro / Ion / TOML /
   commons-csv / protobuf-java、`hibernate-core` 上游提供（或升级到带显式 `module-info` 的）
   版本，解除 `jlink` 与「自动模块抱团」限制；Ion 的 `ion-java` 后续版本、Hibernate 7 等
   可跟踪。
4. **服务声明的完整性**：`serialization` 的 `uses JavaRecordSupport` 与 `record-support` /
   `codegen` 的 `provides` 已就位；后续评估格式模块与 `jpa-hibernate` 的 `uses`，并考虑把
   `codegen` 的 `META-INF/services` 与 `provides` 维护收敛到一处。
5. **内部包拆分与生成包策略**：把 `internal` 实现移到独立包并由 `exports` 真正隐藏，而不是
   依赖 Kotlin `internal` 的字节码可见性（`jdbc.internal` / `jpa.internal` /
   `jpa-hibernate.internal` 已经不导出，可作为模板；格式模块目前没有 internal 包）。同时
   评估 `codegen-test` 生成包 `com.mingliqiye.document.generated` 的命名/导出策略，避免
   未来与业务模块的生成包发生 split package。
6. **CI 校验**：在 CI 中增加 JDK 17 module path smoke、`jar --validate`、
   `jar --describe-module --release 9`、`java --validate-modules` 与 `jdeps --check`
   的断言，防止 Multi-Release 打包与 `requires` 漂移回归；`jdeps --check` 对
   `jpa-hibernate` 需先补齐 `jakarta.cdi` / `jakarta.interceptor`（见[验证方式](#验证方式)）。
