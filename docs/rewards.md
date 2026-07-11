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
- 切换为里程碑时，如果当前时间为零，会自动设置成 `30m`。
