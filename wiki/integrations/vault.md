# Vault 经济

货币奖励需要：

1. Vault
2. 一个向 Vault 注册经济服务的经济插件

配置：

```yaml
- type: money
  amount: 500.0
  chance: 100.0
  weight: 50.0
  trigger: cycle
  unlock-after: 0s
```

货币奖励在主线程调用 Vault，并通过唯一结算 ID 保证同一结算不会主动重复执行。

{% hint style="warning" %}
如果服务端在经济插件已经入账、但 IdlePool 尚未写入 `SUCCESS` 时崩溃，流水会停留在 `PROCESSING`。这是外部事务无法原子提交的安全取舍；请核对经济插件流水，不要直接重复发放。
{% endhint %}

倍率活动的 `money-multiplier` 会乘算最终金额。
