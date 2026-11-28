# TODO

当前分支：`dev`，版本 `2.3.0`。

## 已完成

- 大文件流式 Bean 映射
- 对象池 / 零拷贝
- Schema Registry / Bean Validation
- JDBC / JPA 注解映射
- Hibernate Session / EntityManager 运行期集成
- Hibernate `@Convert` / Map 关联 / 多态
- TOML 注释保留
- Avro 完整 logical type
- XML / Properties / TOML / CSV maxDepth
- JPMS Automatic-Module-Name
- 显式 module-info：21 个模块
- GraalVM Native Image 实测
- DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
- @DocumentNaming / @DocumentInclude
- 对象映射便捷 API（readTree/writeTree/readValue/readerFor/writer）
- reified 扩展迁移到 serialization-kotlin

## 已放弃

- Gitea CI
- Maven Central 发布

## 暂停

- 2.4.0 代码与发布：按当前决定延期

## 保留待办

- [ ] serialization-kotlin / codegen / codegen-test / openapi 的显式 module-info
- [ ] format-json 去掉 jackson 内部包 `DoubleToDecimal` 依赖
- [ ] Hibernate `@Convert(attributeName)` / 关系转换 / `@MapKeyJoinColumn`
- [ ] `jlink` 支持（当前只有 annotation 可 jlink）
- [ ] native-image 构建接入 CI
