# Changelog

## 1.1.0

- 使用 Kotlin 2.4.0 完整重写插件主体与测试代码。
- 暂存箱新增领取预约令牌、异常复核状态、分页与一键领取。
- 新增奖励唯一结算 ID、`reward_ledger` 流水与重复结算保护。
- 管理 GUI 新增奖励方案可视化选择，以及挂机池/奖励方案复制和删除。
- 新增 YAML schema 自动迁移、迁移前备份和语言缺失键回退。
- 新增可选 PlaceholderAPI 支持。

## 1.0.0

- 正式版包名迁移至 `top.cnuo.idlepool`。
- 新增 `message.yml` 默认中文语言包与完整 `en.yml` 英文语言包。
- 所有玩家消息、命令反馈、GUI 标题、按钮和 Lore 均可配置。
- 新增 `inbox.open-on-stop`，结束挂机后可自动打开暂存箱。
- 所有示例 YAML 配置补充中英双语注释。

- 新增 `/afkpool info` 插件信息与更新状态命令。
- 启动时异步读取 GitHub `version.txt` 并比较本地版本。
- 新版本可用时在后台和 info 输出中显示版本与下载地址。
- 作者更新为 Chirnuo，并加入项目网站与联系邮箱。

## 1.0.0-rc.3

- 单个奖励新增 `trigger` 与 `unlock-after` 时间参数。
- 支持连续挂机达到指定时间后才进入普通周期概率抽取。
- 支持单次连续挂机里程碑奖励，同一方案可配置多个时间阶段。
- 管理员奖励 GUI 可直接切换触发方式并编辑解锁/里程碑时间。

## 1.0.0-rc.2

- 玩家、管理员及暂存箱界面统一改为 Minecraft 原生箱子 GUI。
- 移除会受客户端 GUI 缩放、字体和模组影响的三张 ItemsAdder 容器背景。
- 保留 ItemsAdder 按钮、挂机币与 ActionBar 小图标。

## 1.0.0-rc.1

- 多区域挂机池与主动开始 GUI。
- ActionBar 挂机状态显示。
- SQLite 未满周期进度与默认七天保留。
- 实物暂存箱、命令奖励和 Vault 货币奖励。
- 原版、ItemsAdder、MythicMobs、MMOItems、Slimefun 与物品快照支持。
- 游戏内挂机池和奖励方案管理员 GUI。
- IdlePool 官方 ItemsAdder 配套内容包。
- 初版容器背景、按钮、挂机币与 HUD 图标。
