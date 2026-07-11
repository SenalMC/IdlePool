package cn.guajichi.idlepool.gui;

import cn.guajichi.idlepool.reward.ItemRewardFactory;
import cn.guajichi.idlepool.storage.InboxEntry;
import cn.guajichi.idlepool.storage.SqliteStore;
import cn.guajichi.idlepool.util.Messages;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InboxGuiManager implements Listener {
    private final JavaPlugin plugin;
    private final SqliteStore store;
    private final ItemRewardFactory items;
    private final Set<Long> claimsInFlight = ConcurrentHashMap.newKeySet();

    public InboxGuiManager(JavaPlugin plugin, SqliteStore store, ItemRewardFactory items) {
        this.plugin = plugin;
        this.store = store;
        this.items = items;
    }

    public void open(Player player) {
        player.sendMessage(Messages.parse("<gray>正在读取奖励暂存箱……"));
        store.listInbox(player.getUniqueId(), 45).whenComplete((entries, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendMessage(Messages.parse("<red>读取暂存箱失败，请查看后台日志。"));
                        return;
                    }
                    show(player, entries);
                })
        );
    }

    private void show(Player player, List<InboxEntry> entries) {
        Map<Integer, InboxEntry> bySlot = new HashMap<>();
        for (int slot = 0; slot < entries.size() && slot < 45; slot++) {
            bySlot.put(slot, entries.get(slot));
        }
        InboxGuiHolder holder = new InboxGuiHolder(bySlot);
        Inventory inventory = Bukkit.createInventory(holder, 54, Messages.parse("<gold>挂机奖励暂存箱"));
        holder.attach(inventory);

        bySlot.forEach((slot, entry) -> inventory.setItem(slot, preview(entry)));
        inventory.setItem(49, named(Material.CHEST, "<green>暂存奖励：" + entries.size() + " 项", List.of(
                "<gray>点击上方奖励领取。",
                "<gray>背包放不下的数量会继续保留。"
        )));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof InboxGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InboxEntry entry = holder.entry(event.getRawSlot());
        if (entry == null || !claimsInFlight.add(entry.id())) {
            return;
        }
        List<ItemStack> generated = items.create(entry.provider(), entry.itemId(), entry.amount()).orElse(null);
        if (generated == null) {
            claimsInFlight.remove(entry.id());
            player.sendMessage(Messages.parse("<red>当前无法生成该奖励：</red>" + entry.provider() + ":" + entry.itemId()));
            return;
        }

        player.closeInventory();
        int before = generated.stream().mapToInt(ItemStack::getAmount).sum();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(generated.toArray(ItemStack[]::new));
        int notInserted = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int inserted = before - notInserted;
        if (inserted <= 0) {
            claimsInFlight.remove(entry.id());
            player.sendMessage(Messages.parse("<red>背包空间不足，奖励仍保留在暂存箱。"));
            open(player);
            return;
        }

        int remaining = entry.amount() - inserted;
        store.updateInboxAmount(player.getUniqueId(), entry.id(), remaining).whenComplete((unused, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    claimsInFlight.remove(entry.id());
                    if (failure != null) {
                        player.sendMessage(Messages.parse("<red>更新暂存箱记录失败，请立即联系管理员。"));
                        return;
                    }
                    player.sendMessage(Messages.parse("<green>已领取奖励 ×" + inserted + "。"));
                    if (player.isOnline()) {
                        open(player);
                    }
                })
        );
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof InboxGuiHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack preview(InboxEntry entry) {
        ItemStack item = items.preview(entry.provider(), entry.itemId()).orElseGet(() -> new ItemStack(Material.BARRIER));
        item.setAmount(Math.min(item.getMaxStackSize(), Math.max(1, entry.amount())));
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Messages.parse("<gray>数量：<white>" + entry.amount()),
                Messages.parse("<gray>来源：<white>" + entry.provider()),
                Messages.parse("<gray>挂机池：<white>" + entry.poolId()),
                Messages.parse(""),
                Messages.parse("<yellow>点击领取")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse(name));
        meta.lore(lore.stream().map(Messages::parse).toList());
        item.setItemMeta(meta);
        return item;
    }
}
