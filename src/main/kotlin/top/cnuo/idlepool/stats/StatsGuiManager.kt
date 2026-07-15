package top.cnuo.idlepool.stats

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.storage.LeaderboardEntry
import top.cnuo.idlepool.storage.PlayerStats
import top.cnuo.idlepool.storage.SqliteStore
import top.cnuo.idlepool.storage.StatsPeriod
import top.cnuo.idlepool.util.Messages
import top.cnuo.idlepool.util.TimeFormats
import java.util.UUID

class StatsGuiManager(private val plugin: JavaPlugin, private val store: SqliteStore) : Listener {
    fun openStats(viewer: Player, target: OfflinePlayer = viewer, period: StatsPeriod = StatsPeriod.TOTAL) {
        Messages.send(viewer, "stats.loading")
        store.loadStats(target.uniqueId, period).whenComplete { stats, failure -> sync {
            if (!viewer.isOnline) return@sync
            if (failure != null) Messages.send(viewer, "stats.load-failed") else showStats(viewer, stats)
        } }
    }

    fun openTop(viewer: Player, period: StatsPeriod = StatsPeriod.TOTAL) {
        Messages.send(viewer, "stats.loading")
        val limit = plugin.config.getInt("statistics.leaderboard-size", 45).coerceIn(1, 45)
        store.loadLeaderboard(period, limit).whenComplete { entries, failure -> sync {
            if (!viewer.isOnline) return@sync
            if (failure != null) Messages.send(viewer, "stats.load-failed") else showTop(viewer, period, entries)
        } }
    }

    private fun showStats(player: Player, stats: PlayerStats) {
        val holder = StatsHolder(StatsHolder.View.PLAYER, UUID.fromString(stats.playerId), stats.period)
        val inventory = Bukkit.createInventory(holder, 27, Messages.get("stats.title", mapOf("player" to stats.playerName))); holder.attach(inventory)
        inventory.setItem(13, keyed(Material.CLOCK, "stats.summary", mapOf(
            "player" to stats.playerName, "period" to periodName(stats.period), "time" to TimeFormats.clock(stats.seconds),
            "cycles" to stats.cycles.toString(), "rewards" to stats.rewards.toString(),
            "longest" to TimeFormats.clock(stats.longestSession), "rank" to rank(stats.rank),
        )))
        periodButtons(inventory)
        inventory.setItem(22, keyed(Material.GOLDEN_HELMET, "stats.open-top"))
        player.openInventory(inventory)
    }

    private fun showTop(player: Player, period: StatsPeriod, entries: List<LeaderboardEntry>) {
        val holder = StatsHolder(StatsHolder.View.TOP, player.uniqueId, period)
        val inventory = Bukkit.createInventory(holder, 54, Messages.get("stats.top-title", mapOf("period" to periodName(period)))); holder.attach(inventory)
        entries.take(45).forEachIndexed { index, entry -> inventory.setItem(index, keyed(Material.PLAYER_HEAD, "stats.top-entry", mapOf(
            "rank" to entry.rank.toString(), "player" to entry.playerName, "time" to TimeFormats.clock(entry.seconds),
            "cycles" to entry.cycles.toString(), "rewards" to entry.rewards.toString(),
        ))) }
        listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.TOTAL).forEachIndexed { index, value ->
            inventory.setItem(45 + index * 2, keyed(if (value == period) Material.LIME_DYE else Material.GRAY_DYE, "stats.period", mapOf("period" to periodName(value))))
        }
        inventory.setItem(53, keyed(Material.ARROW, "stats.back")); player.openInventory(inventory)
    }

    private fun periodButtons(inventory: Inventory) {
        listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.TOTAL).forEachIndexed { index, value ->
            inventory.setItem(10 + index * 2, keyed(Material.PAPER, "stats.period", mapOf("period" to periodName(value))))
        }
    }

    @EventHandler fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.getHolder(false) as? StatsHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (holder.view == StatsHolder.View.PLAYER) when (event.rawSlot) {
            10 -> openStats(player, Bukkit.getOfflinePlayer(holder.target), StatsPeriod.DAY)
            12 -> openStats(player, Bukkit.getOfflinePlayer(holder.target), StatsPeriod.WEEK)
            14 -> openStats(player, Bukkit.getOfflinePlayer(holder.target), StatsPeriod.MONTH)
            16 -> openStats(player, Bukkit.getOfflinePlayer(holder.target), StatsPeriod.TOTAL)
            22 -> openTop(player, holder.period)
        } else when (event.rawSlot) {
            45 -> openTop(player, StatsPeriod.DAY)
            47 -> openTop(player, StatsPeriod.WEEK)
            49 -> openTop(player, StatsPeriod.MONTH)
            51 -> openTop(player, StatsPeriod.TOTAL)
            53 -> openStats(player)
        }
    }
    @EventHandler fun onDrag(event: InventoryDragEvent) { if (event.view.topInventory.getHolder(false) is StatsHolder) event.isCancelled = true }

    private fun periodName(period: StatsPeriod) = Messages.raw("stats.periods.${period.name.lowercase()}")
    private fun rank(value: Int) = if (value <= 0) Messages.raw("stats.unranked") else "#$value"
    private fun keyed(material: Material, key: String, values: Map<String,String> = emptyMap()) = ItemStack(material).apply {
        itemMeta = itemMeta.apply { displayName(Messages.get("$key.name", values)); lore(Messages.list("$key.lore", values)) }
    }
    private fun sync(action: () -> Unit) { Bukkit.getScheduler().runTask(plugin, Runnable(action)) }
}

private class StatsHolder(val view: View, val target: UUID, val period: StatsPeriod) : InventoryHolder {
    enum class View { PLAYER, TOP }
    private lateinit var attached: Inventory
    fun attach(inventory: Inventory) { attached = inventory }
    override fun getInventory() = attached
}
