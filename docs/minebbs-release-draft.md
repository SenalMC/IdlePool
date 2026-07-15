# MineBBS 发布稿：IdlePool v1.2.0

## 资源页建议标题

**IdlePool v1.2.0｜区域挂机奖励｜权重与保底｜限时倍率｜统计榜单｜异常复核中心**

## 一句话简介

玩家进入指定区域并主动开始挂机，按周期或连续挂机里程碑获得物品、货币及命令奖励。

## 资源信息

- 插件名称：IdlePool
- 当前版本：1.2.0
- 服务端：Paper 1.21.1～26.2
- Java：21 或更高版本
- 开发语言：Kotlin
- 默认语言：简体中文，同时附带完整英文语言文件
- 作者：Chirnuo
- 联系邮箱：mc@mc.email.cn
- 开源协议：Apache License 2.0
- 源代码：https://github.com/SenalMC/IdlePool/
- 下载地址：MineBBS 本帖附件 / GitHub Releases（发布时补充）

> 插件以 Paper 1.21.1 API 编译，Paper 1.21.1 已完成完整运行测试。Paper 26.2 将随服务端实验构建持续验证，如遇兼容问题请携带 `/afkpool info` 与 `/afkpool doctor` 输出反馈。

---

## 插件介绍

IdlePool 是一个面向生存服、RPG 服和综合服的区域挂机奖励插件。

玩家进入管理员划定的挂机池区域后，会自动看到 Minecraft 原生箱子界面。只有点击“开始挂机”按钮后才会累计时间并结算奖励，避免玩家只是路过区域也被计入挂机。

挂机期间，ActionBar 会显示本次挂机时长、距离下次周期奖励的时间以及本次获得的奖励数量。玩家离开区域、主动停止或下线时，本次挂机结束；尚未完成一个奖励周期的进度会继续保留，默认保留 7 天，也可以按挂机池单独设置。

实物奖励不会直接塞进玩家背包，而是进入独立暂存箱；Vault 货币和命令奖励则直接结算。暂存箱使用事务预约、异常复核、分页和一键领取，并为每次奖励生成唯一结算 ID，尽可能避免网络抖动、重复任务或服务器异常造成重复发奖。v1.2.0 进一步加入异常领取复核中心、奖励流水查询与 CSV 导出、统计榜单、权重保底和限时倍率活动。

> [在此插入：玩家进入挂机池后的开始界面截图]
>
> [在此插入：挂机 ActionBar 截图]
>
> [在此插入：暂存箱分页与一键领取截图]
>
> [在此插入：管理员 GUI 截图]

---

## 主要功能

- 支持创建多个长方体挂机区域
- 玩家进入区域后自动打开原生箱子 GUI
- 玩家必须点击按钮才会正式开始挂机
- ActionBar 实时显示挂机时长、下次奖励和本次收益
- SQLite 异步保存玩家累计时间和未满周期进度
- 未满周期进度默认保留 7 天，可按挂机池独立配置
- 支持周期奖励、延迟概率解锁和单次挂机里程碑
- 每项奖励独立设置概率、数量和解锁时间
- 支持独立概率、权重单抽、权重多抽和周期保底
- 支持按时间段启停的进度、实物、货币倍率活动
- 实物奖励进入暂存箱，货币及命令奖励直接结算
- 暂存箱支持事务安全领取、翻页和一键领取
- 奖励流水和唯一结算 ID 防止重复结算
- 管理员复核异常领取，查询玩家流水并导出 CSV
- 日、周、月和总榜统计，记录周期数、奖励数及最长单次挂机
- 游戏内 GUI 创建、选择、复制和删除挂机池及奖励方案
- 管理员可直接手持物品，让插件自动识别物品来源
- 支持原版、ItemsAdder、MythicMobs、MMOItems 和 Slimefun 物品
- 无法识别来源的自定义物品可以保存完整物品快照
- 配置结构自动迁移，迁移前自动创建备份
- 语言文件缺少新键时自动使用内置文本回退
- 提供简体中文 `message.yml` 和完整英文 `en.yml`
- 可选 PlaceholderAPI 变量
- 可选 BossBar，并提供公开 API 与可取消的 Bukkit 事件
- 启动时异步检查 GitHub 更新
- `/afkpool doctor` 提供上线前配置与依赖检查

---

## 奖励时间规则

IdlePool 的每项奖励都可以独立选择触发方式。

### 普通周期奖励

管理员可以给挂机池设置一个基础周期，例如每 10 分钟结算一次。

下面的钻石奖励要求玩家本次连续挂机达到 30 分钟，之后才会在每个普通周期中以 10% 概率参与抽取：

```yaml
- type: item
  provider: vanilla
  id: DIAMOND
  amount: 1
  chance: 10.0
  trigger: cycle
  unlock-after: 30m
```

将 `unlock-after` 设置为 `0s`，表示该奖励从第一次周期结算开始就参与抽取。

### 权重抽取与周期保底

奖励方案可选择逐项独立概率、权重单抽或权重多抽。权重模式通过每项奖励的 `weight` 决定相对概率，多抽模式会在同一周期内不重复抽取。

管理员还可以设置 `20:3` 形式的保底：目标奖励满足解锁时间后，连续 20 个有效周期未获得方案中的第 3 项奖励时强制结算该项。计数会持久化，不会因短暂离开区域或服务器重启而绕过；超过进度保留期后会随过期进度清零。

### 单次挂机里程碑

里程碑奖励会在本次连续挂机首次达到指定时间时额外结算一次：

```yaml
- type: command
  command: "say {player} 连续挂机已满一小时"
  chance: 100.0
  trigger: session-milestone
  unlock-after: 1h
```

同一个奖励方案可以同时设置 10 分钟、30 分钟、1 小时、2 小时等多个阶段。里程碑概率判定失败后，本次挂机不会反复重试；玩家下一次重新开始挂机时可以再次挑战该里程碑。

支持的时间单位为 `s`、`m`、`h`、`d`，也可以写成 `1h30m`。

---

## 支持的奖励类型

### 原版物品

```yaml
provider: vanilla
id: DIAMOND
```

### ItemsAdder 物品

```yaml
provider: itemsadder
id: "idlepool:afk_coin"
```

### MythicMobs 物品

```yaml
provider: mythicmobs
id: "SKELETON_SWORD"
```

### MMOItems 物品

MMOItems 使用 `类型:ID` 格式：

```yaml
provider: mmoitems
id: "SWORD:CUTLASS"
```

### Slimefun 物品

```yaml
provider: slimefun
id: "CARBONADO"
```

此外还支持 Vault 货币奖励、控制台命令奖励，以及管理员手持物品生成的完整物品快照。

---

## 安装教程

1. 确认服务端使用 Java 21 或更高版本，并运行兼容版本的 Paper。
2. 将 `IdlePool-1.2.0.jar` 放入服务端的 `plugins` 目录。
3. 根据需要安装可选依赖；如果只使用原版物品，可以不安装任何额外插件。
4. 启动服务器，等待生成 `plugins/IdlePool` 配置目录。
5. 执行 `/afkpool doctor` 检查插件、奖励方案和可选依赖状态。
6. 执行 `/afkpool admin rewards` 创建或编辑奖励方案。
7. 执行 `/afkpool admin` 创建挂机池，设置区域两点、奖励周期、进度保留时间和奖励方案。
8. 检查配置无误后，在管理员 GUI 中启用挂机池。

首次生成的示例挂机池默认处于关闭状态，不会直接影响主城或其他世界。

### 从 v1.0 / v1.1 升级

直接关闭服务器并替换 JAR，随后重新启动即可。

v1.2.0 会自动迁移：

- `config.yml`
- `pools.yml`
- `rewards.yml`
- `events.yml`
- SQLite 数据库表结构

首次迁移前，旧 YAML 会保存为相应版本的 `*.bak`。虽然插件会自动备份配置，正式服升级前仍建议额外备份整个 `plugins/IdlePool` 目录。

---

## ItemsAdder 配套资源

ItemsAdder 完全可选。未安装 ItemsAdder 时，插件会自动使用原版按钮和纯文字 ActionBar，不影响挂机与奖励功能。

如需使用配套按钮、挂机币和 ActionBar 图标，将发布包中的：

```text
ItemsAdder/contents/idlepool
```

合并到服务器的：

```text
plugins/ItemsAdder/contents/idlepool
```

然后执行 `/iazip`，并让客户端重新加载资源包。

为避免客户端 GUI 缩放、字体和模组造成材质偏移，IdlePool 的所有菜单背景始终使用 Minecraft 原生箱子，不再使用 ItemsAdder 字体图片模拟容器背景。ItemsAdder 只负责可选按钮、物品和 ActionBar 小图标。

---

## 命令

| 命令 | 说明 |
|---|---|
| `/afkpool` | 在挂机池区域内打开开始界面 |
| `/afkpool info` | 查看插件版本、服务端版本、ItemsAdder 状态和更新信息 |
| `/afkpool stop` | 结束当前挂机并保存未满周期进度 |
| `/afkpool claim` | 打开实物奖励暂存箱 |
| `/afkpool stats [玩家] [day/week/month/total]` | 查看个人或指定玩家统计 |
| `/afkpool top [day/week/month/total]` | 打开挂机排行榜 |
| `/afkpool history [玩家]` | 查询玩家奖励流水 |
| `/afkpool admin` | 打开挂机池管理员 GUI |
| `/afkpool admin rewards` | 直接打开奖励方案管理 GUI |
| `/afkpool admin operations` | 打开复核、流水和导出中心 |
| `/afkpool event list/start/stop [活动]` | 查看或控制倍率活动 |
| `/afkpool doctor` | 检查配置、奖励方案和依赖状态 |
| `/afkpool reload` | 重载配置、奖励方案和语言文件 |

别名：`/idlepool`

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `idlepool.use` | 所有玩家 | 使用挂机池 |
| `idlepool.admin` | OP | 管理和重载 IdlePool |

每个挂机池还可以配置单独的进入权限；留空时不要求额外权限。

---

## PlaceholderAPI

安装 PlaceholderAPI 后，IdlePool 会自动注册 `idlepool` 扩展，无需通过 eCloud 另外下载。

| 变量 | 内容 |
|---|---|
| `%idlepool_active%` | 玩家当前是否正在挂机 |
| `%idlepool_pool%` | 当前挂机池 ID |
| `%idlepool_session_time%` | 本次连续挂机时长 |
| `%idlepool_total_time%` | 当前挂机池累计挂机时长 |
| `%idlepool_next_reward%` | 距离下次周期奖励的时间 |
| `%idlepool_earned%` | 本次挂机已获得的奖励数量 |
| `%idlepool_inbox_count%` | 暂存箱待领取条目数量 |
| `%idlepool_daily_time%` / `%idlepool_weekly_time%` / `%idlepool_monthly_time%` | 分周期挂机时长 |
| `%idlepool_cycles%` / `%idlepool_rewards%` / `%idlepool_longest_session%` | 累计统计 |
| `%idlepool_rank_daily%` / `%idlepool_rank_weekly%` / `%idlepool_rank_monthly%` / `%idlepool_rank_total%` | 排名 |
| `%idlepool_event%` / `%idlepool_progress_multiplier%` | 当前活动和进度倍率 |

---

## 可选依赖

| 插件 | 用途 | 是否必须 |
|---|---|---|
| ItemsAdder | 自定义按钮、图标和 IA 物品奖励 | 否 |
| Vault + 经济插件 | 货币奖励 | 仅使用货币奖励时需要 |
| MythicMobs | MythicMobs 物品奖励 | 仅使用对应物品时需要 |
| MMOItems | MMOItems 物品奖励 | 仅使用对应物品时需要 |
| Slimefun | Slimefun 物品奖励 | 仅使用对应物品时需要 |
| PlaceholderAPI | 提供变量 | 否 |

插件缺少某个可选依赖时，不会影响其他奖励类型。建议配置完成后使用 `/afkpool doctor` 检查引用了但尚未安装的物品提供器。

---

## 常见问题

### 玩家没有挂满一个周期，进度会消失吗？

不会立即消失。未满周期进度会按挂机池设置保留，默认 7 天。超过保留期后才会重置。

### 玩家只是经过挂机池会获得奖励吗？

不会。进入区域后只会打开开始界面，玩家必须主动点击开始按钮。

### 玩家背包已满怎么办？

所有实物奖励先进入暂存箱。领取时如果背包空间不足，未领取部分会继续保留。

### 服务器异常关闭会不会重复发奖？

IdlePool 使用奖励唯一结算 ID、SQLite 奖励流水和暂存箱领取令牌保护重复结算。对于结果无法确认的异常领取，记录会进入复核状态，不会自动回滚成可重复领取状态；管理员可在 `/afkpool admin operations` 中确认、恢复或作废。

### 必须安装 ItemsAdder 吗？

不需要。ItemsAdder 仅用于可选贴图与 IA 物品，未安装时会自动使用原版界面。

### 可以修改全部提示文字吗？

可以。玩家消息、命令反馈、GUI 标题、按钮和 Lore 均可在 `message.yml` 中修改。将 `config.yml` 的 `language.file` 改为 `en.yml` 可以切换英文。

---

## 更新日志：v1.2.0

- 新增独立概率、权重单抽、权重多抽和周期保底
- 新增限时倍率活动，分别控制进度、实物与货币收益
- 新增日榜、周榜、月榜、总榜与玩家挂机统计
- 新增异常领取复核中心、奖励流水筛选和 CSV 导出
- 新增可选 BossBar，ActionBar 可显示活动与倍率
- 扩展 PlaceholderAPI 统计、排名和活动变量
- 提供公开 `IdlePoolApi` 与挂机、周期、里程碑、结算、领取事件
- 配置与 SQLite 自动迁移到 schema 3
- 新增完整 GitBook Wiki

---

## 开源与再分发说明

IdlePool 使用 Apache License 2.0 开源，版权归 Chirnuo 所有。

你可以修改插件或配套资源，也可以闭源发布修改版；但在再分发 IdlePool、修改版或任何随包资源时，必须保留许可证、NOTICE，并在说明文档或其他清晰可见的位置标明：

```text
由 IdlePool 修改而来：https://github.com/SenalMC/IdlePool
```

修改资源不强制公开源文件。

项目主页：https://github.com/SenalMC/IdlePool/

问题反馈：https://github.com/SenalMC/IdlePool/issues

联系邮箱：mc@mc.email.cn

---

## 资源页短描述备选

### 版本 A

区域挂机奖励插件，支持权重保底、倍率活动、统计榜单、事务暂存箱、运营复核 GUI、多物品库和 PlaceholderAPI。

### 版本 B

进入区域、主动开始、按时间领奖。支持未满周期进度、权重保底、限时倍率、暂存箱和可视化配置。

### 版本 C

适用于 Paper 的 Kotlin 挂机池插件：原生箱子 GUI、SQLite 持久化、统计榜单、运营审计、多奖励源与双语消息。

## 推荐标签

`挂机池` `区域奖励` `生存服` `RPG` `Kotlin` `ItemsAdder` `MythicMobs` `MMOItems` `Slimefun` `Vault` `PlaceholderAPI`
