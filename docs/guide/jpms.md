---
title: JPMS、显式 module-info 与 Multi-Release JAR
---

# JPMS、显式 module-info 与 Multi-Release JAR

> 本页说明 `mingli-document` 在 Java 平台模块系统（JPMS / Project Jigsaw）下的支持方式。
>
> 结论先行：**`annotation` / `exception` / `core` / `schema` / `schema-registry` /
> `serialization` / `jdbc` / `validation` / `jpa` / 11 个 `format-*` / `jpa-hibernate`
> 共 21 个模块的主 jar 已经是 Multi-Release JAR：JDK 9+ 会读到真正的显式
> `module-info.class`（`jar --describe-module --release 9` 不再显示 `automatic`），
> JDK 8 仍然只看到原来的 JDK 8 字节码（class file 52），完全按 classpath 使用。
> 其余可发布模块（`serialization-kotlin`、`codegen`、`codegen-test`、`openapi`）
> 仍是「自动模块」。已知限制：`format-json` 写 `Double` 时引用了 jackson-core 未导出的
> 内部包，module path 上需要消费方加 `--add-exports`，详见[限制](#限制)。**
>
> 版本基线：`gradle.properties` 的 `projectVersion=2.4.0`、`jvmVersion=8`。主 classes 仍由
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
| `annotation` / `exception` / `core` / `schema` / `schema-registry` / `serialization` / `jdbc` / `validation` / `jpa` 的主 jar | ✅ 显式模块（Multi-Release） | JDK 9+ 读取 `META-INF/versions/9/module-info.class`；JDK 8 看不到该目录 |
| `format-avro` / `format-cbor` / `format-csv` / `format-ion` / `format-json` / `format-properties` / `format-protobuf` / `format-smile` / `format-toml` / `format-xml` / `format-yaml`、`jpa-hibernate` 的主 jar | ✅ 显式模块（Multi-Release，本批新增） | 共 12 个；模块 path 用法与单独验证见[各模块的 exports / requires](#各模块的-exports--requires)与[验证方式](#验证方式) |
| 其余可发布模块的主 jar（`serialization-kotlin`、`codegen`、`codegen-test`、`openapi`） | ⚠️ 自动模块 | 只有 `Automatic-Module-Name`，`jar --describe-module` 会显示 `automatic` |
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

### 仍为自动模块的模块

以下 4 个模块的主 jar 只带 `Automatic-Module-Name`（名称与上表规则一致）：

| 模块 | 模块名 |
|---|---|
| `serialization-kotlin` | `com.mingliqiye.document.serialization.kotlin` |
| `codegen` | `com.mingliqiye.document.codegen` |
| `codegen-test` | `com.mingliqiye.document.codegen.test` |
| `openapi` | `com.mingliqiye.document.openapi` |

::: tip 模块名是发布契约
自动模块名和 `module-info` 里的模块名一样，是使用方的编译期/运行期契约。本库按
`com.mingliqiye.document[.format].<模块>` 统一规划，格式模块单独使用 `format` 命名空间；
补显式 `module-info` 时只升级形态、不改名字。
:::

## Multi-Release JAR 的结构与构建方式

### 结构

```text
core-2.4.0.jar
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
     `jackson-core` / `jackson-databind` / `jackson-dataformat-cbor` /
     `jackson-dataformat-smile`、`snakeyaml`、`jakarta.xml.bind` 等是显式模块；
     `mingli.exception`、`alias-for`、`org.apache.avro`、`com.amazon.ion`、`tomlj`、
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
| `com.mingliqiye.document.serialization` | `com.mingliqiye.document.serialization`、`.serialization.codegen`、`.serialization.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.annotation`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`com.mingliqiye.utils.aliasfor`（自动模块）、`mingli.exception`（自动模块）、`java.desktop`、`java.sql` |
| `com.mingliqiye.document.jdbc` | `com.mingliqiye.document.jdbc`、`com.mingliqiye.document.jdbc.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.serialization`、`com.mingliqiye.document.exception`、`transitive java.sql` |
| `com.mingliqiye.document.validation` | `com.mingliqiye.document.validation` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.serialization`、`transitive jakarta.validation` |
| `com.mingliqiye.document.jpa` | `com.mingliqiye.document.jpa`、`com.mingliqiye.document.jpa.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.serialization`、`com.mingliqiye.document.exception`、`transitive jakarta.persistence`、`java.desktop`、`java.sql` |
| `com.mingliqiye.document.format.avro` | `.format.avro`、`.format.avro.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.schema`、`transitive com.mingliqiye.document.serialization`、`org.apache.avro`（自动模块） |
| `com.mingliqiye.document.format.cbor` | `.format.cbor`、`.format.cbor.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`com.fasterxml.jackson.core`、`com.fasterxml.jackson.databind`、`com.fasterxml.jackson.dataformat.cbor` |
| `com.mingliqiye.document.format.csv` | `.format.csv`、`.format.csv.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.schema`、`transitive com.mingliqiye.document.serialization`、`org.apache.commons.csv`（自动模块） |
| `com.mingliqiye.document.format.ion` | `.format.ion`、`.format.ion.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`com.amazon.ion`（自动模块） |
| `com.mingliqiye.document.format.json` | `.format.json`、`.format.json.exception`、`.format.json.schema` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`com.fasterxml.jackson.core` |
| `com.mingliqiye.document.format.properties` | `.format.properties`、`.format.properties.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`（无第三方依赖） |
| `com.mingliqiye.document.format.protobuf` | `.format.protobuf`、`.format.protobuf.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.schema`、`transitive com.mingliqiye.document.serialization`、`com.google.protobuf`（自动模块） |
| `com.mingliqiye.document.format.smile` | `.format.smile`、`.format.smile.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`com.fasterxml.jackson.core`、`com.fasterxml.jackson.dataformat.smile` |
| `com.mingliqiye.document.format.toml` | `.format.toml`、`.format.toml.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`tomlj`（自动模块） |
| `com.mingliqiye.document.format.xml` | `.format.xml`、`.format.xml.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`com.mingliqiye.document.annotation`、`java.xml` |
| `com.mingliqiye.document.format.yaml` | `.format.yaml`、`.format.yaml.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.core`、`transitive com.mingliqiye.document.exception`、`transitive com.mingliqiye.document.serialization`、`org.yaml.snakeyaml` |
| `com.mingliqiye.document.jpa.hibernate` | `.jpa.hibernate`、`.jpa.hibernate.exception` | `transitive kotlin.stdlib`、`transitive com.mingliqiye.document.jpa`、`transitive com.mingliqiye.document.core`、`com.mingliqiye.document.exception`、`transitive jakarta.persistence`、`org.hibernate.orm.core`（自动模块） |

说明：

- `kotlin.stdlib` 是**显式模块**（`kotlin-stdlib-2.0.0.jar` 自带 Multi-Release
  `module-info.class`）。Kotlin 公共 API 的类型来自它，所以用 `requires transitive`，
  消费方 `requires` 本库模块即可获得 stdlib 可读性。
- `mingli.exception` 没有显式 `module-info`，模块名由文件名推导
  （`mingli-exception-1.1.1-Final.jar` → `mingli.exception`）。`exception` 模块只做普通
  `requires`；如果消费方直接使用 `MingLiBaseException` 的 API，需要自行
  `requires mingli.exception`。`serialization` 也保留一条普通 `requires mingli.exception`
  （与 `exception` 模块保持一致；普通 `requires` 只影响本模块可读性，消费方仍需自行声明）。
- `alias-for` 1.0.4 起带 `Automatic-Module-Name: com.mingliqiye.utils.aliasfor`，因此可被
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
  `jackson-core` / `jackson-databind` / `jackson-dataformat-cbor` /
  `jackson-dataformat-smile` / `snakeyaml` 自带显式 `module-info`（Multi-Release），
  其余 `org.apache.avro`、`com.amazon.ion`、`tomlj`、`org.apache.commons.csv`、
  `com.google.protobuf`、`org.hibernate.orm.core` 是自动模块，按本库约定只用普通
  `requires`（自动模块导出全部包，但没有 `requires transitive` 语义，消费方直接使用这些
  第三方类型时建议自行 `requires`）。
- 第三方模块名均经 `jar --describe-module` 实测核对：
  `jackson-core` → `com.fasterxml.jackson.core`，`jackson-dataformat-cbor` →
  `com.fasterxml.jackson.dataformat.cbor`（`module-info` 还 requires
  `com.fasterxml.jackson.databind`，运行时 module path 上必须提供 databind 与
  `com.fasterxml.jackson.annotation`），`jackson-dataformat-smile` →
  `com.fasterxml.jackson.dataformat.smile`（同理需要 databind），`snakeyaml` →
  `org.yaml.snakeyaml`，`avro` → `org.apache.avro`（自动模块），`ion-java` →
  `com.amazon.ion`（自动模块），`tomlj` → `tomlj`（自动模块，其 antlr4-runtime 依赖也是
  自动模块），`commons-csv` → `org.apache.commons.csv`（自动模块），`protobuf-java` →
  `com.google.protobuf`（自动模块），`hibernate-core` → `org.hibernate.orm.core`
  （自动模块）。
- `format-xml` 的 `java.xml` 必须显式声明：`javax.xml.parsers.DocumentBuilderFactory`、
  `org.w3c.dom`、`org.xml.sax` 都在 JDK 的 `java.xml` 模块里（JDK 9+ 不再默认导出给具名
  模块）。它通过 `implementation(project(":annotation"))` 使用 `annotation` 模块，因此只有
  普通 `requires`。
- `format-json` 的已知限制：主代码通过 jackson-core 的**内部包**
  `com.fasterxml.jackson.core.io.schubfach.DoubleToDecimal` 输出最短浮点表示，而
  jackson-core 2.15.3 的 `module-info` 没有 `exports` 该包。module path 上写出 `Double`
  时会抛 `IllegalAccessError`，需要消费方在启动参数追加
  `--add-exports com.fasterxml.jackson.core/com.fasterxml.jackson.core.io.schubfach=com.mingliqiye.document.format.json`
  （实测可恢复）；整数、字符串等其它类型与 classpath 使用不受影响。补显式 `module-info`
  之前（自动模块形态）这条路径同样不可用（当时报 `ClassNotFoundException`，因为
  jackson-core 没有进入模块图），因此不是本次升级引入的回归，彻底修复需要 format-json
  主源码不再引用 jackson 内部包，属于后续工作。
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

`lib/` 至少需要：`exception-2.4.0.jar`、`core-2.4.0.jar`、`schema-2.4.0.jar`，以及依赖
`kotlin-stdlib-2.0.0.jar`、`mingli-exception-1.1.1-Final.jar`（只有直接使用注解时才需要
`annotation-2.4.0.jar`）。

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
smoke.app requires com.mingliqiye.document.schema file:///.../lib/schema-2.4.0.jar
smoke.app requires com.mingliqiye.document.core file:///.../lib/core-2.4.0.jar
com.mingliqiye.document.schema requires kotlin.stdlib file:///.../lib/kotlin-stdlib-2.0.0.jar
com.mingliqiye.document.schema requires com.mingliqiye.document.core file:///.../lib/core-2.4.0.jar
com.mingliqiye.document.core requires com.mingliqiye.document.exception file:///.../lib/exception-2.4.0.jar
com.mingliqiye.document.core requires kotlin.stdlib file:///.../lib/kotlin-stdlib-2.0.0.jar
com.mingliqiye.document.exception requires kotlin.stdlib file:///.../lib/kotlin-stdlib-2.0.0.jar
com.mingliqiye.document.exception requires mingli.exception file:///.../lib/mingli-exception-1.1.1-Final.jar automatic
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
`mingli-exception-1.1.1-Final.jar`、`alias-for-1.0.4.jar`、
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
smoke.app requires com.mingliqiye.document.jpa file:///.../lib/jpa-2.4.0.jar
smoke.app requires com.mingliqiye.document.serialization file:///.../lib/serialization-2.4.0.jar
smoke.app requires com.mingliqiye.document.jdbc file:///.../lib/jdbc-2.4.0.jar
smoke.app requires com.mingliqiye.document.validation file:///.../lib/validation-2.4.0.jar
com.mingliqiye.document.jpa requires jakarta.persistence file:///.../lib/jakarta.persistence-api-3.1.0.jar
com.mingliqiye.document.jpa requires java.desktop jrt:/java.desktop
com.mingliqiye.document.jpa requires java.sql jrt:/java.sql
com.mingliqiye.document.serialization requires com.mingliqiye.utils.aliasfor file:///.../lib/alias-for-1.0.4.jar automatic
com.mingliqiye.document.serialization requires mingli.exception file:///.../lib/mingli-exception-1.1.1-Final.jar automatic
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
jar + `kotlin-stdlib-2.0.0.jar`、`mingli-exception-1.1.1-Final.jar`、
`alias-for-1.0.4.jar`，以及该格式的第三方依赖（JSON 是
`jackson-core-2.15.3.jar`；YAML 是 `snakeyaml-2.6.jar`）。
`--show-module-resolution` 中可以看到格式模块的 `requires` 逐条解析（节选）：

```text
root smoke.json file:///.../out/
smoke.json requires com.mingliqiye.document.format.json file:///.../lib/format-json-2.4.0.jar
com.mingliqiye.document.format.json requires com.fasterxml.jackson.core file:///.../lib/jackson-core-2.15.3.jar
com.mingliqiye.document.format.json requires com.mingliqiye.document.serialization file:///.../lib/serialization-2.4.0.jar
com.mingliqiye.document.serialization requires com.mingliqiye.utils.aliasfor file:///.../lib/alias-for-1.0.4.jar automatic
```

::: warning format-json 写 Double 需要 --add-exports
module path 上 `JsonMapper.format(...)` 遇到 `Double` 会抛 `IllegalAccessError`（根因是
jackson-core 未导出内部包 `com.fasterxml.jackson.core.io.schubfach`，见[限制](#限制)）。
追加
`--add-exports com.fasterxml.jackson.core/com.fasterxml.jackson.core.io.schubfach=com.mingliqiye.document.format.json`
后实测输出 `json double format: {"double":1.5}`。
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

### 显式模块与自动模块的解析差异

- 自动模块会「抱团」进入模块图：`requires` 一个自动模块时，module path 上其它自动模块会一起
  解析。**显式模块不会**：只按 `requires` / `requires transitive` 逐个解析。
- 21 个模块变成显式模块后，module path 上必须同时提供它们声明的依赖：
  `com.mingliqiye.document.exception`（`core` / `schema-registry` 需要）、
  `com.mingliqiye.document.annotation` / `com.mingliqiye.document.core`（`serialization` 需要）、
  `com.mingliqiye.utils.aliasfor`（`serialization` 需要）、
  `mingli.exception`（`exception` / `serialization` 需要）、`kotlin.stdlib`（全部需要）、
  `jakarta.validation`（`validation` 需要）、`jakarta.persistence`（`jpa` / `jpa-hibernate`
  需要）、`jackson-core` / `jackson-databind` / `jackson-dataformat-cbor` /
  `jackson-dataformat-smile`（`format-cbor` / `format-smile` / `format-json` 需要）、
  `org.yaml.snakeyaml`（`format-yaml` 需要）、`org.hibernate.orm.core`（`jpa-hibernate` 需要）。
  缺任何一个都会在编译或启动解析阶段失败。其余格式的第三方依赖（`org.apache.avro`、
  `com.amazon.ion`、`tomlj`、`org.apache.commons.csv`、`com.google.protobuf`）是自动模块，
  module path 上也必须提供。
- `requires transitive` 让消费方无需重复声明：`requires com.mingliqiye.document.core` 时，
  `com.mingliqiye.document.exception`（以及 `kotlin.stdlib`）自动可读；`requires
  com.mingliqiye.document.format.json` 时，`core` / `exception` / `serialization` 自动可读
  （11 个格式模块与 `jpa-hibernate` 都按 Gradle 的 `api(...)` 语义声明了 `requires
  transitive`）。
- 仍为自动模块的 `serialization-kotlin` / `codegen` / `codegen-test` / `openapi` 的显式模块依赖不会自动带上：
  例如只 `requires com.mingliqiye.document.codegen`（自动模块）时，它引用的显式模块
  （`core` / `schema` 等）不会被解析，需要显式补 `requires`；自动模块同时会导出全部包，
  与本库显式模块混用时注意包可见性冲突（见下文[混合使用注意](#混合使用注意)）。
- 第三方自动模块（`org.apache.avro`、`com.amazon.ion`、`tomlj`、`org.apache.commons.csv`、
  `com.google.protobuf`、`org.hibernate.orm.core` 等）被 `requires` 后仍会「抱团」解析
  module path 上的其它自动模块；它们没有 `requires transitive`，消费方直接使用其类型时
  需要自己声明 `requires`。

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
| Kotlin data class | `com.mingliqiye.document.serialization.kotlin` | 自动模块 |
| JSON / YAML / XML / ... | `com.mingliqiye.document.format.<name>` | 显式模块（`core` / `exception` / `serialization` 等已传递可读） |
| JPA + Hibernate | `com.mingliqiye.document.jpa.hibernate`（或 `com.mingliqiye.document.jpa`） | 显式模块（直接使用 `org.hibernate.Session` 时再加 `requires org.hibernate.orm.core`） |
| KSP 代码生成 | `com.mingliqiye.document.codegen`（注解处理器场景一般走 classpath） | 自动模块 |

### 混合使用注意

::: warning
`serialization-kotlin` / `codegen` / `codegen-test` / `openapi` 仍是自动模块。自动模块会导出全部包，并且
`requires` 一个自动模块时会把 module path 上其它自动模块一并拉入模块图；但它们**不会**把
显式模块拉入模块图。与本库的显式模块混用时：

```java
// 只 requires 自动模块 codegen 不够：它引用的显式模块不会被自动解析
module smoke.codegen {
    requires com.mingliqiye.document.codegen; // 自动模块
    requires com.mingliqiye.document.schema;  // 显式补上 codegen 用到的显式模块
}
```

`format-*` 与 `jpa-hibernate` 已经改为显式模块，不再有这个问题：`requires
com.mingliqiye.document.format.json` 会自动解析并传递可读 `core` / `exception` /
`serialization`，`requires com.mingliqiye.document.jpa.hibernate` 会自动传递可读 `jpa` /
`core` / `jakarta.persistence`。

反过来，显式模块也不会把自动模块「带上」：消费方直接使用自动模块里的类型（如
`MingLiBaseException`、`org.hibernate.Session`、格式模块的第三方类型）时需要自行
`requires`；module path 上也必须提供对应 jar。
:::

## 验证方式

下面命令不需要跑全量测试。构建 21 个显式模块 jar（`format-*` 换成 `:format-json:jar`
等对应模块；`jpa-hibernate` 依赖 `jpa`）：

```bash
gradle :annotation:jar :exception:jar :core:jar :schema:jar :schema-registry:jar \
  :serialization:jar :jdbc:jar :validation:jar :jpa:jar \
  :format-avro:jar :format-cbor:jar :format-csv:jar :format-ion:jar :format-json:jar \
  :format-properties:jar :format-protobuf:jar :format-smile:jar :format-toml:jar \
  :format-xml:jar :format-yaml:jar :jpa-hibernate:jar
# 需要连同测试/ktlint/dokka 一起验证时，把 :jar 换成 :build：
gradle :annotation:build :exception:build :core:build :schema:build :schema-registry:build \
  :serialization:build :jdbc:build :validation:build :jpa:build \
  :format-avro:build :format-cbor:build :format-csv:build :format-ion:build \
  :format-json:build :format-properties:build :format-protobuf:build :format-smile:build \
  :format-toml:build :format-xml:build :format-yaml:build :jpa-hibernate:build
```

> 本批改动实测（Zulu JDK 17.0.19 + Gradle 8.14.7，`--no-daemon`）：
> 12 个模块（11 个 `format-*` + `jpa-hibernate`）的 `build` 全部通过
> （含各模块测试、ktlint、Dokka；`BUILD SUCCESSFUL in 57s`，224 个 task 中 93 个执行），
> 12 个 `compileJava9ModuleInfo` 任务全部执行；生成的主 jar 全部为显式模块。

### 1. 查看模块描述（确认是显式模块）

```bash
jar --describe-module --file core/build/libs/core-2.4.0.jar --release 9
```

JDK 17 实际输出：

```text
releases: 9

com.mingliqiye.document.core jar:file:///.../core-2.4.0.jar!/META-INF/versions/9/module-info.class
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
com.mingliqiye.document.format.json jar:file:///.../format-json-2.4.0.jar!/META-INF/versions/9/module-info.class
exports com.mingliqiye.document.format.json
exports com.mingliqiye.document.format.json.exception
exports com.mingliqiye.document.format.json.schema
requires com.fasterxml.jackson.core
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

### 2. 校验 Multi-Release jar

```bash
jar --validate --file core/build/libs/core-2.4.0.jar
```

命令无输出且退出码为 0 表示 jar 结构合法。本批 12 个新 jar 全部实测退出码 0
（`META-INF/versions/9/` 下的 `previous-compilation-data.bin` 不影响校验）。

### 3. 检查 jar 内容与清单

```bash
jar tf core/build/libs/core-2.4.0.jar | grep module-info
# META-INF/versions/9/module-info.class
# （基版本根目录没有 module-info.class）
```

Windows PowerShell：

```powershell
Expand-Archive core\build\libs\core-2.4.0.jar -DestinationPath tmp
Get-Content tmp\META-INF\MANIFEST.MF | Select-String 'Multi-Release|Automatic-Module-Name'
# Automatic-Module-Name: com.mingliqiye.document.core
# Multi-Release: true
```

本批 12 个新模块的清单同样保留 `Automatic-Module-Name`（模块名与上表一致）并写入
`Multi-Release: true`；全部 21 个显式模块的清单都保留 `Automatic-Module-Name` 作为
JDK 8 / 无法形成显式模块时的兜底。

> 备注：当前 jar 的 `META-INF/versions/9/` 下除 `module-info.class` 外还有一个
> `previous-compilation-data.bin`，它是 Gradle `JavaCompile` 的增量编译缓存文件，会被
> `compileJava9ModuleInfo` 的输出目录带入 jar。JDK 不读取该文件，不影响模块解析与
> `jar --validate`；更干净的 jar 需要后续在根构建里显式排除，不属于模块 `module-info` 的
> 范围。

### 4. 主 classes 仍是 JDK 8 字节码

```bash
javap -verbose -cp core/build/libs/core-2.4.0.jar com.mingliqiye.document.core.StringNode \
  | grep 'major version'
# major version: 52
```

`META-INF/versions/9/module-info.class` 的 class file 版本是 53，只对 JDK 9+ 生效。
本批 12 个新模块实测：主 classes `major version: 52`，`module-info.class`
`major version: 53`（`format-json` / `format-yaml` / `format-xml` / `jpa-hibernate`
逐项用 `javap -verbose` 核对了 module-info 的版本，其余模块同为 52/53）。

### 5. JDK 17 module path smoke

见上文[端到端示例](#端到端示例jdk-17)、「serialization / jdbc / validation / jpa smoke
（实测）」、「format-* smoke（实测：format-json / format-yaml）」与「jpa-hibernate smoke
（实测）」小节：用 `javac --module-path lib` 编译、`java -m` 运行，输出 `JPMS smoke OK`。
本批实测另有 `java --validate-modules`（11 个格式模块 + 依赖共 31 个 jar）退出码 0，
以及一个 `requires` 全部 11 个格式模块的消费方 `module-info` 编译通过。

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
（它们的依赖链里只有 `mingli.exception` / `alias-for` 等自动模块，可正常解析）。

::: warning jpa-hibernate 的 jdeps --check 需要额外模块
`jpa-hibernate` 依赖自动模块 `org.hibernate.orm.core`；`jdeps --check` 会尝试解析
module path 上的全部模块，而 `jakarta.transaction-api:2.0.1` 的 `module-info` 声明了
`requires transitive jakarta.cdi` / `requires jakarta.interceptor`，这两个模块不在
Hibernate 的运行时依赖里。因此对 `jpa-hibernate` 直接跑 `jdeps --check` 会报
`Module jakarta.cdi not found`，需要额外补充 `jakarta.cdi` / `jakarta.interceptor`
jar 才能完成检查；`java --module-path ... -m` 的运行时解析不受影响（`jakarta.transaction`
默认不进入本模块的模块图，jpa-hibernate smoke 已实测通过）。
:::

### 8. jlink（仅对没有自动模块依赖的模块可用）

```bash
jlink --module-path lib --add-modules com.mingliqiye.document.annotation --output image
# 成功：annotation 只依赖 kotlin.stdlib（显式模块），可以进入自定义运行时镜像
```

`core` / `schema` / `schema-registry` / `serialization` / `jdbc` / `validation` / `jpa`
的依赖链里都有自动模块（`mingli.exception`，`serialization` 还有 `aliasfor`），`jlink` 只接受
显式模块，因此仍不能为这些模块生成自定义运行时镜像（见[限制](#限制)）。`format-*` 也
不可用：它们的依赖链里既有 `mingli.exception` / `alias-for`，也有 Avro / Ion / TOML /
Protobuf / commons-csv 等自动模块，`jpa-hibernate` 还依赖自动模块 `hibernate-core`。

## 限制

1. **仍是自动模块的只有 4 个可发布模块**：`serialization-kotlin` / `codegen` /
   `codegen-test` / `openapi` 没有 `src/main/java9/module-info.java`，只带
   `Automatic-Module-Name`；`format-*` 与 `jpa-hibernate` 已在本批补齐。自动模块不能再
   「带上」本库的显式模块依赖，混用时必须显式 `requires`（见[混合使用注意](#混合使用注意)）。
2. **`format-json` 在 module path 上写 `Double` 需要 `--add-exports`**：主代码引用
   jackson-core 的内部包 `com.fasterxml.jackson.core.io.schubfach.DoubleToDecimal`，而
   jackson-core 2.15.3 的 `module-info` 没有导出该包，运行期会抛 `IllegalAccessError`
   （classpath 使用不受影响）。消费方启动参数需追加
   `--add-exports com.fasterxml.jackson.core/com.fasterxml.jackson.core.io.schubfach=com.mingliqiye.document.format.json`。
   这不是本次显式模块化引入的回归（自动模块形态下该路径本就不可用），彻底修复需要
   format-json 主源码去掉对 jackson 内部包的依赖，属于后续工作。
3. **部分格式模块的第三方依赖仍是自动模块**：`org.apache.avro`、`com.amazon.ion`、
   `tomlj`（以及它依赖的 `antlr4-runtime`）、`org.apache.commons.csv`、
   `com.google.protobuf` 没有显式 `module-info`。自动模块导出全部包、并且被 `requires` 后
   会把 module path 上的其它自动模块一并拉入模块图；`tomlj` 的实现还需要
   `antlr4-runtime` 在 module path 上（Gradle 传递依赖已包含）。这些依赖不解决，
   `format-avro` / `format-ion` / `format-toml` / `format-csv` / `format-protobuf` 不能
   `jlink`，也无法获得「只导出声明包」的强封装收益。
4. **`hibernate-core` 是自动模块**：`org.hibernate.orm.core` 由清单
   `Automatic-Module-Name` 提供，`jpa-hibernate` 对它只用普通 `requires`（消费方直接使用
   `org.hibernate.Session` 等类型时需自行 `requires`）。另外 Hibernate 的运行期依赖
   （`jakarta.transaction`、`jboss-logging`、byte-buddy、jandex、classmate、jaxb、antlr4
   等）都是自动模块，`hibernate-core` 的自动模块描述不会声明它们；在 module path 上做
   完整的 Hibernate bootstrap / `EntityManager` 运行期初始化需要额外 `--add-modules` 并
   提供这些 jar（`jakarta.transaction-api:2.0.1` 的 `module-info` 还要求
   `jakarta.cdi` / `jakarta.interceptor`）。因此 `jpa-hibernate` 的 module path smoke 覆盖
   不传 `EntityManager` 的 detach 映射路径；完整 ORM 运行期仍建议 classpath。
5. **`mingli.exception` 是自动模块**：模块名由文件名推导，不受本库控制；`exception` 与
   `serialization` 模块对它只做普通 `requires`，消费方直接使用 `MingLiBaseException` 时需
   自行 `requires mingli.exception`。
6. **`alias-for` 仍是自动模块**：1.0.4 起带合法 `Automatic-Module-Name`，可正常参与模块解析，
   但自动模块导出全部包、也没有显式 `requires`，后续可推动上游提供显式 `module-info`。
7. **`jlink` 仅 `annotation` 可用**：`core` / `schema` / `schema-registry` / `serialization` /
   `jdbc` / `validation` / `jpa` / 11 个 `format-*` / `jpa-hibernate` 的依赖链里都有自动模块
   （`mingli.exception`、`alias-for`，格式模块还有 Avro / Ion / TOML / Protobuf / commons-csv，
   `jpa-hibernate` 有 `hibernate-core`），`jlink` 只接受显式模块，因此仍不能生成自定义运行时
   镜像。
8. **`jpa` / `jpa-hibernate` 的 module path 运行期要求 JDK 11+**：
   `jakarta.persistence-api:3.1.0` 主 classes 是 class file 55，Hibernate 6.5 的 class 文件
   也是 55；JDK 9/10 无法加载这些类。`serialization` / `jdbc` / `validation` / 格式模块及其它
   依赖仍是 Java 8 字节码，可在 JDK 9+ 使用。
9. **没有 `uses` / `provides`**：本库这 21 个显式模块没有服务提供者。`codegen` 的注解处理器
   日常仍建议走 classpath，而不是 module path；`codegen` 补显式 `module-info` 时需要
   `provides javax.annotation.processing.Processor`。
10. **Kotlin 消费方的 JPMS 支持有限**：Kotlin 编译器不编译 `module-info.kt`，Kotlin + Java
    混合模块需要由 Java 侧 `module-info.java` 承担模块描述；Kotlin 2.0 对 module path 上消费
    Kotlin 库的支持仍在演进。
11. **依赖必须同时放在 module path 上**：显式模块不会自动带上兄弟模块，`kotlin-stdlib`、
    `mingli-exception`、`alias-for`、`jakarta.validation`、`jakarta.persistence`、
    `jackson-*`、`snakeyaml`、`hibernate-core` 以及格式模块的第三方依赖都必须提供；命名模块
    默认读不到 classpath（unnamed module）里的类。
12. **split package 仍需注意**：21 个模块已改为显式 `exports`，但自动模块（其余 4 个可发布
    模块与第三方自动模块）导出全部包；混用时若消费方或其它依赖声明了同名包，模块解析会报错
    或出现可见性冲突。新增模块时应保证包名不重叠（本库现有模块已按包名核对，
    `--validate-modules` 实测通过）。
13. **JDK 8 没有模块化收益**：JDK 8 忽略 `Multi-Release` 与模块描述，仍按 classpath 使用；
    新增的 `module-info.class` 对 JDK 8 不可见。
14. **模块名不可随意变更**：显式模块名与 `Automatic-Module-Name` 必须一致（构建脚本会校验），
    改名属于破坏性变更。
15. **构建工具链要求**：编译 module-info 需要 JDK 9+（本仓库固定 JDK 17）工具链。只装 JDK 8
    的环境需要让 Gradle 自动下载/指定 JDK 17，否则这 21 个模块的 `jar` 任务会失败。

## 后续计划

1. **剩余 4 个自动模块的显式 `module-info`**：`serialization-kotlin`（`kotlin.reflect`）、
   `codegen`（需要 `provides javax.annotation.processing.Processor`）、`codegen-test`、
   `openapi`。`alias-for` 1.0.4 已合法，可按批次逐个补 `src/main/java9/module-info.java`，
   并同步更新本页与 README。
2. **去掉 format-json 对 jackson 内部包的依赖**：把
   `com.fasterxml.jackson.core.io.schubfach.DoubleToDecimal` 换成公开 API 或本库自带的
   最短浮点输出实现，解除 module path 上的 `--add-exports` 限制。
3. **第三方依赖显式模块化**：推动 `mingli-exception`、`alias-for`、Avro / Ion / TOML /
   commons-csv / protobuf-java、`hibernate-core` 上游提供（或升级到带显式 `module-info` 的）
   版本，解除 `jlink` 与「自动模块抱团」限制；Ion 的 `ion-java` 后续版本、Hibernate 7 等
   可跟踪。
4. **服务声明**：为 `codegen` 的注解处理器补 `provides javax.annotation.processing.Processor`，
   并评估各格式模块与 `jpa-hibernate` 的 `uses`。
5. **内部包拆分**：把 `internal` 实现移到独立包并由 `exports` 真正隐藏，而不是依赖 Kotlin
   `internal` 的字节码可见性（`jdbc.internal` / `jpa.internal` / `jpa-hibernate.internal`
   已经不导出，可作为模板；格式模块目前没有 internal 包）。
6. **CI 校验**：在 CI 中增加 JDK 17 module path smoke、`jar --validate`、
   `jar --describe-module --release 9`、`java --validate-modules` 与 `jdeps --check`
   的断言，防止 Multi-Release 打包与 `requires` 漂移回归；`jdeps --check` 对
   `jpa-hibernate` 需先补齐 `jakarta.cdi` / `jakarta.interceptor`（见[验证方式](#验证方式)）。
