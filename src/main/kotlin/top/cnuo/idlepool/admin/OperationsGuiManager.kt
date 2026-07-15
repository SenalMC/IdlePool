package top.cnuo.idlepool.admin

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.storage.*
import top.cnuo.idlepool.util.Messages
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class OperationsGuiManager(private val plugin: JavaPlugin, private val store: SqliteStore) : Listener {
    fun openHome(player: Player) {
        val holder = OperationsHolder(OperationsHolder.View.HOME)
        val inventory = Bukkit.createInventory(holder, 27, Messages.get("operations.title")); holder.attach(inventory)
        inventory.setItem(11, keyed(Material.RECOVERY_COMPASS, "operations.review"))
        inventory.setItem(13, keyed(Material.WRITABLE_BOOK, "operations.logs"))
        inventory.setItem(15, keyed(Material.PAPER, "operations.export"))
        inventory.setItem(22, keyed(Material.ARROW, "operations.back")); player.openInventory(inventory)
    }

    fun openReviews(player: Player, page: Int = 0) {
        Messages.send(player, "operations.loading")
        store.listReviews(page).whenComplete { result, failure -> sync {
            if (!player.isOnline) return@sync
            if (failure != null) { Messages.send(player, "operations.load-failed"); return@sync }
            val holder = OperationsHolder(OperationsHolder.View.REVIEWS, page = result.page, reviews = result.entries)
            val inventory = Bukkit.createInventory(holder, 54, Messages.get("operations.review-title", mapOf("count" to result.totalEntries.toString()))); holder.attach(inventory)
            result.entries.forEachIndexed { index, entry -> inventory.setItem(index, keyed(Material.CHEST, "operations.review-entry", mapOf(
                "id" to entry.id.toString(), "player" to entry.playerId.take(8), "item" to "${entry.provider}:${entry.itemId}", "amount" to entry.amount.toString(), "reason" to entry.detail,
            ))) }
            navigation(inventory, result.page, result.pages); inventory.setItem(49, keyed(Material.ARROW, "operations.back")); player.openInventory(inventory)
        } }
    }

    fun openLogs(player: Player, playerId: UUID? = null, status: String = "ALL", page: Int = 0, history: Boolean = false) {
        Messages.send(player, "operations.loading")
        store.listRewardLogs(playerId, status, page).whenComplete { result, failure -> sync {
            if (!player.isOnline) return@sync
            if (failure != null) { Messages.send(player, "operations.load-failed"); return@sync }
            val holder = OperationsHolder(OperationsHolder.View.LOGS, result.page, logs = result.entries, playerFilter = playerId, status = status, history = history)
            val inventory = Bukkit.createInventory(holder, 54, Messages.get(if (history) "operations.history-title" else "operations.log-title", mapOf("status" to status, "count" to result.totalEntries.toString()))); holder.attach(inventory)
            result.entries.forEachIndexed { index, entry -> inventory.setItem(index, keyed(statusMaterial(entry.status), "operations.log-entry", mapOf(
                "type" to entry.rewardType, "key" to entry.rewardKey, "amount" to entry.amount.toString(), "pool" to entry.poolId,
                "status" to entry.status, "time" to entry.createdAt.toString(), "id" to entry.settlementId.take(18), "detail" to entry.detail,
            ))) }
            navigation(inventory, result.page, result.pages)
            if (!history) inventory.setItem(47, keyed(Material.COMPARATOR, "operations.log-filter", mapOf("status" to status)))
            inventory.setItem(49, keyed(Material.ARROW, "operations.back")); player.openInventory(inventory)
        } }
    }

    private fun openReview(player: Player, entry: ReviewEntry, page: Int) {
        val holder = OperationsHolder(OperationsHolder.View.REVIEW_DETAIL, page = page, reviews = listOf(entry))
        val inventory = Bukkit.createInventory(holder, 27, Messages.get("operations.review-detail-title", mapOf("id" to entry.id.toString()))); holder.attach(inventory)
        inventory.setItem(4, keyed(Material.CHEST, "operations.review-entry", mapOf("id" to entry.id.toString(), "player" to entry.playerId, "item" to "${entry.provider}:${entry.itemId}", "amount" to entry.amount.toString(), "reason" to entry.detail)))
        inventory.setItem(11, keyed(Material.LIME_DYE, "operations.restore")); inventory.setItem(13, keyed(Material.EMERALD, "operations.confirm"))
        inventory.setItem(15, keyed(Material.BARRIER, "operations.void")); inventory.setItem(22, keyed(Material.ARROW, "operations.back")); player.openInventory(inventory)
    }

    fun export(player: Player) {
        val file = File(plugin.dataFolder, "exports/reward-ledger-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())}.csv")
        Messages.send(player, "operations.exporting")
        store.exportRewardLogs(file).whenComplete { count, failure -> sync {
            if (failure != null) Messages.send(player, "operations.export-failed")
            else Messages.send(player, "operations.exported", mapOf("count" to count.toString(), "file" to file.relativeTo(plugin.dataFolder).path))
        } }
    }

    @EventHandler fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.getHolder(false) as? OperationsHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (!holder.history && !player.hasPermission("idlepool.admin")) return
        when (holder.view) {
            OperationsHolder.View.HOME -> when (event.rawSlot) { 11 -> openReviews(player); 13 -> openLogs(player); 15 -> export(player); 22 -> player.performCommand("afkpool admin") }
            OperationsHolder.View.REVIEWS -> when (event.rawSlot) {
                45 -> openReviews(player, holder.page - 1); 49 -> openHome(player); 53 -> openReviews(player, holder.page + 1)
                else -> holder.reviews.getOrNull(event.rawSlot)?.let { openReview(player, it, holder.page) }
            }
            OperationsHolder.View.REVIEW_DETAIL -> when (event.rawSlot) {
                11 -> resolve(player, holder.reviews.first(), "RESTORE", holder.page)
                13 -> resolve(player, holder.reviews.first(), "CONFIRM", holder.page)
                15 -> resolve(player, holder.reviews.first(), "VOID", holder.page)
                22 -> openReviews(player, holder.page)
            }
            OperationsHolder.View.LOGS -> when (event.rawSlot) {
                45 -> openLogs(player, holder.playerFilter, holder.status, holder.page - 1, holder.history)
                47 -> if (!holder.history) openLogs(player, holder.playerFilter, nextStatus(holder.status), 0)
                49 -> if (holder.history) player.closeInventory() else openHome(player)
                53 -> openLogs(player, holder.playerFilter, holder.status, holder.page + 1, holder.history)
            }
        }
    }
    @EventHandler fun onDrag(event: InventoryDragEvent) { if (event.view.topInventory.getHolder(false) is OperationsHolder) event.isCancelled = true }

    private fun resolve(player: Player, entry: ReviewEntry, action: String, page: Int) {
        player.closeInventory(); Messages.send(player, "operations.resolving")
        store.resolveReview(entry.id, action, player.name).whenComplete { success, failure -> sync {
            if (failure != null || success != true) Messages.send(player, "operations.resolve-failed") else { Messages.send(player, "operations.resolved", mapOf("action" to action)); openReviews(player, page) }
        } }
    }
    private fun navigation(inventory: Inventory, page: Int, pages: Int) {
        if (page > 0) inventory.setItem(45, keyed(Material.ARROW, "operations.previous"))
        if (page + 1 < pages) inventory.setItem(53, keyed(Material.ARROW, "operations.next"))
    }
    private fun nextStatus(value: String): String { val values = listOf("ALL","SUCCESS","FAILED","PROCESSING"); return values[(values.indexOf(value.uppercase()).coerceAtLeast(0) + 1) % values.size] }
    private fun statusMaterial(status: String) = when (status.uppercase()) { "SUCCESS" -> Material.LIME_DYE; "FAILED" -> Material.RED_DYE; "PROCESSING" -> Material.YELLOW_DYE; else -> Material.PAPER }
    private fun keyed(material: Material, key: String, values: Map<String,String> = emptyMap()) = ItemStack(material).apply { itemMeta = itemMeta.apply { displayName(Messages.get("$key.name", values)); lore(Messages.list("$key.lore", values)) } }
    private fun sync(action: () -> Unit) { Bukkit.getScheduler().runTask(plugin, Runnable(action)) }
}

private class OperationsHolder(
    val view: View,
    val page: Int = 0,
    val reviews: List<ReviewEntry> = emptyList(),
    val logs: List<RewardLogEntry> = emptyList(),
    val playerFilter: UUID? = null,
    val status: String = "ALL",
    val history: Boolean = false,
) : InventoryHolder {
    enum class View { HOME, REVIEWS, REVIEW_DETAIL, LOGS }
    private lateinit var attached: Inventory
    fun attach(inventory: Inventory) { attached = inventory }
    override fun getInventory() = attached
}
