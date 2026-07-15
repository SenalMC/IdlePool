# 统计、排行榜与历史

## 个人统计

```text
/afkpool stats
/afkpool stats day
```

可以切换：

- 今日
- 本周
- 本月
- 总计

统计内容包括挂机时间、完成周期、奖励数量、最长单次挂机与当前排名。

管理员可以查看其他玩家：

```text
/afkpool stats <玩家> [day|week|month|total]
```

## 排行榜

```text
/afkpool top [day|week|month|total]
```

日、周、月边界使用 `statistics.time-zone`。排行榜只展示存在有效统计的玩家，默认最多 45 名。

## 奖励历史

```text
/afkpool history
```

玩家可以查看自己的奖励类型、数量、挂机池、时间和结算状态。该界面只读，不能补发或修改流水。

状态说明：

| 状态 | 含义 |
|---|---|
| `SUCCESS` | 已进入暂存箱或外部奖励执行成功 |
| `FAILED` | 概率未命中、事件取消或奖励执行失败 |
| `PROCESSING` | 外部货币/命令结果无法安全重复执行，等待管理员核对 |
