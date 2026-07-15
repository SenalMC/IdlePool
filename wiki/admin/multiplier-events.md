# 限时倍率活动

活动在 `events.yml` 配置，可分别影响奖励进度、实物数量和 Vault 金额。

```yaml
events:
  weekend-double:
    enabled: true
    display-name: "周末双倍挂机"
    start: "2026-07-18 00:00:00"
    end: "2026-07-19 23:59:59"
    pools: ["*"]
    progress-multiplier: 2.0
    item-multiplier: 2.0
    money-multiplier: 1.5
```

## 倍率含义

- `progress-multiplier`：加速普通周期进度，不虚增真实挂机时长或排行榜时间
- `item-multiplier`：结算时向下取整实物数量，至少为 1
- `money-multiplier`：乘算 Vault 金额
- 命令奖励始终执行一次，避免重复执行任意命令

多个活动同时生效时倍率相乘，单项最终上限为 100 倍。

## 管理命令

```text
/afkpool event list
/afkpool event start <活动ID>
/afkpool event stop <活动ID>
```

`start` 会把开始时间设为当前时间；如果原结束时间已过，会临时设置为当前时间后一小时。长期活动建议直接编辑 `events.yml` 后执行 `/afkpool reload`。
