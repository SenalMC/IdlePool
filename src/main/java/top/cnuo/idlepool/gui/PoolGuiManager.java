package top.cnuo.idlepool.gui;

import top.cnuo.idlepool.integration.VisualBridge;
import top.cnuo.idlepool.pool.PoolDefinition;
import top.cnuo.idlepool.session.SessionManager;
import top.cnuo.idlepool.util.Messages;
import net.kyori.adventure.text.Component;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class PoolGuiManager implements Listener {
    private static final int INFO_SLOT = 11;
    private static final int START_SLOT = 13;
    private static final int REWARDS_SLOT = 15;

    private final JavaPlugin plugin;
    private final VisualBridge visuals;
    private final SessionManager sessions;

    public PoolGuiManager(JavaPlugin plugin, VisualBridge visuals, SessionManager sessions) {
        this.plugin = plugin;
        this.visuals = visuals;
        this.sessions = sessions;
    }

    public void open(Player player, PoolDefinition pool) {
        Component title = Messages.parse(pool.displayName());
        PoolGuiHolder holder = new PoolGuiHolder(pool);
        Inventory inventory = Bukkit.createInventory(holder, 27, title);
        holder.attach(inventory);

        inventory.setItem(INFO_SLOT, item(
                pool.visuals().infoItem(), Material.BOOK,
                "gui.pool.info.name", "gui.pool.info.lore", Map.of()
        ));
        boolean active = sessions.isActive(player.getUniqueId());
        inventory.setItem(START_SLOT, item(
                pool.visuals().startItem(), active ? Material.RED_DYE : Material.LIME_DYE,
                active ? "gui.pool.stop.name" : "gui.pool.start.name",
                active ? "gui.pool.stop.lore" : "gui.pool.start.lore", Map.of()
        ));
        inventory.setItem(REWARDS_SLOT, item(
                pool.visuals().rewardsItem(), Material.CHEST,
                "gui.pool.rewards.name", "gui.pool.rewards.lore", Map.of("plan", pool.rewardPlan())
        ));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof PoolGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() != START_SLOT) {
            return;
        }
        if (sessions.isActive(player.getUniqueId())) {
            player.closeInventory();
            sessions.stop(player, false);
        } else {
            sessions.start(player, holder.pool());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PoolGuiHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack item(
            String customId,
            Material fallback,
            String nameKey,
            String loreKey,
            Map<String, String> placeholders
    ) {
        ItemStack item = visuals.customItem(customId).orElseGet(() -> new ItemStack(fallback));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.get(nameKey, placeholders));
        meta.lore(Messages.list(loreKey, placeholders));
        item.setItemMeta(meta);
        return item;
    }
}
