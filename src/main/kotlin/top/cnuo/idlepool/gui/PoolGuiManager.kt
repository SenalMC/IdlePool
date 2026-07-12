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
import top.cnuo.idlepool.integration.VisualBridge
import top.cnuo.idlepool.pool.PoolDefinition
import top.cnuo.idlepool.session.SessionManager
import top.cnuo.idlepool.util.Messages

class PoolGuiManager(
    private val plugin: JavaPlugin,
    private val visuals: VisualBridge,
    private val sessions: SessionManager,
) : Listener {
    fun open(player: Player, pool: PoolDefinition) {
        val holder = PoolGuiHolder(pool)
        val inventory = Bukkit.createInventory(holder, 27, Messages.parse(pool.displayName))
        holder.attach(inventory)
        inventory.setItem(11, item(pool.visuals.infoItem, Material.BOOK, "gui.pool.info", emptyMap()))
        val active = sessions.isActive(player.uniqueId)
        inventory.setItem(13, item(pool.visuals.startItem, if (active) Material.RED_DYE else Material.LIME_DYE, if (active) "gui.pool.stop" else "gui.pool.start", emptyMap()))
        inventory.setItem(15, item(pool.visuals.rewardsItem, Material.CHEST, "gui.pool.rewards", mapOf("plan" to pool.rewardPlan)))
        player.openInventory(inventory)
    }

    @EventHandler fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.getHolder(false) as? PoolGuiHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (event.rawSlot != 13) return
        if (sessions.isActive(player.uniqueId)) { player.closeInventory(); sessions.stop(player, false) } else sessions.start(player, holder.pool)
    }
    @EventHandler fun onDrag(event: InventoryDragEvent) { if (event.view.topInventory.getHolder(false) is PoolGuiHolder) event.isCancelled = true }

    private fun item(customId: String, fallback: Material, key: String, placeholders: Map<String, String>): ItemStack =
        visuals.customItem(customId).orElseGet { ItemStack(fallback) }.apply {
            itemMeta = itemMeta.apply { displayName(Messages.get("$key.name", placeholders)); lore(Messages.list("$key.lore", placeholders)) }
        }
}
