# 异常复核与奖励流水

打开运营中心：

```text
/afkpool admin operations
```

也可以直接进入：

```text
/afkpool admin review
/afkpool admin logs [玩家] [状态]
/afkpool admin export
```

## 异常领取复核

`REVIEW` 表示物品已经尝试放入玩家背包，但数据库无法确认最终提交。为了防止复制，系统不会自动重新开放领取。

管理员可以选择：

| 操作 | 用途 |
|---|---|
| 恢复到暂存箱 | 确认玩家没有收到物品，允许重新领取 |
| 确认已经领取 | 玩家已经收到物品，删除异常记录 |
| 作废 | 奖励不应继续存在，删除记录 |

所有操作都会写入 `admin_audit_log`，包含管理员、动作、目标和时间。

## 奖励流水

流水支持 `ALL`、`SUCCESS`、`FAILED`、`PROCESSING` 状态筛选，并可按玩家查询。显示结算 ID、奖励键、数量、挂机池、状态、详情和时间。

## CSV 导出

导出文件位于：

```text
plugins/IdlePool/exports/reward-ledger-时间.csv
```

CSV 使用 UTF-8，并对每个字段进行双引号转义。

{% hint style="danger" %}
不要把 `PROCESSING` 外部奖励直接重复执行。服务器可能已经发放货币或执行命令，只是尚未来得及记录成功；重复执行可能造成复制。
{% endhint %}
