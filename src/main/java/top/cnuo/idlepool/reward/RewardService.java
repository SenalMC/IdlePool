package top.cnuo.idlepool.reward;

import top.cnuo.idlepool.integration.EconomyBridge;
import top.cnuo.idlepool.pool.PoolDefinition;
import top.cnuo.idlepool.storage.SqliteStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class RewardService {
    private final JavaPlugin plugin;
    private final RewardPlanRepository plans;
    private final SqliteStore store;
    private final EconomyBridge economy;

    public RewardService(JavaPlugin plugin, RewardPlanRepository plans, SqliteStore store, EconomyBridge economy) {
        this.plugin = plugin;
        this.plans = plans;
        this.store = store;
        this.economy = economy;
    }

    public int grantCycle(Player player, PoolDefinition pool, long sessionSeconds) {
        RewardPlan plan = plans.find(pool.rewardPlan()).orElse(null);
        if (plan == null) {
            plugin.getLogger().warning("挂机池 " + pool.id() + " 引用了不存在的奖励方案 " + pool.rewardPlan());
            return 0;
        }

        int granted = 0;
        for (RewardDefinition reward : plan.rewards()) {
            if (!reward.eligibleForCycle(sessionSeconds)) {
                continue;
            }
            granted += tryGrant(player, pool, reward);
        }
        return granted;
    }

    public MilestoneGrant grantMilestones(
            Player player,
            PoolDefinition pool,
            long previousSessionSeconds,
            long sessionSeconds
    ) {
        RewardPlan plan = plans.find(pool.rewardPlan()).orElse(null);
        if (plan == null) {
            return new MilestoneGrant(0, 0);
        }

        int eligible = 0;
        int granted = 0;
        for (RewardDefinition reward : plan.rewards()) {
            if (!reward.milestoneCrossed(previousSessionSeconds, sessionSeconds)) {
                continue;
            }
            eligible++;
            granted += tryGrant(player, pool, reward);
        }
        return new MilestoneGrant(eligible, granted);
    }

    private int tryGrant(Player player, PoolDefinition pool, RewardDefinition reward) {
        if (ThreadLocalRandom.current().nextDouble(100.0) >= reward.chance()) {
            return 0;
        }
        return switch (reward.type()) {
            case ITEM -> {
                store.enqueueItem(
                        player.getUniqueId(), pool.id(), reward.provider(), reward.itemId(),
                        reward.itemAmount(), reward.itemId()
                );
                yield reward.itemAmount();
            }
            case MONEY -> {
                if (economy.deposit(player, reward.moneyAmount())) {
                    yield 1;
                } else {
                    plugin.getLogger().warning("无法给 " + player.getName() + " 发放货币奖励：没有可用经济服务或交易失败。");
                    yield 0;
                }
            }
            case COMMAND -> {
                String command = reward.command()
                        .replace("{player}", player.getName())
                        .replace("{uuid}", player.getUniqueId().toString())
                        .replace("{pool}", pool.id());
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                if (!command.isBlank() && Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    yield 1;
                }
                yield 0;
            }
        };
    }

    public record MilestoneGrant(int eligible, int granted) {
    }
}
