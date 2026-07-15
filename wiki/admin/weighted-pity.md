# 权重抽取与周期保底

## 抽取模式

### `independent`

每项周期奖励独立使用 `chance` 判定，一次周期可能获得零项、一项或多项奖励。

### `weighted-one`

从所有已解锁且 `weight > 0` 的周期奖励中按相对权重抽取一项。`chance` 仍用于里程碑，不用于权重周期选择。

### `weighted-multiple`

按权重无放回抽取 `draw-count` 项。同一奖励在一个周期内不会被重复选中；候选数量不足时以实际候选数为准。

```yaml
selection-mode: weighted-multiple
draw-count: 2
```

权重只表示相对比例。例如 70、25、5 与 700、250、50 的结果比例相同。

## 周期保底

```yaml
pity:
  enabled: true
  after-cycles: 20
  reward-index: 3
  reset-on-win: true
```

- `after-cycles`：连续多少周期后触发
- `reward-index`：目标奖励序号，从 1 开始
- `reset-on-win`：自然抽中目标时是否归零；触发保底后始终归零

保底目标必须是 `cycle` 奖励，并且已经满足 `unlock-after` 后才开始累计有效保底周期。保底计数存入玩家对应挂机池的进度记录，重启和短暂离开区域不会绕过保底；超过 `progress-retention` 后会随过期进度一起清零。

游戏内可在奖励方案的“方案抽取设置”中输入 `20:3`，表示 20 周期保底第 3 项；输入 `off` 关闭。
