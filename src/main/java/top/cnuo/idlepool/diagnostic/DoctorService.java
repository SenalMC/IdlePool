package top.cnuo.idlepool.diagnostic;

import top.cnuo.idlepool.integration.EconomyBridge;
import top.cnuo.idlepool.integration.VisualBridge;
import top.cnuo.idlepool.pool.PoolDefinition;
import top.cnuo.idlepool.pool.PoolRepository;
import top.cnuo.idlepool.reward.RewardDefinition;
import top.cnuo.idlepool.reward.RewardPlan;
import top.cnuo.idlepool.reward.RewardPlanRepository;
import top.cnuo.idlepool.reward.RewardType;
import top.cnuo.idlepool.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Map;

public final class DoctorService {
    private static final Map<String, String> PROVIDER_PLUGINS = Map.of(
            "itemsadder", "ItemsAdder",
            "mythicmobs", "MythicMobs",
            "mmoitems", "MMOItems",
            "slimefun", "Slimefun"
    );

    private final PoolRepository pools;
    private final RewardPlanRepository plans;
    private final VisualBridge visuals;
    private final EconomyBridge economy;

    public DoctorService(
            PoolRepository pools,
            RewardPlanRepository plans,
            VisualBridge visuals,
            EconomyBridge economy
    ) {
        this.pools = pools;
        this.plans = plans;
        this.visuals = visuals;
        this.economy = economy;
    }

    public void report(CommandSender sender) {
        Result result = new Result(sender);
        Messages.send(sender, "doctor.header");

        if (pools.all().isEmpty()) {
            result.error("doctor.no-pools", Map.of());
        } else {
            result.ok("doctor.pools-loaded", Map.of("count", Integer.toString(pools.all().size())));
        }
        long enabledPools = pools.all().stream().filter(PoolDefinition::enabled).count();
        if (enabledPools == 0) {
            result.warning("doctor.no-enabled-pools", Map.of());
        } else {
            result.ok("doctor.enabled-pools", Map.of("count", Long.toString(enabledPools)));
        }

        for (PoolDefinition pool : pools.all()) {
            if (plans.find(pool.rewardPlan()).isEmpty()) {
                result.error("doctor.missing-plan", Map.of("pool", pool.id(), "plan", pool.rewardPlan()));
            }
        }

        if (visuals.available()) {
            result.ok("doctor.itemsadder-ready", Map.of());
        } else {
            result.warning("doctor.itemsadder-unavailable", Map.of());
        }

        for (RewardPlan plan : plans.all()) {
            if (plan.rewards().isEmpty()) {
                result.warning("doctor.empty-plan", Map.of("plan", plan.id()));
            }
            for (RewardDefinition reward : plan.rewards()) {
                validateReward(result, plan, reward);
            }
        }

        Messages.send(sender, "doctor.separator");
        if (result.errors == 0) {
            Messages.send(sender, "doctor.success", Map.of("warnings", Integer.toString(result.warnings)));
        } else {
            Messages.send(sender, "doctor.failure", Map.of("errors", Integer.toString(result.errors)));
        }
    }

    private void validateReward(Result result, RewardPlan plan, RewardDefinition reward) {
        if (reward.type() == RewardType.MONEY && !economy.available()) {
            result.warning("doctor.money-without-vault", Map.of("plan", plan.id()));
            return;
        }
        if (reward.type() != RewardType.ITEM) {
            return;
        }

        String provider = reward.provider().toLowerCase(Locale.ROOT);
        if (provider.equals("vanilla") && Material.matchMaterial(reward.itemId()) == null) {
            result.error("doctor.invalid-vanilla-item", Map.of("plan", plan.id(), "item", reward.itemId()));
        }
        String pluginName = PROVIDER_PLUGINS.get(provider);
        if (pluginName != null && !Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            result.warning("doctor.provider-missing", Map.of(
                    "plan", plan.id(), "provider", provider, "plugin", pluginName
            ));
        }
        if (provider.equals("itemsadder") && visuals.available() && visuals.customItem(reward.itemId()).isEmpty()) {
            result.error("doctor.itemsadder-item-missing", Map.of("item", reward.itemId()));
        }
    }

    private static final class Result {
        private final CommandSender sender;
        private int warnings;
        private int errors;

        private Result(CommandSender sender) {
            this.sender = sender;
        }

        private void ok(String key, Map<String, String> placeholders) {
            sender.sendMessage(Messages.get(key, placeholders));
        }

        private void warning(String key, Map<String, String> placeholders) {
            warnings++;
            sender.sendMessage(Messages.get(key, placeholders));
        }

        private void error(String key, Map<String, String> placeholders) {
            errors++;
            sender.sendMessage(Messages.get(key, placeholders));
        }
    }
}
