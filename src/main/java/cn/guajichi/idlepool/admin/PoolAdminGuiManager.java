package cn.guajichi.idlepool.admin;

import cn.guajichi.idlepool.IdlePoolPlugin;
import cn.guajichi.idlepool.pool.PoolDefinition;
import cn.guajichi.idlepool.pool.PoolRepository;
import cn.guajichi.idlepool.util.DurationParser;
import cn.guajichi.idlepool.util.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class PoolAdminGuiManager implements Listener, AutoCloseable {
    private static final int[] POOL_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final IdlePoolPlugin plugin;
    private final PoolRepository pools;
    private final PoolConfigEditor editor;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public PoolAdminGuiManager(IdlePoolPlugin plugin, PoolRepository pools) {
        this.plugin = plugin;
        this.pools = pools;
        this.editor = new PoolConfigEditor(plugin);
    }

    public void openList(Player player) {
        AdminGuiHolder holder = new AdminGuiHolder(AdminGuiHolder.View.POOL_LIST, "");
        Inventory inventory = Bukkit.createInventory(holder, 54, title("<gold>挂机池管理"));
        holder.attach(inventory);
        int index = 0;
        for (PoolDefinition pool : pools.all()) {
            if (index >= POOL_SLOTS.length) {
                break;
            }
            inventory.setItem(POOL_SLOTS[index++], item(
                    pool.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                    pool.displayName(),
                    List.of(
                            "<gray>ID：<white>" + pool.id(),
                            "<gray>状态：" + (pool.enabled() ? "<green>启用" : "<red>停用"),
                            "<gray>周期：<white>" + pool.rewardCycle(),
                            "",
                            "<yellow>点击编辑"
                    )
            ));
        }
        inventory.setItem(49, item(Material.RECOVERY_COMPASS, "<green>重载配置", List.of("<gray>重新读取所有配置文件。")));
        inventory.setItem(45, item(Material.ANVIL, "<green>创建挂机池", List.of("<gray>以当前位置创建一个新的挂机池。")));
        inventory.setItem(47, item(Material.EMERALD, "<green>奖励方案管理", List.of("<gray>创建和编辑物品、货币及命令奖励。")));
        player.openInventory(inventory);
    }

    public void openDetail(Player player, PoolDefinition pool) {
        AdminGuiHolder holder = new AdminGuiHolder(AdminGuiHolder.View.POOL_DETAIL, pool.id());
        Inventory inventory = Bukkit.createInventory(holder, 54, title("<gold>编辑：" + pool.id()));
        holder.attach(inventory);

        inventory.setItem(10, item(pool.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                pool.enabled() ? "<green>已启用" : "<red>已停用", List.of("<yellow>点击切换状态")));
        inventory.setItem(12, item(Material.REDSTONE_TORCH, "<red>设置区域点 1", List.of("<gray>设置为你当前站立的方块。")));
        inventory.setItem(14, item(Material.SOUL_TORCH, "<aqua>设置区域点 2", List.of("<gray>设置为你当前站立的方块。")));
        inventory.setItem(16, item(Material.CLOCK, "<yellow>奖励周期", List.of(
                "<gray>当前：<white>" + pool.rewardCycle(), "<yellow>点击后在聊天输入，例如 10m"
        )));
        inventory.setItem(20, item(Material.NAME_TAG, "<yellow>使用权限", List.of(
                "<gray>当前：<white>" + pool.permission(), "<yellow>点击修改"
        )));
        inventory.setItem(22, item(Material.CHEST, "<yellow>奖励方案", List.of(
                "<gray>当前：<white>" + pool.rewardPlan(), "<yellow>点击修改"
        )));
        inventory.setItem(24, item(Material.PLAYER_HEAD, "<yellow>最大挂机人数", List.of(
                "<gray>当前：<white>" + pool.maxActivePlayers(), "<yellow>点击修改，0 表示不限"
        )));
        inventory.setItem(30, item(Material.REPEATER, "<yellow>进度保留时间", List.of(
                "<gray>当前：<white>" + pool.progressRetention(), "<yellow>点击后输入，例如 7d"
        )));
        inventory.setItem(49, item(Material.ARROW, "<yellow>返回", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof AdminGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("idlepool.admin")) {
            return;
        }

        if (holder.view() == AdminGuiHolder.View.POOL_LIST) {
            handleListClick(player, event.getRawSlot());
        } else {
            handleDetailClick(player, holder.poolId(), event.getRawSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof AdminGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        PendingInput pending = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyInput(event.getPlayer(), pending, input));
    }

    private void handleListClick(Player player, int rawSlot) {
        if (rawSlot == 47) {
            player.performCommand("afkpool admin rewards");
            return;
        }
        if (rawSlot == 45) {
            requestInput(player, "", "", InputKind.CREATE_POOL, "请输入新挂机池 ID，只能使用小写字母、数字、_ 和 -");
            return;
        }
        if (rawSlot == 49) {
            plugin.reloadIdlePool();
            openList(player);
            return;
        }
        for (int index = 0; index < POOL_SLOTS.length; index++) {
            if (POOL_SLOTS[index] == rawSlot && index < pools.all().size()) {
                openDetail(player, pools.all().get(index));
                return;
            }
        }
    }

    private void handleDetailClick(Player player, String poolId, int rawSlot) {
        PoolDefinition pool = pools.byId(poolId).orElse(null);
        if (pool == null) {
            openList(player);
            return;
        }
        switch (rawSlot) {
            case 10 -> editAndReopen(player, poolId, editor.set(poolId, "enabled", !pool.enabled()));
            case 12 -> editAndReopen(player, poolId, editor.setPosition(poolId, "min", player.getLocation()));
            case 14 -> editAndReopen(player, poolId, editor.setPosition(poolId, "max", player.getLocation()));
            case 16 -> requestInput(player, poolId, "reward-cycle", InputKind.DURATION, "请输入奖励周期，例如 <white>10m");
            case 20 -> requestInput(player, poolId, "permission", InputKind.TEXT, "请输入权限节点");
            case 22 -> requestInput(player, poolId, "reward-plan", InputKind.TEXT, "请输入奖励方案 ID");
            case 24 -> requestInput(player, poolId, "max-active-players", InputKind.NON_NEGATIVE_INTEGER, "请输入最大人数，<white>0</white> 表示不限");
            case 30 -> requestInput(player, poolId, "progress-retention", InputKind.DURATION, "请输入进度保留时间，例如 <white>7d");
            case 49 -> openList(player);
            default -> {
            }
        }
    }

    private void requestInput(Player player, String poolId, String path, InputKind kind, String prompt) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(poolId, path, kind));
        player.closeInventory();
        player.sendMessage(Messages.parse("<gold>[挂机池]</gold> " + prompt + "；输入 <red>cancel</red> 取消。"));
    }

    private void applyInput(Player player, PendingInput pending, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            if (pending.kind() == InputKind.CREATE_POOL) {
                openList(player);
            } else {
                pools.byId(pending.poolId()).ifPresent(pool -> openDetail(player, pool));
            }
            return;
        }
        try {
            Object value = switch (pending.kind()) {
                case TEXT -> {
                    if (input.isBlank() || input.length() > 128) {
                        throw new IllegalArgumentException("文本长度不合法");
                    }
                    yield input;
                }
                case DURATION -> {
                    DurationParser.parse(input);
                    yield input;
                }
                case NON_NEGATIVE_INTEGER -> {
                    int parsed = Integer.parseInt(input);
                    if (parsed < 0) {
                        throw new IllegalArgumentException("数字不能小于 0");
                    }
                    yield parsed;
                }
                case CREATE_POOL -> {
                    if (!input.matches("[a-z0-9_-]{1,32}") || pools.byId(input).isPresent()) {
                        throw new IllegalArgumentException("ID 格式不正确或已经存在");
                    }
                    yield input;
                }
            };
            if (pending.kind() == InputKind.CREATE_POOL) {
                String poolId = String.valueOf(value);
                editAndReopen(player, poolId, editor.createPool(poolId, player.getLocation()));
                return;
            }
            editAndReopen(player, pending.poolId(), editor.set(pending.poolId(), pending.path(), value));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Messages.parse("<red>输入无效：</red>" + exception.getMessage()));
            requestInput(player, pending.poolId(), pending.path(), pending.kind(), "请重新输入");
        }
    }

    private void editAndReopen(Player player, String poolId, java.util.concurrent.CompletableFuture<Void> edit) {
        player.closeInventory();
        player.sendMessage(Messages.parse("<gray>正在保存配置……"));
        edit.whenComplete((unused, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                player.sendMessage(Messages.parse("<red>保存失败：</red>" + cause.getMessage()));
                return;
            }
            plugin.reloadIdlePool();
            pools.byId(poolId).ifPresent(pool -> openDetail(player, pool));
        }));
    }

    private Component title(String name) {
        return Messages.parse(name);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse(name));
        meta.lore(lore.stream().map(Messages::parse).toList());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void close() {
        pendingInputs.clear();
        editor.close();
    }

    private record PendingInput(String poolId, String path, InputKind kind) {
    }

    private enum InputKind {
        TEXT,
        DURATION,
        NON_NEGATIVE_INTEGER,
        CREATE_POOL
    }
}
