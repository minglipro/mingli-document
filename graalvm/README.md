# GraalVM Native Image 支持

本目录为 `mingli-document` 提供 GraalVM Native Image（AOT 静态镜像）的配置骨架、构建说明与
可运行的冒烟示例。

> **当前状态**：JVM smoke 已用 GraalVM JDK 21 与 JBR 25 实测通过；native 二进制已在
> Windows + GraalVM JDK 21 + Visual Studio Build Tools + Windows SDK 环境实测通过
> （`native-smoke.exe` 运行输出 `SMOKE OK`）。
> 所有反射注册规则都基于库源码中实际使用反射的位置整理，构建时请以 GraalVM 的报错和
> tracing agent 的输出为准。

## 1. 能力范围

`mingli-document` 是纯 JVM 库（Kotlin 2.0 编译，主代码与发布 jar 以 JDK 8 字节码为目标）。

> **Native Image 构建的 JDK 版本**：GraalVM **没有 JDK 8 发行版**，不能用 JDK 8 构建
> native-image；必须使用 **GraalVM for JDK 17+**（推荐 JDK 17/21/23+，常用 LTS 为 21）来消费
> 这些 JDK 8 字节码，构建出的 native 可执行文件运行环境最低也是 JDK 17。库本身不需要为了
> native 支持而把主代码升级到 17。
> 另外：本库 Bean 属性发现依赖 `java.beans.Introspector`，该路径建议使用 GraalVM for JDK 22+
> （见[第 8 节](#8-已知限制重要)）。

各模块在 Native Image 下的情况如下：

| 模块 | native 支持 | 说明 |
|---|---|---|
| `annotation` / `exception` / `core` | ✅ 无需额外配置 | 纯数据/异常类，库 jar 已内嵌最小配置 |
| `serialization` | ✅ 需要用户补充配置 | Bean 映射依赖反射，用户模型类必须注册（见第 4 节） |
| `format-*`（json/yaml/xml/cbor/ion/smile/csv/toml/properties/protobuf/avro） | ✅ 无需额外配置 | 通过 `JsonMapper()` 等具体类显式实例化，没有运行期 SPI/ServiceLoader，全部静态可达 |
| `serialization-kotlin` | ⚠️ 未验证 | 依赖 `kotlin-reflect`，镜像更大且需要 Kotlin 数据类构造器注册 |
| `validation` | ⚠️ 需额外配置 | Jakarta Bean Validation Provider 由 ServiceLoader 查找，建议用 tracing agent 采集 |
| `codegen`（KSP/注解处理器） | ✅ 构建期工具 | 不进入运行期镜像 |
| `schema` / `openapi` / `jdbc` / `schema-registry` | ⚠️ 未验证 | 不含在本目录的 smoke 范围内 |

**结论**：格式模块不需要反射配置；native 支持的工作量集中在**给用户自己的模型类、自定义注解、
自定义 converter 注册反射元数据**。

各格式模块的入口类是普通类（`JsonMapper`、`YamlMapper`、`XmlMapper`、`CborMapper`、`IonMapper`、
`SmileMapper`、`CsvMapper`、`TomlMapper`、`PropertiesMapper`、`ProtobufMapper`、`AvroMapper`），
应用通过 `new XxxMapper()` 或 `XxxMapper.builder()` 直接实例化；库内部没有按类名加载格式、
没有 `Class.forName` 加载格式类、也没有格式 SPI/`ServiceLoader` 注册文件，所以**这些格式类
及其内部类全部是静态可达代码，不需要出现在 reflect-config.json 里**。同理，
`Codec`/`TokenReader`/`TokenWriter`/`Parser` 等实现类也不需要注册。

### 1.1 库运行期用到的反射位置（配置依据）

| 位置 | 用到的反射 API | native 镜像中的影响 |
|---|---|---|
| Bean 属性发现 | `java.beans.Introspector.getBeanInfo` → `Class.getMethods()` | 被映射类需要注册（含父类） |
| Bean/构造函数/Builder 发现 | `Class.getDeclaredFields/Methods/Constructors`、`getDeclaredField/Method/Constructor` | 同上 |
| 注解解析 | `alias-for` 的 `AnnotationGetter`：`annotationType.getDeclaredMethods()` + `Method.invoke(annotation)` | 元素上出现的**每个运行时注解类型**都要注册 |
| `@AliasFor` 自定义注解 | `java.lang.reflect.Proxy.newProxyInstance` | 需要 proxy 配置（见 4.3） |
| Java record | `Class.isRecord()` / `getRecordComponents()` / `RecordComponent#getAccessor`（由可选 `record-support` 模块直接调用） | record 类需要 `allRecordComponents`；JDK 17+ 且引入 `record-support` 时生效 |
| `@DocumentConverter` 等注解声明的 converter | `getDeclaredField("INSTANCE")` / `getDeclaredConstructor()` | converter 类需要注册 |
| 属性访问加速 | `MethodHandles.lookup().unreflect` + `LambdaMetafactory` | 不可用时库自动回退到 `Method.invoke`，不注册也不会崩，但回退路径同样需要方法可反射 |
| Java 序列化 | 无 | 库不使用 `ObjectOutputStream` |
| Resource 读取 | 无 | 库运行期不读取 classpath 资源；格式模块也没有 SPI 文件 |

## 2. 目录与文件

| 路径 | 用途 | 是否随 jar 发布 |
|---|---|---|
| `graalvm/native-image.properties` | 应用侧构建参数模板（推荐把 `Args` 加到命令行/buildArgs；作用域注意事项见第 7 节） | ❌ 模板 |
| `graalvm/reflect-config.json` | 反射注册模板（`com.example.*` 为占位示例，使用前替换/删除） | ❌ 模板 |
| `graalvm/resource-config.json` | 资源与资源包模板（库本身不需要） | ❌ 模板 |
| `graalvm/serialization-config.json` | Java 序列化骨架（库不使用；仅当你的 DTO 使用 Java 序列化时需要） | ❌ 模板 |
| `graalvm/proxy-config.json` | 动态代理模板（仅 `@AliasFor` 自定义注解需要） | ❌ 模板 |
| `graalvm/native-smoke/` | 可运行的 smoke 示例（JVM 可直接跑，不要求 GraalVM） | ❌ 示例 |
| `core/src/main/resources/META-INF/native-image/com.mingliqiye.document/core/native-image.properties` | core 模块构建参数（构建期初始化） | ✅ 随 `core` jar |
| `serialization/src/main/resources/META-INF/native-image/com.mingliqiye.document/serialization/native-image.properties` | serialization 模块构建参数（`JavaRecordSupportLoader` 运行期初始化） | ✅ 随 `serialization` jar |
| `serialization/src/main/resources/META-INF/native-image/com.mingliqiye.document/serialization/reflect-config.json` | 库全部注解类型的反射注册（record 反射已迁移到 `record-support`） | ✅ 随 `serialization` jar |

库内嵌配置会被 `native-image` **自动读取**：`native-image` 扫描 classpath 上所有
`META-INF/native-image/<groupId>/<artifactId>/` 目录（`native-image.properties`、`reflect-config.json`
等）。因此引用 `core` / `serialization` jar 的应用**不需要**再复制这些内容。

内嵌配置只包含库自身必需的条目（JDK record API、库注解类型），**不会**注册用户模型类或强制
包含通用资源，避免污染下游应用镜像。

## 3. 快速开始

### 3.1 JVM 冒烟（不需要 GraalVM）

先构建库 jar（在仓库根目录，下面命令适用于使用 wrapper 的仓库；本仓库在无网络环境也可用
本机已安装的 `gradle` 替代 `./gradlew`）：

```bat
:: Windows
gradlew.bat :annotation:jar :exception:jar :core:jar :serialization:jar :format-json:jar
```

```bash
# Linux / macOS
./gradlew :annotation:jar :exception:jar :core:jar :serialization:jar :format-json:jar
```

然后运行 smoke（自动拼装 classpath 与依赖，见脚本头注释）：

```powershell
# PowerShell
.\graalvm\native-smoke\run-jvm-smoke.ps1
```

```bash
# Bash
bash graalvm/native-smoke/run-jvm-smoke.sh
```

预期输出包含：

```text
JSON        : {"active":true,"address":{"city":"Hangzhou","zip":310000},"age":30,"user_name":"Li Ming"}
Record JSON : {"x":3,"y":4}
SMOKE OK
```

脚本最后会打印可直接复制的 `javac`/`native-image` 命令（含完整 classpath）。

### 3.2 使用 `native-image` CLI 构建

需要 [GraalVM for JDK 17+](https://www.graalvm.org/downloads/) 并确保 `native-image` 在 `PATH` 中
（GraalVM 发行版通常自带 `native-image`；很旧的版本可能需要 `gu install native-image`）。
注意 GraalVM **没有 JDK 8 发行版**：库 jar 是 JDK 8 字节码，但 native-image 构建与 native 运行时
都要求 JDK 17+。本库的 Bean 属性发现依赖 `java.beans.Introspector`，该路径建议使用
GraalVM for JDK 22+（见第 8 节）。

```bat
:: 1) 构建库 jar（同 3.1）
gradlew.bat :annotation:jar :exception:jar :core:jar :serialization:jar :format-json:jar

:: 2) 编译 smoke，并把 META-INF/native-image 配置拷到 classes 目录
javac -encoding UTF-8 --release 17 -cp "<classpath>" -d graalvm\native-smoke\build\classes graalvm\native-smoke\NativeSmoke.java
xcopy /E /I /Y graalvm\native-smoke\META-INF graalvm\native-smoke\build\classes\META-INF

:: 3) 构建 native 镜像（Windows 用 ; 分隔 classpath，Linux/macOS 用 :)
native-image -cp "<classpath>" -H:Name=graalvm\native-smoke\build\native-smoke ^
  --initialize-at-run-time=java.beans.Introspector ^
  --initialize-at-run-time=com.mingliqiye.document.serialization.JavaRecordSupportLoader ^
  com.mingliqiye.document.graalvm.smoke.NativeSmoke

:: 4) 运行
graalvm\native-smoke\build\native-smoke.exe
```

其中 `<classpath>` 由脚本打印，也可以自行拼装：

```text
graalvm/native-smoke/build/classes
+ annotation、exception、core、serialization、format-json 的 jar
+ kotlin-stdlib-2.x.jar
+ alias-for-1.0.3.jar
+ mingli-exception-1.1.2-Final.jar
```

如果配置不在 classpath 上，也可以用外部配置目录（目录中放 `reflect-config.json` 等文件）：

```bat
native-image -cp "<classpath>" -H:ConfigurationFileDirectories=graalvm <主类>
```

### 3.3 使用 Gradle Native Build Tools（nativeBuild）

在**应用**工程的 `build.gradle.kts` 中添加（版本以
[official native-build-tools](https://github.com/graalvm/native-build-tools/releases) 为准，
0.10.x 与 1.1.x 的 DSL 相同）：

```kotlin
plugins {
    application
    id("org.graalvm.buildtools.native") version "1.1.14" // 以官方最新版本为准
}

dependencies {
    implementation("com.mingliqiye.document:serialization:2.4.0")
    implementation("com.mingliqiye.document.format:json:2.4.0")
}

application {
    mainClass.set("com.example.Main")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("my-app")
            buildArgs.add("--initialize-at-run-time=java.beans.Introspector")
            buildArgs.add("--initialize-at-run-time=com.mingliqiye.document.serialization.JavaRecordSupportLoader")
            // 需要非 UTF-8 字符集 / 多 Locale / 全时区时按需启用：
            // buildArgs.add("-H:+AddAllCharsets")
            // buildArgs.add("-H:+IncludeAllLocales")
            // buildArgs.add("-H:+IncludeAllTimeZones")
        }
    }
}
```

```bash
./gradlew nativeCompile     # 产物：build/native/nativeCompile/my-app
./gradlew nativeTest        # 可选的 native 测试
```

### 3.4 Maven（可选）

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <version>1.1.14</version>
    <extensions>true</extensions>
    <configuration>
        <buildArgs>
            <buildArg>--initialize-at-run-time=java.beans.Introspector</buildArg>
            <buildArg>--initialize-at-run-time=com.mingliqiye.document.serialization.JavaRecordSupportLoader</buildArg>
        </buildArgs>
    </configuration>
</plugin>
```

## 4. 用户必须补充的反射配置

以下配置是 **应用侧** 的事情（模板见 `graalvm/reflect-config.json`，使用前把 `com.example.*`
替换成自己的类）。漏配的典型错误：GraalVM 23+ 抛 `MissingReflectionRegistrationError`；
更早版本可能静默返回空属性列表，导致字段丢失或反序列化异常。

> 本目录的 JSON 使用各版本都兼容的 `name` 字段（GraalVM 23+ 推荐新的 `type` 字段和统一的
> `reachability-metadata.json`，但旧格式仍然可用）。

### 4.1 每个被映射的类（含父类）

```json
{
  "name": "com.example.model.User",
  "allDeclaredFields": true,
  "allPublicFields": true,
  "allDeclaredMethods": true,
  "allPublicMethods": true,
  "allDeclaredConstructors": true,
  "allPublicConstructors": true
}
```

必填原因（与库内反射调用一一对应）：

| 配置项 | 对应库行为 |
|---|---|
| `allDeclaredFields` | `getDeclaredField()`、`getDeclaredFields()`（`DocumentRuntimeCodecFactory`） |
| `allPublicFields` | `Class.getFields()`（Builder/字段补充路径） |
| `allDeclaredMethods` | `getDeclaredMethod()`、`getDeclaredMethods()`（`@DocumentBuilder`/`@DocumentBuild` 扫描、setter 定位） |
| `allPublicMethods` | `Class.getMethods()`（`java.beans.Introspector` 属性发现、getter 调用） |
| `allDeclaredConstructors` | `getDeclaredConstructor()`（无参构造、`@DocumentCreator` 构造器、Builder 静态工厂） |
| `allPublicConstructors` | 兼容 `getConstructor()`/`getConstructors()` 的注册（当前主流程主要使用 declared 构造器，保留更稳） |
| `allRecordComponents` | **Java record 必加**：`Class.getRecordComponents()` 的组件列表 |

补充规则：

- **父类也要注册**：库查找字段/方法时会沿父类向上遍历，`Introspector` 也会读取继承的 public 方法。
- **Java record** 额外加 `"allRecordComponents": true`。
- **Builder 模式**：`@DocumentBuilder` 的静态工厂方法所在类、Builder 类都要按同样规则注册。
- **多态（`SubtypeRegistry`）**：注册每个子类型。
- **自定义集合/Map 实现**（非接口/非抽象的具体类）：注册其无参构造器（`allDeclaredConstructors`）。
- **`@DocumentConverter` / `@DocumentSerializer` / `@DocumentDeserializer` 注解的 converter 类**：

```json
{
  "name": "com.example.converter.MoneyConverter",
  "allDeclaredFields": true,
  "allDeclaredMethods": true,
  "allDeclaredConstructors": true
}
```

（若 converter 是 Kotlin `object`，库通过 `INSTANCE` 静态字段取实例，`allDeclaredFields` 即可覆盖。）

### 4.2 注解类型（最容易漏的一项）

库通过 `alias-for` 的 `AnnotationGetter` 解析注解：对元素上的注解会调用
`annotationType.getDeclaredMethods()` 并 `Method.invoke(annotation)` 读取属性值。
因此 **Bean 的字段/getter/构造器/构造参数上出现的每个 RUNTIME 注解类型**都需要注册：

```json
{
  "name": "com.example.annotation.MyBeanAnnotation",
  "allDeclaredMethods": true
}
```

- 库自带的 14 个注解（`@DocumentName`、`@DocumentIgnore`、`@DocumentCreator` 等）已经内嵌在
  `serialization` jar 的 `reflect-config.json` 中，**不需要**重复配置。
- 你自己的注解、第三方注解（例如字段上的 `@Deprecated`、校验注解）如果出现在被映射元素上，
  都需要按上面方式注册；否则注解解析会失败。
- 只写 `queryAllDeclaredMethods` 不够：查询到的方法还需要可**反射调用**，所以用
  `allDeclaredMethods`。

### 4.3 `@AliasFor` 自定义注解需要动态代理配置

`alias-for` 对带 `@AliasFor` 的注解会 `Proxy.newProxyInstance` 生成注解代理，native 镜像需要
知道代理接口列表。若你使用自定义别名注解：

```json
[
  {
    "interfaces": [
      "com.example.annotation.MyAliasAnnotation"
    ]
  }
]
```

写入应用 classpath 的 `META-INF/native-image/<group>/<artifact>/proxy-config.json`（模板：
`graalvm/proxy-config.json`）。同时该注解类型本身也要按 4.2 注册。

### 4.4 自定义扩展点

以下接口的实现类由应用代码提供并会被库调用；如果实现类不是通过普通 Java 代码直接可达
（例如只把 `Class` 对象交给库），按 4.1 注册：

- `DocumentBaseSerializer` / `DocumentBaseDeserializer` / `DocumentBaseConverter`
- `ObjectInstantiator`
- `PropertyMetadataResolver`
- `RemoteReferenceResolver`
- `DocumentModule`（模块类本身）

### 4.5 枚举

字段类型用到的枚举类随代码引用进入镜像后，`Class.getEnumConstants()` 一般无需额外注册；
`@DocumentEnumCodec` 的转换器由 KSP/注解处理器在编译期生成，属于普通代码。若枚举只通过
外部字符串间接使用、代码中没有静态引用，需要保证该枚举类可达（例如在应用代码中直接引用它，
或按 4.1 注册反射）。

### 4.6 Kotlin data class（serialization-kotlin，可选模块）

`serialization-kotlin` 依赖 `kotlin-reflect` 读取 Kotlin 元数据与主构造器，除了 4.1 的注册外，
还需要 `kotlin-reflect` 及其内部元数据的可达性；这一步本仓库**未验证**。建议直接使用 tracing
agent 采集，或暂时用 Java 风格可变 Bean 替代不可变 Kotlin 数据类。

### 4.7 推荐：用 tracing agent 自动收集

在 JVM 上跑一遍代表性业务路径（序列化、反序列化、record、注解、多态、校验），让 agent 生成配置：

```bat
:: 首次采集（GraalVM 23+ 输出统一的 reachability-metadata.json；旧版本输出 reflect-config.json 等分文件）
java -agentlib:native-image-agent=config-output-dir=src\main\resources\META-INF\native-image\agent ^
  -cp "<应用 classpath>" com.example.Main

:: 多次运行合并
java -agentlib:native-image-agent=config-merge-dir=src\main\resources\META-INF\native-image\agent ^
  -cp "<应用 classpath>" com.example.Main
```

```bash
java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/agent \
  -cp "<应用 classpath>" com.example.Main
```

采集结果会包含模型类、注解类型、converter、资源等条目；人工检查后保留必要项即可。
agent 输出的是「运行到的路径」，**没有跑到的分支仍会漏**，所以要用有代表性的测试数据。

## 5. 资源、SPI 与服务文件

- 本库运行期不调用 `getResource`/`getResourceAsStream`，也不通过 ServiceLoader 加载格式模块：
  格式由应用显式选择（`new JsonMapper()` / `YamlMapper()` …）。因此 **core/serialization/format-***
  不需要 `resource-config.json` 或服务文件条目。
- `graalvm/resource-config.json` 是应用侧模板：应用自己的配置文件、模板、`META-INF/services/**`
  服务文件需要时按模板加入。GraalVM 对可达的 `ServiceLoader.load(...)` 有自动支持，通常无需
  手工列出 `META-INF/services`；但服务实现类本身仍需可达。
- `validation` 模块通过 `Validation.buildDefaultValidatorFactory()` 使用 ServiceLoader 查找
  Jakarta Bean Validation Provider（如 hibernate-validator）。Provider 的
  `META-INF/services/jakarta.validation.spi.ValidationProvider`、`META-INF/validation.xml`、
  约束消息资源等建议用 tracing agent 采集；本地化消息还可能用到
  `-H:+IncludeAllLocales`。

## 6. Java 序列化

本库不使用 `java.io.ObjectOutputStream`/`ObjectInputStream`，`graalvm/serialization-config.json`
仅为应用骨架：

```json
{
  "types": [],
  "lambdaCapturingTypes": [],
  "proxies": []
}
```

只有当你自己的 DTO 实现 `java.io.Serializable` 并通过 Java 序列化写入文件/网络时才需要：

```json
{
  "types": [
    { "name": "com.example.dto.MySerializableDto" }
  ],
  "lambdaCapturingTypes": [],
  "proxies": []
}
```

（GraalVM 更早版本的 `serialization-config.json` 是数组格式 `[{"name": ...}]`，以所用版本
schema 为准；也可直接用 tracing agent 生成对应条目。）

## 7. 构建参数与初始化时机

| 参数 | 作用 | 建议 |
|---|---|---|
| `--initialize-at-run-time=com.mingliqiye.document.serialization.JavaRecordSupportLoader` | 该对象在运行期用 `ServiceLoader` 查找 `record-support` 提供的 `JavaRecordSupport` 实现，加载结果不能固化进镜像 | 库 jar 已内嵌，**不要移除** |
| `--initialize-at-build-time=com.mingliqiye.document.core` | core 是无状态纯数据类，构建期初始化可减少运行期开销 | 库 jar 已内嵌，可按需覆盖 |
| `--initialize-at-run-time=java.beans.Introspector` | 避免 `java.beans.Introspector was unintentionally initialized at build time` 构建错误 | 建议加到应用侧（模板已含） |
| `-H:+ReportExceptionStackTraces` | 构建期异常输出完整堆栈 | 建议排查阶段开启 |
| `-H:+AddAllCharsets` | 包含全部字符集（默认只有 UTF-8/UTF-16/ISO-8859-1/US-ASCII） | 使用非 UTF-8 的 `fileCharset` 时开启 |
| `-H:+IncludeAllLocales` | 包含全部 Locale 数据 | `DateTimeFormatter`/`NumberFormat` 使用非默认 Locale 时开启 |
| `-H:+IncludeAllTimeZones` | 包含完整时区数据库（默认只有 UTC、GMT、构建机时区） | 日期时间转换依赖任意时区时开启 |
| `--exact-reachability-metadata` | 严格校验元数据（GraalVM 23+） | CI 中建议开启，尽早发现无效条目 |

使用方式（注意嵌入参数的「作用域」差异）：

> GraalVM 对嵌入 jar 的 `native-image.properties` 参数有作用域/覆盖规则：
> `--link-at-build-time` 明确要求引用的类/包必须来自同一个 jar；`--initialize-at-*` 无该强制
> 校验，但在库 jar 中建议只引用本 jar 的类。为避免不同 GraalVM 版本的差异，**引用库类
> （`com.mingliqiye.document.*`）或 JDK 类（`java.beans.*`）的初始化参数推荐放在命令行 /
> Gradle `buildArgs` / Maven `<buildArg>` 中**；库自身需要的参数已经内嵌在 `core` /
> `serialization` jar 里（只引用自身类），随 jar 自动生效。

1. **命令行参数（推荐）**：把 `graalvm/native-image.properties` 中 `Args =` 后面的参数写到
   `native-image` 命令行或 Gradle `buildArgs` / Maven `<buildArg>`（第 3.2/3.3/3.4 节示例即如此）；
2. **随应用 classpath 自动加载**：可以复制到应用
   `src/main/resources/META-INF/native-image/<groupId>/<artifactId>/native-image.properties`，
   但请先在目标 GraalVM 版本上用 `native-image --verbose` 验证作用域与最终生效参数；
3. 库自身需要的初始化参数（`JavaRecordSupportLoader` 运行期初始化、core 构建期初始化）已经内嵌在
   `core` / `serialization` jar 中，随 jar 自动生效，应用无需重复；
4. 注意 `native-image.properties` 只有在 `META-INF/native-image` 目录下才会被自动读取，
   普通目录（例如直接把 `graalvm/` 作为配置 JSON 目录）不会加载它，需要手工传参。

## 8. 已知限制（重要）

1. **Native Image 构建最低 GraalVM for JDK 17，且不存在 JDK 8 版 native-image**。
   `mingli-document` 的主代码与发布 jar 仍以 JDK 8 字节码为目标（JVM 兼容性不变），但
   native-image **只能**用 GraalVM for JDK 17+（推荐 17/21/23+，常用 LTS 为 21）构建，构建出的
   native 可执行文件运行环境最低也是 JDK 17。不要尝试用 JDK 8 构建 native-image，也不需要为了
   native 支持把库主代码升级到 17。
2. **Bean 映射路径建议 GraalVM for JDK 22+（推荐最新，如 25）**。本库的 Bean 属性发现依赖
   `java.beans.Introspector`，GraalVM CE 对 `java.beans.**` 的 Native Image 支持从
   GraalVM CE for JDK 22 才开始（可参考
   [ShardingSphere 的 GraalVM 适配文档](https://shardingsphere.apache.org/document/current/en/user-manual/shardingsphere-jdbc/graalvm-native-image/)
   中的版本要求）；在 JDK 17/21 的 GraalVM 上运行 Bean 映射需要自行验证/补充 metadata，
   可能不可用。具体以所用 GraalVM 版本的 release notes 为准，参考
   [GraalVM 下载](https://www.graalvm.org/downloads/)。
3. **静态镜像没有「反射回退」**：JVM 上能跑不代表 native 能跑。native 构建请以
   第 4 节注册 + tracing agent 为准，并在 CI 中加入 native 冒烟测试（`nativeTest` 或运行镜像）。
4. **注解反射比常见框架更敏感**：库会遍历元素上的全部注解并读取注解方法，任何一个未注册的
   注解类型都可能让整个映射失败（见 4.2）。
5. **`serialization-kotlin` 未验证**：`kotlin-reflect` 体积大、元数据注册复杂，建议本阶段在
   native 场景使用 Java 风格 Bean 或先做 PoC。
6. **字符集 / Locale / 时区数据默认被裁剪**，使用非默认值时按第 7 节加参数，否则运行期会抛
   `UnsupportedCharsetException` / 缺少时区的异常。
7. **`MethodHandles`/`LambdaMetafactory` 访问器加速在 native 下可能不可用**：库内已 `catch`
   并回退到 `Method.invoke`，功能不受影响，但被回退的方法仍需要注册。
8. **`validation` 模块的 Jakarta 校验**：Provider 由 ServiceLoader 查找，native 下需要 Provider
   自身的 reachability metadata（建议 agent 采集），本仓库未提供。
9. **运行期代码生成器**（`DocumentAccessorCodegen`、`DocumentRuntimeCodecFactory`）仍走反射注册，
   不是「生成字节码」；KSP/注解处理器属于编译期，不进入镜像。
10. **Java record 支持是可选的 JDK 17+ 模块**：需要 classpath / module path 上有 `record-support`，native 构建同样如此；provider 由 Native Image 的 ServiceLoader 支持注册，record 类仍需 `allRecordComponents`。未引入该模块时 record 按普通 Bean 处理。
11. 本目录是仓库内的开发文档，与 `docs/` 下的 VuePress 文档站相互独立（文档站由文档任务维护）。

## 9. 验证与限制

本仓库当前环境**没有安装 GraalVM/native-image**，因此只做了以下验证（不包含真正的镜像构建）：

1. `gradle --offline :core:jar :serialization:jar` 构建成功；
   `core` / `serialization` jar 内已包含 `META-INF/native-image/**` 资源
   （可用 `jar tf core/build/libs/core-2.4.0.jar` 查看）。
2. 本目录下所有 JSON 配置文件均通过 JSON 解析校验，字段符合
   [reflect-config-schema](https://www.graalvm.org/docs/reference-manual/native-image/assets/reflect-config-schema-v1.1.0.json)
   中的已有配置项。
3. JVM 冒烟通过：`graalvm/native-smoke/run-jvm-smoke.ps1`（或 `.sh`）输出 `SMOKE OK`，
   覆盖 Bean/嵌套 Bean、`@DocumentName`/`@DocumentIgnore`、Java record、文本/字节/Node 四条路径。
4. **未执行** `native-image` 构建与运行。

在有 GraalVM 的环境中（最低 for JDK 17，Bean 映射建议 for JDK 22+，推荐最新版本），建议用以下
步骤完成最终验证：

```bash
# 1) 构建示例（见 3.2 或 3.3）
# 2) nativeTest 或直接构建后运行示例
native-image -cp "<classpath>" -H:Name=native-smoke \
  --initialize-at-run-time=java.beans.Introspector \
  --initialize-at-run-time=com.mingliqiye.document.serialization.JavaRecordSupportLoader \
  com.mingliqiye.document.graalvm.smoke.NativeSmoke
./native-smoke   # 预期输出 SMOKE OK
```

## 10. 参考

- [Native Image Reachability Metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/)
- [Native Image Build Configuration](https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildConfiguration/)
- [Assisted Configuration with Tracing Agent](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/)
- [GraalVM Native Build Tools](https://github.com/graalvm/native-build-tools)
- [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata)
- [MissingReflectionRegistrationError](https://www.graalvm.org/latest/javadoc/sdk/org/graalvm/nativeimage/MissingReflectionRegistrationError.html)
- [ShardingSphere GraalVM Native Image 适配文档（java.beans 版本要求的实践参考）](https://shardingsphere.apache.org/document/current/en/user-manual/shardingsphere-jdbc/graalvm-native-image/)
