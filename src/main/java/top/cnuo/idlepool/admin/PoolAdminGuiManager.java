package top.cnuo.idlepool.admin;

import top.cnuo.idlepool.IdlePoolPlugin;
import top.cnuo.idlepool.pool.PoolDefinition;
import top.cnuo.idlepool.pool.PoolRepository;
import top.cnuo.idlepool.util.DurationParser;
import top.cnuo.idlepool.util.Messages;
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
        Inventory inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.pool.list.title"));
        holder.attach(inventory);
        int index = 0;
        for (PoolDefinition pool : pools.all()) {
            if (index >= POOL_SLOTS.length) {
                break;
            }
            inventory.setItem(POOL_SLOTS[index++], item(
                    pool.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                    Messages.parse(pool.displayName()),
                    Messages.list("admin.pool.list.entry-lore", Map.of(
                            "id", pool.id(),
                            "status", Messages.raw(pool.enabled() ? "common.enabled" : "common.disabled"),
                            "cycle", pool.rewardCycle().toString()
                    ))
            ));
        }
        inventory.setItem(49, item(Material.RECOVERY_COMPASS, "admin.pool.list.reload", Map.of()));
        inventory.setItem(45, item(Material.ANVIL, "admin.pool.list.create", Map.of()));
        inventory.setItem(47, item(Material.EMERALD, "admin.pool.list.rewards", Map.of()));
        player.openInventory(inventory);
    }

    public void openDetail(Player player, PoolDefinition pool) {
        AdminGuiHolder holder = new AdminGuiHolder(AdminGuiHolder.View.POOL_DETAIL, pool.id());
        Inventory inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.pool.detail.title", Map.of("id", pool.id())));
        holder.attach(inventory);

        inventory.setItem(10, item(pool.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                pool.enabled() ? "admin.pool.detail.enabled" : "admin.pool.detail.disabled", Map.of()));
        inventory.setItem(12, item(Material.REDSTONE_TORCH, "admin.pool.detail.point-one", Map.of()));
        inventory.setItem(14, item(Material.SOUL_TORCH, "admin.pool.detail.point-two", Map.of()));
        inventory.setItem(16, item(Material.CLOCK, "admin.pool.detail.cycle", Map.of("value", pool.rewardCycle().toString())));
        inventory.setItem(20, item(Material.NAME_TAG, "admin.pool.detail.permission", Map.of("value", pool.permission())));
        inventory.setItem(22, item(Material.CHEST, "admin.pool.detail.plan", Map.of("value", pool.rewardPlan())));
        inventory.setItem(24, item(Material.PLAYER_HEAD, "admin.pool.detail.maximum", Map.of("value", Integer.toString(pool.maxActivePlayers()))));
        inventory.setItem(30, item(Material.REPEATER, "admin.pool.detail.retention", Map.of("value", pool.progressRetention().toString())));
        inventory.setItem(49, item(Material.ARROW, "admin.pool.detail.back", Map.of()));
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
            requestInput(player, "", "", InputKind.CREATE_POOL, "admin.pool.prompt.create");
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
            case 16 -> requestInput(player, poolId, "reward-cycle", InputKind.DURATION, "admin.pool.prompt.cycle");
            case 20 -> requestInput(player, poolId, "permission", InputKind.TEXT, "admin.pool.prompt.permission");
            case 22 -> requestInput(player, poolId, "reward-plan", InputKind.TEXT, "admin.pool.prompt.plan");
            case 24 -> requestInput(player, poolId, "max-active-players", InputKind.NON_NEGATIVE_INTEGER, "admin.pool.prompt.maximum");
            case 30 -> requestInput(player, poolId, "progress-retention", InputKind.DURATION, "admin.pool.prompt.retention");
            case 49 -> openList(player);
            default -> {
            }
        }
    }

    private void requestInput(Player player, String poolId, String path, InputKind kind, String promptKey) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(poolId, path, kind));
        player.closeInventory();
        Messages.send(player, "admin.input.prompt", Map.of("prompt", Messages.raw(promptKey)));
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
                        throw new IllegalArgumentException(Messages.raw("admin.error.invalid-text"));
                    }
                    yield input;
                }
                case DURATION -> {
                    try {
                        DurationParser.parse(input);
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException(Messages.raw("admin.error.invalid-duration"));
                    }
                    yield input;
                }
                case NON_NEGATIVE_INTEGER -> {
                    int parsed = Integer.parseInt(input);
                    if (parsed < 0) {
                        throw new IllegalArgumentException(Messages.raw("admin.error.negative-number"));
                    }
                    yield parsed;
                }
                case CREATE_POOL -> {
                    if (!input.matches("[a-z0-9_-]{1,32}") || pools.byId(input).isPresent()) {
                        throw new IllegalArgumentException(Messages.raw("admin.error.pool-id"));
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
            Messages.send(player, "admin.input.invalid", Map.of("error", exception.getMessage()));
            requestInput(player, pending.poolId(), pending.path(), pending.kind(), "admin.input.retry");
        }
    }

    private void editAndReopen(Player player, String poolId, java.util.concurrent.CompletableFuture<Void> edit) {
        player.closeInventory();
        Messages.send(player, "admin.pool.saving");
        edit.whenComplete((unused, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                Messages.send(player, "admin.save-failed", Map.of("error", String.valueOf(cause.getMessage())));
                return;
            }
            plugin.reloadIdlePool();
            pools.byId(poolId).ifPresent(pool -> openDetail(player, pool));
        }));
    }

    private static ItemStack item(Material material, String key, Map<String, String> placeholders) {
        return item(material, Messages.get(key + ".name", placeholders), Messages.list(key + ".lore", placeholders));
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
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
