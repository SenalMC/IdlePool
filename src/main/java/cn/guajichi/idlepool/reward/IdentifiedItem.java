package cn.guajichi.idlepool.reward;

import org.bukkit.inventory.ItemStack;

public record IdentifiedItem(
        String provider,
        String itemId,
        int amount,
        ItemStack snapshot
) {
    public boolean requiresSnapshot() {
        return snapshot != null;
    }
}
