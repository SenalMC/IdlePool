# ItemsAdder 资源说明

IdlePool 已附带完整 ItemsAdder 内容包，默认命名空间为 `idlepool`。

## 安装

将项目中的：

```text
itemsadder-pack/contents/idlepool
```

复制到服务器：

```text
plugins/ItemsAdder/contents/idlepool
```

然后执行 `/iazip` 并让客户端重新加载资源包。正式发布 ZIP 已经按服务器目录结构放好，可以直接合并到服务端根目录。

若从 `1.0.0-rc.1` 更新，请删除旧的 `plugins/ItemsAdder/contents/idlepool/textures/font/gui` 目录，或使用配套 `install.ps1` 自动清理三个旧背景文件，再执行 `/iazip`。

## 默认资源 ID

| 用途 | ID |
|---|---|
| 开始按钮 | `idlepool:start_button` |
| 说明按钮 | `idlepool:info_button` |
| 奖励预览按钮 | `idlepool:rewards_button` |
| ActionBar 时钟 | `idlepool:clock` |
| ActionBar 礼物 | `idlepool:gift` |
| ActionBar 货币 | `idlepool:coin` |
| 示例挂机币 | `idlepool:afk_coin` |

完整配置位于 `itemsadder-pack/contents/idlepool/idlepool.yml`。

## 显示方式

- 所有菜单背景均为 Minecraft 原生箱子 GUI，不使用 ItemsAdder 字体图片。
- 自定义物品：32×32。
- ActionBar 图标：16×16，显示比例为 9px。

按钮通过 `CustomStack` 获取；ItemsAdder 尚未完成异步加载时插件会使用原版按钮和纯文字 ActionBar，收到 `ItemsAdderLoadDataEvent` 后自动恢复图标资源。原生箱子背景始终不变。
