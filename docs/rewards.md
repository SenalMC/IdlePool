# 奖励时间规则

每项奖励都可以独立设置触发方式、连续挂机时间和概率，因此同一个奖励方案可以组合普通周期奖励、延迟解锁奖励和多个挂机里程碑。

## 周期抽取

```yaml
- type: item
  provider: vanilla
  id: DIAMOND
  amount: 1
  chance: 10.0
  trigger: cycle
  unlock-after: 30m
```

`trigger: cycle` 表示该奖励跟随挂机池的 `reward-cycle` 反复抽取。上例要求玩家本次连续挂机达到 30 分钟后，钻石才会进入每个普通周期的独立概率抽取；30 分钟前的周期仍会正常结算其他已解锁奖励。

将 `unlock-after` 设为 `0s` 表示立即参与周期抽取。旧配置没有这两个字段时，自动按 `trigger: cycle` 和 `unlock-after: 0s` 处理。

## 抽取模式与保底

v1.2 支持三种周期抽取模式：

- `independent`：逐项按 `chance` 独立判定，可以同时获得多项奖励。
- `weighted-one`：按 `weight` 从已解锁奖励中抽取一项。
- `weighted-multiple`：按 `weight` 不重复抽取 `draw-count` 项。

权重模式下，`weight` 只表示奖励之间的相对权重，不是百分比。下面的方案每周期不重复抽取两项，并在连续 20 个周期未获得第 3 项奖励后触发保底：

```yaml
selection-mode: weighted-multiple
draw-count: 2
pity:
  enabled: true
  after-cycles: 20
  reward-index: 3
  reset-on-win: true
```

保底目标必须是周期奖励，并在满足 `unlock-after` 后才开始累计有效保底周期。保底计数随玩家的未满周期进度一同持久化，离开区域或重启服务器不会清除；超过进度保留期时会一起清零。

## 单次挂机里程碑

```yaml
- type: command
  command: "say {player} 连续挂机已满一小时"
  chance: 100.0
  trigger: session-milestone
  unlock-after: 1h
```

`trigger: session-milestone` 表示玩家本次连续挂机第一次跨过指定时间时额外结算一次。概率仍然有效：`100.0` 为必得，`20.0` 表示到达时进行一次 20% 判定，失败后本次挂机不会重试。

停止挂机、离开区域、下线或服务器关闭后，本次连续挂机时长归零；下次开始可以重新挑战里程碑。普通奖励周期的未满进度仍按挂机池原有规则保留，两套时间互不混用。

## 多阶段示例

可以给同一个方案添加多项里程碑：

```yaml
rewards:
  - type: item
    provider: vanilla
    id: IRON_INGOT
    amount: 8
    chance: 100.0
    trigger: session-milestone
    unlock-after: 10m
  - type: money
    amount: 500
    chance: 100.0
    trigger: session-milestone
    unlock-after: 30m
  - type: item
    provider: mythicmobs
    id: RARE_AFK_CHEST
    amount: 1
    chance: 25.0
    trigger: session-milestone
    unlock-after: 2h
```

支持 `s`、`m`、`h`、`d`，也可以组合成 `1h30m`。

## 游戏内配置

执行 `/afkpool admin rewards`，进入奖励方案并点击某项奖励：

- “触发方式”按钮在“周期抽取”和“单次挂机里程碑”之间切换。
- “概率解锁时间/里程碑时间”按钮通过聊天输入时间。
- “权重”按钮通过聊天输入正数；独立概率模式仍使用 `chance`。
- 切换为里程碑时，如果当前时间为零，会自动设置成 `30m`。

在方案界面点击“方案抽取设置”可切换抽取模式、设置多抽数量，并输入 `20:3` 形式的保底规则；输入 `off` 可关闭保底。
