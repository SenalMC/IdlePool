package top.cnuo.idlepool.admin

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class AdminGuiHolder(val view: View, val poolId: String) : InventoryHolder {
    enum class View { POOL_LIST, POOL_DETAIL, PLAN_PICKER, POOL_DELETE_CONFIRM }
    private lateinit var attached: Inventory
    fun view() = view
    fun poolId() = poolId
    fun attach(inventory: Inventory) { attached = inventory }
    override fun getInventory() = attached
}

class RewardAdminGuiHolder(val view: View, val planId: String, val rewardIndex: Int) : InventoryHolder {
    enum class View { PLAN_LIST, PLAN_DETAIL, PLAN_SETTINGS, REWARD_DETAIL, DELETE_CONFIRM, PLAN_DELETE_CONFIRM }
    private lateinit var attached: Inventory
    fun view() = view
    fun planId() = planId
    fun rewardIndex() = rewardIndex
    fun attach(inventory: Inventory) { attached = inventory }
    override fun getInventory() = attached
}
