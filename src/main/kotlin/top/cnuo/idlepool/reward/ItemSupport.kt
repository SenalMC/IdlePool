package top.cnuo.idlepool.reward

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.integration.VisualBridge
import java.io.File
import java.util.Base64
import java.util.Locale
import java.util.Optional
import java.util.logging.Level
import java.util.logging.Logger

class SnapshotRepository(private val plugin: JavaPlugin) {
    private val file = File(plugin.dataFolder, "reward-snapshots.yml")
    @Volatile private var snapshots: Map<String, ItemStack> = emptyMap()

    fun reload() {
        if (!file.exists()) { snapshots = emptyMap(); return }
        val root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("snapshots")
            ?: run { snapshots = emptyMap(); return }
        snapshots = root.getKeys(false).mapNotNull { id ->
            val encoded = root.getString(id)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            try { id to ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)) }
            catch (exception: RuntimeException) { plugin.logger.log(Level.WARNING, "无法读取物品快照 $id", exception); null }
        }.toMap()
    }

    fun find(id: String) = Optional.ofNullable(snapshots[id]?.clone())

    companion object {
        @JvmStatic fun encode(itemStack: ItemStack) = Base64.getEncoder().encodeToString(itemStack.serializeAsBytes())
    }
}

class HeldItemIdentifier(private val visuals: VisualBridge, private val logger: Logger) {
    fun identify(original: ItemStack?): Optional<IdentifiedItem> {
        if (original == null || original.type.isAir || original.amount <= 0) return Optional.empty()
        val amount = original.amount
        val itemsAdder = visuals.identifyCustomItem(original).orElse(null)
        if (itemsAdder != null) return Optional.of(IdentifiedItem("itemsadder", itemsAdder, amount, null))
        identifyMmoItems(original)?.let { return Optional.of(IdentifiedItem("mmoitems", it, amount, null)) }
        identifySlimefun(original)?.let { return Optional.of(IdentifiedItem("slimefun", it, amount, null)) }
        if (!original.hasItemMeta()) return Optional.of(IdentifiedItem("vanilla", original.type.name, amount, null))
        return Optional.of(IdentifiedItem("snapshot", "", amount, original.clone().also { it.amount = 1 }))
    }

    private fun identifyMmoItems(stack: ItemStack): String? {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return null
        return runCatching {
            val api = Class.forName("net.Indyuce.mmoitems.MMOItems")
            val type = api.getMethod("getTypeName", ItemStack::class.java).invoke(null, stack) as? String
            val id = api.getMethod("getID", ItemStack::class.java).invoke(null, stack) as? String
            if (type.isNullOrBlank() || id.isNullOrBlank()) null else "$type:$id"
        }.getOrElse { logger.log(Level.FINE, "无法反查 MMOItems 物品。", it); null }
    }

    private fun identifySlimefun(stack: ItemStack): String? {
        if (!Bukkit.getPluginManager().isPluginEnabled("Slimefun")) return null
        return runCatching {
            val api = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem")
            api.getMethod("getByItem", ItemStack::class.java).invoke(null, stack)?.let { api.getMethod("getId").invoke(it) as String }
        }.getOrElse { logger.log(Level.FINE, "无法反查 Slimefun 物品。", it); null }
    }
}

class ItemRewardFactory(
    private val visuals: VisualBridge,
    private val logger: Logger,
    private val snapshots: SnapshotRepository,
) {
    fun preview(provider: String, itemId: String): Optional<ItemStack> = when (provider.lowercase(Locale.ROOT)) {
        "vanilla" -> Optional.ofNullable(Material.matchMaterial(itemId)?.takeIf(Material::isItem)?.let(::ItemStack))
        "itemsadder" -> visuals.customItem(itemId)
        "snapshot" -> snapshots.find(itemId)
        "mythicmobs" -> reflectiveItem("MythicMobs") {
            val api = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
            val instance = api.getMethod("inst").invoke(null)
            val manager = api.getMethod("getItemManager").invoke(instance)
            manager.javaClass.getMethod("getItemStack", String::class.java).invoke(manager, itemId) as? ItemStack
        }
        "mmoitems" -> reflectiveItem("MMOItems") {
            val parts = itemId.split(':', limit = 2); if (parts.size != 2) return@reflectiveItem null
            val api = Class.forName("net.Indyuce.mmoitems.MMOItems")
            val instance = api.getField("plugin").get(null)
            val types = instance.javaClass.getMethod("getTypes").invoke(instance)
            val type = types.javaClass.getMethod("get", String::class.java).invoke(types, parts[0]) ?: return@reflectiveItem null
            instance.javaClass.methods.firstOrNull { it.name == "getItem" && it.parameterCount == 2 && it.parameterTypes[1] == String::class.java }
                ?.invoke(instance, type, parts[1]) as? ItemStack
        }
        "slimefun" -> reflectiveItem("Slimefun") {
            val api = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem")
            api.getMethod("getById", String::class.java).invoke(null, itemId)?.let { api.getMethod("getItem").invoke(it) as? ItemStack }
        }
        else -> Optional.empty()
    }

    fun create(provider: String, itemId: String, amount: Int): Optional<List<ItemStack>> {
        val template = preview(provider, itemId).orElse(null) ?: return Optional.empty()
        var remaining = amount
        val stacks = mutableListOf<ItemStack>()
        while (remaining > 0) {
            val current = minOf(template.maxStackSize.coerceAtLeast(1), remaining)
            stacks += template.clone().also { it.amount = current }
            remaining -= current
        }
        return Optional.of(stacks)
    }

    private fun reflectiveItem(pluginName: String, supplier: () -> ItemStack?): Optional<ItemStack> {
        if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) return Optional.empty()
        return runCatching { Optional.ofNullable(supplier()?.clone()) }.getOrElse {
            logger.log(Level.WARNING, "无法通过 $pluginName 生成奖励物品。", it); Optional.empty()
        }
    }
}
