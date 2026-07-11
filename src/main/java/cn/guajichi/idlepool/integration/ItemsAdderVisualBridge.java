package cn.guajichi.idlepool.integration;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.ItemsAdder;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.logging.Level;

@SuppressWarnings("deprecation")
public final class ItemsAdderVisualBridge implements VisualBridge, Listener {
    private final JavaPlugin plugin;
    private volatile boolean loaded;

    public ItemsAdderVisualBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.loaded = ItemsAdder.areItemsLoaded();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onItemsAdderLoaded(ItemsAdderLoadDataEvent event) {
        loaded = true;
        plugin.getLogger().info("ItemsAdder 数据已加载，挂机池物品和 ActionBar 图标已就绪。");
    }

    @Override
    public boolean available() {
        return loaded;
    }

    @Override
    public String status() {
        return loaded ? "正常（资源已加载）" : "已安装，正在等待资源数据加载";
    }

    @Override
    public String fontImage(String namespacedId) {
        if (!loaded || namespacedId == null || namespacedId.isBlank()) {
            return "";
        }
        try {
            return new FontImageWrapper(namespacedId).getString();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "无法读取 ItemsAdder 字体图片 " + namespacedId, exception);
            return "";
        }
    }

    @Override
    public Optional<ItemStack> customItem(String namespacedId) {
        if (!loaded || namespacedId == null || namespacedId.isBlank()) {
            return Optional.empty();
        }
        try {
            CustomStack stack = CustomStack.getInstance(namespacedId);
            return stack == null ? Optional.empty() : Optional.of(stack.getItemStack());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "无法读取 ItemsAdder 物品 " + namespacedId, exception);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> identifyCustomItem(ItemStack itemStack) {
        if (!loaded || itemStack == null || itemStack.getType().isAir()) {
            return Optional.empty();
        }
        try {
            CustomStack stack = CustomStack.byItemStack(itemStack);
            return stack == null ? Optional.empty() : Optional.of(stack.getNamespacedID());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "无法识别 ItemsAdder 物品。", exception);
            return Optional.empty();
        }
    }
}
