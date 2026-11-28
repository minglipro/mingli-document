# Gitea CI

本目录提供与 GitHub Actions 对齐的 Gitea Actions 工作流：

```text
.gitea/workflows/build.yml    构建与测试
.gitea/workflows/release.yml  手动发布构建（校验和 + SBOM）
.gitea/workflows/docs.yml     文档站构建
```

## 前提

```text
Gitea 实例启用 Actions
已注册 act_runner
runner 支持 ubuntu-latest（或替换为实际 label）
```

## 说明

- 工作流使用 `actions/checkout`、`actions/setup-java`、`gradle/actions/setup-gradle`、
  `pnpm/action-setup` 等 GitHub Action 兼容动作；Gitea 需允许代理这些 action。
- `release.yml` 与 `docs.yml` 只生成 artifact，不直接创建 Gitea Release / Pages。
- 当前仓库未配置 Gitea remote，工作流尚未在 Gitea runner 上实际执行。
