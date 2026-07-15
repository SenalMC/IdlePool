# 自定义物品提供器

## 原版

```yaml
provider: vanilla
id: DIAMOND
```

## ItemsAdder

```yaml
provider: itemsadder
id: "idlepool:afk_coin"
```

## MythicMobs

```yaml
provider: mythicmobs
id: "SKELETON_SWORD"
```

## MMOItems

格式为 `类型:ID`：

```yaml
provider: mmoitems
id: "SWORD:CUTLASS"
```

## Slimefun

```yaml
provider: slimefun
id: "CARBONADO"
```

## 物品快照

如果管理员手持的自定义物品无法可靠反查模板，IdlePool 会把完整物品序列化到 `reward-snapshots.yml`：

```yaml
provider: snapshot
id: snapshot_xxxxx
```

不要手动编辑编码后的快照。删除奖励方案不会自动清理未引用快照，避免误删仍被其他配置使用的物品。
