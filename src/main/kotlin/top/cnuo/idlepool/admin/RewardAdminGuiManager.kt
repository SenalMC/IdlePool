package top.cnuo.idlepool.admin

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
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
import top.cnuo.idlepool.reward.*
import top.cnuo.idlepool.util.DurationParser
import top.cnuo.idlepool.util.Messages
import top.cnuo.idlepool.util.TimeFormats
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap

class RewardAdminGuiManager(
    private val plugin: IdlePoolPlugin,
    private val plans: RewardPlanRepository,
    private val items: ItemRewardFactory,
    private val identifier: HeldItemIdentifier,
) : Listener, AutoCloseable {
    private val editor = RewardConfigEditor(plugin)
    private val pending = ConcurrentHashMap<UUID, PendingInput>()

    fun openList(player: Player) {
        val holder = RewardAdminGuiHolder(RewardAdminGuiHolder.View.PLAN_LIST, "", -1)
        val inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.reward.list.title")); holder.attach(inventory)
        plans.all().take(45).forEachIndexed { index, plan -> inventory.setItem(index, keyed(Material.WRITABLE_BOOK, "admin.reward.list.entry", mapOf("id" to plan.id, "count" to plan.rewards.size.toString(), "mode" to selectionLabel(plan.selectionMode)))) }
        inventory.setItem(45, keyed(Material.ANVIL, "admin.reward.list.create")); inventory.setItem(49, keyed(Material.ARROW, "admin.reward.list.back"))
        player.openInventory(inventory)
    }

    fun openPlan(player: Player, plan: RewardPlan) {
        val holder = RewardAdminGuiHolder(RewardAdminGuiHolder.View.PLAN_DETAIL, plan.id, -1)
        val inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.reward.plan.title", mapOf("id" to plan.id))); holder.attach(inventory)
        plan.rewards.take(45).forEachIndexed { index, reward -> inventory.setItem(index, rewardIcon(reward, index)) }
        inventory.setItem(45, keyed(Material.HOPPER, "admin.reward.plan.add-held")); inventory.setItem(46, keyed(Material.COMMAND_BLOCK, "admin.reward.plan.add-command"))
        inventory.setItem(47, keyed(Material.GOLD_INGOT, "admin.reward.plan.add-money")); inventory.setItem(48, keyed(Material.NAME_TAG, "admin.reward.plan.add-id"))
        inventory.setItem(49, keyed(Material.ARROW, "admin.reward.plan.back")); inventory.setItem(50, keyed(Material.CARTOGRAPHY_TABLE, "admin.reward.plan.copy"))
        inventory.setItem(51, keyed(Material.COMPARATOR, "admin.reward.plan.settings", mapOf("mode" to selectionLabel(plan.selectionMode))))
        inventory.setItem(53, keyed(Material.LAVA_BUCKET, "admin.reward.plan.delete")); player.openInventory(inventory)
    }

    private fun openSettings(player: Player, plan: RewardPlan) {
        val holder = RewardAdminGuiHolder(RewardAdminGuiHolder.View.PLAN_SETTINGS, plan.id, -1)
        val inventory = Bukkit.createInventory(holder, 27, Messages.get("admin.reward.settings.title", mapOf("id" to plan.id))); holder.attach(inventory)
        inventory.setItem(11, keyed(Material.COMPARATOR, "admin.reward.settings.mode", mapOf("mode" to selectionLabel(plan.selectionMode))))
        inventory.setItem(13, keyed(Material.HOPPER, "admin.reward.settings.draw-count", mapOf("count" to plan.drawCount.toString())))
        inventory.setItem(15, keyed(if (plan.pity.enabled) Material.TOTEM_OF_UNDYING else Material.GRAY_DYE, "admin.reward.settings.pity", mapOf(
            "status" to Messages.raw(if (plan.pity.enabled) "common.enabled" else "common.disabled"),
            "cycles" to plan.pity.afterCycles.toString(), "index" to (plan.pity.rewardIndex + 1).toString(),
        )))
        inventory.setItem(22, keyed(Material.ARROW, "admin.reward.detail.back")); player.openInventory(inventory)
    }

    fun openReward(player: Player, planId: String, index: Int) {
        val reward = reward(planId, index) ?: run { openList(player); return }
        val holder = RewardAdminGuiHolder(RewardAdminGuiHolder.View.REWARD_DETAIL, planId, index)
        val inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.reward.detail.title", mapOf("index" to (index + 1).toString()))); holder.attach(inventory)
        inventory.setItem(13, rewardIcon(reward, index))
        inventory.setItem(27, keyed(Material.NETHER_STAR, "admin.reward.detail.weight", mapOf("value" to reward.weight.toString())))
        when (reward.type) {
            RewardType.ITEM -> inventory.setItem(29, keyed(Material.CHEST, "admin.reward.detail.amount", mapOf("value" to reward.itemAmount.toString())))
            RewardType.MONEY -> inventory.setItem(29, keyed(Material.GOLD_INGOT, "admin.reward.detail.money", mapOf("value" to reward.moneyAmount.toString())))
            RewardType.COMMAND -> inventory.setItem(29, keyed(Material.COMMAND_BLOCK, "admin.reward.detail.command", mapOf("value" to abbreviate(reward.command, 32))))
        }
        inventory.setItem(31, keyed(Material.PAPER, "admin.reward.detail.chance", mapOf("value" to reward.chance.toString())))
        inventory.setItem(33, keyed(if (reward.trigger == RewardTrigger.CYCLE) Material.REPEATER else Material.CLOCK,
            if (reward.trigger == RewardTrigger.CYCLE) "admin.reward.detail.trigger-cycle" else "admin.reward.detail.trigger-milestone", mapOf("value" to triggerLabel(reward))))
        inventory.setItem(35, keyed(Material.CLOCK, if (reward.trigger == RewardTrigger.CYCLE) "admin.reward.detail.unlock-cycle" else "admin.reward.detail.unlock-milestone", mapOf("value" to unlockLabel(reward))))
        inventory.setItem(45, keyed(Material.LAVA_BUCKET, "admin.reward.detail.delete")); inventory.setItem(49, keyed(Material.ARROW, "admin.reward.detail.back"))
        player.openInventory(inventory)
    }

    private fun openConfirm(player: Player, planId: String, index: Int) {
        val holder = RewardAdminGuiHolder(RewardAdminGuiHolder.View.DELETE_CONFIRM, planId, index)
        val inventory = Bukkit.createInventory(holder, 27, Messages.get("admin.reward.delete.title")); holder.attach(inventory)
        inventory.setItem(11, keyed(Material.LIME_DYE, "admin.reward.delete.confirm")); inventory.setItem(15, keyed(Material.RED_DYE, "admin.reward.delete.cancel")); player.openInventory(inventory)
    }
    private fun openPlanConfirm(player: Player, planId: String) {
        val holder = RewardAdminGuiHolder(RewardAdminGuiHolder.View.PLAN_DELETE_CONFIRM, planId, -1)
        val inventory = Bukkit.createInventory(holder, 27, Messages.get("admin.reward.plan-delete.title", mapOf("id" to planId))); holder.attach(inventory)
        inventory.setItem(11, keyed(Material.LIME_DYE, "admin.reward.plan-delete.confirm")); inventory.setItem(15, keyed(Material.RED_DYE, "admin.reward.plan-delete.cancel")); player.openInventory(inventory)
    }

    @EventHandler fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.getHolder(false) as? RewardAdminGuiHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission("idlepool.admin")) return
        when (holder.view) {
            RewardAdminGuiHolder.View.PLAN_LIST -> listClick(player, event.rawSlot)
            RewardAdminGuiHolder.View.PLAN_DETAIL -> planClick(player, holder.planId, event.rawSlot)
            RewardAdminGuiHolder.View.PLAN_SETTINGS -> settingsClick(player, holder.planId, event.rawSlot)
            RewardAdminGuiHolder.View.REWARD_DETAIL -> rewardClick(player, holder.planId, holder.rewardIndex, event.rawSlot)
            RewardAdminGuiHolder.View.DELETE_CONFIRM -> when (event.rawSlot) { 11 -> savePlan(player, holder.planId, editor.remove(holder.planId, holder.rewardIndex)); 15 -> openReward(player, holder.planId, holder.rewardIndex) }
            RewardAdminGuiHolder.View.PLAN_DELETE_CONFIRM -> when (event.rawSlot) { 11 -> save(player, editor.deletePlan(holder.planId)) { openList(player) }; 15 -> plans.find(holder.planId).ifPresent { openPlan(player, it) } }
        }
    }
    @EventHandler fun onDrag(event: InventoryDragEvent) { if (event.view.topInventory.getHolder(false) is RewardAdminGuiHolder) event.isCancelled = true }
    @EventHandler fun onChat(event: AsyncChatEvent) {
        val request = pending.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        val input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()
        Bukkit.getScheduler().runTask(plugin, Runnable { applyInput(event.player, request, input) })
    }

    private fun listClick(player: Player, slot: Int) = when (slot) {
        45 -> request(player, PendingInput(InputKind.CREATE_PLAN, "", -1), "admin.reward.prompt.create-plan")
        49 -> player.performCommand("afkpool admin")
        else -> plans.all().getOrNull(slot)?.let { openPlan(player, it) }
    }
    private fun planClick(player: Player, id: String, slot: Int) {
        val plan = plans.find(id).orElse(null) ?: run { openList(player); return }
        when (slot) {
            in 0..44 -> plan.rewards.getOrNull(slot)?.let { openReward(player, id, slot) }
            45 -> addHeld(player, id)
            46 -> request(player, PendingInput(InputKind.ADD_COMMAND, id, -1), "admin.reward.prompt.add-command")
            47 -> request(player, PendingInput(InputKind.ADD_MONEY, id, -1), "admin.reward.prompt.add-money")
            48 -> request(player, PendingInput(InputKind.ADD_ITEM_ID, id, -1), "admin.reward.prompt.add-item-id")
            49 -> openList(player)
            50 -> request(player, PendingInput(InputKind.COPY_PLAN, id, -1), "admin.reward.prompt.copy-plan")
            51 -> openSettings(player, plan)
            53 -> openPlanConfirm(player, id)
        }
    }
    private fun settingsClick(player: Player, id: String, slot: Int) {
        val plan = plans.find(id).orElse(null) ?: run { openList(player); return }
        when (slot) {
            11 -> {
                val next = when (plan.selectionMode) { SelectionMode.INDEPENDENT -> "weighted-one"; SelectionMode.WEIGHTED_ONE -> "weighted-multiple"; SelectionMode.WEIGHTED_MULTIPLE -> "independent" }
                save(player, editor.setPlan(id, "selection-mode", next)) { plans.find(id).ifPresent { openSettings(player, it) } }
            }
            13 -> request(player, PendingInput(InputKind.DRAW_COUNT, id, -1), "admin.reward.prompt.draw-count")
            15 -> request(player, PendingInput(InputKind.PITY, id, -1), "admin.reward.prompt.pity")
            22 -> openPlan(player, plan)
        }
    }
    private fun rewardClick(player: Player, plan: String, index: Int, slot: Int) {
        val reward = reward(plan, index) ?: run { openList(player); return }
        when (slot) {
            27 -> request(player, PendingInput(InputKind.WEIGHT, plan, index), "admin.reward.prompt.weight")
            29 -> request(player, PendingInput(when (reward.type) { RewardType.ITEM -> InputKind.ITEM_AMOUNT; RewardType.MONEY -> InputKind.MONEY_AMOUNT; RewardType.COMMAND -> InputKind.EDIT_COMMAND }, plan, index),
                when (reward.type) { RewardType.ITEM -> "admin.reward.prompt.item-amount"; RewardType.MONEY -> "admin.reward.prompt.money-amount"; RewardType.COMMAND -> "admin.reward.prompt.edit-command" })
            31 -> request(player, PendingInput(InputKind.CHANCE, plan, index), "admin.reward.prompt.chance")
            33 -> saveReward(player, plan, index, editor.setTrigger(plan, index, if (reward.trigger == RewardTrigger.CYCLE) "session-milestone" else "cycle"))
            35 -> request(player, PendingInput(InputKind.UNLOCK, plan, index), if (reward.trigger == RewardTrigger.CYCLE) "admin.reward.prompt.unlock-cycle" else "admin.reward.prompt.unlock-milestone")
            45 -> openConfirm(player, plan, index)
            49 -> plans.find(plan).ifPresent { openPlan(player, it) }
        }
    }

    private fun addHeld(player: Player, plan: String) {
        val identified = identifier.identify(player.inventory.itemInMainHand).orElse(null) ?: run { Messages.send(player, "admin.reward.no-held-item"); return }
        val source: String
        val future = if (identified.requiresSnapshot()) {
            val id = "snapshot_${UUID.randomUUID().toString().replace("-", "")}"; source = "snapshot"
            editor.addSnapshot(plan, id, SnapshotRepository.encode(identified.snapshot!!), identified.amount)
        } else { source = "${identified.provider}:${identified.itemId}"; editor.addItem(plan, identified.provider, identified.itemId, identified.amount) }
        Messages.send(player, "admin.reward.identified", mapOf("provider" to source)); savePlan(player, plan, future)
    }

    private fun applyInput(player: Player, request: PendingInput, input: String) {
        if (input.equals("cancel", true)) { reopen(player, request); return }
        try {
            val future = when (request.kind) {
                InputKind.CREATE_PLAN -> { validateId(input); editor.createPlan(input) }
                InputKind.COPY_PLAN -> { validateId(input); editor.copyPlan(request.planId, input) }
                InputKind.ADD_COMMAND -> editor.addCommand(request.planId, command(input))
                InputKind.ADD_MONEY -> editor.addMoney(request.planId, positiveDouble(input))
                InputKind.ADD_ITEM_ID -> providerItem(input).let { editor.addItem(request.planId, it.first, it.second, 1) }
                InputKind.ITEM_AMOUNT -> editor.update(request.planId, request.index, "amount", positiveInt(input))
                InputKind.MONEY_AMOUNT -> editor.update(request.planId, request.index, "amount", positiveDouble(input))
                InputKind.EDIT_COMMAND -> editor.update(request.planId, request.index, "command", command(input))
                InputKind.CHANCE -> editor.update(request.planId, request.index, "chance", chance(input))
                InputKind.WEIGHT -> editor.update(request.planId, request.index, "weight", weight(input))
                InputKind.UNLOCK -> editor.update(request.planId, request.index, "unlock-after", unlock(input, reward(request.planId, request.index)))
                InputKind.DRAW_COUNT -> editor.setPlan(request.planId, "draw-count", positiveInt(input).coerceAtMost(45))
                InputKind.PITY -> pity(request.planId, input)
            }
            when {
                request.kind == InputKind.CREATE_PLAN || request.kind == InputKind.COPY_PLAN -> save(player, future) { plans.find(input).ifPresent { openPlan(player, it) } }
                request.index >= 0 -> saveReward(player, request.planId, request.index, future)
                request.kind == InputKind.DRAW_COUNT || request.kind == InputKind.PITY -> save(player, future) { plans.find(request.planId).ifPresent { openSettings(player, it) } }
                else -> savePlan(player, request.planId, future)
            }
        } catch (exception: RuntimeException) {
            Messages.send(player, "admin.input.invalid", mapOf("error" to (exception.message ?: "invalid"))); request(player, request, "admin.input.retry")
        }
    }

    private fun request(player: Player, input: PendingInput, prompt: String) { pending[player.uniqueId] = input; player.closeInventory(); Messages.send(player, "admin.input.prompt", mapOf("prompt" to Messages.raw(prompt))) }
    private fun savePlan(player: Player, plan: String, future: CompletableFuture<Void>) = save(player, future) { plans.find(plan).ifPresentOrElse({ openPlan(player, it) }, { openList(player) }) }
    private fun saveReward(player: Player, plan: String, index: Int, future: CompletableFuture<Void>) = save(player, future) { openReward(player, plan, index) }
    private fun save(player: Player, future: CompletableFuture<Void>, after: () -> Unit) {
        player.closeInventory(); Messages.send(player, "admin.reward.saving")
        future.whenComplete { _, failure -> Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            if (failure != null) { val cause = if (failure is CompletionException) failure.cause else failure; Messages.send(player, "admin.save-failed", mapOf("error" to (cause?.message ?: "unknown"))) }
            else { plugin.reloadIdlePool(); after() }
        }) }
    }
    private fun reopen(player: Player, request: PendingInput) { if (request.planId.isBlank()) openList(player) else if (request.index >= 0) openReward(player, request.planId, request.index) else plans.find(request.planId).ifPresent { if (request.kind == InputKind.DRAW_COUNT || request.kind == InputKind.PITY) openSettings(player, it) else openPlan(player, it) } }
    private fun reward(plan: String, index: Int) = plans.find(plan).orElse(null)?.rewards?.getOrNull(index)
    private fun validateId(value: String) { require(value.matches(Regex("[a-z0-9_-]{1,32}"))) { Messages.raw("admin.error.invalid-id") } }
    private fun command(value: String) = value.removePrefix("/").also { require(it.isNotBlank() && it.length <= 512) { Messages.raw("admin.error.invalid-command") } }
    private fun positiveInt(value: String) = value.toInt().also { require(it in 1..1_000_000) { Messages.raw("admin.error.invalid-amount") } }
    private fun positiveDouble(value: String) = value.toDouble().also { require(it.isFinite() && it > 0 && it <= 1_000_000_000) { Messages.raw("admin.error.invalid-money") } }
    private fun chance(value: String) = value.toDouble().also { require(it.isFinite() && it in 0.0..100.0) { Messages.raw("admin.error.invalid-chance") } }
    private fun weight(value: String) = value.toDouble().also { require(it.isFinite() && it in 0.0..1_000_000.0) { Messages.raw("admin.error.invalid-weight") } }
    private fun pity(plan: String, value: String): CompletableFuture<Void> {
        if (value.equals("off", true) || value == "0") return editor.setPity(plan, false)
        val parts = value.split(':')
        require(parts.size == 2) { Messages.raw("admin.error.invalid-pity") }
        val cycles = parts[0].toInt(); val index = parts[1].toInt()
        val rewardCount = plans.find(plan).orElseThrow().rewards.size
        require(cycles in 1..1_000_000 && index in 1..rewardCount) { Messages.raw("admin.error.invalid-pity") }
        return editor.setPity(plan, true, cycles, index)
    }
    private fun unlock(value: String, reward: RewardDefinition?): String {
        require(reward != null) { Messages.raw("admin.error.reward-missing") }; val normalized = value.trim().lowercase()
        if (normalized in setOf("0", "0s", "none")) { require(reward.trigger != RewardTrigger.SESSION_MILESTONE) { Messages.raw("admin.error.milestone-zero") }; return "0s" }
        try { DurationParser.parse(normalized) } catch (_: RuntimeException) { throw IllegalArgumentException(Messages.raw("admin.error.invalid-duration")) }; return normalized
    }
    private fun providerItem(value: String): Pair<String, String> { val index = value.indexOf(':'); require(index > 0 && index < value.lastIndex) { Messages.raw("admin.error.provider-format") }; val provider = value.substring(0, index).lowercase(); require(provider in setOf("vanilla","itemsadder","mythicmobs","mmoitems","slimefun")) { Messages.raw("admin.error.unsupported-provider", mapOf("provider" to provider)) }; return provider to value.substring(index + 1) }
    private fun triggerLabel(reward: RewardDefinition) = Messages.raw(if (reward.trigger == RewardTrigger.CYCLE) "admin.reward.label.cycle" else "admin.reward.label.milestone")
    private fun selectionLabel(mode: SelectionMode) = Messages.raw("admin.reward.label.${mode.name.lowercase().replace('_','-')}")
    private fun unlockLabel(reward: RewardDefinition) = if (reward.unlockAfter.isZero) Messages.raw("admin.reward.label.immediate") else TimeFormats.clock(reward.unlockAfter.seconds)
    private fun rewardIcon(reward: RewardDefinition, index: Int): ItemStack {
        val icon = when (reward.type) { RewardType.ITEM -> items.preview(reward.provider, reward.itemId).orElseGet { ItemStack(Material.BARRIER) }; RewardType.MONEY -> ItemStack(Material.GOLD_INGOT); RewardType.COMMAND -> ItemStack(Material.COMMAND_BLOCK) }
        icon.itemMeta = icon.itemMeta.apply {
            if (!hasDisplayName()) displayName(Messages.get(when (reward.type) { RewardType.ITEM -> "admin.reward.icon.item-name"; RewardType.MONEY -> "admin.reward.icon.money-name"; RewardType.COMMAND -> "admin.reward.icon.command-name" }, mapOf("provider" to reward.provider, "id" to reward.itemId, "amount" to reward.moneyAmount.toString())))
            val lines = Messages.list("admin.reward.icon.lore", mapOf("index" to (index + 1).toString(), "chance" to reward.chance.toString(), "weight" to reward.weight.toString(), "trigger" to triggerLabel(reward), "time" to unlockLabel(reward))).toMutableList()
            if (reward.type == RewardType.ITEM) lines += Messages.get("admin.reward.icon.item-amount", mapOf("amount" to reward.itemAmount.toString()))
            if (reward.type == RewardType.COMMAND) lines += Messages.get("admin.reward.icon.command", mapOf("command" to abbreviate(reward.command, 36)))
            lines += Messages.list("admin.reward.icon.footer"); lore(lines)
        }; icon.amount = 1; return icon
    }
    private fun keyed(material: Material, key: String, values: Map<String,String> = emptyMap()) = ItemStack(material).apply { itemMeta = itemMeta.apply { displayName(Messages.get("$key.name", values)); lore(Messages.list("$key.lore", values)) } }
    private fun abbreviate(value: String, max: Int) = if (value.length <= max) value else value.take(max - 1) + "…"
    override fun close() { pending.clear(); editor.close() }
    private data class PendingInput(val kind: InputKind, val planId: String, val index: Int)
    private enum class InputKind { CREATE_PLAN, COPY_PLAN, ADD_COMMAND, ADD_MONEY, ADD_ITEM_ID, ITEM_AMOUNT, MONEY_AMOUNT, EDIT_COMMAND, CHANCE, WEIGHT, UNLOCK, DRAW_COUNT, PITY }
}
