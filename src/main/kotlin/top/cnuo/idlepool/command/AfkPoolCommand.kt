package top.cnuo.idlepool.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.Bukkit
import top.cnuo.idlepool.IdlePoolPlugin
import top.cnuo.idlepool.admin.PoolAdminGuiManager
import top.cnuo.idlepool.admin.RewardAdminGuiManager
import top.cnuo.idlepool.admin.OperationsGuiManager
import top.cnuo.idlepool.activity.ActivityEventRepository
import top.cnuo.idlepool.diagnostic.DoctorService
import top.cnuo.idlepool.diagnostic.PluginInfoService
import top.cnuo.idlepool.gui.InboxGuiManager
import top.cnuo.idlepool.gui.PoolGuiManager
import top.cnuo.idlepool.pool.PoolRepository
import top.cnuo.idlepool.session.SessionManager
import top.cnuo.idlepool.util.Messages
import top.cnuo.idlepool.stats.StatsGuiManager
import top.cnuo.idlepool.storage.StatsPeriod

class AfkPoolCommand(
    private val plugin: IdlePoolPlugin, private val pools: PoolRepository, private val gui: PoolGuiManager,
    private val sessions: SessionManager, private val admin: PoolAdminGuiManager, private val rewardAdmin: RewardAdminGuiManager,
    private val operations: OperationsGuiManager, private val stats: StatsGuiManager, private val inbox: InboxGuiManager,
    private val activities: ActivityEventRepository, private val doctor: DoctorService, private val info: PluginInfoService,
) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            "info" -> info.show(sender)
            "reload" -> if (admin(sender)) { plugin.reloadIdlePool(); Messages.send(sender, "command.reload-success") }
            "doctor" -> if (admin(sender)) doctor.report(sender)
            "admin" -> if (admin(sender) && sender is Player) when (args.getOrNull(1)?.lowercase()) {
                "rewards" -> rewardAdmin.openList(sender)
                "operations" -> operations.openHome(sender)
                "review" -> operations.openReviews(sender)
                "logs" -> operations.openLogs(sender, args.getOrNull(2)?.let { Bukkit.getOfflinePlayer(it).uniqueId }, args.getOrNull(3) ?: "ALL")
                "export" -> operations.export(sender)
                else -> admin.openList(sender)
            }
            "claim", "inbox" -> if (sender is Player) inbox.open(sender) else Messages.send(sender, "command.players-only")
            "history" -> if (sender is Player) {
                val target = if (args.size > 1 && sender.hasPermission("idlepool.admin")) Bukkit.getOfflinePlayer(args[1]).uniqueId else sender.uniqueId
                operations.openLogs(sender, target, "ALL", history = true)
            } else Messages.send(sender, "command.players-only")
            "stats" -> if (sender is Player) {
                val first = args.getOrNull(1)
                val firstPeriod = first?.takeIf(::isPeriod)
                val target = if (first != null && firstPeriod == null && sender.hasPermission("idlepool.admin")) Bukkit.getOfflinePlayer(first) else sender
                val period = StatsPeriod.parse(firstPeriod ?: args.getOrNull(2))
                stats.openStats(sender, target, period)
            } else Messages.send(sender, "command.players-only")
            "top" -> if (sender is Player) stats.openTop(sender, StatsPeriod.parse(args.getOrNull(1))) else Messages.send(sender, "command.players-only")
            "event" -> if (admin(sender)) eventCommand(sender, args)
            "stop" -> if (sender is Player) sessions.stop(sender, false) else Messages.send(sender, "command.players-only")
            else -> if (sender is Player) pools.at(sender.location).ifPresentOrElse({ gui.open(sender, it) }, { Messages.send(sender, "session.no-pool") }) else Messages.send(sender, "command.players-only")
        }
        return true
    }
    private fun eventCommand(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase()) {
            "start", "stop" -> {
                val id = args.getOrNull(2) ?: run { Messages.send(sender, "event.usage"); return }
                val enabled = args[1].equals("start", true)
                activities.setEnabled(id, enabled, enabled).whenComplete { _, failure ->
                    Bukkit.getScheduler().runTask(plugin, Runnable { if (failure != null) Messages.send(sender, "event.failed", mapOf("error" to (failure.message ?: "unknown"))) else Messages.send(sender, "event.changed", mapOf("id" to id, "state" to Messages.raw(if (enabled) "common.enabled" else "common.disabled"))) })
                }
            }
            else -> {
                Messages.send(sender, "event.header")
                activities.all().forEach { event -> Messages.send(sender, "event.entry", mapOf("id" to event.id, "name" to event.displayName, "state" to Messages.raw(if (event.enabled) "common.enabled" else "common.disabled"), "progress" to event.progressMultiplier.toString(), "item" to event.itemMultiplier.toString(), "money" to event.moneyMultiplier.toString())) }
            }
        }
    }
    private fun isPeriod(value: String) = StatsPeriod.entries.any { it.name.equals(value, true) }
    private fun admin(sender: CommandSender): Boolean { if (sender.hasPermission("idlepool.admin")) return true; Messages.send(sender, "command.no-permission"); return false }
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 2 && args[0].equals("admin", true) && sender.hasPermission("idlepool.admin")) return listOf("rewards","operations","review","logs","export").filter { it.startsWith(args[1], true) }
        if (args.size == 2 && args[0].equals("top", true)) return listOf("day","week","month","total").filter { it.startsWith(args[1], true) }
        if (args.size == 2 && args[0].equals("event", true) && sender.hasPermission("idlepool.admin")) return listOf("list","start","stop").filter { it.startsWith(args[1], true) }
        if (args.size == 3 && args[0].equals("event", true) && sender.hasPermission("idlepool.admin")) return activities.all().map { it.id }.filter { it.startsWith(args[2], true) }
        if (args.size != 1) return emptyList()
        val options = if (sender.hasPermission("idlepool.admin")) listOf("info","admin","claim","history","stats","top","event","doctor","stop","reload") else listOf("info","claim","history","stats","top","stop")
        return options.filter { it.startsWith(args[0], true) }
    }
}
