# 参与 IdlePool 开发

感谢你愿意改进 IdlePool。提交问题前请先搜索现有 Issues，并附上 `/afkpool info`、`/afkpool doctor`、Paper/Java 版本和可复现步骤。

## 本地构建

需要 Java 21：

```shell
./gradlew test validateIaAssets releaseBundle
```

Windows：

```powershell
.\gradlew.bat test validateIaAssets releaseBundle
```

## Pull Request

- 一个 PR 尽量只处理一个主题。
- 不要提交服务端数据、数据库、日志、密钥或构建产物。
- 新行为应补充测试；用户可见变化应更新 README 和 CHANGELOG。
- 保持 Paper 主线程安全，数据库和网络操作不得阻塞服务器主线程。
- 新配置必须考虑旧版本配置的向后兼容。

除非贡献者另行明确声明，提交至本仓库并被合并的贡献将按 Apache License 2.0 提供。
