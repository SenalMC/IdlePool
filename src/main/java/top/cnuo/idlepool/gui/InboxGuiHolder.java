package top.cnuo.idlepool.gui;

import top.cnuo.idlepool.storage.InboxEntry;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

public final class InboxGuiHolder implements InventoryHolder {
    private final Map<Integer, InboxEntry> entries;
    private Inventory inventory;

    public InboxGuiHolder(Map<Integer, InboxEntry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public InboxEntry entry(int slot) {
        return entries.get(slot);
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory);
    }
}
