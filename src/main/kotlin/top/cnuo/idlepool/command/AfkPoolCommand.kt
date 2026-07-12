package top.cnuo.idlepool.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import top.cnuo.idlepool.IdlePoolPlugin
import top.cnuo.idlepool.admin.PoolAdminGuiManager
import top.cnuo.idlepool.admin.RewardAdminGuiManager
import top.cnuo.idlepool.diagnostic.DoctorService
import top.cnuo.idlepool.diagnostic.PluginInfoService
import top.cnuo.idlepool.gui.InboxGuiManager
import top.cnuo.idlepool.gui.PoolGuiManager
import top.cnuo.idlepool.pool.PoolRepository
import top.cnuo.idlepool.session.SessionManager
import top.cnuo.idlepool.util.Messages

class AfkPoolCommand(
    private val plugin: IdlePoolPlugin, private val pools: PoolRepository, private val gui: PoolGuiManager,
    private val sessions: SessionManager, private val admin: PoolAdminGuiManager, private val rewardAdmin: RewardAdminGuiManager,
    private val inbox: InboxGuiManager, private val doctor: DoctorService, private val info: PluginInfoService,
) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            "info" -> info.show(sender)
            "reload" -> if (admin(sender)) { plugin.reloadIdlePool(); Messages.send(sender, "command.reload-success") }
            "doctor" -> if (admin(sender)) doctor.report(sender)
            "admin" -> if (admin(sender) && sender is Player) { if (args.getOrNull(1).equals("rewards", true)) rewardAdmin.openList(sender) else admin.openList(sender) }
            "claim", "inbox" -> if (sender is Player) inbox.open(sender) else Messages.send(sender, "command.players-only")
            "stop" -> if (sender is Player) sessions.stop(sender, false) else Messages.send(sender, "command.players-only")
            else -> if (sender is Player) pools.at(sender.location).ifPresentOrElse({ gui.open(sender, it) }, { Messages.send(sender, "session.no-pool") }) else Messages.send(sender, "command.players-only")
        }
        return true
    }
    private fun admin(sender: CommandSender): Boolean { if (sender.hasPermission("idlepool.admin")) return true; Messages.send(sender, "command.no-permission"); return false }
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 2 && args[0].equals("admin", true) && sender.hasPermission("idlepool.admin")) return listOf("rewards").filter { it.startsWith(args[1], true) }
        if (args.size != 1) return emptyList()
        val options = if (sender.hasPermission("idlepool.admin")) listOf("info","admin","claim","doctor","stop","reload") else listOf("info","claim","stop")
        return options.filter { it.startsWith(args[0], true) }
    }
}
