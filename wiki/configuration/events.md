# events.yml

```yaml
schema-version: 3
time-zone: "Asia/Shanghai"

events:
  weekend-double:
    enabled: false
    display-name: "周末双倍挂机"
    start: "2026-07-18 00:00:00"
    end: "2026-07-19 23:59:59"
    pools: ["*"]
    progress-multiplier: 2.0
    item-multiplier: 2.0
    money-multiplier: 1.5
```

## 时间格式

支持：

- `yyyy-MM-dd HH:mm:ss`，按 `time-zone` 解析
- ISO-8601 Instant，例如 `2026-07-18T00:00:00Z`
- 留空或 `none` 表示无边界

## 挂机池范围

- `["*"]`：所有挂机池
- `["spawn", "vip"]`：只对指定 ID 生效
- 空列表：视为所有挂机池

倍率必须为有限非负数，加载时限制到 0～100。
