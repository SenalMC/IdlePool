package cn.guajichi.idlepool.reward;

import cn.guajichi.idlepool.integration.VisualBridge;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HeldItemIdentifier {
    private final VisualBridge visuals;
    private final Logger logger;

    public HeldItemIdentifier(VisualBridge visuals, Logger logger) {
        this.visuals = visuals;
        this.logger = logger;
    }

    public Optional<IdentifiedItem> identify(ItemStack original) {
        if (original == null || original.getType().isAir() || original.getAmount() <= 0) {
            return Optional.empty();
        }

        int amount = original.getAmount();
        Optional<String> itemsAdder = visuals.identifyCustomItem(original);
        if (itemsAdder.isPresent()) {
            return Optional.of(new IdentifiedItem("itemsadder", itemsAdder.get(), amount, null));
        }

        Optional<String> mmoItems = identifyMmoItems(original);
        if (mmoItems.isPresent()) {
            return Optional.of(new IdentifiedItem("mmoitems", mmoItems.get(), amount, null));
        }

        Optional<String> slimefun = identifySlimefun(original);
        if (slimefun.isPresent()) {
            return Optional.of(new IdentifiedItem("slimefun", slimefun.get(), amount, null));
        }

        if (!original.hasItemMeta()) {
            return Optional.of(new IdentifiedItem("vanilla", original.getType().name(), amount, null));
        }

        ItemStack snapshot = original.clone();
        snapshot.setAmount(1);
        return Optional.of(new IdentifiedItem("snapshot", "", amount, snapshot));
    }

    private Optional<String> identifyMmoItems(ItemStack itemStack) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return Optional.empty();
        }
        try {
            Class<?> api = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Method typeMethod = api.getMethod("getTypeName", ItemStack.class);
            Method idMethod = api.getMethod("getID", ItemStack.class);
            String type = (String) typeMethod.invoke(null, itemStack);
            String id = (String) idMethod.invoke(null, itemStack);
            return type == null || type.isBlank() || id == null || id.isBlank()
                    ? Optional.empty()
                    : Optional.of(type + ":" + id);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logger.log(Level.FINE, "无法反查 MMOItems 物品。", exception);
            return Optional.empty();
        }
    }

    private Optional<String> identifySlimefun(ItemStack itemStack) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
            return Optional.empty();
        }
        try {
            Class<?> api = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
            Object item = api.getMethod("getByItem", ItemStack.class).invoke(null, itemStack);
            return item == null ? Optional.empty() : Optional.of((String) api.getMethod("getId").invoke(item));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logger.log(Level.FINE, "无法反查 Slimefun 物品。", exception);
            return Optional.empty();
        }
    }
}
