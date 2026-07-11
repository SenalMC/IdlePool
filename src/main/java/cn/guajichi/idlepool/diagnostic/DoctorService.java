package cn.guajichi.idlepool.diagnostic;

import cn.guajichi.idlepool.integration.EconomyBridge;
import cn.guajichi.idlepool.integration.VisualBridge;
import cn.guajichi.idlepool.pool.PoolDefinition;
import cn.guajichi.idlepool.pool.PoolRepository;
import cn.guajichi.idlepool.reward.RewardDefinition;
import cn.guajichi.idlepool.reward.RewardPlan;
import cn.guajichi.idlepool.reward.RewardPlanRepository;
import cn.guajichi.idlepool.reward.RewardType;
import cn.guajichi.idlepool.util.Messages;
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
        sender.sendMessage(Messages.parse("<gold>======= IdlePool Doctor ======="));

        if (pools.all().isEmpty()) {
            result.error("没有加载任何挂机池。");
        } else {
            result.ok("已加载 " + pools.all().size() + " 个挂机池。");
        }
        long enabledPools = pools.all().stream().filter(PoolDefinition::enabled).count();
        if (enabledPools == 0) {
            result.warning("当前没有启用的挂机池。");
        } else {
            result.ok("已启用 " + enabledPools + " 个挂机池。");
        }

        for (PoolDefinition pool : pools.all()) {
            if (plans.find(pool.rewardPlan()).isEmpty()) {
                result.error("挂机池 " + pool.id() + " 引用了不存在的奖励方案 " + pool.rewardPlan() + "。");
            }
        }

        if (visuals.available()) {
            result.ok("ItemsAdder API、按钮物品和 ActionBar 图标已进入可用状态。");
        } else {
            result.warning("ItemsAdder 未安装或数据尚未加载，当前会使用原版按钮和 ActionBar 文本。");
        }

        for (RewardPlan plan : plans.all()) {
            if (plan.rewards().isEmpty()) {
                result.warning("奖励方案 " + plan.id() + " 没有任何奖励。");
            }
            for (RewardDefinition reward : plan.rewards()) {
                validateReward(result, plan, reward);
            }
        }

        sender.sendMessage(Messages.parse("<gold>-------------------------------"));
        if (result.errors == 0) {
            sender.sendMessage(Messages.parse("<green>检查完成：没有阻止上线的错误。</green> <gray>警告 " + result.warnings + " 项。"));
        } else {
            sender.sendMessage(Messages.parse("<red>检查完成：发现 " + result.errors + " 项错误，建议修复后再上线。</red>"));
        }
    }

    private void validateReward(Result result, RewardPlan plan, RewardDefinition reward) {
        if (reward.type() == RewardType.MONEY && !economy.available()) {
            result.warning("奖励方案 " + plan.id() + " 含有货币奖励，但没有可用 Vault 经济服务。");
            return;
        }
        if (reward.type() != RewardType.ITEM) {
            return;
        }

        String provider = reward.provider().toLowerCase(Locale.ROOT);
        if (provider.equals("vanilla") && Material.matchMaterial(reward.itemId()) == null) {
            result.error("奖励方案 " + plan.id() + " 的原版物品不存在：" + reward.itemId());
        }
        String pluginName = PROVIDER_PLUGINS.get(provider);
        if (pluginName != null && !Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            result.warning("奖励方案 " + plan.id() + " 使用 " + provider + " 物品，但 " + pluginName + " 未启用。");
        }
        if (provider.equals("itemsadder") && visuals.available() && visuals.customItem(reward.itemId()).isEmpty()) {
            result.error("ItemsAdder 奖励物品不存在：" + reward.itemId());
        }
    }

    private static final class Result {
        private final CommandSender sender;
        private int warnings;
        private int errors;

        private Result(CommandSender sender) {
            this.sender = sender;
        }

        private void ok(String message) {
            sender.sendMessage(Messages.parse("<green>✔</green> <gray>" + message));
        }

        private void warning(String message) {
            warnings++;
            sender.sendMessage(Messages.parse("<yellow>⚠</yellow> <gray>" + message));
        }

        private void error(String message) {
            errors++;
            sender.sendMessage(Messages.parse("<red>✘ " + message + "</red>"));
        }
    }
}
