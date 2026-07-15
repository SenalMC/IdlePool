# ItemsAdder

ItemsAdder 是可选依赖。

## 安装内容包

将：

```text
itemsadder-pack/contents/idlepool
```

复制到：

```text
plugins/ItemsAdder/contents/idlepool
```

执行 `/iazip` 并重新加载客户端资源包。

## 默认资源 ID

| 用途 | ID |
|---|---|
| 开始按钮 | `idlepool:start_button` |
| 说明按钮 | `idlepool:info_button` |
| 奖励按钮 | `idlepool:rewards_button` |
| 时钟图标 | `idlepool:clock` |
| 礼物图标 | `idlepool:gift` |
| 货币图标 | `idlepool:coin` |
| 示例挂机币 | `idlepool:afk_coin` |

## 原生箱子背景

IdlePool 不使用字体图片模拟容器背景，因此不受 GUI 缩放、字体包和客户端模组造成的整体偏移影响。IA 只负责按钮、物品和 ActionBar 图标。

ItemsAdder 数据尚未完成加载时，插件会临时显示原版图标；收到加载事件后自动恢复 IA 资源。
