package top.cnuo.idlepool.admin

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import top.cnuo.idlepool.IdlePoolPlugin
import top.cnuo.idlepool.pool.PoolDefinition
import top.cnuo.idlepool.pool.PoolRepository
import top.cnuo.idlepool.reward.RewardPlanRepository
import top.cnuo.idlepool.util.DurationParser
import top.cnuo.idlepool.util.Messages
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap

class PoolAdminGuiManager(
    private val plugin: IdlePoolPlugin,
    private val pools: PoolRepository,
    private val plans: RewardPlanRepository,
) : Listener, AutoCloseable {
    constructor(plugin: IdlePoolPlugin, pools: PoolRepository) : this(
        plugin, pools, RewardPlanRepository(plugin).also { it.reload() }
    )
    private val editor = PoolConfigEditor(plugin)
    private val pending = ConcurrentHashMap<UUID, PendingInput>()
    private val slots = intArrayOf(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34)

    fun openList(player: Player) {
        val holder = AdminGuiHolder(AdminGuiHolder.View.POOL_LIST, "")
        val inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.pool.list.title")); holder.attach(inventory)
        pools.all().take(slots.size).forEachIndexed { index, pool ->
            inventory.setItem(slots[index], dynamic(pool.enabled.let { if (it) Material.LIME_DYE else Material.GRAY_DYE }, Messages.parse(pool.displayName),
                Messages.list("admin.pool.list.entry-lore", mapOf("id" to pool.id, "status" to Messages.raw(if (pool.enabled) "common.enabled" else "common.disabled"), "cycle" to pool.rewardCycle.toString()))))
        }
        inventory.setItem(45, keyed(Material.ANVIL, "admin.pool.list.create"))
        inventory.setItem(47, keyed(Material.EMERALD, "admin.pool.list.rewards"))
        inventory.setItem(49, keyed(Material.RECOVERY_COMPASS, "admin.pool.list.reload"))
        inventory.setItem(51, keyed(Material.ENDER_EYE, "admin.pool.list.operations"))
        player.openInventory(inventory)
    }

    fun openDetail(player: Player, pool: PoolDefinition) {
        val holder = AdminGuiHolder(AdminGuiHolder.View.POOL_DETAIL, pool.id)
        val inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.pool.detail.title", mapOf("id" to pool.id))); holder.attach(inventory)
        inventory.setItem(10, keyed(if (pool.enabled) Material.LIME_DYE else Material.GRAY_DYE, if (pool.enabled) "admin.pool.detail.enabled" else "admin.pool.detail.disabled"))
        inventory.setItem(12, keyed(Material.REDSTONE_TORCH, "admin.pool.detail.point-one")); inventory.setItem(14, keyed(Material.SOUL_TORCH, "admin.pool.detail.point-two"))
        inventory.setItem(16, keyed(Material.CLOCK, "admin.pool.detail.cycle", mapOf("value" to pool.rewardCycle.toString())))
        inventory.setItem(20, keyed(Material.NAME_TAG, "admin.pool.detail.permission", mapOf("value" to pool.permission)))
        inventory.setItem(22, keyed(Material.CHEST, "admin.pool.detail.plan", mapOf("value" to pool.rewardPlan)))
        inventory.setItem(24, keyed(Material.PLAYER_HEAD, "admin.pool.detail.maximum", mapOf("value" to pool.maxActivePlayers.toString())))
        inventory.setItem(30, keyed(Material.REPEATER, "admin.pool.detail.retention", mapOf("value" to pool.progressRetention.toString())))
        inventory.setItem(46, keyed(Material.CARTOGRAPHY_TABLE, "admin.pool.detail.copy")); inventory.setItem(49, keyed(Material.ARROW, "admin.pool.detail.back"))
        inventory.setItem(53, keyed(Material.LAVA_BUCKET, "admin.pool.detail.delete"))
        player.openInventory(inventory)
    }

    private fun openPlanPicker(player: Player, poolId: String) {
        val holder = AdminGuiHolder(AdminGuiHolder.View.PLAN_PICKER, poolId)
        val inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.pool.plan-picker.title")); holder.attach(inventory)
        plans.all().take(45).forEachIndexed { index, plan -> inventory.setItem(index, keyed(Material.WRITABLE_BOOK, "admin.pool.plan-picker.entry", mapOf("id" to plan.id, "count" to plan.rewards.size.toString()))) }
        inventory.setItem(49, keyed(Material.ARROW, "admin.pool.detail.back")); player.openInventory(inventory)
    }

    private fun openDelete(player: Player, poolId: String) {
        val holder = AdminGuiHolder(AdminGuiHolder.View.POOL_DELETE_CONFIRM, poolId)
        val inventory = Bukkit.createInventory(holder, 27, Messages.get("admin.pool.delete.title", mapOf("id" to poolId))); holder.attach(inventory)
        inventory.setItem(11, keyed(Material.LIME_DYE, "admin.pool.delete.confirm")); inventory.setItem(15, keyed(Material.RED_DYE, "admin.pool.delete.cancel")); player.openInventory(inventory)
    }

    @EventHandler fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.getHolder(false) as? AdminGuiHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission("idlepool.admin")) return
        when (holder.view) {
            AdminGuiHolder.View.POOL_LIST -> listClick(player, event.rawSlot)
            AdminGuiHolder.View.POOL_DETAIL -> detailClick(player, holder.poolId, event.rawSlot)
            AdminGuiHolder.View.PLAN_PICKER -> pickerClick(player, holder.poolId, event.rawSlot)
            AdminGuiHolder.View.POOL_DELETE_CONFIRM -> when (event.rawSlot) {
                11 -> save(player, "", editor.deletePool(holder.poolId)) { openList(player) }
                15 -> pools.byId(holder.poolId).ifPresent { openDetail(player, it) }
            }
        }
    }
    @EventHandler fun onDrag(event: InventoryDragEvent) { if (event.view.topInventory.getHolder(false) is AdminGuiHolder) event.isCancelled = true }
    @EventHandler fun onChat(event: AsyncChatEvent) {
        val request = pending.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        val input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()
        Bukkit.getScheduler().runTask(plugin, Runnable { applyInput(event.player, request, input) })
    }

    private fun listClick(player: Player, slot: Int) = when (slot) {
        45 -> request(player, PendingInput(InputKind.CREATE, "", ""), "admin.pool.prompt.create")
        47 -> player.performCommand("afkpool admin rewards")
        49 -> { plugin.reloadIdlePool(); openList(player) }
        51 -> player.performCommand("afkpool admin operations")
        else -> slots.indexOf(slot).takeIf { it >= 0 }?.let { pools.all().getOrNull(it) }?.let { openDetail(player, it) }
    }

    private fun detailClick(player: Player, id: String, slot: Int) {
        val pool = pools.byId(id).orElse(null) ?: run { openList(player); return }
        when (slot) {
            10 -> saveAndDetail(player, id, editor.set(id, "enabled", !pool.enabled))
            12 -> saveAndDetail(player, id, editor.setPosition(id, "min", player.location))
            14 -> saveAndDetail(player, id, editor.setPosition(id, "max", player.location))
            16 -> request(player, PendingInput(InputKind.DURATION, id, "reward-cycle"), "admin.pool.prompt.cycle")
            20 -> request(player, PendingInput(InputKind.TEXT, id, "permission"), "admin.pool.prompt.permission")
            22 -> openPlanPicker(player, id)
            24 -> request(player, PendingInput(InputKind.INTEGER, id, "max-active-players"), "admin.pool.prompt.maximum")
            30 -> request(player, PendingInput(InputKind.DURATION, id, "progress-retention"), "admin.pool.prompt.retention")
            46 -> request(player, PendingInput(InputKind.COPY, id, ""), "admin.pool.prompt.copy")
            49 -> openList(player)
            53 -> openDelete(player, id)
        }
    }

    private fun pickerClick(player: Player, poolId: String, slot: Int) {
        if (slot == 49) { pools.byId(poolId).ifPresent { openDetail(player, it) }; return }
        plans.all().getOrNull(slot)?.let { saveAndDetail(player, poolId, editor.set(poolId, "reward-plan", it.id)) }
    }

    private fun request(player: Player, input: PendingInput, prompt: String) {
        pending[player.uniqueId] = input; player.closeInventory(); Messages.send(player, "admin.input.prompt", mapOf("prompt" to Messages.raw(prompt)))
    }

    private fun applyInput(player: Player, request: PendingInput, input: String) {
        if (input.equals("cancel", true)) { reopen(player, request.poolId); return }
        try {
            when (request.kind) {
                InputKind.CREATE -> { validateId(input); require(pools.byId(input).isEmpty) { Messages.raw("admin.error.pool-exists") }; save(player, input, editor.createPool(input, player.location)) { pools.byId(input).ifPresent { openDetail(player, it) } } }
                InputKind.COPY -> { validateId(input); save(player, input, editor.copyPool(request.poolId, input)) { pools.byId(input).ifPresent { openDetail(player, it) } } }
                InputKind.TEXT -> { require(input.isNotBlank() && input.length <= 128) { Messages.raw("admin.error.invalid-text") }; saveAndDetail(player, request.poolId, editor.set(request.poolId, request.path, input)) }
                InputKind.INTEGER -> { val value = input.toInt(); require(value >= 0) { Messages.raw("admin.error.negative-number") }; saveAndDetail(player, request.poolId, editor.set(request.poolId, request.path, value)) }
                InputKind.DURATION -> { try { DurationParser.parse(input) } catch (_: RuntimeException) { throw IllegalArgumentException(Messages.raw("admin.error.invalid-duration")) }; saveAndDetail(player, request.poolId, editor.set(request.poolId, request.path, input)) }
            }
        } catch (exception: RuntimeException) {
            Messages.send(player, "admin.input.invalid", mapOf("error" to (exception.message ?: "invalid"))); request(player, request, "admin.input.retry")
        }
    }

    private fun validateId(id: String) { require(id.matches(Regex("[a-z0-9_-]{1,32}"))) { Messages.raw("admin.error.invalid-id") } }
    private fun saveAndDetail(player: Player, id: String, future: CompletableFuture<Void>) = save(player, id, future) { pools.byId(id).ifPresent { openDetail(player, it) } }
    private fun save(player: Player, id: String, future: CompletableFuture<Void>, after: () -> Unit) {
        player.closeInventory(); Messages.send(player, "admin.pool.saving")
        future.whenComplete { _, failure -> Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            if (failure != null) { val cause = if (failure is CompletionException) failure.cause else failure; Messages.send(player, "admin.save-failed", mapOf("error" to (cause?.message ?: "unknown"))) }
            else { plugin.reloadIdlePool(); after() }
        }) }
    }
    private fun reopen(player: Player, id: String) { if (id.isBlank()) openList(player) else pools.byId(id).ifPresentOrElse({ openDetail(player, it) }, { openList(player) }) }
    private fun keyed(material: Material, key: String, values: Map<String, String> = emptyMap()) = dynamic(material, Messages.get("$key.name", values), Messages.list("$key.lore", values))
    private fun dynamic(material: Material, name: net.kyori.adventure.text.Component, lore: List<net.kyori.adventure.text.Component>) = ItemStack(material).apply { itemMeta = itemMeta.apply { displayName(name); lore(lore) } }
    override fun close() { pending.clear(); editor.close() }
    private data class PendingInput(val kind: InputKind, val poolId: String, val path: String)
    private enum class InputKind { CREATE, COPY, TEXT, INTEGER, DURATION }
}
