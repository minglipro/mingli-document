# native-smoke 示例

`mingli-document` 的 GraalVM Native Image 冒烟示例。示例只使用公开 API，**不需要 GraalVM**
即可在 JVM 上运行；装好 GraalVM for JDK 17+ 后，同一份代码可构建为 native 静态镜像验证
（本库 Bean 属性发现依赖 `java.beans.Introspector`，建议使用 GraalVM for JDK 22+）。

## 文件

| 文件 | 说明 |
|---|---|
| `NativeSmoke.java` | 示例入口：JSON 序列化/反序列化、`@DocumentName`/`@DocumentIgnore`、嵌套 Bean、Java record、UTF-8 字节、Node 树 |
| `META-INF/native-image/com.mingliqiye.document.graalvm/native-smoke/reflect-config.json` | 示例 Bean 的反射注册（native 构建时由 classpath 自动发现） |
| `run-jvm-smoke.ps1` | Windows/PowerShell：编译 + JVM 运行（自动拼 classpath） |
| `run-jvm-smoke.sh` | Linux/macOS/Bash：同上 |

## 前置条件

1. JDK 17+（`javac`、`java` 在 `PATH`，或设置 `JAVA_HOME`）。
2. 仓库已构建库 jar（在仓库根目录执行）：

   ```bat
   gradlew.bat :annotation:jar :exception:jar :core:jar :serialization:jar :format-json:jar
   ```

   ```bash
   ./gradlew :annotation:jar :exception:jar :core:jar :serialization:jar :format-json:jar
   ```

3. 依赖 jar 由脚本从 Gradle 缓存（`GRADLE_USER_HOME`，默认 `~/.gradle`）自动探测：
   `kotlin-stdlib`、`alias-for`、`mingli-exception`。探测失败时可用环境变量手动指定：

   | 环境变量 | 指向 |
   |---|---|
   | `KOTLIN_STDLIB_JAR` | `kotlin-stdlib-2.x.jar` |
   | `ALIAS_FOR_JAR` | `alias-for-1.0.3.jar` |
   | `MINGLI_EXCEPTION_JAR` | `mingli-exception-1.1.1-Final.jar` |

## 在 JVM 上运行（无需 GraalVM）

```powershell
.\graalvm\native-smoke\run-jvm-smoke.ps1
```

```bash
bash graalvm/native-smoke/run-jvm-smoke.sh
```

预期输出：

```text
JSON        : {"active":true,"address":{"city":"Hangzhou","zip":310000},"age":30,"user_name":"Li Ming"}
Record JSON : {"x":3,"y":4}
SMOKE OK
```

脚本会把 `META-INF/native-image` 配置一起拷到 `build/classes`，并打印后续可复制的
`javac` / `native-image` 命令。

## 构建并运行 native 镜像

需要 [GraalVM for JDK 17+](https://www.graalvm.org/downloads/)（GraalVM **没有 JDK 8 发行版**：
库 jar 是 JDK 8 字节码，但 native-image 构建与 native 运行时都要求 JDK 17+）。本库的 Bean 属性
发现依赖 `java.beans.Introspector`，该路径建议使用 GraalVM for JDK 22+，详见上级
[README](../README.md) 第 8 节；同时确保 `native-image` 在 `PATH` 中。

Windows 还需要：

```text
Visual Studio C++ Build Tools（vcvarsall.bat）
Windows 10/11 SDK（Include/.../ucrt/stdio.h 等 C 头文件）
```

先初始化 MSVC 环境再构建：

```bat
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x64
```

如果出现 `fatal error C1083: stdio.h: No such file or directory`，说明只装了 Build Tools，
没有安装 Windows SDK 的“Windows 10/11 SDK”组件。

```bat
:: 1) 编译示例并拷贝配置（classpath 用脚本打印的那条）
javac -encoding UTF-8 --release 17 -cp "<classpath>" -d graalvm\native-smoke\build\classes graalvm\native-smoke\NativeSmoke.java
xcopy /E /I /Y graalvm\native-smoke\META-INF graalvm\native-smoke\build\classes\META-INF

:: 2) 构建 native 镜像
native-image -cp "<classpath>" -H:Name=graalvm\native-smoke\build\native-smoke ^
  --initialize-at-run-time=java.beans.Introspector ^
  --initialize-at-run-time=com.mingliqiye.document.serialization.JavaRecordSupportLoader ^
  com.mingliqiye.document.graalvm.smoke.NativeSmoke

:: 3) 运行
graalvm\native-smoke\build\native-smoke.exe
```

脚本在 JVM 运行成功后也会打印包含完整 classpath 的 `native-image` 命令，可直接复制执行
（`graalvm/native-smoke/build/` 已被 `.gitignore` 里的 `build` 规则忽略）。

## 实测结果

Windows 环境：

```text
GraalVM JDK 21.0.12
Visual Studio 18 BuildTools
Windows SDK 10.0.26100 / 10.0.28000
```

构建成功并运行：

```text
JSON        : {"active":true,"address":{"city":"Hangzhou","zip":310000},"age":30,"user_name":"Li Ming"}
Record JSON : {"x":3,"y":4}
SMOKE OK
```

native 可执行文件约 21 MB；构建耗时约 26 秒。

## 配置说明

- 示例 Bean（`NativeSmoke$User`、`NativeSmoke$Address`、`NativeSmoke$Point`）按
  「全量成员 + record 组件」注册，覆盖 `Introspector`、`getDeclared*`、构造器与 record 反射路径。
- `serialization` jar 内嵌的配置提供了 JDK record API 与库注解类型的注册，
  `native-image` 会从 classpath 自动读取，示例中无需重复。
- 若示例在 native 下报 `MissingReflectionRegistrationError`，优先检查：
  1. 配置是否随 `build/classes/META-INF/native-image` 进入 classpath；
  2. 使用的 GraalVM 是否 ≥ JDK 17（Bean 映射路径建议 ≥ JDK 22）；
  3. 是否加了 `--initialize-at-run-time=java.beans.Introspector`。

## 限制

- 本示例只验证库的核心映射路径，不覆盖多态子类型、`@DocumentCreator`/Builder、自定义 converter、
  `serialization-kotlin`、Jakarta 校验等；这些场景的注册规则见上级 README 第 4 节。
- 示例用 JDK 17 的 record 特性；若运行在 JDK 16 以下，`NativeSmoke$Point` 相关断言不适用
  （示例按 JDK 17+ 编写）。
