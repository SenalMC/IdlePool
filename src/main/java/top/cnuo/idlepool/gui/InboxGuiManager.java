package top.cnuo.idlepool.gui;

import top.cnuo.idlepool.reward.ItemRewardFactory;
import top.cnuo.idlepool.storage.InboxEntry;
import top.cnuo.idlepool.storage.SqliteStore;
import top.cnuo.idlepool.util.Messages;
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
        Messages.send(player, "inbox.loading");
        store.listInbox(player.getUniqueId(), 45).whenComplete((entries, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        Messages.send(player, "inbox.load-failed");
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
        Inventory inventory = Bukkit.createInventory(holder, 54, Messages.get("inbox.title"));
        holder.attach(inventory);

        bySlot.forEach((slot, entry) -> inventory.setItem(slot, preview(entry)));
        inventory.setItem(49, named(Material.CHEST, "inbox.status.name", "inbox.status.lore", Map.of(
                "count", Integer.toString(entries.size())
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
            Messages.send(player, "inbox.generate-failed", Map.of(
                    "provider", entry.provider(), "item", entry.itemId()
            ));
            return;
        }

        player.closeInventory();
        int before = generated.stream().mapToInt(ItemStack::getAmount).sum();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(generated.toArray(ItemStack[]::new));
        int notInserted = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int inserted = before - notInserted;
        if (inserted <= 0) {
            claimsInFlight.remove(entry.id());
            Messages.send(player, "inbox.inventory-full");
            open(player);
            return;
        }

        int remaining = entry.amount() - inserted;
        store.updateInboxAmount(player.getUniqueId(), entry.id(), remaining).whenComplete((unused, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    claimsInFlight.remove(entry.id());
                    if (failure != null) {
                        Messages.send(player, "inbox.update-failed");
                        return;
                    }
                    Messages.send(player, "inbox.claimed", Map.of("amount", Integer.toString(inserted)));
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
        meta.lore(Messages.list("inbox.item.lore", Map.of(
                "amount", Integer.toString(entry.amount()),
                "provider", entry.provider(),
                "pool", entry.poolId()
        )));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack named(
            Material material,
            String nameKey,
            String loreKey,
            Map<String, String> placeholders
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.get(nameKey, placeholders));
        meta.lore(Messages.list(loreKey, placeholders));
        item.setItemMeta(meta);
        return item;
    }
}
