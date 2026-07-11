package cn.guajichi.idlepool.admin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class RewardConfigEditor implements AutoCloseable {
    private final JavaPlugin plugin;
    private final File rewardsFile;
    private final File snapshotsFile;
    private final ExecutorService executor;

    public RewardConfigEditor(JavaPlugin plugin) {
        this.plugin = plugin;
        this.rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
        this.snapshotsFile = new File(plugin.getDataFolder(), "reward-snapshots.yml");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "IdlePool-RewardConfig");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<Void> createPlan(String planId) {
        return edit(yaml -> {
            String root = root(planId);
            if (yaml.contains(root)) {
                throw new IllegalArgumentException("奖励方案 ID 已存在");
            }
            yaml.set(root + ".selection-mode", "independent");
            yaml.set(root + ".rewards", new ArrayList<>());
        });
    }

    public CompletableFuture<Void> addItem(String planId, String provider, String itemId, int amount) {
        return edit(yaml -> {
            List<Map<String, Object>> rewards = rewards(yaml, planId);
            Map<String, Object> reward = base("item");
            reward.put("provider", provider);
            reward.put("id", itemId);
            reward.put("amount", Math.max(1, amount));
            rewards.add(reward);
            yaml.set(root(planId) + ".rewards", rewards);
        });
    }

    public CompletableFuture<Void> addSnapshot(
            String planId,
            String snapshotId,
            String encodedSnapshot,
            int amount
    ) {
        return CompletableFuture.runAsync(() -> {
            YamlConfiguration snapshots = YamlConfiguration.loadConfiguration(snapshotsFile);
            snapshots.set("snapshots." + snapshotId, encodedSnapshot);
            save(snapshots, snapshotsFile);

            YamlConfiguration rewardsYaml = YamlConfiguration.loadConfiguration(rewardsFile);
            List<Map<String, Object>> rewards = rewards(rewardsYaml, planId);
            Map<String, Object> reward = base("item");
            reward.put("provider", "snapshot");
            reward.put("id", snapshotId);
            reward.put("amount", Math.max(1, amount));
            rewards.add(reward);
            rewardsYaml.set(root(planId) + ".rewards", rewards);
            save(rewardsYaml, rewardsFile);
        }, executor);
    }

    public CompletableFuture<Void> addCommand(String planId, String command) {
        return edit(yaml -> {
            List<Map<String, Object>> rewards = rewards(yaml, planId);
            Map<String, Object> reward = base("command");
            reward.put("command", command);
            rewards.add(reward);
            yaml.set(root(planId) + ".rewards", rewards);
        });
    }

    public CompletableFuture<Void> addMoney(String planId, double amount) {
        return edit(yaml -> {
            List<Map<String, Object>> rewards = rewards(yaml, planId);
            Map<String, Object> reward = base("money");
            reward.put("amount", Math.max(0, amount));
            rewards.add(reward);
            yaml.set(root(planId) + ".rewards", rewards);
        });
    }

    public CompletableFuture<Void> update(String planId, int index, String key, Object value) {
        return edit(yaml -> {
            List<Map<String, Object>> rewards = rewards(yaml, planId);
            requireIndex(rewards, index);
            rewards.get(index).put(key, value);
            yaml.set(root(planId) + ".rewards", rewards);
        });
    }

    public CompletableFuture<Void> setTrigger(String planId, int index, String trigger) {
        return edit(yaml -> {
            List<Map<String, Object>> rewards = rewards(yaml, planId);
            requireIndex(rewards, index);
            Map<String, Object> reward = rewards.get(index);
            reward.put("trigger", trigger);
            if (trigger.equals("session-milestone") && isImmediate(reward.get("unlock-after"))) {
                reward.put("unlock-after", "30m");
            }
            yaml.set(root(planId) + ".rewards", rewards);
        });
    }

    public CompletableFuture<Void> remove(String planId, int index) {
        return edit(yaml -> {
            List<Map<String, Object>> rewards = rewards(yaml, planId);
            requireIndex(rewards, index);
            rewards.remove(index);
            yaml.set(root(planId) + ".rewards", rewards);
        });
    }

    private CompletableFuture<Void> edit(YamlEdit action) {
        return CompletableFuture.runAsync(() -> {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(rewardsFile);
            action.apply(yaml);
            save(yaml, rewardsFile);
        }, executor);
    }

    private static List<Map<String, Object>> rewards(YamlConfiguration yaml, String planId) {
        String root = root(planId);
        if (!yaml.contains(root)) {
            throw new IllegalArgumentException("奖励方案不存在：" + planId);
        }
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList(root + ".rewards")) {
            Map<String, Object> reward = new LinkedHashMap<>();
            raw.forEach((key, value) -> reward.put(String.valueOf(key), value));
            copy.add(reward);
        }
        return copy;
    }

    private static Map<String, Object> base(String type) {
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("type", type);
        reward.put("chance", 100.0);
        reward.put("trigger", "cycle");
        reward.put("unlock-after", "0s");
        return reward;
    }

    private static boolean isImmediate(Object value) {
        if (value == null) {
            return true;
        }
        String text = String.valueOf(value).trim();
        return text.equals("0") || text.equalsIgnoreCase("0s") || text.equalsIgnoreCase("none");
    }

    private static String root(String planId) {
        return "reward-plans." + planId;
    }

    private static void requireIndex(List<?> rewards, int index) {
        if (index < 0 || index >= rewards.size()) {
            throw new IllegalArgumentException("奖励索引已经失效");
        }
    }

    private void save(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "无法保存 " + file.getName(), exception);
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("奖励配置保存线程未能在 5 秒内结束。");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface YamlEdit {
        void apply(YamlConfiguration yaml);
    }
}
