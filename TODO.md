# TODO

当前分支：`dev`，版本 `2.4.0`（`2.5.0` 开发中）。

## 已完成（2.4.0）

- jpa-hibernate
- JPA @Convert / Map 关联 / 多态
- Hibernate Map 关联 / 多态
- Avro logical type
- XML / Properties / TOML / CSV maxDepth
- TOML 注释便捷 API
- 21 个显式 MR-JAR module-info
- DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
- @DocumentNaming / @DocumentInclude
- 对象映射便捷 API
- reified 扩展迁移到 serialization-kotlin

## 已完成（2.5.0 未发布）

- Java record 支持独立为 `record-support` 模块（JDK 17+）：`JavaRecordSupport` SPI +
  `ServiceLoader`，JDK 8-15 / 无 provider 时安全降级
- record 映射测试迁移：删除 `:serialization:recordTest`，改在 `:record-support:test`
  （JDK 17 工具链）执行
- BOM 纳入 `record-support`；GraalVM 初始化参数改指 `JavaRecordSupportLoader`
- 26 个显式 MR-JAR module-info：补齐 `serialization-kotlin` / `record-support` / `codegen` /
  `codegen-test` / `openapi`；`record-support` 提供 `provides JavaRecordSupport`，
  `codegen` 提供 KSP `provides SymbolProcessorProvider`

## 已放弃

- Gitea CI
- Maven Central 发布

## 保留待办

- [ ] Hibernate `@Convert(attributeName)` / 关系转换 / `@MapKeyJoinColumn`
- [ ] `jlink` 支持
- [ ] native-image 构建接入 CI
