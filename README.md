# IdlePool

适用于 Paper 1.21.1～26.2 的区域挂机奖励插件。玩家进入挂机池区域后会看到原生箱子 GUI，点击按钮才开始累计挂机时间和奖励。

> 当前开发版本为 `1.1.0`，Paper 26.2 仍需跟随服务端实验构建持续验证。

## 已实现

- 多挂机池长方体区域检测
- 进入区域自动打开 Minecraft 原生箱子 GUI
- 点击按钮开始/结束挂机
- ActionBar 显示本次时长、下次奖励和本次收益
- SQLite 异步保存累计时间与未满周期进度
- 未满周期进度按挂机池配置过期，默认 7 天
- 独立概率奖励方案
- 奖励可设置连续挂机解锁时间，解锁后才参与普通周期概率抽取
- 单次挂机里程碑奖励，可配置 10m、30m、1h 等多个阶段
- 命令奖励即时执行
- Vault 货币奖励即时发放
- 实物奖励进入暂存箱
- `/afkpool claim` 领取暂存物品
- 原版、ItemsAdder、MythicMobs、MMOItems、Slimefun 物品生成适配
- `/afkpool admin` 游戏内创建和编辑挂机池
- 游戏内创建和编辑奖励方案、概率、数量、命令及 Vault 金额
- 管理员手持物品自动识别来源，未知自定义物品保存为完整快照
- ItemsAdder 可选提供按钮、挂机币与 ActionBar 图标，菜单背景始终使用原版箱子
- `/afkpool info` 显示版本、服务端、ItemsAdder 状态并异步检查 GitHub 更新
- 所有玩家消息和 GUI 文本均可在 `message.yml` 配置，并附带完整 `en.yml`
- 正常结束挂机后自动打开暂存箱，可用 `inbox.open-on-stop` 关闭
- 暂存箱事务预约、分页、一键领取和异常复核，避免重复领取
- 奖励唯一结算 ID 与 SQLite 流水，防止周期奖励重复执行
- 管理 GUI 可视化选择、复制和删除挂机池/奖励方案
- 配置 schema 自动迁移并生成 `.bak` 备份，语言缺失键自动回退
- 可选 PlaceholderAPI 扩展

## 构建

需要 Java 21 或更高版本：

```shell
./gradlew clean test shadowJar
```

Windows：

```powershell
.\gradlew.bat clean test shadowJar
```

插件产物位于 `build/libs/IdlePool-1.1.0.jar`。

正式发布包：

```powershell
.\gradlew.bat clean test releaseBundle
```

输出位于 `build/distributions/IdlePool-1.1.0-release.zip`。

## 开始使用

1. 把插件 JAR 放进 Paper 的 `plugins` 目录。
2. 如需自定义按钮和 ActionBar 图标，将 `itemsadder-pack/contents/idlepool` 安装到 ItemsAdder 的 `contents` 目录并执行 `/iazip`。
3. ItemsAdder、Vault、MythicMobs、MMOItems、Slimefun 均为可选依赖。
4. 启动服务器后执行 `/afkpool doctor`。
5. 执行 `/afkpool admin`，点击“创建挂机池”并输入 ID。
6. 设置区域两点、奖励周期、进度保留时间和奖励方案。
7. 启用挂机池。

默认使用 `message.yml` 简体中文语言文件。若要切换英文，将 `config.yml` 中的 `language.file` 改为 `en.yml` 后执行 `/afkpool reload`。

从 v1.0 升级时会自动迁移 `config.yml`、`pools.yml`、`rewards.yml` 和 SQLite 表结构。原 YAML 会在首次迁移前保存为 `*.v1.bak`。

默认示例挂机池处于停用状态，避免首次启动后意外覆盖主城区域。

## 命令

- `/afkpool`：在挂机池内打开开始界面
- `/afkpool info`：显示插件、服务端、ItemsAdder 与更新状态
- `/afkpool stop`：结束挂机并保存进度
- `/afkpool claim`：打开实物奖励暂存箱
- `/afkpool admin`：打开管理员 GUI
- `/afkpool admin rewards`：直接打开奖励方案管理 GUI
- `/afkpool reload`：重载配置
- `/afkpool doctor`：执行上线配置与集成检查

## PlaceholderAPI

安装 PlaceholderAPI 后会自动注册：

- `%idlepool_active%`
- `%idlepool_pool%`
- `%idlepool_session_time%`
- `%idlepool_total_time%`
- `%idlepool_next_reward%`
- `%idlepool_earned%`
- `%idlepool_inbox_count%`

## 奖励物品 ID

```yaml
# 原版物品
provider: vanilla
id: DIAMOND

# ItemsAdder
provider: itemsadder
id: "idlepool:afk_coin"

# MythicMobs
provider: mythicmobs
id: "SKELETON_SWORD"

# MMOItems，格式为 类型:ID
provider: mmoitems
id: "SWORD:CUTLASS"

# Slimefun
provider: slimefun
id: "CARBONADO"
```

ItemsAdder 图标和按钮命名约定见 [ItemsAdder 资源说明](docs/itemsadder.md)。
奖励触发时间与配置示例见 [奖励时间规则](docs/rewards.md)。

项目主页：https://github.com/SenalMC/IdlePool/

## 开源许可

IdlePool 使用 [Apache License 2.0](LICENSE) 开源，版权归 Chirnuo 所有。修改版可以闭源或采用不同许可，但分发 IdlePool、修改版或随包资源时必须同时遵守 [NOTICE](NOTICE) 并保留以下署名：

```text
由 IdlePool 修改而来：https://github.com/SenalMC/IdlePool
```
