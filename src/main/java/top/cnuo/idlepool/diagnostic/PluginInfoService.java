package top.cnuo.idlepool.diagnostic;

import top.cnuo.idlepool.integration.VisualBridge;
import top.cnuo.idlepool.update.UpdateChecker;
import top.cnuo.idlepool.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

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
        Messages.send(sender, "info.header");
        Messages.send(sender, "info.version", Map.of("version", plugin.getPluginMeta().getVersion()));
        Messages.send(sender, "info.server-version", Map.of("version", Bukkit.getVersion()));
        Messages.send(sender, "info.itemsadder-status", Map.of("status", visuals.status()));
        Messages.send(sender, "info.project-url", Map.of("url", UpdateChecker.PROJECT_URL));
        Messages.send(sender, "info.footer");

        UpdateChecker.Result result = updates.result();
        switch (result.state()) {
            case UPDATE_AVAILABLE -> {
                Messages.send(sender, "info.update-available", Map.of("version", result.remoteVersion()));
                Messages.send(sender, "info.current-version", Map.of("version", updates.currentVersion()));
                Messages.send(sender, "info.update-url", Map.of("url", UpdateChecker.RELEASES_URL));
            }
            case UP_TO_DATE -> Messages.send(sender, "info.up-to-date", Map.of("version", result.remoteVersion()));
            case ERROR -> Messages.send(sender, "info.update-error", Map.of("error", result.message()));
            case CHECKING -> Messages.send(sender, "info.checking");
            case DISABLED -> Messages.send(sender, "info.disabled");
            case NOT_CHECKED -> Messages.send(sender, "info.not-checked");
        }
    }
}
