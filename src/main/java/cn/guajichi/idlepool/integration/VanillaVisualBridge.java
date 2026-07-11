package cn.guajichi.idlepool.integration;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class VanillaVisualBridge implements VisualBridge {
    private final String status;

    public VanillaVisualBridge(String status) {
        this.status = status;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public String fontImage(String namespacedId) {
        return "";
    }

    @Override
    public Optional<ItemStack> customItem(String namespacedId) {
        return Optional.empty();
    }

    @Override
    public Optional<String> identifyCustomItem(ItemStack itemStack) {
        return Optional.empty();
    }
}
