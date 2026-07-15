# rewards.yml

```yaml
schema-version: 3

reward-plans:
  basic:
    selection-mode: independent
    draw-count: 1
    pity:
      enabled: false
      after-cycles: 20
      reward-index: 1
      reset-on-win: true
    rewards:
      - type: item
        provider: vanilla
        id: DIAMOND
        amount: 1
        chance: 10.0
        weight: 10.0
        trigger: cycle
        unlock-after: 30m
```

## 字段规则

- `selection-mode`：`independent`、`weighted-one`、`weighted-multiple`
- `draw-count`：权重多抽数量，1～45
- `chance`：0～100，独立周期和里程碑使用
- `weight`：非负相对权重，权重周期使用
- `trigger`：`cycle` 或 `session-milestone`
- `unlock-after`：周期解锁或里程碑时间
- `reward-index`：保底目标，从 1 开始

无效奖励方案会记录错误并跳过加载。使用 `/afkpool doctor` 检查无正权重方案、无效保底和缺失物品提供器。
