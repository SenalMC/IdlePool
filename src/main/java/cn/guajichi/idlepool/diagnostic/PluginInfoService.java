package cn.guajichi.idlepool.diagnostic;

import cn.guajichi.idlepool.integration.VisualBridge;
import cn.guajichi.idlepool.update.UpdateChecker;
import cn.guajichi.idlepool.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginInfoService {
    private final JavaPlugin plugin;
    private final VisualBridge visuals;
    private final UpdateChecker updates;

    public PluginInfoService(JavaPlugin plugin, VisualBridge visuals, UpdateChecker updates) {
        this.plugin = plugin;
        this.visuals = visuals;
        this.updates = updates;
    }

    public void show(CommandSender sender) {
        sender.sendMessage(Messages.parse("<gold>===IdlePool信息==="));
        sender.sendMessage(Messages.parse("<yellow>版本：<white>" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(Messages.parse("<yellow>服务端版本:<white>" + Bukkit.getVersion()));
        sender.sendMessage(Messages.parse("<yellow>ItemsAdder状态:<white>" + visuals.status()));
        sender.sendMessage(Messages.parse("<aqua>" + UpdateChecker.PROJECT_URL));
        sender.sendMessage(Messages.parse("<gold>===IdlePool@Chirnuo==="));

        UpdateChecker.Result result = updates.result();
        switch (result.state()) {
            case UPDATE_AVAILABLE -> {
                sender.sendMessage(Messages.parse("<red>检测到新版本：<white>" + result.remoteVersion()));
                sender.sendMessage(Messages.parse("<yellow>当前版本：<white>" + updates.currentVersion()));
                sender.sendMessage(Messages.parse("<aqua>更新地址：" + UpdateChecker.RELEASES_URL));
            }
            case UP_TO_DATE -> sender.sendMessage(Messages.parse(
                    "<green>更新检查：已是最新版本（" + result.remoteVersion() + "）"
            ));
            case ERROR -> sender.sendMessage(Messages.parse("<red>更新检查失败：<white>" + result.message()));
            case CHECKING -> sender.sendMessage(Messages.parse("<gray>更新检查：正在检查……"));
            case DISABLED -> sender.sendMessage(Messages.parse("<gray>更新检查：已由管理员关闭"));
            case NOT_CHECKED -> sender.sendMessage(Messages.parse("<gray>更新检查：尚未执行"));
        }
    }
}
