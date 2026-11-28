# GraalVM Native Image

## 支持范围

```text
库 jar：JDK 8 字节码
Native Image 构建：GraalVM for JDK 17+
Bean 映射（java.beans）：建议 GraalVM for JDK 22+
```

GraalVM 已不再提供 JDK 8 发行版，不能用 GraalVM JDK8 构建 native-image。

## 配置目录

```text
graalvm/
  native-image.properties
  reflect-config.json
  resource-config.json
  proxy-config.json
  serialization-config.json
  native-smoke/
```

核心模块内嵌：

```text
core/src/main/resources/META-INF/native-image/.../native-image.properties
serialization/src/main/resources/META-INF/native-image/.../
```

## 构建命令

Windows 需要先初始化 Visual Studio C++ 环境，并安装 Windows 10/11 SDK：

```bat
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x64
```

```bash
native-image \
  -cp "build/libs/*:..." \
  -H:Name=native-smoke \
  --initialize-at-run-time=java.beans.Introspector \
  --initialize-at-run-time=com.mingliqiye.document.serialization.JavaRecordSupportLoader \
  com.mingliqiye.document.graalvm.smoke.NativeSmoke
```

缺少 Windows SDK 时会报：

```text
fatal error C1083: stdio.h: No such file or directory
```

## Java record 支持

- record 映射需要 JDK 17+ 运行时并引入 `record-support` 模块（见 [record-support](../modules/record-support.md)）；
- provider 由 Native Image 的 ServiceLoader 支持自动注册；
- record 类本身需要注册组件元数据，例如：

```json
{ "name": "com.example.User", "allRecordComponents": true }
```

## 用户需要补充的反射配置

```text
被映射 Bean 及其父类
字段/方法/构造器
元素上出现的 RUNTIME 注解类型
@AliasFor 动态代理
自定义转换器类
```

## JVM smoke

```bash
graalvm/native-smoke/run-jvm-smoke.ps1
```

不安装 GraalVM 也可以运行，用于确认类路径和映射逻辑。

## 实测状态

```text
JVM smoke：GraalVM JDK 21 / JBR 25 通过
native ：GraalVM JDK 21 + VS BuildTools + Windows SDK 通过
输出   ：SMOKE OK
```

## CI

native-image 构建已接入 GitHub Actions：

```text
.github/workflows/native-image.yml
```

CI 使用 GraalVM JDK 21，在 Ubuntu runner 上完成：

```text
编译 NativeSmoke
native-image 构建
运行并断言 SMOKE OK
上传产物
```

## 限制

```text
Windows 本地构建需要 Visual Studio C++ Build Tools + Windows SDK
Kotlin reflect / validation / Jakarta Provider 需要额外配置
字符集/Locale/时区默认裁剪
```
