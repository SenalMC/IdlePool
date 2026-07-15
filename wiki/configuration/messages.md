# 消息与语言

默认简体中文文件：

```text
plugins/IdlePool/message.yml
```

英文文件：

```text
plugins/IdlePool/en.yml
```

切换英文：

```yaml
language:
  file: en.yml
```

执行 `/afkpool reload` 生效。

## 格式

消息使用 MiniMessage，例如：

```yaml
session:
  started: "<gold>[IdlePool]</gold> <green>已开始在 <white>{pool}</white> 挂机。"
```

请保留 `{placeholder}` 占位符。玩家名称、活动名称等动态值会进行 MiniMessage 转义，防止注入标签。

## 缺失键回退

升级版本时插件不会覆盖管理员修改过的语言文件。若新版本新增键，读取时会使用 JAR 内置同语言文件作为默认值，并在控制台报告缺失数量。

语言文件名只允许字母、数字、点、下划线和连字符，并且必须以 `.yml` 结尾。
