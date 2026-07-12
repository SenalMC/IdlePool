package top.cnuo.idlepool.integration;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public interface VisualBridge {
    boolean available();

    String status();

    String fontImage(String namespacedId);

    Optional<ItemStack> customItem(String namespacedId);

    Optional<String> identifyCustomItem(ItemStack itemStack);
}
