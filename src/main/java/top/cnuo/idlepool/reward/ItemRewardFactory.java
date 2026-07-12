package top.cnuo.idlepool.reward;

import top.cnuo.idlepool.integration.VisualBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ItemRewardFactory {
    private final VisualBridge visuals;
    private final Logger logger;
    private final SnapshotRepository snapshots;

    public ItemRewardFactory(VisualBridge visuals, Logger logger, SnapshotRepository snapshots) {
        this.visuals = visuals;
        this.logger = logger;
        this.snapshots = snapshots;
    }

    public Optional<ItemStack> preview(String provider, String itemId) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "vanilla" -> {
                Material material = Material.matchMaterial(itemId);
                yield material == null || !material.isItem()
                        ? Optional.empty()
                        : Optional.of(new ItemStack(material));
            }
            case "itemsadder" -> visuals.customItem(itemId);
            case "snapshot" -> snapshots.find(itemId);
            case "mythicmobs" -> reflectiveItem("MythicMobs", () -> {
                Class<?> mythic = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Object instance = mythic.getMethod("inst").invoke(null);
                Object manager = mythic.getMethod("getItemManager").invoke(instance);
                return (ItemStack) manager.getClass().getMethod("getItemStack", String.class).invoke(manager, itemId);
            });
            case "mmoitems" -> reflectiveItem("MMOItems", () -> {
                String[] parts = itemId.split(":", 2);
                if (parts.length != 2) {
                    return null;
                }
                Class<?> mmoItems = Class.forName("net.Indyuce.mmoitems.MMOItems");
                Object instance = mmoItems.getField("plugin").get(null);
                Object typeManager = instance.getClass().getMethod("getTypes").invoke(instance);
                Object type = typeManager.getClass().getMethod("get", String.class).invoke(typeManager, parts[0]);
                if (type == null) {
                    return null;
                }
                for (java.lang.reflect.Method method : instance.getClass().getMethods()) {
                    if (method.getName().equals("getItem") && method.getParameterCount() == 2
                            && method.getParameterTypes()[1] == String.class) {
                        return (ItemStack) method.invoke(instance, type, parts[1]);
                    }
                }
                return null;
            });
            case "slimefun" -> reflectiveItem("Slimefun", () -> {
                Class<?> slimefunItem = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
                Object definition = slimefunItem.getMethod("getById", String.class).invoke(null, itemId);
                return definition == null ? null : (ItemStack) slimefunItem.getMethod("getItem").invoke(definition);
            });
            default -> Optional.empty();
        };
    }

    public Optional<List<ItemStack>> create(String provider, String itemId, int amount) {
        Optional<ItemStack> template = preview(provider, itemId);
        if (template.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        int maximum = Math.max(1, template.get().getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = template.get().clone();
            int current = Math.min(maximum, remaining);
            stack.setAmount(current);
            stacks.add(stack);
            remaining -= current;
        }
        return Optional.of(stacks);
    }

    private Optional<ItemStack> reflectiveItem(String pluginName, ItemSupplier supplier) {
        if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(supplier.get()).map(ItemStack::clone);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logger.log(Level.WARNING, "无法通过 " + pluginName + " 生成奖励物品。", exception);
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface ItemSupplier {
        ItemStack get() throws ReflectiveOperationException;
    }
}
