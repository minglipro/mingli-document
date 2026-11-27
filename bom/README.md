# bom

`mingli-document` 的 Maven BOM，用于统一依赖版本。

## Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.mingliqiye.document</groupId>
            <artifactId>mingli-document-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

导入后依赖不需要写版本：

```xml
<dependency>
    <groupId>com.mingliqiye.document.format</groupId>
    <artifactId>json</artifactId>
</dependency>
```

## Gradle

```kotlin
dependencies {
    implementation(platform("com.mingliqiye.document:mingli-document-bom:1.0.0"))

    implementation("com.mingliqiye.document:core")
    implementation("com.mingliqiye.document:serialization")
    implementation("com.mingliqiye.document.format:json")
}
```

## 管理范围

普通模块：

```text
annotation
exception
core
schema
serialization
serialization-kotlin
```

格式模块：

```text
json yaml xml properties toml csv ion cbor smile avro protobuf
```

## 说明

- BOM 只管理版本，不引入依赖
- 版本由根 `gradle.properties` 的 `projectVersion` 决定
- `benchmarks`、`fuzz` 不在 BOM 中

## 构建

```bash
gradle :bom:build
gradle :bom:publishToMavenLocal
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
