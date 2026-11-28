# 发布

## 私服发布

当前发布通过本地 Gradle init script 注入私服凭据：

```bash
gradle publish -PprojectVersion=2.4.0
```

## Maven Central

发布配置位于根 `build.gradle.kts`：

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
GPG 私钥 / GPG agent
```

手动 workflow：

```text
.github/workflows/publish-central.yml
```

需要配置 GitHub Secrets：

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
GPG_PRIVATE_KEY
GPG_PASSPHRASE
```

当前中央仓库额度未恢复，**尚未执行真实 Central 发布**。

## 发布前检查

```bash
gradle clean ktlintCheck build
gradle generateChecksums generateSbom
```

## 版本与 tag

```bash
git tag -a v2.4.0 -m 'release: 2.4.0'
git push origin v2.4.0
```
