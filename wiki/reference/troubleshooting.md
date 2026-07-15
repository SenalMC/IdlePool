# 故障排查

## 第一步

执行：

```text
/afkpool info
/afkpool doctor
```

反馈问题时请附带这两项输出、Paper 构建号、Java 版本和相关控制台异常。

## 进入区域没有界面

检查：

- 挂机池 `enabled: true`
- 世界名和区域坐标正确
- 玩家拥有 `permission`
- 区域没有与另一个挂机池重叠
- 插件没有因缺失奖励方案而禁用挂机池逻辑

## ItemsAdder 图标没有显示

- 确认 IA 内容包路径是 `plugins/ItemsAdder/contents/idlepool`
- 执行 `/iazip`
- 客户端重新接受资源包
- `/afkpool info` 的 IA 状态为资源已加载

图标失败时原版按钮仍应可用。

## Vault 奖励失败

安装 Vault 并确认经济插件注册了服务。`/afkpool doctor` 会报告仅安装 Vault、但没有经济实现的情况。

## 排行榜没有立即更新

统计按 `storage.checkpoint-interval-seconds` 保存，默认最多延迟约 30 秒。PlaceholderAPI 使用异步缓存，首次查询可能先显示旧值。

## 奖励停在 PROCESSING

不要直接重发。先核对经济或命令执行结果；该状态表示外部效果可能已经发生。必要时结合控制台、经济流水和 `reward_ledger` 人工处理。

## 暂存记录进入 REVIEW

使用 `/afkpool admin review`。确认玩家是否实际收到物品后，选择恢复、确认或作废。

## 数据库 locked

确认没有两个服务端实例共用同一个 `data.db`，不要把 SQLite 放在不可靠的网络共享目录，也不要使用外部程序长时间持有写锁。
