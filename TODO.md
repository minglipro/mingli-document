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
- GraalVM Native Image 实测

## 下一批

- [ ] 发布 2.4.0
- [ ] format-* 的显式 module-info
- [ ] jpa-hibernate 的显式 module-info
- [ ] Maven Central 实际发布（等额度）
- [ ] Gitea CI

## 发布前检查

- [ ] `gradle clean ktlintCheck build`
- [ ] README / ROADMAP / CHANGELOG / docs 同步
- [ ] 私服发布 2.4.0
- [ ] tag v2.4.0 / GitHub Release
