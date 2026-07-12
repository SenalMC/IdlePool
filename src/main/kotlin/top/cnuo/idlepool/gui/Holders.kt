package top.cnuo.idlepool.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import top.cnuo.idlepool.pool.PoolDefinition
import top.cnuo.idlepool.storage.InboxEntry

class PoolGuiHolder(val pool: PoolDefinition) : InventoryHolder {
    private lateinit var attached: Inventory
    fun pool() = pool
    fun attach(inventory: Inventory) { attached = inventory }
    override fun getInventory() = attached
}

class InboxGuiHolder @JvmOverloads constructor(
    val entries: Map<Int, InboxEntry>,
    val page: Int = 0,
) : InventoryHolder {
    private lateinit var attached: Inventory
    fun entry(slot: Int) = entries[slot]
    fun attach(inventory: Inventory) { attached = inventory }
    override fun getInventory() = attached
}
