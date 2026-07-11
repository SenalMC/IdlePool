package cn.guajichi.idlepool.pool;

import cn.guajichi.idlepool.util.DurationParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public final class PoolRepository {
    private final JavaPlugin plugin;
    private volatile List<PoolDefinition> pools = List.of();

    public PoolRepository(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(Duration defaultRetention) {
        File file = new File(plugin.getDataFolder(), "pools.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("pools");
        if (root == null) {
            pools = List.of();
            return;
        }

        List<PoolDefinition> loaded = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                loaded.add(parse(id, section, defaultRetention));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "无法加载挂机池 " + id, exception);
            }
        }
        pools = Collections.unmodifiableList(loaded);
        plugin.getLogger().info("已加载 " + loaded.size() + " 个挂机池。");
    }

    public List<PoolDefinition> all() {
        return pools;
    }

    public Optional<PoolDefinition> at(org.bukkit.Location location) {
        return pools.stream().filter(PoolDefinition::enabled).filter(pool -> pool.region().contains(location)).findFirst();
    }

    public Optional<PoolDefinition> byId(String id) {
        return pools.stream().filter(pool -> pool.id().equalsIgnoreCase(id)).findFirst();
    }

    private PoolDefinition parse(String id, ConfigurationSection section, Duration defaultRetention) {
        String world = require(section, "world");
        ConfigurationSection region = requireSection(section, "region");
        ConfigurationSection min = requireSection(region, "min");
        ConfigurationSection max = requireSection(region, "max");
        ConfigurationSection gui = requireSection(section, "gui");

        return new PoolDefinition(
                id,
                section.getBoolean("enabled", true),
                section.getString("display-name", id),
                new CuboidRegion(
                        world,
                        min.getInt("x"), min.getInt("y"), min.getInt("z"),
                        max.getInt("x"), max.getInt("y"), max.getInt("z")
                ),
                section.getString("permission", "idlepool.use"),
                Math.max(0, section.getInt("max-active-players", 0)),
                DurationParser.parse(section.getString("reward-cycle", "10m")),
                section.contains("progress-retention")
                        ? DurationParser.parse(section.getString("progress-retention", "7d"))
                        : defaultRetention,
                section.getString("reward-plan", "basic"),
                new PoolVisuals(
                        gui.getString("start-item", ""),
                        gui.getString("info-item", ""),
                        gui.getString("rewards-item", "")
                )
        );
    }

    private static String require(ConfigurationSection section, String path) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing value: " + section.getCurrentPath() + "." + path);
        }
        return value;
    }

    private static ConfigurationSection requireSection(ConfigurationSection section, String path) {
        ConfigurationSection value = section.getConfigurationSection(path);
        if (value == null) {
            throw new IllegalArgumentException("Missing section: " + section.getCurrentPath() + "." + path);
        }
        return value;
    }
}
