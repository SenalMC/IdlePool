# pools.yml

```yaml
schema-version: 3

pools:
  spawn:
    enabled: false
    display-name: "<gold>主城挂机池"
    world: world
    region:
      min: { x: -10, y: 60, z: -10 }
      max: { x: 10, y: 80, z: 10 }
    permission: "idlepool.use"
    max-active-players: 50
    reward-cycle: 10m
    progress-retention: 7d
    reward-plan: basic
    gui:
      start-item: "idlepool:start_button"
      info-item: "idlepool:info_button"
      rewards-item: "idlepool:rewards_button"
```

时间支持 `s`、`m`、`h`、`d` 和组合格式，例如 `1h30m`。

`world` 必须与 Bukkit 世界名完全一致。区域坐标不要求 min 数值真的小于 max，加载时会自动归一化。

`gui` 中 IA ID 失效时会使用原版图标，不影响挂机逻辑。
