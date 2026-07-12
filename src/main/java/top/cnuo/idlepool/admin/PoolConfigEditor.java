package top.cnuo.idlepool.admin;

import top.cnuo.idlepool.util.Messages;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.concurrent.TimeUnit;

public final class PoolConfigEditor implements AutoCloseable {
    private final JavaPlugin plugin;
    private final File poolsFile;
    private final ExecutorService executor;

    public PoolConfigEditor(JavaPlugin plugin) {
        this.plugin = plugin;
        this.poolsFile = new File(plugin.getDataFolder(), "pools.yml");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "IdlePool-PoolConfig");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<Void> set(String poolId, String relativePath, Object value) {
        return CompletableFuture.runAsync(() -> {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(poolsFile);
            yaml.set("pools." + poolId + "." + relativePath, value);
            save(yaml);
        }, executor);
    }

    public CompletableFuture<Void> setPosition(String poolId, String corner, Location location) {
        String root = "pools." + poolId;
        return CompletableFuture.runAsync(() -> {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(poolsFile);
            yaml.set(root + ".world", location.getWorld().getName());
            yaml.set(root + ".region." + corner + ".x", location.getBlockX());
            yaml.set(root + ".region." + corner + ".y", location.getBlockY());
            yaml.set(root + ".region." + corner + ".z", location.getBlockZ());
            save(yaml);
        }, executor);
    }

    public CompletableFuture<Void> createPool(String poolId, Location location) {
        return CompletableFuture.runAsync(() -> {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(poolsFile);
            String root = "pools." + poolId;
            if (yaml.contains(root)) {
                throw new IllegalArgumentException(Messages.raw("admin.error.pool-exists"));
            }
            yaml.set(root + ".enabled", false);
            yaml.set(root + ".display-name", "<gold>" + poolId);
            yaml.set(root + ".world", location.getWorld().getName());
            for (String corner : new String[]{"min", "max"}) {
                yaml.set(root + ".region." + corner + ".x", location.getBlockX());
                yaml.set(root + ".region." + corner + ".y", location.getBlockY());
                yaml.set(root + ".region." + corner + ".z", location.getBlockZ());
            }
            yaml.set(root + ".permission", "idlepool.use");
            yaml.set(root + ".max-active-players", 0);
            yaml.set(root + ".reward-cycle", "10m");
            yaml.set(root + ".progress-retention", "7d");
            yaml.set(root + ".reward-plan", "basic");
            yaml.set(root + ".gui.start-item", "idlepool:start_button");
            yaml.set(root + ".gui.info-item", "idlepool:info_button");
            yaml.set(root + ".gui.rewards-item", "idlepool:rewards_button");
            save(yaml);
        }, executor);
    }

    private void save(YamlConfiguration yaml) {
        try {
            yaml.save(poolsFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "无法保存 pools.yml", exception);
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("挂机池配置保存线程未能在 5 秒内结束。");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
