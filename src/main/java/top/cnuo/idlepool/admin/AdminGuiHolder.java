package top.cnuo.idlepool.admin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class AdminGuiHolder implements InventoryHolder {
    public enum View {
        POOL_LIST,
        POOL_DETAIL
    }

    private final View view;
    private final String poolId;
    private Inventory inventory;

    public AdminGuiHolder(View view, String poolId) {
        this.view = view;
        this.poolId = poolId;
    }

    public View view() {
        return view;
    }

    public String poolId() {
        return poolId;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory);
    }
}
