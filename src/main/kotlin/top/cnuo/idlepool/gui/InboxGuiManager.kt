package top.cnuo.idlepool.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.reward.ItemRewardFactory
import top.cnuo.idlepool.storage.ClaimReservation
import top.cnuo.idlepool.storage.InboxEntry
import top.cnuo.idlepool.storage.InboxPage
import top.cnuo.idlepool.storage.SqliteStore
import top.cnuo.idlepool.util.Messages
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InboxGuiManager(
    private val plugin: JavaPlugin,
    private val store: SqliteStore,
    private val items: ItemRewardFactory,
) : Listener {
    private val claimsInFlight = ConcurrentHashMap.newKeySet<Long>()
    private val bulkInFlight = ConcurrentHashMap.newKeySet<UUID>()

    @JvmOverloads
    fun open(player: Player, page: Int = 0) {
        Messages.send(player, "inbox.loading")
        val pageSize = plugin.config.getInt("inbox.page-size", 45).coerceIn(1, 45)
        store.listInboxPage(player.uniqueId, page, pageSize).whenComplete { result, failure -> sync {
            if (!player.isOnline) return@sync
            if (failure != null) Messages.send(player, "inbox.load-failed") else show(player, result)
        } }
    }

    private fun show(player: Player, page: InboxPage) {
        val bySlot = page.entries.mapIndexed { index, entry -> index to entry }.toMap()
        val holder = InboxGuiHolder(bySlot, page.page)
        val inventory = Bukkit.createInventory(holder, 54, Messages.get("inbox.title"))
        holder.attach(inventory)
        bySlot.forEach { (slot, entry) -> inventory.setItem(slot, preview(entry)) }
        if (page.page > 0) inventory.setItem(45, named(Material.ARROW, "inbox.previous", mapOf("page" to page.page.toString())))
        inventory.setItem(47, named(Material.HOPPER, "inbox.claim-all", mapOf("count" to page.totalEntries.toString())))
        inventory.setItem(49, named(Material.CHEST, "inbox.status", mapOf(
            "count" to page.totalEntries.toString(), "page" to (page.page + 1).toString(), "pages" to page.pages.toString()
        )))
        if (page.page + 1 < page.pages) inventory.setItem(53, named(Material.ARROW, "inbox.next", mapOf("page" to (page.page + 2).toString())))
        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.getHolder(false) as? InboxGuiHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        when (event.rawSlot) {
            45 -> if (holder.page > 0) open(player, holder.page - 1)
            47 -> claimAll(player)
            53 -> open(player, holder.page + 1)
            else -> holder.entry(event.rawSlot)?.let { claimOne(player, it, holder.page) }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is InboxGuiHolder) event.isCancelled = true
    }

    private fun claimOne(player: Player, entry: InboxEntry, page: Int) {
        if (!claimsInFlight.add(entry.id)) return
        player.closeInventory()
        store.reserveInbox(player.uniqueId, entry.id).whenComplete { reservation, failure -> sync {
            if (failure != null || reservation == null) {
                claimsInFlight.remove(entry.id); Messages.send(player, "inbox.claim-conflict"); open(player, page); return@sync
            }
            deliver(player, reservation) { claimed, continueClaims ->
                claimsInFlight.remove(entry.id)
                if (claimed > 0) Messages.send(player, "inbox.claimed", mapOf("amount" to claimed.toString()))
                if (player.isOnline) open(player, page)
            }
        } }
    }

    private fun claimAll(player: Player) {
        if (!bulkInFlight.add(player.uniqueId)) return
        player.closeInventory()
        Messages.send(player, "inbox.claim-all-start")
        store.listAllInbox(player.uniqueId).whenComplete { entries, failure -> sync {
            if (failure != null) { bulkInFlight.remove(player.uniqueId); Messages.send(player, "inbox.load-failed"); return@sync }
            claimQueue(player, ArrayDeque(entries), 0)
        } }
    }

    private fun claimQueue(player: Player, queue: ArrayDeque<InboxEntry>, total: Int) {
        if (!player.isOnline || queue.isEmpty()) {
            bulkInFlight.remove(player.uniqueId)
            Messages.send(player, "inbox.claim-all-finished", mapOf("amount" to total.toString()))
            if (player.isOnline) open(player)
            return
        }
        val entry = queue.removeFirst()
        store.reserveInbox(player.uniqueId, entry.id).whenComplete { reservation, failure -> sync {
            if (failure != null || reservation == null) { claimQueue(player, queue, total); return@sync }
            deliver(player, reservation) { claimed, canContinue ->
                if (canContinue) claimQueue(player, queue, total + claimed)
                else {
                    bulkInFlight.remove(player.uniqueId)
                    Messages.send(player, "inbox.claim-all-finished", mapOf("amount" to (total + claimed).toString()))
                    if (player.isOnline) open(player)
                }
            }
        } }
    }

    private fun deliver(player: Player, reservation: ClaimReservation, done: (Int, Boolean) -> Unit) {
        val entry = reservation.entry
        val generated = items.create(entry.provider, entry.itemId, entry.amount).orElse(null)
        if (generated == null) {
            store.releaseClaim(player.uniqueId, reservation.token, "item-generation-failed")
            Messages.send(player, "inbox.generate-failed", mapOf("provider" to entry.provider, "item" to entry.itemId))
            done(0, true); return
        }
        val before = generated.sumOf(ItemStack::getAmount)
        val leftovers = player.inventory.addItem(*generated.toTypedArray()).values.sumOf(ItemStack::getAmount)
        val inserted = before - leftovers
        if (inserted <= 0) {
            store.releaseClaim(player.uniqueId, reservation.token, "inventory-full")
            Messages.send(player, "inbox.inventory-full")
            done(0, false); return
        }
        val remaining = entry.amount - inserted
        store.finishClaim(player.uniqueId, reservation.token, remaining, inserted).whenComplete { committed, failure -> sync {
            if (failure != null || committed != true) {
                store.markClaimReview(player.uniqueId, reservation.token, inserted, failure?.message ?: "commit-rejected")
                Messages.send(player, "inbox.review-required")
                done(inserted, false)
            } else done(inserted, leftovers == 0)
        } }
    }

    private fun preview(entry: InboxEntry): ItemStack {
        val item = items.preview(entry.provider, entry.itemId).orElseGet { ItemStack(Material.BARRIER) }
        item.amount = minOf(item.maxStackSize, entry.amount.coerceAtLeast(1))
        item.itemMeta = item.itemMeta.apply {
            lore(Messages.list("inbox.item.lore", mapOf(
                "amount" to entry.amount.toString(), "provider" to entry.provider, "pool" to entry.poolId,
                "time" to entry.createdAt.toString(),
            )))
        }
        return item
    }

    private fun named(material: Material, key: String, placeholders: Map<String, String>): ItemStack = ItemStack(material).apply {
        itemMeta = itemMeta.apply { displayName(Messages.get("$key.name", placeholders)); lore(Messages.list("$key.lore", placeholders)) }
    }

    private fun sync(action: () -> Unit) { Bukkit.getScheduler().runTask(plugin, Runnable(action)) }
}
