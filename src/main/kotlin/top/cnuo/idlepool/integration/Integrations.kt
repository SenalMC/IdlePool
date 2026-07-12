package top.cnuo.idlepool.integration

import dev.lone.itemsadder.api.CustomStack
import dev.lone.itemsadder.api.ItemsAdder
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent
import dev.lone.itemsadder.api.FontImages.FontImageWrapper
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.util.Messages
import java.lang.reflect.Method
import java.util.Optional
import java.util.logging.Level
import java.util.logging.Logger

class EconomyBridge(private val logger: Logger) {
    private val provider: Any?
    private val depositMethod: Method?

    init {
        var service: Any? = null
        var method: Method? = null
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            try {
                val economy = Class.forName("net.milkbowl.vault.economy.Economy")
                @Suppress("UNCHECKED_CAST")
                val registration = Bukkit.getServicesManager().getRegistration(economy as Class<Any>)
                service = registration?.provider
                method = economy.getMethod("depositPlayer", OfflinePlayer::class.java, Double::class.javaPrimitiveType)
            } catch (exception: ReflectiveOperationException) {
                logger.log(Level.WARNING, "Vault 已安装，但无法连接经济服务。", exception)
            }
        }
        provider = service
        depositMethod = method
    }

    fun available() = provider != null && depositMethod != null
    fun deposit(player: OfflinePlayer, amount: Double): Boolean = try {
        if (!available()) false else {
            val response = depositMethod!!.invoke(provider, player, amount)
            response.javaClass.getMethod("transactionSuccess").invoke(response) == true
        }
    } catch (exception: ReflectiveOperationException) {
        logger.log(Level.SEVERE, "发放 Vault 货币失败。", exception)
        false
    }
}

@Suppress("DEPRECATION")
class ItemsAdderVisualBridge(private val plugin: JavaPlugin) : VisualBridge, Listener {
    @Volatile private var loaded = ItemsAdder.areItemsLoaded()

    init { plugin.server.pluginManager.registerEvents(this, plugin) }

    @EventHandler
    fun onItemsAdderLoaded(event: ItemsAdderLoadDataEvent) {
        loaded = true
        plugin.logger.info("ItemsAdder 数据已加载，挂机池物品和 ActionBar 图标已就绪。")
    }

    override fun available() = loaded
    override fun status() = Messages.raw(if (loaded) "itemsadder.status.ready" else "itemsadder.status.waiting")
    override fun fontImage(namespacedId: String?): String {
        if (!loaded || namespacedId.isNullOrBlank()) return ""
        return runCatching { FontImageWrapper(namespacedId).string }.getOrElse {
            plugin.logger.log(Level.WARNING, "无法读取 ItemsAdder 字体图片 $namespacedId", it); ""
        }
    }

    override fun customItem(namespacedId: String?): Optional<ItemStack> {
        if (!loaded || namespacedId.isNullOrBlank()) return Optional.empty()
        return runCatching { Optional.ofNullable(CustomStack.getInstance(namespacedId)?.itemStack) }.getOrElse {
            plugin.logger.log(Level.WARNING, "无法读取 ItemsAdder 物品 $namespacedId", it); Optional.empty()
        }
    }

    override fun identifyCustomItem(itemStack: ItemStack?): Optional<String> {
        if (!loaded || itemStack == null || itemStack.type.isAir) return Optional.empty()
        return runCatching { Optional.ofNullable(CustomStack.byItemStack(itemStack)?.namespacedID) }.getOrElse {
            plugin.logger.log(Level.WARNING, "无法识别 ItemsAdder 物品。", it); Optional.empty()
        }
    }
}
