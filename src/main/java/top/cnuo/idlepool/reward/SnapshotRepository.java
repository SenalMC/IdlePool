package top.cnuo.idlepool.reward;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class SnapshotRepository {
    private final JavaPlugin plugin;
    private final File file;
    private volatile Map<String, ItemStack> snapshots = Map.of();

    public SnapshotRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reward-snapshots.yml");
    }

    public void reload() {
        if (!file.exists()) {
            snapshots = Map.of();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("snapshots");
        if (root == null) {
            snapshots = Map.of();
            return;
        }
        Map<String, ItemStack> loaded = new HashMap<>();
        for (String id : root.getKeys(false)) {
            String encoded = root.getString(id);
            if (encoded == null || encoded.isBlank()) {
                continue;
            }
            try {
                loaded.put(id, ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "无法读取物品快照 " + id, exception);
            }
        }
        snapshots = Map.copyOf(loaded);
    }

    public Optional<ItemStack> find(String id) {
        ItemStack item = snapshots.get(id);
        return item == null ? Optional.empty() : Optional.of(item.clone());
    }

    public static String encode(ItemStack itemStack) {
        return Base64.getEncoder().encodeToString(itemStack.serializeAsBytes());
    }
}
