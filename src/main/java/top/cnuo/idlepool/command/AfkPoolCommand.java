package top.cnuo.idlepool.command;

import top.cnuo.idlepool.IdlePoolPlugin;
import top.cnuo.idlepool.admin.PoolAdminGuiManager;
import top.cnuo.idlepool.admin.RewardAdminGuiManager;
import top.cnuo.idlepool.gui.PoolGuiManager;
import top.cnuo.idlepool.gui.InboxGuiManager;
import top.cnuo.idlepool.pool.PoolRepository;
import top.cnuo.idlepool.session.SessionManager;
import top.cnuo.idlepool.util.Messages;
import top.cnuo.idlepool.diagnostic.DoctorService;
import top.cnuo.idlepool.diagnostic.PluginInfoService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class AfkPoolCommand implements CommandExecutor, TabCompleter {
    private final IdlePoolPlugin plugin;
    private final PoolRepository pools;
    private final PoolGuiManager gui;
    private final SessionManager sessions;
    private final PoolAdminGuiManager adminGui;
    private final RewardAdminGuiManager rewardAdminGui;
    private final InboxGuiManager inboxGui;
    private final DoctorService doctor;
    private final PluginInfoService info;

    public AfkPoolCommand(
            IdlePoolPlugin plugin,
            PoolRepository pools,
            PoolGuiManager gui,
            SessionManager sessions,
            PoolAdminGuiManager adminGui,
            RewardAdminGuiManager rewardAdminGui,
            InboxGuiManager inboxGui,
            DoctorService doctor,
            PluginInfoService info
    ) {
        this.plugin = plugin;
        this.pools = pools;
        this.gui = gui;
        this.sessions = sessions;
        this.adminGui = adminGui;
        this.rewardAdminGui = rewardAdminGui;
        this.inboxGui = inboxGui;
        this.doctor = doctor;
        this.info = info;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            info.show(sender);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("idlepool.admin")) {
                Messages.send(sender, "command.no-permission");
                return true;
            }
            plugin.reloadIdlePool();
            Messages.send(sender, "command.reload-success");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("doctor")) {
            if (!sender.hasPermission("idlepool.admin")) {
                Messages.send(sender, "command.no-permission");
                return true;
            }
            doctor.report(sender);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!(sender instanceof Player player) || !sender.hasPermission("idlepool.admin")) {
                Messages.send(sender, "command.no-permission");
                return true;
            }
            if (args.length > 1 && args[1].equalsIgnoreCase("rewards")) {
                rewardAdminGui.openList(player);
            } else {
                adminGui.openList(player);
            }
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("claim") || args[0].equalsIgnoreCase("inbox"))) {
            if (!(sender instanceof Player player)) {
                Messages.send(sender, "command.players-only");
                return true;
            }
            inboxGui.open(player);
            return true;
        }

        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.players-only");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            sessions.stop(player, false);
            return true;
        }
        pools.at(player.getLocation()).ifPresentOrElse(
                pool -> gui.open(player, pool),
                () -> Messages.send(player, "session.no-pool")
        );
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("idlepool.admin")) {
            return "rewards".startsWith(args[1].toLowerCase(java.util.Locale.ROOT))
                    ? List.of("rewards")
                    : List.of();
        }
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = sender.hasPermission("idlepool.admin")
                ? List.of("info", "admin", "claim", "doctor", "stop", "reload")
                : List.of("info", "claim", "stop");
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
