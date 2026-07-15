# 奖励方案

使用 `/afkpool admin rewards` 管理方案。

## 奖励类型

### 实物

```yaml
- type: item
  provider: vanilla
  id: DIAMOND
  amount: 1
  chance: 10.0
  weight: 10.0
  trigger: cycle
  unlock-after: 30m
```

### Vault 货币

```yaml
- type: money
  amount: 500.0
  chance: 100.0
  weight: 50.0
  trigger: cycle
  unlock-after: 0s
```

### 控制台命令

```yaml
- type: command
  command: "give {player} apple 1"
  chance: 100.0
  weight: 10.0
  trigger: session-milestone
  unlock-after: 1h
```

支持 `{player}`、`{uuid}` 和 `{pool}`。

## 触发方式

### `cycle`

达到 `unlock-after` 后，每次完成挂机池的 `reward-cycle` 时参与结算。`0s` 表示立即参与。

### `session-milestone`

本次连续挂机首次跨过 `unlock-after` 时结算一次。概率失败后本次会话不重试；停止后重新开始可以再次挑战。

## 游戏内添加物品

管理员手持物品点击“添加手持物品”时，插件会识别原版、ItemsAdder、MMOItems 和 Slimefun。无法稳定反查模板的物品会保存完整 `ItemStack` 快照。

方案支持复制和删除。删除仍被挂机池引用的方案会导致该池无法发奖，`/afkpool doctor` 会报告错误。
