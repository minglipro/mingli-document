# mingli-document 文档站

基于 VuePress 2 的官方文档。

## 本地开发

```bash
pnpm install
pnpm docs:dev
```

默认地址：

```text
http://localhost:8080/mingli-document/
```

## 构建

```bash
pnpm docs:build
```

产物在 `.vuepress/dist/`。

## 目录

```text
docs/
  index.md              首页
  guide/                入门、配置、序列化、IO、Token、代码生成、安全、迁移、FAQ
  formats/              11 种格式的完整参考
  modules/              各 Maven 模块说明
  reference/            注解、API、异常、Token、CHANGELOG、ROADMAP
  .vuepress/            站点配置
  patches/              依赖补丁（修复 VuePress rc.33 sidebar 空页面问题）
```

## 依赖说明

当前固定 VuePress `2.3.0-rc.33` 全家桶。`patches/@vuepress__theme-default@2.3.0-rc.33.patch`
修复了 theme 在页面数据缺少 `headers` 时 SSR 失败的问题；升级 VuePress 前需要确认上游已修复并移除补丁。
