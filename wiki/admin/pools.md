# 挂机池管理

执行 `/afkpool admin` 打开管理 GUI。

## 挂机池属性

| 属性 | 说明 |
|---|---|
| `enabled` | 是否启用 |
| `display-name` | GUI 显示名，支持 MiniMessage |
| `world` | 世界名 |
| `region.min/max` | 长方体两个端点 |
| `permission` | 使用权限，留空表示不额外检查 |
| `max-active-players` | 同时挂机人数，0 表示不限 |
| `reward-cycle` | 普通奖励周期 |
| `progress-retention` | 未满周期进度保留时间 |
| `reward-plan` | 奖励方案 ID |

## 复制与删除

复制会保留区域和全部设置，但新挂机池默认停用。删除前需要二次确认；删除挂机池不会删除玩家旧统计或奖励流水。

## 区域重叠

如果多个已启用挂机池区域重叠，插件使用配置加载顺序中第一个匹配的挂机池。建议避免重叠，尤其不要让不同权限或奖励方案覆盖同一方块。
