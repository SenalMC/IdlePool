package top.cnuo.idlepool

import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.admin.PoolAdminGuiManager
import top.cnuo.idlepool.admin.RewardAdminGuiManager
import top.cnuo.idlepool.admin.OperationsGuiManager
import top.cnuo.idlepool.activity.ActivityEventRepository
import top.cnuo.idlepool.api.IdlePoolApi
import top.cnuo.idlepool.api.IdlePoolApiImpl
import top.cnuo.idlepool.command.AfkPoolCommand
import top.cnuo.idlepool.config.ConfigMigrator
import top.cnuo.idlepool.diagnostic.DoctorService
import top.cnuo.idlepool.diagnostic.PluginInfoService
import top.cnuo.idlepool.gui.InboxGuiManager
import top.cnuo.idlepool.gui.PoolGuiManager
import top.cnuo.idlepool.integration.*
import top.cnuo.idlepool.listener.PoolPresenceListener
import top.cnuo.idlepool.pool.PoolRepository
import top.cnuo.idlepool.reward.*
import top.cnuo.idlepool.session.SessionManager
import top.cnuo.idlepool.stats.StatsGuiManager
import top.cnuo.idlepool.storage.SqliteStore
import top.cnuo.idlepool.update.UpdateChecker
import top.cnuo.idlepool.util.DurationParser
import top.cnuo.idlepool.util.Messages
import java.io.File
import org.bukkit.plugin.ServicePriority

class IdlePoolPlugin : JavaPlugin() {
    private lateinit var pools: PoolRepository
    private lateinit var rewardPlans: RewardPlanRepository
    private lateinit var store: SqliteStore
    private lateinit var sessions: SessionManager
    private lateinit var adminGui: PoolAdminGuiManager
    private lateinit var rewardAdminGui: RewardAdminGuiManager
    private lateinit var snapshots: SnapshotRepository
    private lateinit var activityEvents: ActivityEventRepository

    override fun onEnable() {
        saveDefaultConfig(); listOf("pools.yml","rewards.yml","events.yml","message.yml","en.yml").forEach(::saveBundledFile)
        try { ConfigMigrator(this).migrate(); reloadConfig() } catch (exception: Exception) {
            logger.severe("配置迁移失败，插件将被禁用：${exception.message}"); server.pluginManager.disablePlugin(this); return
        }
        store = SqliteStore(this, File(dataFolder, config.getString("storage.file", "data.db") ?: "data.db"))
        try { store.initialize() } catch (exception: RuntimeException) {
            logger.severe("SQLite 初始化失败，插件将被禁用：${exception.message}"); server.pluginManager.disablePlugin(this); return
        }
        pools = PoolRepository(this); rewardPlans = RewardPlanRepository(this); snapshots = SnapshotRepository(this); activityEvents = ActivityEventRepository(this); reloadIdlePool()
        val visuals = createVisualBridge(); val updater = UpdateChecker(this); val economy = EconomyBridge(logger)
        val factory = ItemRewardFactory(visuals, logger, snapshots); val inbox = InboxGuiManager(this, store, factory)
        val rewards = RewardService(this, rewardPlans, store, economy, activityEvents); sessions = SessionManager(this, store, rewards, visuals, inbox, activityEvents)
        val poolGui = PoolGuiManager(this, visuals, sessions)
        server.servicesManager.register(IdlePoolApi::class.java, IdlePoolApiImpl(sessions, activityEvents), this, ServicePriority.Normal)
        val statsGui = StatsGuiManager(this, store); val operationsGui = OperationsGuiManager(this, store)
        adminGui = PoolAdminGuiManager(this, pools, rewardPlans)
        rewardAdminGui = RewardAdminGuiManager(this, rewardPlans, factory, HeldItemIdentifier(visuals, logger))
        listOf(poolGui, inbox, adminGui, rewardAdminGui, statsGui, operationsGui, PoolPresenceListener(this, pools, poolGui, sessions, store)).forEach { server.pluginManager.registerEvents(it, this) }
        val command = AfkPoolCommand(this, pools, poolGui, sessions, adminGui, rewardAdminGui, operationsGui, statsGui, inbox, activityEvents, DoctorService(pools, rewardPlans, visuals, economy), PluginInfoService(this, visuals, updater))
        requireNotNull(getCommand("afkpool")) { "Command afkpool is missing from plugin.yml" }.apply { setExecutor(command); tabCompleter = command }
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            runCatching { IdlePoolPlaceholderExpansion(this, sessions, store, activityEvents).register() }
                .onFailure { logger.warning("PlaceholderAPI 扩展注册失败：${it.message}") }
        }
        sessions.beginTicking(); updater.checkAsync(); logger.info("IdlePool v${pluginMeta.version} 已启用（Kotlin）。ItemsAdder=${visuals.available()}, Vault=${economy.available()}")
    }

    override fun onDisable() {
        if (::sessions.isInitialized) sessions.close(); if (::adminGui.isInitialized) adminGui.close(); if (::rewardAdminGui.isInitialized) rewardAdminGui.close(); if (::activityEvents.isInitialized) activityEvents.close(); if (::store.isInitialized) store.close()
    }

    fun reloadIdlePool() {
        reloadConfig(); Messages.load(this); val retention = DurationParser.parse(config.getString("progress.default-retention", "7d"))
        if (::pools.isInitialized) pools.reload(retention); if (::rewardPlans.isInitialized) rewardPlans.reload(); if (::snapshots.isInitialized) snapshots.reload(); if (::activityEvents.isInitialized) activityEvents.reload()
    }

    private fun createVisualBridge(): VisualBridge {
        if (!server.pluginManager.isPluginEnabled("ItemsAdder")) { logger.warning("未检测到 ItemsAdder，将使用原版按钮和 ActionBar 文本。"); return VanillaVisualBridge(Messages.raw("itemsadder.status.not-installed")) }
        return try { ItemsAdderVisualBridge(this) } catch (error: Throwable) {
            val detail = error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName
            logger.warning("ItemsAdder API 不兼容，将使用原版图标：$detail"); VanillaVisualBridge(Messages.raw("itemsadder.status.error", mapOf("error" to detail)))
        }
    }
    private fun saveBundledFile(name: String) { if (!File(dataFolder, name).exists()) saveResource(name, false) }
}
