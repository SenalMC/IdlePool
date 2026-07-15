# 安装教程

1. 关闭 Paper 服务端。
2. 将 `IdlePool-1.2.0.jar` 放入 `plugins` 目录。
3. 根据需要安装可选依赖。
4. 启动服务器，等待 `plugins/IdlePool` 生成。
5. 执行：

```text
/afkpool info
/afkpool doctor
```

6. 确认控制台没有 SQLite、配置或奖励方案加载错误。

首次生成的 `spawn` 示例挂机池默认停用，不会直接影响主城。

## 发布包目录

正式发布 ZIP 包含：

```text
plugins/IdlePool-1.2.0.jar
ItemsAdder/contents/idlepool/...
docs/...
LICENSE
NOTICE
```

如果不使用 ItemsAdder，只需要安装插件 JAR。

## 安装 IA 配套资源

将发布包中的：

```text
ItemsAdder/contents/idlepool
```

复制到：

```text
plugins/ItemsAdder/contents/idlepool
```

执行 `/iazip`，并让客户端重新加载资源包。详细说明见 [ItemsAdder 集成](../integrations/itemsadder.md)。
