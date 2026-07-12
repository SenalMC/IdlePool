package top.cnuo.idlepool.reward;

import top.cnuo.idlepool.util.DurationParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class RewardPlanRepository {
    private final JavaPlugin plugin;
    private volatile Map<String, RewardPlan> plans = Map.of();

    public RewardPlanRepository(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "rewards.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("reward-plans");
        if (root == null) {
            plans = Map.of();
            return;
        }

        Map<String, RewardPlan> loaded = new HashMap<>();
        for (String planId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(planId);
            if (section == null) {
                continue;
            }
            try {
                List<RewardDefinition> rewards = new ArrayList<>();
                for (Map<?, ?> raw : section.getMapList("rewards")) {
                    rewards.add(parse(raw));
                }
                loaded.put(planId.toLowerCase(Locale.ROOT), new RewardPlan(planId, List.copyOf(rewards)));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "无法加载奖励方案 " + planId, exception);
            }
        }
        plans = Map.copyOf(loaded);
        plugin.getLogger().info("已加载 " + loaded.size() + " 个奖励方案。");
    }

    public Optional<RewardPlan> find(String id) {
        return Optional.ofNullable(plans.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<RewardPlan> all() {
        return plans.values().stream().sorted(Comparator.comparing(RewardPlan::id)).toList();
    }

    private static RewardDefinition parse(Map<?, ?> raw) {
        RewardType type = RewardType.valueOf(string(raw, "type", "item").toUpperCase(Locale.ROOT));
        double chance = number(raw, "chance", 100.0).doubleValue();
        if (!Double.isFinite(chance) || chance < 0 || chance > 100) {
            throw new IllegalArgumentException("Reward chance must be between 0 and 100");
        }
        RewardTrigger trigger = RewardTrigger.parse(string(raw, "trigger", "cycle"));
        Duration unlockAfter = duration(raw.get("unlock-after"));
        return switch (type) {
            case ITEM -> new RewardDefinition(
                    type,
                    string(raw, "provider", "vanilla").toLowerCase(Locale.ROOT),
                    string(raw, "id", "STONE"),
                    Math.max(1, number(raw, "amount", 1).intValue()),
                    0,
                    "",
                    chance,
                    trigger,
                    unlockAfter
            );
            case MONEY -> new RewardDefinition(
                    type, "vault", "", 0,
                    Math.max(0, number(raw, "amount", 0).doubleValue()),
                    "", chance, trigger, unlockAfter
            );
            case COMMAND -> new RewardDefinition(
                    type, "command", "", 0, 0,
                    string(raw, "command", ""), chance, trigger, unlockAfter
            );
        };
    }

    private static Duration duration(Object value) {
        if (value == null) {
            return Duration.ZERO;
        }
        String input = String.valueOf(value).trim();
        if (input.equals("0") || input.equalsIgnoreCase("0s") || input.equalsIgnoreCase("none")) {
            return Duration.ZERO;
        }
        return DurationParser.parse(input);
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static Number number(Map<?, ?> map, String key, Number fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number;
        }
        return value == null ? fallback : Double.parseDouble(String.valueOf(value));
    }
}
