# MineBBS 资源发布草稿

> 本文件按 MineBBS Markdown 正文结构编写。发布前请替换所有“待填写/待上传”内容，并上传至少 3 张真实游戏截图。

## 资源页面信息

- 推荐标题：`IdlePool —— 主动式区域挂机奖励池｜进度保留｜时间里程碑｜暂存箱｜游戏内管理 [Paper 1.21.1+]`
- 简短介绍：`进入区域后由玩家主动开始挂机，支持周期概率、时间解锁、单次里程碑、实物暂存箱和游戏内管理 GUI。`
- 分类：`Java版服务器资源 → 服务器插件 → 服务端插件`
- 当前版本：`1.0.0`
- 版权类型：`原创`
- 资源状态：`上线候选版 / 公开测试`
- 推荐标签：`Paper`、`挂机池`、`挂机奖励`、`时间奖励`、`SQLite`、`ItemsAdder`、`Vault`
- 价格：`[待填写：免费/金粒/RMB]`
- 开源协议：`Apache License 2.0`
- 作者：`Chirnuo`
- 邮箱：`mc@mc.email.cn`
- 项目与反馈：https://github.com/SenalMC/IdlePool/

---

# IdlePool

> 不是“站进区域就无脑发奖”，而是一套由玩家主动开始、支持连续挂机阶段奖励与安全暂存的挂机池系统。

IdlePool 是面向 Paper 服务器的区域挂机奖励插件。玩家进入管理员设置的挂机池后，会打开 Minecraft 原生箱子界面；点击开始按钮后才累计本次挂机时间并结算奖励。离开区域、下线或主动停止时会立即结束本次挂机。

插件将“普通奖励周期”和“本次连续挂机时间”分开计算：普通周期未满进度可以保留；单次连续挂机则可以用于 10 分钟、30 分钟、1 小时等里程碑奖励，以及“挂机满一定时间后才解锁概率奖励”的玩法。

## 图片展示

> `[待上传截图 1：玩家进入区域后打开的原生挂机 GUI]`

> `[待上传截图 2：开始挂机后的 ActionBar 时长、下次奖励与本次收益]`

> `[待上传截图 3：管理员奖励编辑 GUI，展示触发方式和时间参数]`

建议额外上传：暂存箱领取界面、挂机池区域编辑界面、`/afkpool doctor` 检查结果。

## 核心特色

### 玩家主动开始

- 进入区域后只打开开始界面，不会直接计算挂机时间。
- 玩家点击按钮后才开始挂机。
- 离开区域、下线或执行停止命令会立即结束并保存进度。
- GUI 始终使用 Minecraft 原生箱子背景，不受客户端 GUI 缩放或字体偏移影响。

### 两套时间奖励规则

每项奖励都可以单独配置概率和连续挂机时间。

```yaml
# 连续挂机 30 分钟后，才加入每个普通周期的 10% 抽取
- type: item
  provider: vanilla
  id: DIAMOND
  amount: 1
  chance: 10.0
  trigger: cycle
  unlock-after: 30m
```

```yaml
# 本次连续挂机达到 1 小时时额外结算一次
- type: command
  command: "say {player} 连续挂机已满一小时"
  chance: 100.0
  trigger: session-milestone
  unlock-after: 1h
```

- `cycle`：满足时间要求后，参与挂机池每个普通奖励周期的独立概率抽取。
- `session-milestone`：本次连续挂机第一次到达指定时间时结算一次。
- 可以在同一个方案内配置多个 `10m`、`30m`、`1h`、`2h` 阶段。
- 停止挂机后里程碑重新计时，但普通周期的未满进度仍会保留。

### 实物奖励暂存箱

- 实物奖励不会直接塞入玩家背包，而是先进入 SQLite 暂存箱。
- 背包已满时奖励不会掉在地上或消失。
- 玩家使用 `/afkpool claim` 打开暂存箱并领取。
- 暂存箱支持部分领取，未放入背包的数量继续保留。
- 命令奖励与 Vault 货币奖励即时发放。

### 多物品生态支持

支持以下物品来源：

- Minecraft 原版物品
- ItemsAdder
- MythicMobs
- MMOItems
- Slimefun
- 无法稳定识别的自定义物品可保存完整物品快照

管理员可以直接手持物品，在奖励管理 GUI 中添加；MythicMobs 等模板物品也可以输入 `provider:item_id` 添加。

### 游戏内管理

- 游戏内创建多个挂机池。
- 站在对应位置设置区域点 1、区域点 2。
- 编辑权限、奖励周期、最大人数、进度保留时间与奖励方案。
- 创建和编辑物品、命令、Vault 货币奖励。
- 编辑奖励概率、数量、触发方式与解锁/里程碑时间。
- `/afkpool doctor` 检查依赖、奖励方案和配置状态。
- `/afkpool info` 显示插件版本、服务端版本、ItemsAdder 状态和 GitHub 更新结果。

### 可选 ItemsAdder 配套

ItemsAdder 不是必需前置。安装随包附带的 IA 内容后，可显示自定义按钮、挂机币和 ActionBar 小图标；没有 ItemsAdder 时会自动使用原版按钮和纯文字 ActionBar。

菜单背景始终使用原生箱子，不再使用容易受客户端缩放影响的字体图片背景。

## 环境与依赖

| 项目 | 要求 |
|---|---|
| 服务端 | Paper |
| Java | Java 21 或更高 |
| 编译 API | Paper 1.21.1 API |
| 已完整启动测试 | Paper 1.21.1 build 133、Java 21 |
| 目标兼容范围 | Paper 1.21.1～26.2 |
| 必需前置 | 无 |
| 可选前置 | ItemsAdder、Vault + 经济插件、MythicMobs、MMOItems、Slimefun |
| 数据存储 | 本地 SQLite |

> 当前对 Paper 26.2 保持目标兼容，但仍建议在正式服使用的具体 Paper 构建上先进行测试。暂未声明支持 Spigot、Purpur 或 Folia。

## 安装方法

### 只使用原版界面与物品

1. 安装 Java 21 和兼容版本的 Paper。
2. 将 `IdlePool-1.0.0.jar` 放入服务端 `plugins` 目录。
3. 启动服务器。
4. 执行 `/afkpool doctor` 检查当前环境。

### 使用完整发布包和 ItemsAdder 图标

1. 解压 `IdlePool-1.0.0-release.zip`。
2. 将压缩包中的 `plugins/IdlePool-1.0.0.jar` 放入服务端 `plugins`。
3. 将 `ItemsAdder/contents/idlepool` 合并至 `plugins/ItemsAdder/contents/idlepool`。
4. 启动服务器并执行 `/iazip`。
5. 让测试账号重新接受服务器资源包。
6. 执行 `/afkpool doctor`。

从 `rc.1` 升级且继续使用 ItemsAdder 时，请删除旧的 `contents/idlepool/textures/font/gui` 目录，或者使用发布包内的 `install.ps1` 完成安装和清理。

## 五分钟创建第一个挂机池

1. 使用管理员账号执行 `/afkpool admin rewards`。
2. 编辑默认 `basic` 方案，或点击创建新的奖励方案。
3. 通过手持物品、物品 ID、命令或 Vault 金额添加奖励。
4. 点击具体奖励，设置概率、触发方式和时间参数。
5. 执行 `/afkpool admin`，点击“创建挂机池”并输入 ID。
6. 站到区域第一个角落设置“区域点 1”。
7. 站到另一个角落设置“区域点 2”。
8. 设置奖励周期、方案 ID、权限、最大人数和进度保留时间。
9. 点击状态按钮启用挂机池。
10. 使用普通玩家进入区域，点击开始按钮进行测试。

默认示例挂机池为停用状态，避免首次安装后意外覆盖主城区域。

## 玩家使用方法

1. 进入已启用的挂机池区域。
2. 在原生箱子 GUI 中查看说明和奖励，点击开始挂机。
3. 通过 ActionBar 查看本次时间、下次周期和本次收益。
4. 离开区域或执行 `/afkpool stop` 停止挂机。
5. 执行 `/afkpool claim` 领取实物奖励。

## 命令

| 命令 | 说明 | 使用者 |
|---|---|---|
| `/afkpool` | 在当前挂机池内打开开始界面 | 玩家 |
| `/afkpool info` | 显示插件、服务端、ItemsAdder 和更新状态 | 玩家/控制台 |
| `/afkpool stop` | 停止挂机并保存未满周期进度 | 玩家 |
| `/afkpool claim` | 打开实物奖励暂存箱 | 玩家 |
| `/afkpool admin` | 打开挂机池管理 GUI | 管理员 |
| `/afkpool admin rewards` | 打开奖励方案管理 GUI | 管理员 |
| `/afkpool reload` | 重载配置 | 管理员/控制台 |
| `/afkpool doctor` | 检查配置和依赖状态 | 管理员/控制台 |

别名：`/idlepool`

## 权限

| 权限 | 说明 | 默认 |
|---|---|---|
| `idlepool.use` | 使用默认挂机池 | 所有玩家 |
| `idlepool.admin` | 管理挂机池、奖励方案、重载和检查 | OP |

每个挂机池还可以设置单独的权限节点。

## 文件说明

```text
plugins/IdlePool/
├─ config.yml                 # 存储、ActionBar、消息与 IA 图标设置
├─ pools.yml                  # 挂机池区域与周期配置
├─ rewards.yml                # 奖励方案、概率和时间条件
├─ reward-snapshots.yml       # 无法识别物品的完整快照
└─ data.db                    # 玩家进度与奖励暂存箱
```

更新或迁移前建议备份整个 `plugins/IdlePool` 目录。

## 外部物品 ID 示例

```yaml
# 原版
provider: vanilla
id: DIAMOND

# ItemsAdder
provider: itemsadder
id: "idlepool:afk_coin"

# MythicMobs
provider: mythicmobs
id: "SKELETON_SWORD"

# MMOItems，类型:ID
provider: mmoitems
id: "SWORD:CUTLASS"

# Slimefun
provider: slimefun
id: "CARBONADO"
```

## 常见问题

### 不安装 ItemsAdder 能使用吗？

可以。GUI 背景本来就是原生箱子，按钮会使用原版物品，ActionBar 会使用纯文字。只有 IA 自定义按钮、图标和 IA 奖励物品不可用。

### 为什么货币奖励没有发放？

需要同时安装 Vault 和一个兼容的经济插件，然后执行 `/afkpool doctor` 检查经济服务状态。

### 为什么实物没有直接进入背包？

这是防丢设计。实物先进入暂存箱，请执行 `/afkpool claim` 领取。

### 里程碑概率失败后还会重试吗？

不会。`session-milestone` 在本次连续挂机跨过时间点时只判定一次；停止后开启新的挂机会话，才可以重新挑战。

### 未满普通周期的时间会消失吗？

默认保留 7 天，也可以给每个挂机池单独配置。超过保留时间后才会归零。

### 资源包更新后仍看到旧背景怎么办？

新版已经完全取消自定义容器背景。请删除 rc.1 的 `textures/font/gui`，重新执行 `/iazip`，并让客户端重新接受资源包。

## 当前限制

- 当前暂存箱一次展示 45 项，尚未加入翻页和全部领取。
- 当前仅提供 SQLite，本版本没有 MySQL/Redis。
- 当前抽取模式为独立概率，权重单抽、保底和奖励流水计划后续加入。
- Paper 26.2 仍需要在对应正式构建发布后持续验证。

## 隐私与网络

插件不包含遥测、统计上报、广告或授权联网验证。默认只会在启动时向 GitHub Raw 发起一次匿名 `version.txt` 请求用于检查更新，可在 `config.yml` 的 `update-check.enabled` 中关闭。玩家进度与暂存奖励保存在服务器本地 SQLite 数据库中。

## 更新日志：1.0.0

- 新增奖励连续挂机解锁时间。
- 新增单次挂机里程碑奖励。
- 管理 GUI 可编辑触发方式与时间参数。
- 玩家、管理员和暂存箱统一使用原生箱子背景。
- 保留可选 ItemsAdder 按钮与 ActionBar 图标。
- 已在 Paper 1.21.1 + Java 21 完成启动、命令、配置重载、Doctor 和安全停服测试。
- 新增 `/afkpool info` 与 GitHub `version.txt` 异步更新检查。

## 下载与反馈

- 下载：`[上传 MineBBS 附件后删除此占位文字]`
- 问题反馈：https://github.com/SenalMC/IdlePool/issues
- 源代码：https://github.com/SenalMC/IdlePool/
- 使用协议：Apache License 2.0。修改版可以闭源，但分发插件、修改版或随包资源时必须保留：`由 IdlePool 修改而来：https://github.com/SenalMC/IdlePool`

如果反馈问题，请尽量提供 Paper 版本、Java 版本、IdlePool 版本、相关配置和完整报错日志。
