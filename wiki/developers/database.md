# 数据库与事务模型

IdlePool 默认使用 SQLite WAL 模式，所有数据库任务在单独的 `IdlePool-Storage` 单线程执行器中顺序执行。

## schema 3 表

| 表 | 用途 |
|---|---|
| `player_pool_progress` | 周期进度、累计时间、序号和保底计数 |
| `reward_inbox` | 实物暂存与领取状态 |
| `reward_ledger` | 奖励唯一结算流水 |
| `inbox_claim_log` | 领取尝试和异常详情 |
| `session_stats_checkpoint` | 会话绝对检查点，用于幂等统计 |
| `player_stats` | 玩家/挂机池总计统计 |
| `player_period_stats` | 日、周、月统计 |
| `admin_audit_log` | 管理员复核审计 |
| `idlepool_meta` | 数据库 schema 版本 |

## 奖励防重

周期结算 ID 由玩家、挂机池、周期序号和奖励序号确定。里程碑 ID 还包含会话 ID 和里程碑时间。插入 `reward_ledger` 成功后才会继续执行外部奖励；重复 ID 会被拒绝。

物品奖励的流水与暂存箱插入在同一 SQLite 事务完成。

Vault 和命令属于外部系统，无法与 SQLite 构成分布式原子事务，因此采用 at-most-once：先预约 `PROCESSING`，再执行；崩溃后不自动重放。

## 统计防重

每个会话以绝对值写入 `session_stats_checkpoint`。数据库读取上次绝对值，只把增量加入排行榜表，因此定时保存、停止保存或重复回调不会重复累计相同秒数。

## 备份

正常关闭服务器后备份：

```text
plugins/IdlePool/data.db
plugins/IdlePool/data.db-wal
plugins/IdlePool/data.db-shm
```

服务端运行时不要只复制主数据库文件。
