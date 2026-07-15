# Services API 与事件

IdlePool 通过 Bukkit `ServicesManager` 注册 `IdlePoolApi`。

当前未发布到 Maven 仓库，可把 IdlePool JAR 作为仅编译依赖：

```kotlin
dependencies {
    compileOnly(files("libs/IdlePool-1.2.0.jar"))
}
```

并在调用方的 `plugin.yml` 中声明：

```yaml
softdepend: [IdlePool]
```

## 获取 API

```kotlin
val registration = Bukkit.getServicesManager().getRegistration(IdlePoolApi::class.java)
val api = registration?.provider ?: return

if (api.isActive(player.uniqueId)) {
    val session = api.session(player.uniqueId).orElse(null)
    logger.info("${player.name} is AFK in ${session.poolId}")
}
```

API 提供：

- `isActive(UUID)`
- `session(UUID)`
- `activePlayers(poolId)`
- `activeEvents(poolId)`

`IdlePoolSessionView` 是只读快照，不允许外部插件直接修改内部会话。

## 事件

包名：

```text
top.cnuo.idlepool.api.event
```

| 事件 | 说明 |
|---|---|
| `IdlePoolStartEvent` | 会话真正开始前，可取消 |
| `IdlePoolStopEvent` | 会话结束，包含原因 |
| `IdlePoolCycleCompleteEvent` | 完成普通周期，包含保底状态 |
| `IdlePoolMilestoneEvent` | 跨过单次挂机里程碑 |
| `IdlePoolRewardPreGrantEvent` | 发奖前，可取消或修改数量倍率 |
| `IdlePoolRewardPostGrantEvent` | 发奖完成或进入暂存箱后 |
| `IdlePoolInboxClaimEvent` | 暂存奖励成功提交后 |

示例：

```kotlin
@EventHandler
fun onReward(event: IdlePoolRewardPreGrantEvent) {
    if (event.player.hasPermission("server.afk.vip")) {
        event.amountMultiplier *= 1.25
    }
}
```

数量倍率最终限制到 0～100。命令奖励不会因为倍率执行多次。
