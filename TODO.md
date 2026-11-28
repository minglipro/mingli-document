# TODO

当前分支：`dev`，已发布 `2.3.0`。

## 已完成

- 大文件流式 Bean 映射
- 对象池 / 零拷贝
- Schema Registry / Bean Validation
- JDBC / JPA 注解映射
- Hibernate Session / EntityManager 运行期集成
- TOML 注释保留
- Avro 完整 logical type
- XML / Properties / TOML / CSV maxDepth
- JPMS Automatic-Module-Name
- 显式 module-info：annotation / exception / core / schema / schema-registry / serialization / jdbc / validation / jpa
- 显式 module-info：11 个 format-*（avro / cbor / csv / ion / json / properties / protobuf / smile / toml / xml / yaml）
- 显式 module-info：jpa-hibernate
- GraalVM Native Image 实测

## 下一批

- [ ] 发布 2.4.0
- [ ] serialization-kotlin / codegen / codegen-test / openapi 的显式 module-info
- [ ] format-json 去掉 jackson 内部包依赖（module path 写 Double 需要 --add-exports）
- [ ] Maven Central 实际发布（等额度）
- [x] Gitea CI workflow 配置

## 发布前检查

- [ ] `gradle clean ktlintCheck build`
- [ ] README / ROADMAP / CHANGELOG / docs 同步
- [ ] 私服发布 2.4.0
- [ ] tag v2.4.0 / GitHub Release
