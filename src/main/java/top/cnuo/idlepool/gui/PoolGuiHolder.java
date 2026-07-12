package top.cnuo.idlepool.gui;

import top.cnuo.idlepool.pool.PoolDefinition;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class PoolGuiHolder implements InventoryHolder {
    private final PoolDefinition pool;
    private Inventory inventory;

    public PoolGuiHolder(PoolDefinition pool) {
        this.pool = pool;
    }

    public PoolDefinition pool() {
        return pool;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Inventory has not been attached yet");
    }
}
