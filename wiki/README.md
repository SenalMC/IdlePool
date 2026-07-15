# IdlePool Wiki

IdlePool 是适用于 Paper 1.21.1～26.2 的区域挂机奖励插件。玩家进入管理员划定的区域后，必须主动点击开始按钮才会累计挂机时间并获得奖励。

当前文档对应 **IdlePool 1.2.0**。

## v1.2 核心能力

- 多挂机池区域与未满周期进度保留
- 周期奖励、连续挂机解锁和单次里程碑
- 独立概率、权重单抽、权重多抽与周期保底
- 原版、ItemsAdder、MythicMobs、MMOItems、Slimefun 和物品快照
- 事务安全暂存箱、分页、一键领取和异常复核
- 今日、本周、本月与总计统计排行榜
- 限时进度、物品和货币倍率活动
- 奖励流水、管理员审计与 CSV 导出
- ActionBar、可选 BossBar 和 PlaceholderAPI
- Bukkit Services API 与生命周期事件

## 快速入口

- [安装 IdlePool](getting-started/installation.md)
- [五分钟创建第一个挂机池](getting-started/quick-start.md)
- [奖励方案](admin/rewards.md)
- [权重与保底](admin/weighted-pity.md)
- [异常复核与奖励流水](admin/operations.md)
- [命令和权限](reference/commands-permissions.md)
- [故障排查](reference/troubleshooting.md)

## 项目链接

- 源代码：https://github.com/SenalMC/IdlePool/
- 问题反馈：https://github.com/SenalMC/IdlePool/issues
- 作者：Chirnuo
- 邮箱：mc@mc.email.cn

{% hint style="warning" %}
Paper 1.21.1 已完成实际运行测试。Paper 26.2 会随服务端实验构建持续验证；上线前请在测试服执行 `/afkpool doctor`。
{% endhint %}
