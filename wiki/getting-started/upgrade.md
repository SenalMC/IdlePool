# 从旧版本升级

## v1.1 → v1.2

1. 正常关闭服务端。
2. 备份整个 `plugins/IdlePool`。
3. 替换插件 JAR。
4. 启动服务端并等待自动迁移。
5. 执行 `/afkpool doctor`。

v1.2 会自动将 YAML 和 SQLite schema 升级到 3：

- `config.yml` 增加统计时区和 BossBar 设置
- `rewards.yml` 增加抽取模式、抽取数量、权重和保底设置
- 新增 `events.yml`
- `player_pool_progress` 增加保底计数
- 新增统计、会话检查点和管理员审计表

原配置会在首次迁移前保存为 `*.v2.bak`。

## v1.0 → v1.2

可以直接升级，迁移器会依次补齐缺失结构。v1.0 配置首次迁移会生成 `*.v1.bak`。

## 消息文件不会被覆盖

已有 `message.yml` 或 `en.yml` 会保留。新增消息键缺失时，插件会自动使用 JAR 内置语言作为回退，因此升级后不会显示空文本。你可以手动合并新键以继续自定义。

{% hint style="danger" %}
不要手动降低 `config-version` 或 `schema-version`，也不要在服务端运行时替换 SQLite 文件。
{% endhint %}
