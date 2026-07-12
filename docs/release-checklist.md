# IdlePool 正式上线检查清单

## 文件安装

- [ ] 将 `plugins/IdlePool-1.0.0.jar` 放入服务端 `plugins`。
- [ ] 如需 IA 图标，将 `ItemsAdder/contents/idlepool` 放入 `plugins/ItemsAdder/contents`。
- [ ] 从 rc.1 升级且继续使用 IA 时，删除 `contents/idlepool/textures/font/gui` 旧背景目录，或使用 `install.ps1`。
- [ ] 如需 IA 图标或 IA 奖励物品，安装与服务端版本兼容的 ItemsAdder 4.x。
- [ ] 如需货币奖励，安装 Vault 和一个经济插件。
- [ ] 如需外部物品，安装对应的 MythicMobs、MMOItems 或 Slimefun。

## 首次启动

- [ ] 启动 Paper 并确认 IdlePool 没有启动异常。
- [ ] 如使用 ItemsAdder，执行 `/iazip` 重新生成资源包。
- [ ] 如使用 ItemsAdder，让测试账号重新接受资源包。
- [ ] 执行 `/afkpool doctor` 检查配置与集成状态。
- [ ] 执行 `/afkpool info` 检查版本、服务端、IA 与 GitHub 更新状态。
- [ ] 执行 `/afkpool admin` 设置区域并启用挂机池。
- [ ] 执行 `/afkpool admin rewards` 检查奖励方案。

## 玩法测试

- [ ] 第一次进入区域只弹出一次 GUI。
- [ ] 玩家、管理员和暂存箱均显示原生箱子背景，窗口没有图片偏移或重叠。
- [ ] 安装 IA 内容包后按钮与 ActionBar 图标正常；不安装时原版按钮与文字仍可用。
- [ ] 点击开始后 ActionBar 正常刷新。
- [ ] 离开区域后挂机立即停止。
- [ ] 重新进入后未满周期进度能够恢复。
- [ ] `cycle` 奖励在 `unlock-after` 之前不触发，达到时间后参与每周期抽取。
- [ ] `session-milestone` 奖励只在本次连续挂机跨过指定时间时结算一次。
- [ ] 停止并重新开始挂机后，单次里程碑时间从零重新计算。
- [ ] 把进度保留时间临时改短，确认过期后归零。
- [ ] 实物奖励进入暂存箱，背包满时不会丢失。
- [ ] 货币和命令奖励能够即时发放。
- [ ] `/afkpool claim` 能正确领取原版和外部物品。
- [ ] 重启服务器后进度和暂存奖励仍然存在。

## 上线前备份

- [ ] 备份 `plugins/IdlePool/pools.yml`。
- [ ] 备份 `plugins/IdlePool/rewards.yml`。
- [ ] 备份 `plugins/IdlePool/reward-snapshots.yml`。
- [ ] 备份 `plugins/IdlePool/data.db`。
- [ ] 保存当前 ItemsAdder 生成资源包的 SHA-1。

建议正式发布前先让少量玩家进行 24 小时灰度测试，并保留配置与数据库备份。
