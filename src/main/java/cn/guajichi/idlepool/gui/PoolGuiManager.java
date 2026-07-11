package cn.guajichi.idlepool.gui;

import cn.guajichi.idlepool.integration.VisualBridge;
import cn.guajichi.idlepool.pool.PoolDefinition;
import cn.guajichi.idlepool.session.SessionManager;
import cn.guajichi.idlepool.util.Messages;
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

import java.util.List;

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
                "<gold>挂机池说明", List.of(
                        "<gray>点击中间按钮后开始累计挂机时间。",
                        "<gray>离开区域会自动结束并保存进度。"
                )
        ));
        boolean active = sessions.isActive(player.getUniqueId());
        inventory.setItem(START_SLOT, item(
                pool.visuals().startItem(), active ? Material.RED_DYE : Material.LIME_DYE,
                active ? "<red>结束挂机" : "<green>开始挂机",
                List.of(active ? "<gray>点击保存进度并结束挂机。" : "<gray>点击开始获取挂机奖励。")
        ));
        inventory.setItem(REWARDS_SLOT, item(
                pool.visuals().rewardsItem(), Material.CHEST,
                "<yellow>奖励预览", List.of("<gray>奖励方案：<white>" + pool.rewardPlan())
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

    private ItemStack item(String customId, Material fallback, String name, List<String> lore) {
        ItemStack item = visuals.customItem(customId).orElseGet(() -> new ItemStack(fallback));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse(name));
        meta.lore(lore.stream().map(Messages::parse).toList());
        item.setItemMeta(meta);
        return item;
    }
}
