# 运行要求

## 必需环境

| 项目 | 要求 |
|---|---|
| 服务端 | Paper 1.21.1～26.2 |
| Java | Java 21 或更高版本 |
| 数据库 | 内置 SQLite，无需单独安装 |
| 客户端模组 | 不需要 |

IdlePool 使用 `api-version: 1.21`，并以 Paper 1.21.1 API 编译。所有磁盘数据默认保存在 `plugins/IdlePool`。

## 可选依赖

| 插件 | 用途 |
|---|---|
| ItemsAdder | 自定义按钮、ActionBar 图标与 IA 物品奖励 |
| Vault + 经济实现 | 货币奖励 |
| MythicMobs | MythicMobs 模板物品 |
| MMOItems | MMOItems 模板物品 |
| Slimefun | Slimefun 物品 |
| PlaceholderAPI | 统计、状态和排行榜变量 |

不使用某种奖励时，无需安装对应依赖。缺少可选依赖不会阻止 IdlePool 启动，`/afkpool doctor` 会指出配置中无法使用的奖励。

## 不需要 ItemsAdder 也能运行

所有界面都使用 Minecraft 原生箱子背景。ItemsAdder 仅负责可选按钮和小图标；未安装或资源尚未加载时会自动回退到原版物品和纯文本。
