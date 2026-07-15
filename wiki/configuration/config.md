# config.yml

```yaml
config-version: 3

storage:
  file: data.db
  checkpoint-interval-seconds: 30
  reward-log-retention-days: 30

update-check:
  enabled: true
  version-url: "https://raw.githubusercontent.com/SenalMC/IdlePool/main/version.txt"
  timeout-seconds: 5

language:
  file: message.yml

progress:
  default-retention: 7d

actionbar:
  enabled: true

bossbar:
  enabled: false
  color: YELLOW
  overlay: PROGRESS

statistics:
  time-zone: "Asia/Shanghai"
  leaderboard-size: 45

inbox:
  retention: permanent
  max-entries-per-player: 500
  open-on-stop: true
  page-size: 45
```

## 重要设置

- `checkpoint-interval-seconds` 最低按 5 秒处理。越短写入越频繁，异常停服时损失的最后一小段统计越少。
- `reward-log-retention-days` 清理成功和失败流水；`PROCESSING` 不会自动清理。
- `statistics.time-zone` 决定日、周、月统计边界。
- `leaderboard-size` 范围 1～45。
- BossBar 颜色与样式使用 Adventure 的枚举名，无效值会回退到 `YELLOW` 和 `PROGRESS`。

修改后执行 `/afkpool reload`。数据库文件路径只应在停服状态修改。
