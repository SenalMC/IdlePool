# 命令与权限

主命令 `/afkpool`，别名 `/idlepool`。

## 玩家命令

| 命令 | 说明 |
|---|---|
| `/afkpool` | 在当前挂机池打开开始界面 |
| `/afkpool info` | 版本、服务端、IA 和更新状态 |
| `/afkpool stop` | 结束挂机 |
| `/afkpool claim` | 打开奖励暂存箱 |
| `/afkpool history` | 查看自己的奖励流水 |
| `/afkpool stats [周期]` | 查看个人统计 |
| `/afkpool top [周期]` | 查看排行榜 |

## 管理命令

| 命令 | 说明 |
|---|---|
| `/afkpool admin` | 挂机池管理 |
| `/afkpool admin rewards` | 奖励方案管理 |
| `/afkpool admin operations` | 运营中心 |
| `/afkpool admin review` | 异常复核 |
| `/afkpool admin logs [玩家] [状态]` | 奖励流水 |
| `/afkpool admin export` | CSV 导出 |
| `/afkpool stats <玩家> [周期]` | 查看其他玩家统计 |
| `/afkpool history <玩家>` | 查看其他玩家奖励流水 |
| `/afkpool event list` | 活动列表 |
| `/afkpool event start <ID>` | 立即开始活动 |
| `/afkpool event stop <ID>` | 停止活动 |
| `/afkpool doctor` | 配置与集成检查 |
| `/afkpool reload` | 重载配置和语言 |

周期为 `day`、`week`、`month` 或 `total`；流水状态为 `ALL`、`SUCCESS`、`FAILED` 或 `PROCESSING`。

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `idlepool.use` | 所有玩家 | 使用默认挂机池 |
| `idlepool.admin` | OP | 所有管理功能 |

每个挂机池的 `permission` 可以设置额外权限节点。
