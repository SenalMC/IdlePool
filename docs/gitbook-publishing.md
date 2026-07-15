# GitBook 发布与 GitHub 同步

仓库已经按 GitBook Git Sync 结构准备完成：

- 根配置：`.gitbook.yaml`
- 文档根目录：`wiki/`
- 入口：`wiki/README.md`
- 目录：`wiki/SUMMARY.md`
- 同步分支：`main`

## 首次连接

GitBook 的账号和 GitHub App 授权无法通过仓库文件代替，因此仓库所有者需要执行一次：

1. 登录 GitBook，在组织设置中安装 GitHub 集成。
2. 在 GitBook 创建或打开一个 Space，选择 Git Sync。
3. 选择 `SenalMC/IdlePool` 仓库与 `main` 分支。
4. GitBook 会读取根目录 `.gitbook.yaml`，并把 `wiki/` 识别为内容根目录。
5. 首次同步完成后发布 Space；后续推送到 `main` 的 Wiki 修改会继续同步。

若 GitBook 提示同步冲突，先在 GitBook 的 Git Sync 页面查看冲突提交，不要同时在网页编辑器和本地修改同一段内容。

官方说明：

- [Git Sync](https://gitbook.com/docs/integrations/git-sync)
- [启用 GitHub Sync](https://gitbook.com/docs/getting-started/git-sync/enabling-github-sync)
- [`.gitbook.yaml` 内容配置](https://gitbook.com/docs/getting-started/git-sync/content-configuration)

## 发布前检查

```powershell
.\gradlew.bat test releaseBundle
```

同时确认：

- `wiki/SUMMARY.md` 覆盖所有章节。
- Wiki 内相对链接可以解析。
- `.gitbook.yaml` 的 `root` 仍为 `./wiki/`。
- GitBook Space 的同步分支仍为 `main`。
