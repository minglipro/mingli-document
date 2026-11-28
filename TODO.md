# TODO

当前阶段：2.1.0 功能补齐中。

## 已完成（P1）

- [x] 大文件流式 Bean 映射
- [x] 对象池 / 零拷贝
- [x] Schema Registry 适配
- [x] Bean Validation 集成

## 下一批

- [ ] 数据库 / ORM 集成
- [ ] JPMS module-info
- [ ] GraalVM Native Image
- [ ] TOML 注释保留（受 tomlj 限制，需要评估解析器替换）
- [ ] Maven Central 实际发布
- [ ] 发布 2.1.0

## 发布前检查

- [ ] `gradle clean ktlintCheck build`
- [ ] 模块 README / 根 README / CHANGELOG / ROADMAP 同步
- [ ] 更新 docs 文档站
- [ ] 创建 2.1.0 tag / GitHub Release / 私服发布
