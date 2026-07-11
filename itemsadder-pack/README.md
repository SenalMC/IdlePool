# IdlePool ItemsAdder 内容包

将 `contents/idlepool` 整个目录复制到服务器：

```text
plugins/ItemsAdder/contents/idlepool
```

随后执行：

```text
/iazip
```

从 `rc.1` 更新时，推荐运行随包提供的 `install.ps1`，它会移除三个已废弃的 GUI 背景文件；手动安装则可删除旧目录 `textures/font/gui`。新版配置不再引用这些文件。

内容包使用经典 `resource` 物品配置，以便同时覆盖 Minecraft 1.21.1 客户端和新版客户端。请不要把资源 ID、目录或 PNG 文件改成包含大写字母、空格或特殊字符的名字。

挂机开始界面、管理员界面和奖励暂存箱均使用 Minecraft 原生箱子 GUI，不再通过字体图片覆盖容器背景，因此不会受到客户端 GUI 缩放、字体或模组导致的背景偏移影响。

ItemsAdder 内容包只负责按钮物品、示例挂机币和 ActionBar 小图标；不安装本内容包时，插件会自动使用原版物品和纯文字 ActionBar。

## 资源清单

- `textures/font/icons/clock.png`
- `textures/font/icons/gift.png`
- `textures/font/icons/coin.png`
- `textures/item/start_button.png`
- `textures/item/info_button.png`
- `textures/item/rewards_button.png`
- `textures/item/afk_coin.png`
