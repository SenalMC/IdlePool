package top.cnuo.idlepool.integration

import org.bukkit.inventory.ItemStack
import java.util.Optional

interface VisualBridge {
    fun available(): Boolean
    fun status(): String
    fun fontImage(namespacedId: String?): String
    fun customItem(namespacedId: String?): Optional<ItemStack>
    fun identifyCustomItem(itemStack: ItemStack?): Optional<String>
}

class VanillaVisualBridge(private val state: String) : VisualBridge {
    override fun available() = false
    override fun status() = state
    override fun fontImage(namespacedId: String?) = ""
    override fun customItem(namespacedId: String?) = Optional.empty<ItemStack>()
    override fun identifyCustomItem(itemStack: ItemStack?) = Optional.empty<String>()
}
